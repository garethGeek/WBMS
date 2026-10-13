package wbms.tanroads.wbms.capture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import wbms.tanroads.wbms.dtos.LiveCaptureStatusDto;
import wbms.tanroads.wbms.entities.WeighbridgeDevice;
import wbms.tanroads.wbms.enums.ConnectionType;
import wbms.tanroads.wbms.repositories.WeighbridgeDeviceRepository;
import wbms.tanroads.wbms.repositories.IndicatorProfileRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveCaptureService {

    private final WeighbridgeDeviceRepository deviceRepo;
    private final IndicatorProfileRepository profileRepo;

    private SerialWeightStreamer serialStreamer;
    private TcpWeightStreamer tcpStreamer;
    private volatile WeighbridgeDevice connectedDevice;

    /**
     * Starts streaming for the single "active" device (active=true, deleted=false).
     * Good for now while you keep one device active at a time.
     */
    public synchronized void startForActiveDevice() {
        WeighbridgeDevice device = deviceRepo.findFirstByActiveTrueAndDeletedFalseOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("No active device found"));

        log.info("WBMS LIVE CAPTURE: Active-device connect requested; selected {} ({})", device.getDeviceId(), device.getName());
        startForDevice(device);
    }

    /**
     * Starts streaming for a selected device by UUID (best approach for multi-gate setups).
     */
    public synchronized void startForDeviceUuid(String deviceUuid) {
        log.info("WBMS LIVE CAPTURE: Connect requested for device uuid={}", deviceUuid);
        WeighbridgeDevice device = deviceRepo.findByUuidAndDeletedFalse(deviceUuid)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        startForDevice(device);
    }

    private void startForDevice(WeighbridgeDevice device) {
        if (device.getIndicatorProfileUuid() != null && !device.getIndicatorProfileUuid().isBlank()) {
            profileRepo.findByUuidAndActiveTrue(device.getIndicatorProfileUuid()).ifPresent(profile -> {
                device.setProfileStartBytes(profile.getStartBytes());
                device.setProfileEndBytes(profile.getEndBytes());
                device.setProfileSeparator(profile.getSeparator());
                device.setProfileStringLength(profile.getStringLength());
                device.setProfileRemoveBeginning(profile.getRemoveBeginning());
                device.setProfileRemoveEnd(profile.getRemoveEnd());
            });
        }
        ConnectionType type = device.getConnectionType();
        if (type == null) {
            log.warn("WBMS LIVE CAPTURE: Device {} has no connection type configured", device.getDeviceId());
            throw new IllegalArgumentException("Device connection type is not set: " + device.getDeviceId());
        }

        if (connectedDevice != null && device.getUuid().equals(connectedDevice.getUuid()) && isConnectedStreamerRunning()) {
            log.info("WBMS: Device {} is already connected; keeping existing live stream", device.getDeviceId());
            return;
        }

        if (connectedDevice != null && !device.getUuid().equals(connectedDevice.getUuid())) {
            log.info("WBMS LIVE CAPTURE: Switching live stream from {} to {}", connectedDevice.getDeviceId(), device.getDeviceId());
        }
        stop(); // stop only when switching devices or replacing a dead stream

        if (type == ConnectionType.SERIAL) {
            log.info("WBMS LIVE CAPTURE: Opening SERIAL device {} ({}) on {}", device.getDeviceId(), device.getName(), device.getSerialPort());
            serialStreamer = new SerialWeightStreamer();
            serialStreamer.start(device);
            connectedDevice = device;
            log.info("WBMS LIVE CAPTURE: SERIAL device {} connected successfully", device.getDeviceId());
            return;
        }

        if (type == ConnectionType.TCP) {
            log.info("WBMS LIVE CAPTURE: Opening TCP device {} ({}) at {}:{}", device.getDeviceId(), device.getName(), device.getTcpHost(), device.getTcpPort());
            tcpStreamer = new TcpWeightStreamer();
            tcpStreamer.start(device);
            connectedDevice = device;
            log.info("WBMS LIVE CAPTURE: TCP device {} connected successfully", device.getDeviceId());
            return;
        }

        log.warn("WBMS LIVE CAPTURE: Device {} has unsupported connection type {}", device.getDeviceId(), type);
        throw new UnsupportedOperationException("Unsupported connection type: " + type);
    }

    public synchronized void stop() {
        String deviceId = connectedDevice != null ? connectedDevice.getDeviceId() : null;
        if (serialStreamer != null) {
            serialStreamer.stop();
            serialStreamer = null;
        }
        if (tcpStreamer != null) {
            tcpStreamer.stop();
            tcpStreamer = null;
        }
        connectedDevice = null;
        if (deviceId != null) {
            log.info("WBMS LIVE CAPTURE: Device {} disconnected", deviceId);
        }
    }

    public synchronized LiveCaptureStatusDto status() {
        if (connectedDevice == null || !isConnectedStreamerRunning()) {
            if (connectedDevice != null) {
                log.warn("WBMS LIVE CAPTURE: Connected device {} no longer has a running stream; marking disconnected", connectedDevice.getDeviceId());
            }
            connectedDevice = null;
            return LiveCaptureStatusDto.builder()
                    .connected(false)
                    .message("No device connected")
                    .build();
        }

        return LiveCaptureStatusDto.builder()
                .connected(true)
                .deviceUuid(connectedDevice.getUuid())
                .deviceId(connectedDevice.getDeviceId())
                .deviceName(connectedDevice.getName())
                .latestWeightKg(getLatestWeightKg())
                .bvrKg(getLatestWeightKg())
                .latestRawLine(getLatestRawLine())
                .latestCellWeightsKg(getLatestCellWeightsKg())
                .indicatorGvmKg(getLatestIndicatorGvmKg())
                .weightDisplayMode(connectedDevice.getWeightDisplayMode())
                .cellCount(connectedDevice.getCellCount())
                .latestReadAt(getLatestReadAt())
                .message("Device connected")
                .build();
    }

    public synchronized Optional<LiveReadingSnapshot> latestReadingForDevice(String deviceUuid) {
        if (connectedDevice == null || deviceUuid == null || !deviceUuid.equals(connectedDevice.getUuid()) || !isConnectedStreamerRunning()) {
            return Optional.empty();
        }

        Double kg = getLatestWeightKg();
        String rawLine = getLatestRawLine();
        Date readAt = getLatestReadAt();
        if (kg == null || rawLine == null || readAt == null) {
            return Optional.empty();
        }

        return Optional.of(new LiveReadingSnapshot(connectedDevice, kg, rawLine, readAt));
    }

    public synchronized Optional<LiveReadingSnapshot> latestReadingForConnectedDevice() {
        if (connectedDevice == null) {
            return Optional.empty();
        }
        return latestReadingForDevice(connectedDevice.getUuid());
    }

    private boolean isConnectedStreamerRunning() {
        if (connectedDevice == null || connectedDevice.getConnectionType() == null) {
            return false;
        }

        if (connectedDevice.getConnectionType() == ConnectionType.SERIAL) {
            return serialStreamer != null && serialStreamer.isRunning();
        }

        if (connectedDevice.getConnectionType() == ConnectionType.TCP) {
            return tcpStreamer != null && tcpStreamer.isRunning();
        }

        return false;
    }

    private Double getLatestWeightKg() {
        if (connectedDevice == null || connectedDevice.getConnectionType() == null) {
            return null;
        }
        if (connectedDevice.getConnectionType() == ConnectionType.SERIAL && serialStreamer != null) {
            return serialStreamer.getLatestWeightKg();
        }
        if (connectedDevice.getConnectionType() == ConnectionType.TCP && tcpStreamer != null) {
            return tcpStreamer.getLatestWeightKg();
        }
        return null;
    }

    private String getLatestRawLine() {
        if (connectedDevice == null || connectedDevice.getConnectionType() == null) {
            return null;
        }
        if (connectedDevice.getConnectionType() == ConnectionType.SERIAL && serialStreamer != null) {
            return serialStreamer.getLatestRawLine();
        }
        if (connectedDevice.getConnectionType() == ConnectionType.TCP && tcpStreamer != null) {
            return tcpStreamer.getLatestRawLine();
        }
        return null;
    }

    private Date getLatestReadAt() {
        if (connectedDevice == null || connectedDevice.getConnectionType() == null) {
            return null;
        }
        if (connectedDevice.getConnectionType() == ConnectionType.SERIAL && serialStreamer != null) {
            return serialStreamer.getLatestReadAt();
        }
        if (connectedDevice.getConnectionType() == ConnectionType.TCP && tcpStreamer != null) {
            return tcpStreamer.getLatestReadAt();
        }
        return null;
    }

    private Double getLatestIndicatorGvmKg() {
        if (connectedDevice == null || connectedDevice.getConnectionType() == null) return null;
        if (connectedDevice.getConnectionType() == ConnectionType.SERIAL && serialStreamer != null) return serialStreamer.getLatestIndicatorGvmKg();
        if (connectedDevice.getConnectionType() == ConnectionType.TCP && tcpStreamer != null) return tcpStreamer.getLatestIndicatorGvmKg();
        return null;
    }

    private List<Double> getLatestCellWeightsKg() {
        if (connectedDevice == null || connectedDevice.getConnectionType() == null) return List.of();
        if (connectedDevice.getConnectionType() == ConnectionType.SERIAL && serialStreamer != null) {
            return serialStreamer.getLatestCellWeightsKg();
        }
        if (connectedDevice.getConnectionType() == ConnectionType.TCP && tcpStreamer != null) {
            return tcpStreamer.getLatestCellWeightsKg();
        }
        return List.of();
    }

    public record LiveReadingSnapshot(WeighbridgeDevice device, Double weightKg, String rawLine, Date readAt) {
    }
}
