package wbms.tanroads.wbms.repositories;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import wbms.tanroads.wbms.entities.WeighbridgeDevice;
import java.util.Optional;
import java.util.List;

public interface WeighbridgeDeviceRepository extends JpaRepository<WeighbridgeDevice, Long> {
    List<WeighbridgeDevice> findByIndicatorProfileUuid(String indicatorProfileUuid);
    //boolean existsByDeviceIdIgnoreCase(@NotBlank(message = "Device ID is required (e.g. WB-01)") String deviceId);

    boolean existsByDeviceIdIgnoreCase(String deviceId);

    Optional<WeighbridgeDevice> findByDeviceIdIgnoreCase(String deviceId);

    @Query("select d from WeighbridgeDevice d where d.uuid = :uuid and (d.deleted = false or d.deleted is null)")
    Optional<WeighbridgeDevice> findByUuidAndDeletedFalse(@Param("uuid") String uuid);

    @Query("select d from WeighbridgeDevice d where d.deleted = false or d.deleted is null")
    Page<WeighbridgeDevice> findByDeletedFalse(Pageable pageable);

    @Query(value = "select * from wbms.wb_weight_devices d where d.active = true and (d.deleted = false or d.deleted is null) order by d.id desc limit 1", nativeQuery = true)
    Optional<WeighbridgeDevice> findFirstByActiveTrueAndDeletedFalseOrderByIdDesc();

}
