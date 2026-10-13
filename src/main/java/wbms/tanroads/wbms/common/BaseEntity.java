package wbms.tanroads.wbms.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name="created_date", nullable=false,updatable = false)
    @CreationTimestamp
    private Date createdDate = new Date();

    @Column(name="updated_date", nullable=true ,updatable = true)
    private Date updatedDate;

    @Column(name="created_by", nullable=false,updatable = false)
    private String createdBy;

    @Column(name="created_by_user_name", nullable=true,updatable = false)
    private String createdByUserName;

    @Column(name="updated_by", nullable=true)
    private String updatedBy;

    @Column(name="updated_by_user_name", nullable=true,updatable = true)
    private String updatedByUserName;

    @Column(name = "record_status_id", nullable = false)
    private Long recordStatusId = 1L;

    @JsonIgnore
    @Column(name = "deleted")
    private Boolean deleted = false;

    @JsonIgnore
    @Column(name = "deleted_by",nullable = true)
    private String deletedBy;

    @JsonIgnore
    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @JsonIgnore
    @Column(name = "active")
    private Boolean active = true;
}
