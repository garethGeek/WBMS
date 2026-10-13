package wbms.tanroads.wbms.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceConnectionTestResultDto {
    private boolean connected;
    private boolean dataReceived;
    private String message;      // friendly message for user
    private String sampleData;   // first line received (if any)
    private String rawHex;
    private String parserInput;
    private Double parsedWeightKg;
    private java.util.List<Double> parsedCellWeightsKg;
}
