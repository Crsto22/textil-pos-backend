package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sistemapos.sistematextil.model.ShalomAgency;

public interface ShalomAgencyRepository extends JpaRepository<ShalomAgency, Long> {
    Optional<ShalomAgency> findByExternalId(Integer externalId);
    List<ShalomAgency> findByActiveTrueAndDestinationEnabledTrueOrderByNameAsc();
    long countByActiveTrueAndDestinationEnabledTrue();
}
