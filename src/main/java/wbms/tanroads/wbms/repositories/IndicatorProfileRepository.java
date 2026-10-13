package wbms.tanroads.wbms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import wbms.tanroads.wbms.entities.IndicatorProfile;

import java.util.List;
import java.util.Optional;

public interface IndicatorProfileRepository extends JpaRepository<IndicatorProfile, Long> {
    List<IndicatorProfile> findByActiveTrueOrderByProfileNameAsc();
    Optional<IndicatorProfile> findByUuidAndActiveTrue(String uuid);
}
