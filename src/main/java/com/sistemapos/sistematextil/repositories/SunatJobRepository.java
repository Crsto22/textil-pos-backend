package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.SunatJob;
import com.sistemapos.sistematextil.util.sunat.SunatJobEstado;
import com.sistemapos.sistematextil.util.sunat.SunatJobTipoDocumento;

public interface SunatJobRepository extends JpaRepository<SunatJob, Long> {

    Optional<SunatJob> findByTipoDocumentoAndDocumentoId(
            SunatJobTipoDocumento tipoDocumento,
            Integer documentoId);

    @Query("""
            SELECT j
            FROM SunatJob j
            WHERE j.estado IN :estados
              AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now)
              AND (j.lockedAt IS NULL OR j.lockedAt < :lockThreshold)
            ORDER BY j.nextRetryAt ASC, j.createdAt ASC
            """)
    List<SunatJob> findReadyJobs(
            @Param("estados") Collection<SunatJobEstado> estados,
            @Param("now") LocalDateTime now,
            @Param("lockThreshold") LocalDateTime lockThreshold,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE SunatJob j
            SET j.estado = :nuevoEstado,
                j.lockedAt = :now,
                j.lastAttemptAt = :now,
                j.intentos = j.intentos + 1
            WHERE j.idSunatJob = :idSunatJob
              AND j.estado IN :estadosPrevios
              AND (j.lockedAt IS NULL OR j.lockedAt < :lockThreshold)
              AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now)
            """)
    int claimJob(
            @Param("idSunatJob") Long idSunatJob,
            @Param("estadosPrevios") Collection<SunatJobEstado> estadosPrevios,
            @Param("nuevoEstado") SunatJobEstado nuevoEstado,
            @Param("now") LocalDateTime now,
            @Param("lockThreshold") LocalDateTime lockThreshold);
}
