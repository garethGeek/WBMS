package wbms.tanroads.wbms.responseDtos;

import lombok.Builder;
import lombok.Data;
import wbms.tanroads.wbms.enums.ConnectionType;
import wbms.tanroads.wbms.enums.FlowControlMode;
import wbms.tanroads.wbms.enums.ParityMode;
import wbms.tanroads.wbms.enums.StopBitsMode;
import wbms.tanroads.wbms.enums.WeightDisplayMode;
import wbms.tanroads.wbms.enums.WeighbridgeProtocolType;

@Data
@Builder
public class WeighbridgeDeviceResponseDto {
    private String uuid;
    private String deviceId;
    private String name;
    private ConnectionType connectionType;

    private String serialPort;
    private Integer baudRate;
    private Integer dataBits;
    private ParityMode parity;
    private FlowControlMode flowControl;
    private StopBitsMode stopBits;
    private WeighbridgeProtocolType protocolType;
    private WeightDisplayMode weightDisplayMode;
    private Integer cellCount;
    private String indicatorProfileUuid;
    private String tcpHost;
    private Integer tcpPort;

    private Boolean active;
}
