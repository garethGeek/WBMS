package wbms.tanroads.wbms.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.responseDtos.WeightReadingResponseDto;
import wbms.tanroads.wbms.services.WeightReadingService;

import java.util.List;

@RestController
@RequestMapping("/api/readings")
@RequiredArgsConstructor
public class WeightReadingController {

    private final WeightReadingService weightReadingService;

    @GetMapping("/latest")
    public Response<WeightReadingResponseDto> latest() {
        return weightReadingService.getLatestReading();
    }

    @GetMapping
    public Response<List<WeightReadingResponseDto>> list(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return weightReadingService.listRecentReadings(pageNo, pageSize);
    }

    @PostMapping("/capture/active")
    public Response<WeightReadingResponseDto> captureActive() {
        return weightReadingService.captureFromActiveDevice();
    }

    @PostMapping("/capture/{deviceUuid}")
    public Response<WeightReadingResponseDto> captureByDevice(@PathVariable String deviceUuid) {
        return weightReadingService.captureFromDevice(deviceUuid);
    }
}
