package wbms.tanroads.wbms.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;
import wbms.tanroads.wbms.enums.ConnectionType;
import wbms.tanroads.wbms.enums.FlowControlMode;
import wbms.tanroads.wbms.enums.ParityMode;
import wbms.tanroads.wbms.enums.StopBitsMode;
import wbms.tanroads.wbms.enums.WeightDisplayMode;
import wbms.tanroads.wbms.enums.WeighbridgeProtocolType;

@Data
public class WeighbridgeDeviceCreateDto {

    @NotBlank(message = "Device ID is required (e.g. WB-01)")
    private String deviceId;

    @NotBlank(message = "Device name is required (e.g. Gate A Scale)")
    private String name;

    @NotNull(message = "Connection type is required (SERIAL or TCP)")
    private ConnectionType connectionType;

    // SERIAL (simple fields)
    private String serialPort; // COM5
    @Min(value = 1200, message = "Baud rate must be >= 1200")
    @Max(value = 115200, message = "Baud rate must be <= 115200")
    private Integer baudRate;  // optional
    private Integer dataBits;  // optional

    // SERIAL (advanced optional)
    private ParityMode parity;
    private FlowControlMode flowControl;
    private StopBitsMode stopBits;
    private WeighbridgeProtocolType protocolType;
    private WeightDisplayMode weightDisplayMode;
    @Min(value = 1, message = "Cell count must be at least 1")
    @Max(value = 32, message = "Cell count must not exceed 32")
    private Integer cellCount;
    private String indicatorProfileUuid;
    // TCP (simple fields)
    private String tcpHost;
    @Min(value = 1, message = "TCP port must be between 1 and 65535")
    @Max(value = 65535, message = "TCP port must be between 1 and 65535")
    private Integer tcpPort;

    private Boolean active;
}
