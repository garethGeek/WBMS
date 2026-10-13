package wbms.tanroads.wbms.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.dtos.WeighbridgeDeviceCreateDto;
import wbms.tanroads.wbms.entities.WeighbridgeDevice;
import wbms.tanroads.wbms.enums.*;
import wbms.tanroads.wbms.repositories.WeighbridgeDeviceRepository;
import wbms.tanroads.wbms.responseDtos.WeighbridgeDeviceResponseDto;
import wbms.tanroads.wbms.services.WeighbridgeDeviceService;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static wbms.tanroads.wbms.common.ResponseCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeighbridgeDeviceServiceImpl implements WeighbridgeDeviceService {

    private final WeighbridgeDeviceRepository repo;

    @Transactional
    @Override
    public Response<WeighbridgeDeviceResponseDto> register(WeighbridgeDeviceCreateDto dto, BindingResult result) {

        Response<WeighbridgeDeviceResponseDto> res = new Response<>();

        try {
            // 1) Validation errors from DTO annotations
            if (result.hasErrors()) {
                res.setCode(INVALID_REQUEST);
                res.setStatus(false);
                res.setDescription("Something went wrong: " + result.getAllErrors().get(0).getDefaultMessage());

                return res;
            }

            // 2) Trim important fields
            dto.setDeviceId(dto.getDeviceId().trim());
            dto.setName(dto.getName().trim());

            // 3) Duplicate check
            if (repo.existsByDeviceIdIgnoreCase(dto.getDeviceId())) {
                res.setCode(DUPLICATE);
                res.setStatus(false);
                res.setDescription("Device ID already exists: " + dto.getDeviceId());
                log.warn("WBMS DEVICE REGISTRY: Duplicate device ID rejected: {}", dto.getDeviceId());
                return res;
            }

            // 4) Conditional validation based on connection type (user-friendly)
            if (dto.getConnectionType() == ConnectionType.SERIAL) {
                if (dto.getSerialPort() == null || dto.getSerialPort().trim().isEmpty()) {
                    res.setCode(BAD_REQUEST);
                    res.setStatus(false);
                    res.setDescription("Serial port is required when connection type is SERIAL (e.g. COM5)");
                    log.warn("WBMS DEVICE REGISTRY: SERIAL registration rejected for {} because COM port is missing", dto.getDeviceId());
                    return res;
                }
            } else if (dto.getConnectionType() == ConnectionType.TCP) {
                if (dto.getTcpHost() == null || dto.getTcpHost().trim().isEmpty()) {
                    res.setCode(BAD_REQUEST);
                    res.setStatus(false);
                    res.setDescription("TCP Host/IP is required when connection type is TCP (e.g. 192.168.1.50)");
                    log.warn("WBMS DEVICE REGISTRY: TCP registration rejected for {} because host/IP is missing", dto.getDeviceId());
                    return res;
                }
                if (dto.getTcpPort() == null || dto.getTcpPort() < 1 || dto.getTcpPort() > 65535) {
                    res.setCode(BAD_REQUEST);
                    res.setStatus(false);
                    res.setDescription("TCP Port is required and must be between 1 and 65535 (e.g. 4001)");
                    log.warn("WBMS DEVICE REGISTRY: TCP registration rejected for {} because port is invalid: {}", dto.getDeviceId(), dto.getTcpPort());
                    return res;
                }
            } else {
                res.setCode(BAD_REQUEST);
                res.setStatus(false);
                res.setDescription("Invalid connection type");
                log.warn("WBMS DEVICE REGISTRY: Registration rejected for {} because connection type is invalid: {}", dto.getDeviceId(), dto.getConnectionType());
                return res;
            }

            // 5) Build Entity with defaults (simple for non-professional users)
            WeighbridgeDevice device = WeighbridgeDevice.builder()
                    .deviceId(dto.getDeviceId())
                    .name(dto.getName())
                    .connectionType(dto.getConnectionType())

                    // SERIAL
                    .serialPort(dto.getSerialPort() != null ? dto.getSerialPort().trim() : null)
                    .baudRate(dto.getBaudRate() != null ? dto.getBaudRate() : 9600)
                    .dataBits(dto.getDataBits() != null ? dto.getDataBits() : 8)
                    .parity(dto.getParity() != null ? dto.getParity() : ParityMode.NONE)
                    .flowControl(dto.getFlowControl() != null ? dto.getFlowControl() : FlowControlMode.NONE)
                    .stopBits(dto.getStopBits() != null ? dto.getStopBits() : StopBitsMode.ONE)
                    .protocolType(dto.getProtocolType() != null ? dto.getProtocolType() : WeighbridgeProtocolType.GENERIC_NUMERIC)
                    .weightDisplayMode(dto.getWeightDisplayMode() != null ? dto.getWeightDisplayMode() : WeightDisplayMode.SINGLE_CELL)
                    .cellCount(dto.getCellCount() != null ? dto.getCellCount() : defaultCellCount(dto.getWeightDisplayMode()))
                    .indicatorProfileUuid(dto.getIndicatorProfileUuid())

                    // TCP
                    .tcpHost(dto.getTcpHost() != null ? dto.getTcpHost().trim() : null)
                    .tcpPort(dto.getTcpPort())

                    .active(dto.getActive() != null ? dto.getActive() : true)
                    .build();
            device.setCreatedBy("1");
            device.setCreatedByUserName("System");
            device.setCreatedDate(new Date());

            // 6) Save
            WeighbridgeDevice saved = repo.save(device);

            // 7) Map to response DTO
            WeighbridgeDeviceResponseDto out = WeighbridgeDeviceResponseDto.builder()
                    .uuid(saved.getUuid())
                    .deviceId(saved.getDeviceId())
                    .name(saved.getName())
                    .connectionType(saved.getConnectionType())
                    .serialPort(saved.getSerialPort())
                    .baudRate(saved.getBaudRate())
                    .dataBits(saved.getDataBits())
                    .parity(saved.getParity())
                    .flowControl(saved.getFlowControl())
                    .stopBits(saved.getStopBits())
                    .protocolType(saved.getProtocolType())
                    .tcpHost(saved.getTcpHost())
                    .tcpPort(saved.getTcpPort())
                    .active(saved.getActive())
                    .indicatorProfileUuid(saved.getIndicatorProfileUuid())
                    .build();

            res.setCode(SUCCESS);
            res.setStatus(true);
            res.setDescription("Weighbridge device registered successfully");
            log.info("WBMS DEVICE REGISTRY: Registered device {} ({}) as {} endpoint={}",
                    saved.getDeviceId(), saved.getName(), saved.getConnectionType(), endpoint(saved));
            res.setData(out);
            return res;

        } catch (Exception e) {
            res.setCode(FAILURE);
            res.setStatus(false);
            res.setDescription("Error while creating weighbridge device: " + e.getMessage());
            log.error("WBMS DEVICE REGISTRY: Error while creating device {} - {}", dto != null ? dto.getDeviceId() : null, e.getMessage(), e);
            return res;
        }
    }

    @Transactional
    @Override
    public Response<WeighbridgeDeviceResponseDto> update(String uuid, WeighbridgeDeviceCreateDto dto, BindingResult result) {
        Response<WeighbridgeDeviceResponseDto> res = new Response<>();

        try {
            if (uuid == null || uuid.trim().isEmpty()) {
                res.setCode(INVALID_REQUEST);
                res.setStatus(false);
                res.setDescription("Device uuid is required");
                return res;
            }

            if (result.hasErrors()) {
                res.setCode(INVALID_REQUEST);
                res.setStatus(false);
                res.setDescription("Something went wrong: " + result.getAllErrors().get(0).getDefaultMessage());
                return res;
            }

            WeighbridgeDevice device = repo.findByUuidAndDeletedFalse(uuid.trim()).orElse(null);
            if (device == null) {
                res.setCode(NO_RECORD_FOUND);
                res.setStatus(false);
                res.setDescription("Weighbridge device not found");
                return res;
            }

            dto.setDeviceId(dto.getDeviceId().trim());
            dto.setName(dto.getName().trim());

            WeighbridgeDevice duplicate = repo.findByDeviceIdIgnoreCase(dto.getDeviceId()).orElse(null);
            if (duplicate != null && !duplicate.getUuid().equals(device.getUuid())) {
                res.setCode(DUPLICATE);
                res.setStatus(false);
                res.setDescription("Device ID already exists: " + dto.getDeviceId());
                log.warn("WBMS DEVICE REGISTRY: Update rejected for uuid={} because device ID already exists: {}", uuid, dto.getDeviceId());
                return res;
            }

            if (dto.getConnectionType() == ConnectionType.SERIAL) {
                if (dto.getSerialPort() == null || dto.getSerialPort().trim().isEmpty()) {
                    res.setCode(BAD_REQUEST);
                    res.setStatus(false);
                    res.setDescription("Serial port is required when connection type is SERIAL (e.g. COM5)");
                    return res;
                }
            } else if (dto.getConnectionType() == ConnectionType.TCP) {
                if (dto.getTcpHost() == null || dto.getTcpHost().trim().isEmpty()) {
                    res.setCode(BAD_REQUEST);
                    res.setStatus(false);
                    res.setDescription("TCP Host/IP is required when connection type is TCP (e.g. 192.168.1.50)");
                    return res;
                }
                if (dto.getTcpPort() == null || dto.getTcpPort() < 1 || dto.getTcpPort() > 65535) {
                    res.setCode(BAD_REQUEST);
                    res.setStatus(false);
                    res.setDescription("TCP Port is required and must be between 1 and 65535 (e.g. 4001)");
                    return res;
                }
            } else {
                res.setCode(BAD_REQUEST);
                res.setStatus(false);
                res.setDescription("Invalid connection type");
                return res;
            }

            device.setDeviceId(dto.getDeviceId());
            device.setName(dto.getName());
            device.setConnectionType(dto.getConnectionType());
            device.setSerialPort(dto.getConnectionType() == ConnectionType.SERIAL ? dto.getSerialPort().trim() : null);
            device.setBaudRate(dto.getConnectionType() == ConnectionType.SERIAL && dto.getBaudRate() != null ? dto.getBaudRate() : 9600);
            device.setDataBits(dto.getConnectionType() == ConnectionType.SERIAL && dto.getDataBits() != null ? dto.getDataBits() : 8);
            device.setParity(dto.getConnectionType() == ConnectionType.SERIAL && dto.getParity() != null ? dto.getParity() : ParityMode.NONE);
            device.setFlowControl(dto.getConnectionType() == ConnectionType.SERIAL && dto.getFlowControl() != null ? dto.getFlowControl() : FlowControlMode.NONE);
            device.setStopBits(dto.getConnectionType() == ConnectionType.SERIAL && dto.getStopBits() != null ? dto.getStopBits() : StopBitsMode.ONE);
            device.setProtocolType(dto.getProtocolType() != null ? dto.getProtocolType() : WeighbridgeProtocolType.GENERIC_NUMERIC);
            device.setWeightDisplayMode(dto.getWeightDisplayMode() != null ? dto.getWeightDisplayMode() : WeightDisplayMode.SINGLE_CELL);
            device.setCellCount(dto.getCellCount() != null ? dto.getCellCount() : defaultCellCount(dto.getWeightDisplayMode()));
            device.setIndicatorProfileUuid(dto.getIndicatorProfileUuid());
            device.setTcpHost(dto.getConnectionType() == ConnectionType.TCP ? dto.getTcpHost().trim() : null);
            device.setTcpPort(dto.getConnectionType() == ConnectionType.TCP ? dto.getTcpPort() : null);
            device.setActive(dto.getActive() != null ? dto.getActive() : true);

            WeighbridgeDevice saved = repo.save(device);

            res.setCode(SUCCESS);
            res.setStatus(true);
            res.setDescription("Weighbridge device updated successfully");
            res.setData(toDto(saved));
            log.info("WBMS DEVICE REGISTRY: Updated device {} ({}) as {} endpoint={}",
                    saved.getDeviceId(), saved.getName(), saved.getConnectionType(), endpoint(saved));
            return res;

        } catch (Exception e) {
            res.setCode(FAILURE);
            res.setStatus(false);
            res.setDescription("Error while updating weighbridge device: " + e.getMessage());
            log.error("WBMS DEVICE REGISTRY: Error while updating device uuid={} - {}", uuid, e.getMessage(), e);
            return res;
        }
    }


    // ---------------- GET BY UUID ----------------
    @Override
    public Response<WeighbridgeDeviceResponseDto> getByUuid(String uuid) {
        Response<WeighbridgeDeviceResponseDto> res = new Response<>();

        try {
            if (uuid == null || uuid.trim().isEmpty()) {
                res.setCode(INVALID_REQUEST);
                res.setStatus(false);
                res.setDescription("Device uuid is required");
                return res;
            }

            WeighbridgeDevice device = repo.findByUuidAndDeletedFalse(uuid.trim()).orElse(null);
            if (device == null) {
                res.setCode(NO_RECORD_FOUND);
                res.setStatus(false);
                res.setDescription("Weighbridge device not found");
                return res;
            }

            res.setCode(SUCCESS);
            res.setStatus(true);
            res.setDescription("Weighbridge device retrieved successfully");
            res.setData(toDto(device));
            return res;

        } catch (Exception e) {
            res.setCode(FAILURE);
            res.setStatus(false);
            res.setDescription("Error while retrieving weighbridge device: " + e.getMessage());
            return res;
        }
    }

    // ---------------- LIST (PAGED) ----------------
    @Override
    public Response<List<WeighbridgeDeviceResponseDto>> list(Integer pageNo, Integer pageSize) {
        Response<List<WeighbridgeDeviceResponseDto>> res = new Response<>();

        try {
            int pNo = (pageNo == null || pageNo < 0) ? 0 : pageNo;
            int pSize = (pageSize == null || pageSize <= 0) ? 10 : pageSize;

            Pageable pageable = PageRequest.of(pNo, pSize);
            Page<WeighbridgeDevice> page = repo.findByDeletedFalse(pageable);

            if (page.getContent().isEmpty()) {
                res.setCode(NO_RECORD_FOUND);
                res.setStatus(false);
                res.setDescription("No weighbridge devices found");
                return res;
            }

            List<WeighbridgeDeviceResponseDto> dtos = page.getContent()
                    .stream()
                    .map(this::toDto)
                    .toList();

            res.setCode(SUCCESS);
            res.setStatus(true);
            res.setDescription("Weighbridge devices retrieved successfully");
            res.setData(dtos);
            return res;

        } catch (Exception e) {
            res.setCode(FAILURE);
            res.setStatus(false);
            res.setDescription("Error while listing weighbridge devices: " + e.getMessage());
            return res;
        }
    }

    // ---------------- DELETE (SOFT DELETE) ----------------
    @Transactional
    @Override
    public Response<Boolean> delete(String uuid) {
        Response<Boolean> res = new Response<>();

        try {
            if (uuid == null || uuid.trim().isEmpty()) {
                res.setCode(INVALID_REQUEST);
                res.setStatus(false);
                res.setDescription("Device uuid is required");
                res.setData(false);
                return res;
            }

            WeighbridgeDevice device = repo.findByUuidAndDeletedFalse(uuid.trim()).orElse(null);
            if (device == null) {
                res.setCode(NO_RECORD_FOUND);
                res.setStatus(false);
                res.setDescription("Weighbridge device not found");
                res.setData(false);
                return res;
            }

            // Soft delete
            device.setDeleted(true);
            device.setDeletedAt(LocalDateTime.now());

            // If you have currentUserService later:
            // device.setDeletedBy(currentUserService.getUserId());

            repo.save(device);

            res.setCode(SUCCESS);
            res.setStatus(true);
            res.setDescription("Weighbridge device deleted successfully");
            res.setData(true);
            log.info("WBMS DEVICE REGISTRY: Deleted device {} ({})", device.getDeviceId(), device.getName());
            return res;

        } catch (Exception e) {
            res.setCode(FAILURE);
            res.setStatus(false);
            res.setDescription("Error while deleting weighbridge device: " + e.getMessage());
            res.setData(false);
            log.error("WBMS DEVICE REGISTRY: Error while deleting device uuid={} - {}", uuid, e.getMessage(), e);
            return res;
        }
    }

    private String endpoint(WeighbridgeDevice device) {
        if (device.getConnectionType() == ConnectionType.SERIAL) {
            return device.getSerialPort();
        }
        if (device.getConnectionType() == ConnectionType.TCP) {
            return device.getTcpHost() + ":" + device.getTcpPort();
        }
        return "unknown";
    }

    // ---------------- Mapper (Entity -> Response DTO) ----------------
    private WeighbridgeDeviceResponseDto toDto(WeighbridgeDevice d) {
        return WeighbridgeDeviceResponseDto.builder()
                .uuid(d.getUuid())
                .deviceId(d.getDeviceId())
                .name(d.getName())
                .connectionType(d.getConnectionType())
                .serialPort(d.getSerialPort())
                .baudRate(d.getBaudRate())
                .dataBits(d.getDataBits())
                .parity(d.getParity())
                .flowControl(d.getFlowControl())
                .stopBits(d.getStopBits())
                .protocolType(d.getProtocolType())
                .weightDisplayMode(d.getWeightDisplayMode())
                .cellCount(d.getCellCount())
                .indicatorProfileUuid(d.getIndicatorProfileUuid())
                .tcpHost(d.getTcpHost())
                .tcpPort(d.getTcpPort())
                .active(d.getActive())
                .build();
    }

    private int defaultCellCount(WeightDisplayMode mode) {
        return mode == WeightDisplayMode.MULTI_CELL ? 4 : 1;
    }
}
