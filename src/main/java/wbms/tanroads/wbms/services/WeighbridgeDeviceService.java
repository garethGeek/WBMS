package wbms.tanroads.wbms.services;

import org.springframework.validation.BindingResult;
import wbms.tanroads.wbms.common.Response;
import wbms.tanroads.wbms.dtos.WeighbridgeDeviceCreateDto;
import wbms.tanroads.wbms.responseDtos.WeighbridgeDeviceResponseDto;

import java.util.List;

public interface WeighbridgeDeviceService {
    Response<WeighbridgeDeviceResponseDto> register(WeighbridgeDeviceCreateDto dto, BindingResult result);
    Response<WeighbridgeDeviceResponseDto> update(String uuid, WeighbridgeDeviceCreateDto dto, BindingResult result);
    Response<WeighbridgeDeviceResponseDto> getByUuid(String uuid);

    Response<List<WeighbridgeDeviceResponseDto>> list(Integer pageNo, Integer pageSize);

    Response<Boolean> delete(String uuid);
}
