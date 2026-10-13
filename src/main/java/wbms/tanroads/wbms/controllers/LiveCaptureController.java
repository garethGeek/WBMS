package wbms.tanroads.wbms.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import wbms.tanroads.wbms.capture.LiveCaptureService;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.dtos.LiveCaptureStatusDto;

import static wbms.tanroads.wbms.common.ResponseCode.SUCCESS;
import static wbms.tanroads.wbms.common.ResponseCode.FAILURE;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveCaptureController {

    private final LiveCaptureService liveCaptureService;

    @GetMapping("/status")
    public Response<LiveCaptureStatusDto> status() {
        Response<LiveCaptureStatusDto> res = new Response<>();
        LiveCaptureStatusDto status = liveCaptureService.status();
        res.setStatus(true);
        res.setCode(SUCCESS);
        res.setDescription(status.getMessage());
        res.setData(status);
        return res;
    }

    @PostMapping("/start/active")
    public Response<Boolean> startActive() {
        Response<Boolean> res = new Response<>();
        try {
            liveCaptureService.startForActiveDevice();
            res.setStatus(true);
            res.setCode(SUCCESS);
            res.setDescription("Live streaming started for active device. Check terminal logs.");
            res.setData(true);
            return res;
        } catch (Exception e) {
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Failed to start live streaming: " + e.getMessage());
            res.setData(false);
            return res;
        }
    }

    @PostMapping("/start/{deviceUuid}")
    public Response<Boolean> startByDevice(@PathVariable String deviceUuid) {
        Response<Boolean> res = new Response<>();
        try {
            liveCaptureService.startForDeviceUuid(deviceUuid);
            res.setStatus(true);
            res.setCode(SUCCESS);
            res.setDescription("Live streaming started. Check terminal logs.");
            res.setData(true);
            return res;
        } catch (Exception e) {
            res.setStatus(false);
            res.setCode(FAILURE);
            res.setDescription("Failed to start live streaming: " + e.getMessage());
            res.setData(false);
            return res;
        }
    }

    @PostMapping("/stop")
    public Response<Boolean> stop() {
        Response<Boolean> res = new Response<>();
        liveCaptureService.stop();
        res.setStatus(true);
        res.setCode(SUCCESS);
        res.setDescription("Live streaming stopped.");
        res.setData(true);
        return res;
    }
}
