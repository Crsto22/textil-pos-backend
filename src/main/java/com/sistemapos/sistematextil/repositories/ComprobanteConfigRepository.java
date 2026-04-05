package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ComprobanteConfig;

public interface ComprobanteConfigRepository extends JpaRepository<ComprobanteConfig, Integer> {

    @Query("""
            SELECT c
            FROM ComprobanteConfig c
            WHERE c.deletedAt IS NULL
              AND (:activo IS NULL OR c.activo = :activo)
              AND (:habilitadoVenta IS NULL OR c.habilitadoVenta = :habilitadoVenta)
            ORDER BY c.idComprobante ASC
            """)
    List<ComprobanteConfig> buscar(
            @Param("activo") String activo,
            @Param("habilitadoVenta") Boolean habilitadoVenta);

    @Query("""
            SELECT c
            FROM ComprobanteConfig c
            WHERE c.deletedAt IS NULL
              AND (:term IS NULL
                   OR UPPER(c.tipoComprobante) LIKE CONCAT('%', UPPER(:term), '%')
                   OR UPPER(c.serie) LIKE CONCAT('%', UPPER(:term), '%'))
              AND (:activo IS NULL OR c.activo = :activo)
              AND (:habilitadoVenta IS NULL OR c.habilitadoVenta = :habilitadoVenta)
            ORDER BY c.idComprobante ASC
            """)
    Page<ComprobanteConfig> buscarPaginado(
            @Param("term") String term,
            @Param("activo") String activo,
            @Param("habilitadoVenta") Boolean habilitadoVenta,
            Pageable pageable);

    Optional<ComprobanteConfig> findByIdComprobanteAndDeletedAtIsNull(Integer idComprobante);

    Optional<ComprobanteConfig> findByTipoComprobanteAndSerieAndDeletedAtIsNull(
            String tipoComprobante,
            String serie);

    @Query(
            value = """
                    SELECT *
                    FROM comprobante_config
                    WHERE tipo_comprobante = :tipoComprobante
                      AND serie = :serie
                      AND activo = 1
                      AND deleted_at IS NULL
                    ORDER BY id_comprobante DESC
                    LIMIT 1
                    FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<ComprobanteConfig> findActivoForUpdate(
            @Param("tipoComprobante") String tipoComprobante,
            @Param("serie") String serie);
}
