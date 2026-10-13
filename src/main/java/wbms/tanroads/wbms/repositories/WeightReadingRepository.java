package wbms.tanroads.wbms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import wbms.tanroads.wbms.entities.WeightReading;

import java.util.Optional;

public interface WeightReadingRepository extends JpaRepository<WeightReading, Long> {
    Optional<WeightReading> findFirstByOrderByCreatedDateDesc();

    Page<WeightReading> findAllByOrderByCreatedDateDesc(Pageable pageable);
}
