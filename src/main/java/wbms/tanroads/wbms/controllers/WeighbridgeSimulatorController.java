package wbms.tanroads.wbms.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import wbms.tanroads.wbms.capture.WeighbridgeSimulatorService;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.dtos.SimulatorStartRequestDto;
import wbms.tanroads.wbms.dtos.SimulatorStatusDto;
import wbms.tanroads.wbms.dtos.SimulatorWeightRequestDto;

import static wbms.tanroads.wbms.common.ResponseCode.FAILURE;
import static wbms.tanroads.wbms.common.ResponseCode.SUCCESS;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class WeighbridgeSimulatorController {

    private final WeighbridgeSimulatorService simulatorService;

    @GetMapping("/status")
    public Response<SimulatorStatusDto> status() {
        return ok("Simulator status retrieved", simulatorService.status());
    }

    @PostMapping("/start")
    public Response<SimulatorStatusDto> start(@RequestBody(required = false) SimulatorStartRequestDto request) {
        try {
            SimulatorStartRequestDto body = request == null ? new SimulatorStartRequestDto() : request;
            return ok("Simulator started", simulatorService.start(body.getHost(), body.getPort(), body.getWeightKg(), body.getWeightDisplayMode(), body.getCellWeightsKg(), body.getFramingEnabled(), body.getStartBytes(), body.getEndBytes(), body.getSeparator(), body.getProfileUuid()));
        } catch (Exception e) {
            return fail("Simulator start failed: " + e.getMessage(), simulatorService.status());
        }
    }

    @PostMapping("/stop")
    public Response<SimulatorStatusDto> stop() {
        return ok("Simulator stopped", simulatorService.stop());
    }

    @PostMapping("/weight")
    public Response<SimulatorStatusDto> setWeight(@RequestBody SimulatorWeightRequestDto request) {
        try {
            return ok("Simulator weight updated", simulatorService.setWeight(request.getWeightKg(), request.getStable(), request.getCellWeightsKg()));
        } catch (Exception e) {
            return fail("Simulator weight update failed: " + e.getMessage(), simulatorService.status());
        }
    }

    @PostMapping("/demo")
    public Response<SimulatorStatusDto> demo() {
        try {
            return ok("Simulator demo sequence started", simulatorService.runDemo());
        } catch (Exception e) {
            return fail("Simulator demo failed: " + e.getMessage(), simulatorService.status());
        }
    }

    private Response<SimulatorStatusDto> ok(String message, SimulatorStatusDto data) {
        Response<SimulatorStatusDto> res = new Response<>();
        res.setStatus(true);
        res.setCode(SUCCESS);
        res.setDescription(message);
        res.setData(data);
        return res;
    }

    private Response<SimulatorStatusDto> fail(String message, SimulatorStatusDto data) {
        Response<SimulatorStatusDto> res = new Response<>();
        res.setStatus(false);
        res.setCode(FAILURE);
        res.setDescription(message);
        res.setData(data);
        return res;
    }
}
