package wbms.tanroads.wbms.entities;

import jakarta.persistence.*;
import lombok.*;
import wbms.tanroads.wbms.common.BaseEntity;
import wbms.tanroads.wbms.enums.ConnectionType;
import wbms.tanroads.wbms.enums.FlowControlMode;
import wbms.tanroads.wbms.enums.ParityMode;
import wbms.tanroads.wbms.enums.StopBitsMode;
import wbms.tanroads.wbms.utils.CustomGeneratedData;

@Entity
@Table(name = "wb_indicator_profiles", schema = "wbms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorProfile extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;

    @Column(name = "profile_name", nullable = false, unique = true)
    private String profileName;

    private String manufacturer;
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false)
    private ConnectionType connectionType;

    private Integer baudRate = 9600;
    private Integer dataBits = 8;

    @Enumerated(EnumType.STRING)
    private ParityMode parity = ParityMode.NONE;

    @Enumerated(EnumType.STRING)
    private StopBitsMode stopBits = StopBitsMode.ONE;

    @Enumerated(EnumType.STRING)
    private FlowControlMode flowControl = FlowControlMode.NONE;

    private String serialPort;
    private String tcpHost;
    private Integer tcpPort;

    @Column(name = "start_bytes")
    private String startBytes = "02";

    @Column(name = "end_bytes")
    private String endBytes = "03";

    private String separator;
    private Integer stringLength;
    private Integer removeBeginning = 0;
    private Integer removeEnd = 0;
    private Integer weightTokenIndex = 0;
    private Integer scaleCount = 1;
    private Boolean active = true;

    @PrePersist
    public void prePersist() {
        if (uuid == null || uuid.isBlank()) uuid = CustomGeneratedData.GenerateUniqueID();
        if (baudRate == null) baudRate = 9600;
        if (dataBits == null) dataBits = 8;
        if (parity == null) parity = ParityMode.NONE;
        if (stopBits == null) stopBits = StopBitsMode.ONE;
        if (flowControl == null) flowControl = FlowControlMode.NONE;
        if (removeBeginning == null) removeBeginning = 0;
        if (removeEnd == null) removeEnd = 0;
        if (weightTokenIndex == null) weightTokenIndex = 0;
        if (scaleCount == null || scaleCount < 1) scaleCount = 1;
        if (active == null) active = true;
    }
}
