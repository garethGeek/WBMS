package wbms.tanroads.wbms.capture;

import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import wbms.tanroads.wbms.dtos.DeviceConnectionTestDto;
import wbms.tanroads.wbms.dtos.DeviceConnectionTestResultDto;
import wbms.tanroads.wbms.enums.ConnectionType;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
public class DeviceConnectionTester {

    public DeviceConnectionTestResultDto test(DeviceConnectionTestDto dto) {

        int wait = (dto.getWaitSeconds() == null || dto.getWaitSeconds() <= 0) ? 5 : dto.getWaitSeconds();
        log.info("WBMS DEVICE DETECTION: Starting {} connection test with wait={}s", dto.getConnectionType(), wait);

        if (dto.getConnectionType() == ConnectionType.SERIAL) {
            return testSerial(dto, wait);
        }

        if (dto.getConnectionType() == ConnectionType.TCP) {
            return testTcp(dto, wait);
        }

        return DeviceConnectionTestResultDto.builder()
                .connected(false)
                .dataReceived(false)
                .message("Unsupported connection type")
                .build();
    }

    private DeviceConnectionTestResultDto testTcp(DeviceConnectionTestDto dto, int waitSeconds) {
        String host = dto.getTcpHost();
        Integer port = dto.getTcpPort();
        if (host == null || host.isBlank()) {
            log.warn("WBMS DEVICE DETECTION: TCP test rejected because host/IP is empty");
            return fail("TCP Host/IP is required (e.g. 192.168.1.50)");
        }
        if (port == null || port < 1 || port > 65535) {
            log.warn("WBMS DEVICE DETECTION: TCP test rejected for {} because port is invalid: {}", host, port);
            return fail("TCP Port is required and must be between 1 and 65535 (e.g. 4001)");
        }

        String cleanHost = host.trim();
        log.info("WBMS DEVICE DETECTION: Testing TCP indicator at {}:{} for readable data", cleanHost, port);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(cleanHost, port), 3000); // 3s connect timeout
            boolean profileTest = Boolean.TRUE.equals(dto.getProfileTest());
            socket.setSoTimeout(profileTest ? 250 : waitSeconds * 1000);

