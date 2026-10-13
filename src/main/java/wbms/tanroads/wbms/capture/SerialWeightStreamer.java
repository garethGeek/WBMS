package wbms.tanroads.wbms.capture;

import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import wbms.tanroads.wbms.entities.WeighbridgeDevice;
import wbms.tanroads.wbms.enums.ConnectionType;

import java.io.InputStream;
import java.util.List;
import java.util.Date;

@Slf4j
public class SerialWeightStreamer {

    private SerialPort port;
    private volatile boolean running = false;
    private volatile Double latestWeightKg;
    private volatile String latestRawLine;
    private volatile List<Double> latestCellWeightsKg = List.of();
    private volatile Double latestIndicatorGvmKg;
    private volatile Date latestReadAt;

    public void start(WeighbridgeDevice device) {
        if (device.getConnectionType() != ConnectionType.SERIAL) {
            throw new IllegalArgumentException("Device is not SERIAL");
        }

        String portName = device.getSerialPort();
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("Serial port is missing for device " + device.getDeviceId());
        }

        port = SerialPort.getCommPort(portName);
        SerialPortConfiguration.apply(port, device.getBaudRate(), device.getDataBits(),
                device.getParity(), device.getStopBits(), device.getFlowControl());

        // A short timeout lets us flush indicators that do not send CR/LF.
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 250, 0);

        log.info("WBMS SERIAL CONNECT: Trying {} ({}) on {}", device.getDeviceId(), device.getName(), portName);
        if (!port.openPort()) {
            log.warn("WBMS SERIAL CONNECT: Failed to open {} for {} ({})", portName, device.getDeviceId(), device.getName());
            throw new RuntimeException("Failed to open serial port: " + portName);
        }

        running = true;
        log.info("WBMS SERIAL CONNECT: Connected to {} ({}) on {}; live streaming started", device.getDeviceId(), device.getName(), portName);

        new Thread(() -> readLoop(device), "wbms-serial-stream-" + device.getDeviceId()).start();
    }

    private void readLoop(WeighbridgeDevice device) {
        try (InputStream in = port.getInputStream()) {
            byte[] buffer = new byte[1024];
            SerialDataDecoder decoder = new SerialDataDecoder(!ProfileFrameSupport.assigned(device));
            CustomFrameDecoder profileDecoder = ProfileFrameSupport.enabled(device) ? new CustomFrameDecoder(device.getProfileStartBytes(), device.getProfileEndBytes()) : null;

            while (running) {
                int n = in.read(buffer);
                if (n <= 0) {
                    processFrames(device, decoder.flush());
                    continue;
                }

                log.info("SERIAL RAW BYTES [{} @ {}]: hex={} text='{}'",
                        device.getDeviceId(), device.getSerialPort(),
                        SerialDataDecoder.hex(buffer, n), SerialDataDecoder.escaped(buffer, n));
                processFrames(device, profileDecoder != null ? profileDecoder.append(buffer, n) : decoder.append(buffer, n));
            }
        } catch (Exception e) {
            log.error("WBMS: Serial stream error for device {}: {}", device.getDeviceId(), e.getMessage(), e);
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;
        try {
            if (port != null && port.isOpen()) port.closePort();
        } catch (Exception ignored) {}
        log.info("WBMS SERIAL CONNECT: Serial streaming stopped");
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
                log.info("LIVE MULTI-CELL WEIGHT [{} @ {}]: cells={} total={} kg | raw='{}'",
                        device.getDeviceId(), device.getSerialPort(), multi.cellWeightsKg(), latestWeightKg, line);
            } else if (kg != null) {
                latestWeightKg = kg;
                latestCellWeightsKg = List.of();
                latestIndicatorGvmKg = null;
                latestRawLine = line;
                latestReadAt = new Date();
                log.info("LIVE WEIGHT [{} @ {}]: {} kg | raw='{}'",
                        device.getDeviceId(), device.getSerialPort(), kg, line);
            } else {
                log.warn("SERIAL UNPARSED DATA [{} @ {}]: raw='{}' hex={}",
                        device.getDeviceId(), device.getSerialPort(), line,
                        SerialDataDecoder.hex(frame, frame.length));
            }
        }
    }

    public boolean isRunning() {
        return running && port != null && port.isOpen();
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
