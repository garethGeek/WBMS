package wbms.tanroads.wbms.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import wbms.tanroads.wbms.capture.DeviceConnectionTester;
import wbms.tanroads.wbms.capture.SerialPortService;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.dtos.DeviceConnectionTestDto;
import wbms.tanroads.wbms.dtos.DeviceConnectionTestResultDto;
import wbms.tanroads.wbms.dtos.WeighbridgeDeviceCreateDto;
import wbms.tanroads.wbms.responseDtos.WeighbridgeDeviceResponseDto;
import wbms.tanroads.wbms.services.WeighbridgeDeviceService;

import java.util.List;

import static wbms.tanroads.wbms.common.ResponseCode.*;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class WeighbridgeDeviceController {

    private final WeighbridgeDeviceService service;
    private final SerialPortService serialPortService;
    private final DeviceConnectionTester deviceConnectionTester;

    @PostMapping("/register")
    public Response<WeighbridgeDeviceResponseDto> register(
            @Valid @RequestBody WeighbridgeDeviceCreateDto dto,
            BindingResult result
    ) {
        return service.register(dto, result);
    }

    @PutMapping("/{uuid}")
    public Response<WeighbridgeDeviceResponseDto> update(
            @PathVariable String uuid,
            @Valid @RequestBody WeighbridgeDeviceCreateDto dto,
            BindingResult result
    ) {
        return service.update(uuid, dto, result);
    }

    // get by uuid
    @GetMapping("/{uuid}")
    public Response<WeighbridgeDeviceResponseDto> getByUuid(@PathVariable String uuid) {
        return service.getByUuid(uuid);
    }

    // list
    @GetMapping
    public Response<List<WeighbridgeDeviceResponseDto>> list(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return service.list(pageNo, pageSize);
    }

    // delete (soft delete)
    @DeleteMapping("/{uuid}")
    public Response<Boolean> delete(@PathVariable String uuid) {
        return service.delete(uuid);
    }


    //  COM port dropdown support
    @GetMapping("/serial/ports")
    public Response<List<String>> listSerialPorts() {
        Response<List<String>> res = new Response<>();
        try {
            res.setStatus(true);
            res.setCode(SUCCESS);
            res.setDescription("Available COM ports");
            res.setData(serialPortService.listComPorts());
            return res;
        } catch (Exception e) {
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Failed to list COM ports: " + e.getMessage());
            res.setData(List.of());
            return res;
        }
    }

    // TEST button (no save)
    @PostMapping("/test-connection")
    public Response<DeviceConnectionTestResultDto> testConnection(
            @Valid @RequestBody DeviceConnectionTestDto dto,
            BindingResult result
    ) {
        Response<DeviceConnectionTestResultDto> res = new Response<>();
        try {
            if (result.hasErrors()) {
                res.setStatus(false);
                res.setCode(INVALID_REQUEST);
                res.setDescription("Invalid request: " + result.getAllErrors().get(0).getDefaultMessage());
                return res;
            }

            DeviceConnectionTestResultDto out = deviceConnectionTester.test(dto);

            res.setStatus(out.isConnected());
            res.setCode(out.isConnected() ? SUCCESS : FAILURE);
            res.setDescription(out.getMessage());
            res.setData(out);
            return res;

        } catch (Exception e) {
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Test failed: " + e.getMessage());
            return res;
        }
    }
}