            try (InputStream in = socket.getInputStream()) {
                byte[] buffer = new byte[1024];
                long deadline = System.nanoTime() + waitSeconds * 1_000_000_000L;
                java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
                CustomFrameDecoder framedDecoder = hasCustomFraming(dto) ? new CustomFrameDecoder(dto.getStartBytes(), dto.getEndBytes()) : null;
                while (System.nanoTime() < deadline) {
                    try {
                        int n = in.read(buffer);
                        if (n > 0) {
                            raw.write(buffer, 0, n);
                            if (framedDecoder != null) {
                                var frames = framedDecoder.append(buffer, n);
                                if (!frames.isEmpty()) return success(new String(frames.get(0), StandardCharsets.US_ASCII), frames.get(0), dto);
                            } else if (raw.size() > 0 && (new String(raw.toByteArray(), StandardCharsets.US_ASCII).contains("\r") || new String(raw.toByteArray(), StandardCharsets.US_ASCII).contains("\n"))) {
                                return success(new String(raw.toByteArray(), StandardCharsets.US_ASCII).trim(), raw.toByteArray(), dto);
                            }
                        }
                    } catch (java.net.SocketTimeoutException ignored) {
                        if (!profileTest || raw.size() > 0) break;
                    }
                }
                if (raw.size() > 0) return success(new String(raw.toByteArray(), StandardCharsets.US_ASCII).trim(), raw.toByteArray(), dto);

                log.warn("WBMS DEVICE DETECTION: TCP opened at {}:{} but returned an empty line", cleanHost, port);
                return DeviceConnectionTestResultDto.builder()
                        .connected(true)
                        .dataReceived(false)
                        .message("Connected  but no output received ⚠️ (device may not be configured to stream)")
                        .build();
            }

        } catch (java.net.ConnectException e) {
            log.warn("WBMS DEVICE DETECTION: TCP connection refused at {}:{} - {}", cleanHost, port, e.getMessage());
            return fail("Connection refused  (wrong port or port is closed on device)");
        } catch (java.net.SocketTimeoutException e) {
            log.warn("WBMS DEVICE DETECTION: TCP opened at {}:{} but no data arrived within {}s", cleanHost, port, waitSeconds);
            return DeviceConnectionTestResultDto.builder()
                    .connected(true)
                    .dataReceived(false)
                    .message("Connected  but no output within " + waitSeconds + " seconds ⚠️ (device not streaming)")
                    .build();
        } catch (Exception e) {
            log.warn("WBMS DEVICE DETECTION: TCP indicator not reachable at {}:{} - {}", cleanHost, port, e.getMessage());
            return fail("Not reachable  (wrong IP/network/firewall). Details: " + e.getMessage());
        }
    }

    private DeviceConnectionTestResultDto testSerial(DeviceConnectionTestDto dto, int waitSeconds) {
        String portName = dto.getSerialPort();
        if (portName == null || portName.isBlank()) {
            log.warn("WBMS DEVICE DETECTION: Serial test rejected because COM port is empty");
            return fail("Serial COM port is required (e.g. COM5)");
        }

        SerialPort port = null;
        String cleanPortName = portName.trim();
        log.info("WBMS DEVICE DETECTION: Testing serial indicator on {} for readable data", cleanPortName);

        try {
            port = SerialPort.getCommPort(cleanPortName);
            SerialPortConfiguration.apply(port, dto.getBaudRate(), dto.getDataBits(), dto.getParity(),
                    dto.getStopBits(), dto.getFlowControl());
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 250, 0);

            if (!port.openPort()) {
                log.warn("WBMS DEVICE DETECTION: Failed to open serial port {}", cleanPortName);
                return fail("Failed to open port  (port in use or not available): " + portName);
            }

            try (InputStream in = port.getInputStream()) {
                SerialDataDecoder decoder = new SerialDataDecoder();
                CustomFrameDecoder framedDecoder = hasCustomFraming(dto) ? new CustomFrameDecoder(dto.getStartBytes(), dto.getEndBytes()) : null;
                java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                byte[] firstFrame = null;
                long deadline = System.nanoTime() + waitSeconds * 1_000_000_000L;
                while (System.nanoTime() < deadline && firstFrame == null) {
                    int n = in.read(buffer);
                    if (n > 0) {
                        captured.write(buffer, 0, n);
                        log.info("SERIAL TEST RAW BYTES [{}]: hex={} text='{}'", cleanPortName,
                                SerialDataDecoder.hex(buffer, n), SerialDataDecoder.escaped(buffer, n));
                        var frames = framedDecoder != null ? framedDecoder.append(buffer, n) : decoder.append(buffer, n);
                        if (!frames.isEmpty()) firstFrame = frames.get(0);
                    } else {
                        var frames = decoder.flush();
                        if (!frames.isEmpty()) firstFrame = frames.get(0);
                    }
                }
                if (firstFrame == null) {
                    var frames = decoder.flush();
                    if (!frames.isEmpty()) firstFrame = frames.get(0);
                }
                if (Boolean.TRUE.equals(dto.getProfileTest()) && captured.size() > 0) {
                    firstFrame = captured.toByteArray();
                }
                String line = firstFrame == null ? null : SerialDataDecoder.text(firstFrame);

                if (line != null && !line.trim().isBlank()) {
                    log.info("WBMS DEVICE DETECTION: Serial indicator detected on {} with sample='{}'", cleanPortName, line.trim());
                    return success(line.trim(), firstFrame, dto);
                }

                log.warn("WBMS DEVICE DETECTION: Serial port {} opened but no data arrived within {}s", cleanPortName, waitSeconds);
                return DeviceConnectionTestResultDto.builder()
                        .connected(true)
                        .dataReceived(false)
                        .message("Port opened  but no output within " + waitSeconds + " seconds ⚠️ (device not streaming / wrong baud rate)")
                        .build();
            }

        } catch (Exception e) {
            log.warn("WBMS DEVICE DETECTION: Serial indicator test failed on {} - {}", cleanPortName, e.getMessage());
            return fail("Serial test failed : " + e.getMessage());
        } finally {
            try {
                if (port != null && port.isOpen()) port.closePort();
            } catch (Exception ignored) {}
        }
    }

    private DeviceConnectionTestResultDto fail(String msg) {
        return DeviceConnectionTestResultDto.builder()
                .connected(false)
                .dataReceived(false)
                .message(msg)
                .build();
    }

    private boolean hasCustomFraming(DeviceConnectionTestDto dto) {
        return dto.getStartBytes() != null && !dto.getStartBytes().isBlank()
                && dto.getEndBytes() != null && !dto.getEndBytes().isBlank();
    }

    private DeviceConnectionTestResultDto success(String sample, byte[] raw, DeviceConnectionTestDto dto) {
        boolean profileTest = Boolean.TRUE.equals(dto.getProfileTest());
        String payload = profileTest ? parserPayload(raw, dto) : sample;
        boolean formatMatched = !profileTest || payload != null;
        if (payload == null) payload = "";
        if (!profileTest && hasCustomFraming(dto)) {
            int startLength = CustomFrameDecoder.parseHex(dto.getStartBytes()).length;
            int endLength = CustomFrameDecoder.parseHex(dto.getEndBytes()).length;
            if (payload.length() >= startLength + endLength) {
                payload = payload.substring(startLength, payload.length() - endLength);
            }
        }
        if (dto.getRemoveBeginning() != null && dto.getRemoveBeginning() > 0 && payload.length() > dto.getRemoveBeginning()) payload = payload.substring(dto.getRemoveBeginning());
        if (dto.getRemoveEnd() != null && dto.getRemoveEnd() > 0 && payload.length() > dto.getRemoveEnd()) payload = payload.substring(0, payload.length() - dto.getRemoveEnd());
        if (dto.getStringLength() != null && dto.getStringLength() > 0 && payload.length() > dto.getStringLength()) payload = payload.substring(0, dto.getStringLength());
        Double weight = IndicatorReadingParser.parseWeightKg(payload, dto.getProtocolType());
        var multi = dto.getCellCount() != null && dto.getCellCount() > 1
                ? MultiCellWeightParser.parseWithGvm(payload, dto.getCellCount(), dto.getSeparator(), profileTest)
                : new MultiCellWeightParser.MultiCellReading(java.util.List.of(), null);
        var cells = multi.cellWeightsKg();
        if (dto.getCellCount() != null && dto.getCellCount() > 1) {
            // Multi-cell profiles must not fall back to treating the whole
            // concatenated frame as one numeric weight.
            weight = null;
        }
        if (!cells.isEmpty()) {
            weight = multi.indicatorGvmKg() != null && multi.indicatorGvmKg() > 0
                    ? multi.indicatorGvmKg()
                    : cells.stream().mapToDouble(Double::doubleValue).sum();
        }
        String message = profileTest && !formatMatched
                ? "Connected and raw data received, but the incoming frame does not match this profile"
                : profileTest && weight == null
                ? "Connected and raw data received, but no numeric weight was found in the payload"
                : "Connected and data received";
        return DeviceConnectionTestResultDto.builder()
                .connected(true)
                .dataReceived(true)
                .message(message)
                .sampleData(profileTest ? asciiPreview(raw) : asciiPreview(adjustedPreviewBytes(payload, dto)))
                .rawHex(SerialDataDecoder.hex(raw, raw.length))
                .parserInput(profileTest ? asciiPreview(payload.getBytes(StandardCharsets.ISO_8859_1)) : null)
                .parsedWeightKg(weight)
                .parsedCellWeightsKg(cells)
                .build();
    }

    private String parserPayload(byte[] raw, DeviceConnectionTestDto dto) {
        if (!hasCustomFraming(dto)) {
            if (raw.length > 0 && (raw[0] & 0xff) == 0x02) return null;
            return new String(raw, StandardCharsets.ISO_8859_1);
        }
        byte[] start = CustomFrameDecoder.parseHex(dto.getStartBytes());
        byte[] end = CustomFrameDecoder.parseHex(dto.getEndBytes());
        if (raw.length < start.length + end.length) return null;
        for (int i = 0; i < start.length; i++) if (raw[i] != start[i]) return null;
        for (int i = start.length; i <= raw.length - end.length; i++) {
            boolean matches = true;
            for (int j = 0; j < end.length; j++) {
                if (raw[i + j] != end[j]) { matches = false; break; }
            }
            if (matches) return new String(raw, start.length, i - start.length, StandardCharsets.ISO_8859_1);
        }
        return null;
    }

    private byte[] adjustedPreviewBytes(String payload, DeviceConnectionTestDto dto) {
        byte[] adjustedPayload = payload.getBytes(StandardCharsets.ISO_8859_1);
        if (!hasCustomFraming(dto)) return adjustedPayload;
        byte[] start = CustomFrameDecoder.parseHex(dto.getStartBytes());
        byte[] end = CustomFrameDecoder.parseHex(dto.getEndBytes());
        byte[] framed = new byte[start.length + adjustedPayload.length + end.length];
        System.arraycopy(start, 0, framed, 0, start.length);
        System.arraycopy(adjustedPayload, 0, framed, start.length, adjustedPayload.length);
        System.arraycopy(end, 0, framed, start.length + adjustedPayload.length, end.length);
        return framed;
    }

    private String asciiPreview(byte[] raw) {
        String escaped = SerialDataDecoder.escaped(raw, raw.length);
        return escaped.replace("\\x01", "<SOH>")
                .replace("\\x02", "<STX>")
                .replace("\\x03", "<ETX>");
    }
}
