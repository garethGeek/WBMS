package wbms.tanroads.wbms.responseDtos;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class WeightReadingResponseDto {
    private String uuid;
    private String deviceUuid;
    private String deviceId;
    private String deviceName;
    private Double weightKg;
    private String rawLine;
    private Date capturedAt;
}
