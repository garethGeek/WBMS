package wbms.tanroads.wbms.servicesImpl;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wbms.tanroads.wbms.capture.LiveCaptureService;
import wbms.tanroads.wbms.capture.IndicatorReadingParser;
import wbms.tanroads.wbms.capture.SerialDataDecoder;
import wbms.tanroads.wbms.capture.SerialPortConfiguration;
import wbms.tanroads.wbms.capture.WeightParser;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.entities.WeightReading;
import wbms.tanroads.wbms.entities.WeighbridgeDevice;
import wbms.tanroads.wbms.enums.ConnectionType;
import wbms.tanroads.wbms.repositories.WeightReadingRepository;
import wbms.tanroads.wbms.repositories.WeighbridgeDeviceRepository;
import wbms.tanroads.wbms.responseDtos.WeightReadingResponseDto;
import wbms.tanroads.wbms.services.WeightReadingService;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static wbms.tanroads.wbms.common.ResponseCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeightReadingServiceImpl implements WeightReadingService {

    private static final int DEFAULT_WAIT_SECONDS = 5;

    private final WeightReadingRepository readingRepo;
    private final WeighbridgeDeviceRepository deviceRepo;
    private final LiveCaptureService liveCaptureService;

    @Transactional
    @Override
    public Response<WeightReadingResponseDto> captureFromActiveDevice() {
        Response<WeightReadingResponseDto> res = new Response<>();

        try {
            WeighbridgeDevice device = deviceRepo.findFirstByActiveTrueAndDeletedFalseOrderByIdDesc()
                    .orElseThrow(() -> new RuntimeException("No active device found"));
            log.info("WBMS READING CAPTURE: Capture requested from active device {} ({})", device.getDeviceId(), device.getName());
            return liveCaptureService.latestReadingForConnectedDevice()
                    .map(this::saveLiveReading)
                    .orElseGet(() -> captureAndSave(device));
        } catch (Exception e) {
            log.warn("WBMS READING CAPTURE: Active-device capture failed - {}", e.getMessage());
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Capture failed: " + e.getMessage());
            return res;
        }
    }

    @Transactional
    @Override
    public Response<WeightReadingResponseDto> captureFromDevice(String deviceUuid) {
        Response<WeightReadingResponseDto> res = new Response<>();

        try {
            if (deviceUuid == null || deviceUuid.trim().isEmpty()) {
                res.setStatus(false);
                res.setCode(INVALID_REQUEST);
                res.setDescription("Device uuid is required");
                return res;
            }

            WeighbridgeDevice device = deviceRepo.findByUuidAndDeletedFalse(deviceUuid.trim())
                    .orElseThrow(() -> new RuntimeException("Device not found"));
            log.info("WBMS READING CAPTURE: Capture requested from device {} ({})", device.getDeviceId(), device.getName());
            return liveCaptureService.latestReadingForDevice(device.getUuid())
                    .map(this::saveLiveReading)
                    .orElseGet(() -> captureAndSave(device));
        } catch (Exception e) {
            log.warn("WBMS READING CAPTURE: Selected-device capture failed for uuid={} - {}", deviceUuid, e.getMessage());
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Capture failed: " + e.getMessage());
            return res;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Response<WeightReadingResponseDto> getLatestReading() {
        Response<WeightReadingResponseDto> res = new Response<>();

        try {
            WeightReading reading = readingRepo.findFirstByOrderByCreatedDateDesc().orElse(null);
            if (reading == null) {
                res.setStatus(false);
                res.setCode(NO_RECORD_FOUND);
                res.setDescription("No weight readings found");
                return res;
            }

            res.setStatus(true);
            res.setCode(SUCCESS);
            res.setDescription("Latest weight reading retrieved successfully");
            res.setData(toDto(reading));
            return res;
        } catch (Exception e) {
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Failed to get latest reading: " + e.getMessage());
            return res;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Response<List<WeightReadingResponseDto>> listRecentReadings(Integer pageNo, Integer pageSize) {
        Response<List<WeightReadingResponseDto>> res = new Response<>();

        try {
            int pNo = (pageNo == null || pageNo < 0) ? 0 : pageNo;
            int pSize = (pageSize == null || pageSize <= 0) ? 10 : pageSize;

            Pageable pageable = PageRequest.of(pNo, pSize);
            Page<WeightReading> page = readingRepo.findAllByOrderByCreatedDateDesc(pageable);

            if (page.getContent().isEmpty()) {
                res.setStatus(false);
                res.setCode(NO_RECORD_FOUND);
                res.setDescription("No weight readings found");
                res.setData(List.of());
                return res;
            }

            res.setStatus(true);
            res.setCode(SUCCESS);
            res.setDescription("Weight readings retrieved successfully");
            res.setData(page.getContent().stream().map(this::toDto).toList());
            return res;
        } catch (Exception e) {
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Failed to list readings: " + e.getMessage());
            res.setData(List.of());
            return res;
        }
    }

    private Response<WeightReadingResponseDto> captureAndSave(WeighbridgeDevice device) {
        log.info("WBMS READING CAPTURE: No live snapshot available; opening one-shot {} read for {} ({})",
                device.getConnectionType(), device.getDeviceId(), device.getName());
        CapturedWeight captured = readWeight(device);
        return saveReading(device, captured.weightKg(), captured.rawLine());
    }

    private Response<WeightReadingResponseDto> saveLiveReading(LiveCaptureService.LiveReadingSnapshot snapshot) {
        log.info("WBMS READING CAPTURE: Saving latest live snapshot from {} ({}) capturedAt={} raw='{}'",
                snapshot.device().getDeviceId(), snapshot.device().getName(), snapshot.readAt(), snapshot.rawLine());
        return saveReading(snapshot.device(), snapshot.weightKg(), snapshot.rawLine());
    }

    private Response<WeightReadingResponseDto> saveReading(WeighbridgeDevice device, Double weightKg, String rawLine) {
        Response<WeightReadingResponseDto> res = new Response<>();

        WeightReading reading = WeightReading.builder()
                .device(device)
                .weightKg(weightKg)
                .rawLine(rawLine)
                .build();
        reading.setCreatedBy("1");
        reading.setCreatedByUserName("System");
        reading.setCreatedDate(new Date());

        WeightReading saved = readingRepo.save(reading);
        log.info("WBMS READING CAPTURE: Saved reading {} kg for {} ({}) raw='{}'",
                saved.getWeightKg(), device.getDeviceId(), device.getName(), saved.getRawLine());

        res.setStatus(true);
        res.setCode(SUCCESS);
        res.setDescription("Weight captured and saved successfully");
        res.setData(toDto(saved));
        return res;
    }

    private CapturedWeight readWeight(WeighbridgeDevice device) {
        if (device.getConnectionType() == ConnectionType.SERIAL) {
            return readSerial(device);
        }
        if (device.getConnectionType() == ConnectionType.TCP) {
            return readTcp(device);
        }
        throw new IllegalArgumentException("Unsupported connection type: " + device.getConnectionType());
    }

    private CapturedWeight readSerial(WeighbridgeDevice device) {
        String portName = device.getSerialPort();
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("Serial port is missing for device " + device.getDeviceId());
        }

        SerialPort port = SerialPort.getCommPort(portName.trim());
        SerialPortConfiguration.apply(port, device.getBaudRate(), device.getDataBits(), device.getParity(),
                device.getStopBits(), device.getFlowControl());
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 250, 0);

        try {
            log.info("WBMS READING CAPTURE: Opening one-shot SERIAL read for {} ({}) on {}",
                    device.getDeviceId(), device.getName(), portName);
            if (!port.openPort()) {
                log.warn("WBMS READING CAPTURE: Failed to open serial port {} for {} ({})",
                        portName, device.getDeviceId(), device.getName());
                throw new RuntimeException("Failed to open serial port: " + portName);
            }

            try (InputStream in = port.getInputStream()) {
                SerialDataDecoder decoder = new SerialDataDecoder();
                byte[] buffer = new byte[1024];
                byte[] firstFrame = null;
                long deadline = System.nanoTime() + DEFAULT_WAIT_SECONDS * 1_000_000_000L;
                while (System.nanoTime() < deadline && firstFrame == null) {
                    int n = in.read(buffer);
                    if (n > 0) {
                        log.info("WBMS SERIAL RAW BYTES [{} @ {}]: hex={} text='{}'",
                                device.getDeviceId(), device.getSerialPort(),
                                SerialDataDecoder.hex(buffer, n), SerialDataDecoder.escaped(buffer, n));
                        var frames = decoder.append(buffer, n);
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
                String line = firstFrame == null ? null : SerialDataDecoder.text(firstFrame);
                log.info("WBMS READING CAPTURE: One-shot SERIAL read from {} returned raw='{}'", device.getDeviceId(), line);
                return parseCapturedLine(line, device, "No serial data received within " + DEFAULT_WAIT_SECONDS + " seconds");
            }
        } catch (Exception e) {
            log.warn("WBMS READING CAPTURE: One-shot SERIAL capture failed for {} ({}) - {}",
                    device.getDeviceId(), device.getName(), e.getMessage());
            throw new RuntimeException("Serial capture failed: " + e.getMessage(), e);
        } finally {
            try {
                if (port.isOpen()) port.closePort();
            } catch (Exception ignored) {}
        }
    }

    private CapturedWeight readTcp(WeighbridgeDevice device) {
        String host = device.getTcpHost();
        Integer port = device.getTcpPort();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("TCP host/IP is missing for device " + device.getDeviceId());
        }
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException("TCP port is invalid for device " + device.getDeviceId());
        }

        try (Socket socket = new Socket()) {
            log.info("WBMS READING CAPTURE: Opening one-shot TCP read for {} ({}) at {}:{}",
                    device.getDeviceId(), device.getName(), host, port);
            socket.connect(new InetSocketAddress(host.trim(), port), 3000);
            socket.setSoTimeout(DEFAULT_WAIT_SECONDS * 1000);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line = reader.readLine();
                log.info("WBMS READING CAPTURE: One-shot TCP read from {} returned raw='{}'", device.getDeviceId(), line);
                return parseCapturedLine(line, device, "No TCP data received within " + DEFAULT_WAIT_SECONDS + " seconds");
            }
        } catch (Exception e) {
            log.warn("WBMS READING CAPTURE: One-shot TCP capture failed for {} ({}) at {}:{} - {}",
                    device.getDeviceId(), device.getName(), host, port, e.getMessage());
            throw new RuntimeException("TCP capture failed: " + e.getMessage(), e);
        }
    }

    private CapturedWeight parseCapturedLine(String line, WeighbridgeDevice device, String emptyMessage) {
        if (line == null || line.trim().isEmpty()) {
            throw new RuntimeException(emptyMessage);
        }

        String rawLine = line.trim();
        Double kg = IndicatorReadingParser.parseWeightKg(rawLine, device.getProtocolType());
        if (kg == null) {
            log.warn("WBMS READING CAPTURE: Parser could not extract kg value from raw='{}'", rawLine);
            throw new RuntimeException("Could not parse weight from device output: " + rawLine);
        }

        return new CapturedWeight(kg, rawLine);
    }

    private WeightReadingResponseDto toDto(WeightReading reading) {
        WeighbridgeDevice device = reading.getDevice();
        return WeightReadingResponseDto.builder()
                .uuid(reading.getUuid())
                .deviceUuid(device.getUuid())
                .deviceId(device.getDeviceId())
                .deviceName(device.getName())
                .weightKg(reading.getWeightKg())
                .rawLine(reading.getRawLine())
                .capturedAt(reading.getCreatedDate())
                .build();
    }

    private record CapturedWeight(Double weightKg, String rawLine) {
    }
}
