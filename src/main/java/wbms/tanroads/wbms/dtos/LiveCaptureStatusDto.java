package wbms.tanroads.wbms.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import wbms.tanroads.wbms.enums.WeightDisplayMode;

@Data
@Builder
public class LiveCaptureStatusDto {
    private boolean connected;
    private String deviceUuid;
    private String deviceId;
    private String deviceName;
    private Double latestWeightKg;
    private Double bvrKg;
    private String latestRawLine;
    private List<Double> latestCellWeightsKg;
    private Double indicatorGvmKg;
    private WeightDisplayMode weightDisplayMode;
    private Integer cellCount;
    private Date latestReadAt;
    private String message;
}
