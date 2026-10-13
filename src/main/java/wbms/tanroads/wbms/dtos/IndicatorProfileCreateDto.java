package wbms.tanroads.wbms.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import wbms.tanroads.wbms.enums.*;

@Data
public class IndicatorProfileCreateDto {
    @NotBlank private String profileName;
    private String manufacturer;
    private String model;
    @NotNull private ConnectionType connectionType;
    private Integer baudRate = 9600;
    private Integer dataBits = 8;
    private ParityMode parity = ParityMode.NONE;
    private StopBitsMode stopBits = StopBitsMode.ONE;
    private FlowControlMode flowControl = FlowControlMode.NONE;
    private String serialPort;
    private String tcpHost;
    private Integer tcpPort;
    private String startBytes = "02";
    private String endBytes = "03";
    private String separator;
    private Integer stringLength;
    private Integer removeBeginning = 0;
    private Integer removeEnd = 0;
    private Integer weightTokenIndex = 0;
    private Integer scaleCount = 1;
}
