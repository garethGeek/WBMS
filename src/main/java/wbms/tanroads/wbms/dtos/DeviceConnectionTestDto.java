package wbms.tanroads.wbms.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import wbms.tanroads.wbms.enums.ConnectionType;
import wbms.tanroads.wbms.enums.FlowControlMode;
import wbms.tanroads.wbms.enums.ParityMode;
import wbms.tanroads.wbms.enums.StopBitsMode;
import wbms.tanroads.wbms.enums.WeighbridgeProtocolType;

@Data
public class DeviceConnectionTestDto {

    @NotNull(message = "Connection type is required (SERIAL or TCP)")
    private ConnectionType connectionType;

    // SERIAL (simple)
    private String serialPort;   // COM5
    private Integer baudRate = 9600;
    private Integer dataBits = 8;
    private ParityMode parity = ParityMode.NONE;
    private FlowControlMode flowControl = FlowControlMode.NONE;
    private StopBitsMode stopBits = StopBitsMode.ONE;
    private WeighbridgeProtocolType protocolType = WeighbridgeProtocolType.GENERIC_NUMERIC;
    private Integer cellCount = 1;

    // TCP (simple)
    private String tcpHost;      // 192.168.1.50
    private Integer tcpPort;     // 4001

    // optional: how long to wait for data
    private Integer waitSeconds; // default 5

    // Optional live profile tuning. These affect only this connection test.
    private String startBytes;
    private String endBytes;
    private String separator;
    private Integer stringLength;
    private Integer removeBeginning = 0;
    private Integer removeEnd = 0;
    private Integer weightTokenIndex = 0;

    private Boolean profileTest = false;

}
