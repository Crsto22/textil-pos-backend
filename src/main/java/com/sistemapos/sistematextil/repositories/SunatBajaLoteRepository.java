package com.sistemapos.sistematextil.repositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.SunatBajaLote;
import com.sistemapos.sistematextil.util.sunat.SunatBajaEstado;
import com.sistemapos.sistematextil.util.sunat.SunatBajaTipo;

public interface SunatBajaLoteRepository extends JpaRepository<SunatBajaLote, Integer> {

    Optional<SunatBajaLote> findByIdSunatBajaLoteAndDeletedAtIsNull(Integer idSunatBajaLote);

    @Query("""
            SELECT l
            FROM SunatBajaLote l
            WHERE l.deletedAt IS NULL
              AND l.empresa.idEmpresa = :idEmpresa
              AND l.tipoEnvio = :tipoEnvio
              AND l.fechaDocumento = :fechaDocumento
              AND l.fechaGeneracion = :fechaGeneracion
              AND l.estado = com.sistemapos.sistematextil.util.sunat.SunatBajaEstado.PENDIENTE_ENVIO
              AND l.sunatEnviadoAt IS NULL
            ORDER BY l.idSunatBajaLote DESC
            """)
    List<SunatBajaLote> findDraftLotes(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("tipoEnvio") SunatBajaTipo tipoEnvio,
            @Param("fechaDocumento") LocalDate fechaDocumento,
            @Param("fechaGeneracion") LocalDate fechaGeneracion,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(MAX(l.correlativo), 0)
            FROM SunatBajaLote l
            WHERE l.deletedAt IS NULL
              AND l.empresa.idEmpresa = :idEmpresa
              AND l.tipoEnvio = :tipoEnvio
              AND l.fechaGeneracion = :fechaGeneracion
            """)
    Integer findMaxCorrelativoByEmpresaAndTipoAndFechaGeneracion(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("tipoEnvio") SunatBajaTipo tipoEnvio,
            @Param("fechaGeneracion") LocalDate fechaGeneracion);

    @Query(value = """
            SELECT AUTO_INCREMENT
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sunat_baja_lote'
            """, nativeQuery = true)
    Integer findNextAutoIncrement();

    @Query("""
            SELECT l.idSunatBajaLote
            FROM SunatBajaLote l
            WHERE l.deletedAt IS NULL
              AND l.estado IN :estados
              AND NOT EXISTS (
                    SELECT 1
                    FROM SunatJob j
                    WHERE j.tipoDocumento = com.sistemapos.sistematextil.util.sunat.SunatJobTipoDocumento.BAJA_LOTE
                      AND j.documentoId = l.idSunatBajaLote
              )
            ORDER BY l.updatedAt ASC, l.idSunatBajaLote ASC
            """)
    List<Integer> findPendingSunatIdsWithoutJob(
            @Param("estados") Collection<SunatBajaEstado> estados,
            Pageable pageable);
}
