package wbms.tanroads.wbms.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import wbms.tanroads.wbms.common.BaseEntity;
import wbms.tanroads.wbms.enums.*;
import wbms.tanroads.wbms.utils.CustomGeneratedData;

@Entity
@Table(name = "wb_weight_devices", schema = "wbms")
@AuditTable(value = "wb_weight_devices_aud", schema = "aud")
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeighbridgeDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private String uuid;

    @Column(name = "device_id", unique = true, nullable = false)
    private String deviceId; // e.g WB-01

    @Column(name = "name", nullable = false)
    private String name; // Gate A Scale

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false)
    private ConnectionType connectionType;

    // SERIAL CONFIG (defaults to reduce user input)
    @Column(name = "serial_port")
    private String serialPort; // COM5, COM6

    @Column(name = "baud_rate")
    private Integer baudRate = 9600;

    @Column(name = "data_bits")
    private Integer dataBits = 8;

    @Enumerated(EnumType.STRING)
    @Column(name = "parity_mode")
    private ParityMode parity = ParityMode.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_control_mode")
    private FlowControlMode flowControl = FlowControlMode.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_bits_mode")
    private StopBitsMode stopBits = StopBitsMode.ONE;

    // TCP CONFIG
    @Column(name = "tcp_host")
    private String tcpHost;

    @Column(name = "tcp_port")
    private Integer tcpPort;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol_type")
    private WeighbridgeProtocolType protocolType;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_display_mode")
    private WeightDisplayMode weightDisplayMode = WeightDisplayMode.SINGLE_CELL;

    @Column(name = "cell_count")
    private Integer cellCount = 1;

    @Column(name = "indicator_profile_uuid")
    private String indicatorProfileUuid;

    @Transient private String profileStartBytes;
    @Transient private String profileEndBytes;
    @Transient private String profileSeparator;
    @Transient private Integer profileStringLength;
    @Transient private Integer profileRemoveBeginning;
    @Transient private Integer profileRemoveEnd;

    @PrePersist
    public void prePersist() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = CustomGeneratedData.GenerateUniqueID();
        }
        if (this.active == null) {
            this.active = true;
        }
        // Ensure defaults are not null
        if (this.baudRate == null) this.baudRate = 9600;
        if (this.dataBits == null) this.dataBits = 8;
        if (this.parity == null) this.parity = ParityMode.NONE;
        if (this.flowControl == null) this.flowControl = FlowControlMode.NONE;
        if (this.stopBits == null) this.stopBits = StopBitsMode.ONE;
        if (this.protocolType == null) this.protocolType = WeighbridgeProtocolType.GENERIC_NUMERIC;
        if (this.weightDisplayMode == null) this.weightDisplayMode = WeightDisplayMode.SINGLE_CELL;
        if (this.cellCount == null || this.cellCount < 1) this.cellCount = 1;
    }
}
