package wbms.tanroads.wbms.dtos;

import lombok.Builder;
import lombok.Data;
import wbms.tanroads.wbms.enums.WeightDisplayMode;
import java.util.List;

@Data
@Builder
public class SimulatorStatusDto {
    private boolean running;
    private String host;
    private Integer port;
    private Double weightKg;
    private boolean stable;
    private boolean vehicleCycleRunning;
    private Integer clients;
    private String rawLine;
    private WeightDisplayMode weightDisplayMode;
    private List<Double> cellWeightsKg;
    private Boolean framingEnabled;
    private String startBytes;
    private String endBytes;
    private String separator;
    private String profileUuid;
}
