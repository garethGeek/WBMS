package wbms.tanroads.wbms.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import wbms.tanroads.wbms.common.BaseEntity;
import wbms.tanroads.wbms.utils.CustomGeneratedData;

@Entity
@Table(name = "wb_weight_readings", schema="wbms")
@AuditTable(value = "wb_weight_readings_aud", schema = "aud")
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeightReading extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_uuid", referencedColumnName = "uuid", nullable = false)
    private WeighbridgeDevice device;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "raw_line", length = 500)
    private String rawLine;

    @PrePersist
    public void prePersist() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = CustomGeneratedData.GenerateUniqueID();
        }
    }
}