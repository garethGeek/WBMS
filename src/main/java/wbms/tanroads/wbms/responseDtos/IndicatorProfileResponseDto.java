package wbms.tanroads.wbms.responseDtos;

import lombok.Builder;
import lombok.Data;
import wbms.tanroads.wbms.enums.*;

@Data
@Builder
public class IndicatorProfileResponseDto {
    private String uuid;
    private String profileName;
    private String manufacturer;
    private String model;
    private ConnectionType connectionType;
    private Integer baudRate;
    private Integer dataBits;
    private ParityMode parity;
    private StopBitsMode stopBits;
    private FlowControlMode flowControl;
    private String serialPort;
    private String tcpHost;
    private Integer tcpPort;
    private String startBytes;
    private String endBytes;
    private String separator;
    private Integer stringLength;
    private Integer removeBeginning;
    private Integer removeEnd;
    private Integer weightTokenIndex;
    private Integer scaleCount;
}
