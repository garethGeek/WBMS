package wbms.tanroads.wbms.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.dtos.IndicatorProfileCreateDto;
import wbms.tanroads.wbms.entities.IndicatorProfile;
import wbms.tanroads.wbms.repositories.IndicatorProfileRepository;
import wbms.tanroads.wbms.repositories.WeighbridgeDeviceRepository;
import wbms.tanroads.wbms.responseDtos.IndicatorProfileResponseDto;

import java.util.List;

import static wbms.tanroads.wbms.common.ResponseCode.*;

@RestController
@RequestMapping("/api/indicator-profiles")
@RequiredArgsConstructor
public class IndicatorProfileController {
    private final IndicatorProfileRepository repository;
    private final WeighbridgeDeviceRepository deviceRepository;

    @GetMapping
    public Response<List<IndicatorProfileResponseDto>> list() {
        return response(true, SUCCESS, "Indicator profiles retrieved", repository.findByActiveTrueOrderByProfileNameAsc().stream().map(this::toDto).toList());
    }

    @PostMapping
    public Response<IndicatorProfileResponseDto> create(@Valid @RequestBody IndicatorProfileCreateDto dto) {
        IndicatorProfile profile = IndicatorProfile.builder()
                .profileName(dto.getProfileName().trim()).manufacturer(dto.getManufacturer()).model(dto.getModel())
                .connectionType(dto.getConnectionType()).baudRate(dto.getBaudRate()).dataBits(dto.getDataBits())
                .parity(dto.getParity()).stopBits(dto.getStopBits()).flowControl(dto.getFlowControl())
                .serialPort(dto.getSerialPort()).tcpHost(dto.getTcpHost()).tcpPort(dto.getTcpPort())
                .startBytes(dto.getStartBytes()).endBytes(dto.getEndBytes()).separator(dto.getSeparator())
                .stringLength(dto.getStringLength()).removeBeginning(dto.getRemoveBeginning()).removeEnd(dto.getRemoveEnd())
                .weightTokenIndex(dto.getWeightTokenIndex()).scaleCount(dto.getScaleCount())
                .build();
        profile.setCreatedBy(currentUser());
        return response(true, SUCCESS, "Indicator profile saved", toDto(repository.save(profile)));
    }

    @PutMapping("/{uuid}")
    public Response<IndicatorProfileResponseDto> update(@PathVariable String uuid, @Valid @RequestBody IndicatorProfileCreateDto dto) {
        IndicatorProfile profile = repository.findByUuidAndActiveTrue(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Indicator profile not found"));
        profile.setProfileName(dto.getProfileName().trim());
        profile.setManufacturer(dto.getManufacturer());
        profile.setModel(dto.getModel());
        profile.setConnectionType(dto.getConnectionType());
        profile.setBaudRate(dto.getBaudRate());
        profile.setDataBits(dto.getDataBits());
        profile.setParity(dto.getParity());
        profile.setStopBits(dto.getStopBits());
        profile.setFlowControl(dto.getFlowControl());
        profile.setSerialPort(dto.getSerialPort());
        profile.setTcpHost(dto.getTcpHost());
        profile.setTcpPort(dto.getTcpPort());
        profile.setStartBytes(dto.getStartBytes());
        profile.setEndBytes(dto.getEndBytes());
        profile.setSeparator(dto.getSeparator());
        profile.setStringLength(dto.getStringLength());
        profile.setRemoveBeginning(dto.getRemoveBeginning());
        profile.setRemoveEnd(dto.getRemoveEnd());
        profile.setWeightTokenIndex(dto.getWeightTokenIndex());
        profile.setScaleCount(dto.getScaleCount());
        profile.setUpdatedBy(currentUser());
        return response(true, SUCCESS, "Indicator profile updated", toDto(repository.save(profile)));
    }

    @DeleteMapping("/{uuid}")
    public Response<Boolean> delete(@PathVariable String uuid) {
        IndicatorProfile profile = repository.findByUuidAndActiveTrue(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Indicator profile not found"));
        profile.setActive(false);
        profile.setUpdatedBy(currentUser());
        repository.save(profile);
        List<wbms.tanroads.wbms.entities.WeighbridgeDevice> assignedDevices = deviceRepository.findByIndicatorProfileUuid(uuid);
        assignedDevices.forEach(device -> {
            device.setIndicatorProfileUuid(null);
            device.setUpdatedBy(currentUser());
        });
        deviceRepository.saveAll(assignedDevices);
        return response(true, SUCCESS, "Indicator profile deleted", true);
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !authentication.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return authentication.getName();
        }
        return "SYSTEM";
    }

    private IndicatorProfileResponseDto toDto(IndicatorProfile p) {
        return IndicatorProfileResponseDto.builder().uuid(p.getUuid()).profileName(p.getProfileName())
                .manufacturer(p.getManufacturer()).model(p.getModel()).connectionType(p.getConnectionType())
                .baudRate(p.getBaudRate()).dataBits(p.getDataBits()).parity(p.getParity()).stopBits(p.getStopBits())
                .flowControl(p.getFlowControl()).serialPort(p.getSerialPort()).tcpHost(p.getTcpHost()).tcpPort(p.getTcpPort())
                .startBytes(p.getStartBytes()).endBytes(p.getEndBytes()).separator(p.getSeparator())
                .stringLength(p.getStringLength()).removeBeginning(p.getRemoveBeginning()).removeEnd(p.getRemoveEnd())
                .weightTokenIndex(p.getWeightTokenIndex()).scaleCount(p.getScaleCount()).build();
    }

    private <T> Response<T> response(boolean status, int code, String description, T data) {
        Response<T> response = new Response<>(); response.setStatus(status); response.setCode(code); response.setDescription(description); response.setData(data); return response;
    }
}
