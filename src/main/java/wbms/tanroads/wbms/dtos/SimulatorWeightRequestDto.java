package wbms.tanroads.wbms.dtos;

import lombok.Data;
import java.util.List;

@Data
public class SimulatorWeightRequestDto {
    private Double weightKg;
    private Boolean stable;
    private List<Double> cellWeightsKg;
}
