package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sistemapos.sistematextil.model.ShalomAgencySync;

public interface ShalomAgencySyncRepository extends JpaRepository<ShalomAgencySync, Long> {
    Optional<ShalomAgencySync> findFirstByOrderByStartedAtDesc();
    Optional<ShalomAgencySync> findFirstByStatusOrderByCompletedAtDesc(String status);
}
