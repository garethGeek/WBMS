package wbms.tanroads.wbms.services;

import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.responseDtos.WeightReadingResponseDto;

import java.util.List;

public interface WeightReadingService {
    Response<WeightReadingResponseDto> captureFromActiveDevice();

    Response<WeightReadingResponseDto> captureFromDevice(String deviceUuid);

    Response<WeightReadingResponseDto> getLatestReading();

    Response<List<WeightReadingResponseDto>> listRecentReadings(Integer pageNo, Integer pageSize);
}
