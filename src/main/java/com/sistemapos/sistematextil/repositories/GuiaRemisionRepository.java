package com.sistemapos.sistematextil.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public interface GuiaRemisionRepository extends JpaRepository<GuiaRemision, Integer> {

    Optional<GuiaRemision> findByIdGuiaRemisionAndDeletedAtIsNull(Integer idGuiaRemision);

    @Query("""
            SELECT g FROM GuiaRemision g
            WHERE g.deletedAt IS NULL
              AND (:idSucursal IS NULL OR g.sucursal.idSucursal = :idSucursal)
              AND (:estado IS NULL OR g.estado = :estado)
              AND (:sunatEstado IS NULL OR g.sunatEstado = :sunatEstado)
              AND (:term IS NULL
                   OR LOWER(g.serie) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(g.destinatarioRazonSocial) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR CAST(g.correlativo AS string) LIKE CONCAT('%', :term, '%')
              )
            """)
    Page<GuiaRemision> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("estado") String estado,
            @Param("sunatEstado") com.sistemapos.sistematextil.util.sunat.SunatEstado sunatEstado,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(MAX(g.correlativo), 0)
            FROM GuiaRemision g
            WHERE g.serie = :serie
            """)
    Integer obtenerMaxCorrelativoHistoricoPorSerie(@Param("serie") String serie);

    @Query("""
            SELECT g.idGuiaRemision
            FROM GuiaRemision g
            WHERE g.deletedAt IS NULL
              AND g.sunatEstado IN :estadosSunat
              AND NOT EXISTS (
                    SELECT 1
                    FROM SunatJob j
                    WHERE j.tipoDocumento = com.sistemapos.sistematextil.util.sunat.SunatJobTipoDocumento.GUIA_REMISION
                      AND j.documentoId = g.idGuiaRemision
              )
            ORDER BY g.updatedAt ASC, g.idGuiaRemision ASC
            """)
    List<Integer> findPendingSunatIdsWithoutJob(
            @Param("estadosSunat") Collection<SunatEstado> estadosSunat,
            Pageable pageable);
}
