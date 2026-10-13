package wbms.tanroads.wbms.dtos;

import lombok.Data;
import wbms.tanroads.wbms.enums.WeightDisplayMode;
import java.util.List;

@Data
public class SimulatorStartRequestDto {
    private String host;
    private Integer port;
    private Double weightKg;
    private WeightDisplayMode weightDisplayMode = WeightDisplayMode.SINGLE_CELL;
    private List<Double> cellWeightsKg;
    private Boolean framingEnabled = false;
    private String startBytes = "02";
    private String endBytes = "03";
    private String separator = "049";
    private String profileUuid;
}
