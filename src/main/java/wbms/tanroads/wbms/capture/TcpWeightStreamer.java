package wbms.tanroads.wbms.capture;

import lombok.extern.slf4j.Slf4j;
import wbms.tanroads.wbms.entities.WeighbridgeDevice;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
public class TcpWeightStreamer {

    private volatile boolean running = false;
    private Socket socket;
    private volatile Double latestWeightKg;
    private volatile String latestRawLine;
    private volatile Date latestReadAt;
    private volatile List<Double> latestCellWeightsKg = List.of();
    private volatile Double latestIndicatorGvmKg;

    public void start(WeighbridgeDevice device) {

        String host = device.getTcpHost();
        Integer port = device.getTcpPort();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("TCP host/IP is missing for device " + device.getDeviceId());
        }
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException("TCP port is invalid for device " + device.getDeviceId());
        }

        try {
            socket = new Socket();
            log.info("WBMS TCP CONNECT: Trying {} ({}) at {}:{}", device.getDeviceId(), device.getName(), host, port);
            socket.connect(new InetSocketAddress(host.trim(), port), 3000); // 3s connect timeout
            socket.setSoTimeout(250);
        } catch (Exception e) {
            log.warn("WBMS TCP CONNECT: Failed for {} ({}) at {}:{} - {}", device.getDeviceId(), device.getName(), host, port, e.getMessage());
            throw new RuntimeException("Failed to connect TCP device " + host + ":" + port + " - " + e.getMessage(), e);
        }

        running = true;
        new Thread(() -> readLoop(device), "wbms-tcp-stream-" + device.getDeviceId()).start();

        log.info("WBMS TCP CONNECT: Connected to {} ({}) at {}:{}; live streaming started", device.getDeviceId(), device.getName(), host, port);
    }

    private void readLoop(WeighbridgeDevice device) {
        try {
            try (InputStream in = socket.getInputStream()) {
                byte[] buffer = new byte[1024];
                SerialDataDecoder decoder = new SerialDataDecoder(!ProfileFrameSupport.assigned(device));
                CustomFrameDecoder profileDecoder = ProfileFrameSupport.enabled(device) ? new CustomFrameDecoder(device.getProfileStartBytes(), device.getProfileEndBytes()) : null;
                while (running) {
                    try {
                        int n = in.read(buffer);
                        if (n < 0) break;
                        if (n > 0) {
                            log.info("TCP RAW BYTES [{} @ {}:{}]: hex={} text='{}'",
                                    device.getDeviceId(), device.getTcpHost(), device.getTcpPort(),
                                    SerialDataDecoder.hex(buffer, n), SerialDataDecoder.escaped(buffer, n));
                            processFrames(device, profileDecoder != null ? profileDecoder.append(buffer, n) : decoder.append(buffer, n));
                        }
                    } catch (SocketTimeoutException timeout) {
                        processFrames(device, decoder.flush());
                    }
                }
                processFrames(device, decoder.flush());
            }

        } catch (Exception e) {
            log.error("WBMS: TCP stream error for device {}: {}", device.getDeviceId(), e.getMessage(), e);
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        log.info("WBMS TCP CONNECT: TCP streaming stopped");
    }

    private void processFrames(WeighbridgeDevice device, List<byte[]> frames) {
        for (byte[] frame : frames) {
            String line = ProfileFrameSupport.enabled(device) ? ProfileFrameSupport.payload(device, frame) : SerialDataDecoder.text(frame);
            if (ProfileFrameSupport.assigned(device) && !ProfileFrameSupport.enabled(device)
                    && ProfileFrameSupport.containsControlFraming(line)) continue;
            if (line.isBlank()) continue;

            Double kg = IndicatorReadingParser.parseWeightKg(line, device.getProtocolType());
            MultiCellWeightParser.MultiCellReading multi = device.getWeightDisplayMode() == wbms.tanroads.wbms.enums.WeightDisplayMode.MULTI_CELL
                    ? MultiCellWeightParser.parseWithGvm(line, device.getCellCount() == null ? 4 : device.getCellCount(),
                        device.getProfileSeparator(), ProfileFrameSupport.enabled(device))
                    : new MultiCellWeightParser.MultiCellReading(List.of(), null);
            if (!multi.cellWeightsKg().isEmpty()) {
                latestCellWeightsKg = multi.cellWeightsKg();
                latestIndicatorGvmKg = multi.indicatorGvmKg();
                latestWeightKg = multi.indicatorGvmKg() != null && multi.indicatorGvmKg() > 0
                        ? multi.indicatorGvmKg()
                        : multi.cellWeightsKg().stream().mapToDouble(Double::doubleValue).sum();
                latestRawLine = line;
                latestReadAt = new Date();
                log.info("LIVE MULTI-CELL WEIGHT TCP [{} @ {}:{}]: cells={} total={} kg | raw='{}'",
                        device.getDeviceId(), device.getTcpHost(), device.getTcpPort(), multi.cellWeightsKg(), latestWeightKg, line);
            } else if (kg != null) {
                latestWeightKg = kg;
                latestCellWeightsKg = List.of();
                latestIndicatorGvmKg = null;
                latestRawLine = line;
                latestReadAt = new Date();
                log.info("LIVE WEIGHT TCP [{} @ {}:{}]: {} kg | raw='{}'",
                        device.getDeviceId(), device.getTcpHost(), device.getTcpPort(), kg, line);
            } else {
                log.debug("RAW TCP LINE [{}]: {}", device.getDeviceId(), line);
            }
        }
    }

    public boolean isRunning() {
        return running && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public Double getLatestWeightKg() {
        return latestWeightKg;
    }

    public String getLatestRawLine() {
        return latestRawLine;
    }

    public Date getLatestReadAt() {
        return latestReadAt;
    }

    public List<Double> getLatestCellWeightsKg() {
        return latestCellWeightsKg;
    }

    public Double getLatestIndicatorGvmKg() {
        return latestIndicatorGvmKg;
    }
}
