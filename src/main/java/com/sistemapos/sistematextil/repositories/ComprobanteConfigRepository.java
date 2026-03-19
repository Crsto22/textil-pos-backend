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
              AND (:idSucursal IS NULL OR c.sucursal.idSucursal = :idSucursal)
              AND (:habilitadoVenta IS NULL OR c.habilitadoVenta = :habilitadoVenta)
            ORDER BY c.idComprobante ASC
            """)
    List<ComprobanteConfig> buscar(
            @Param("activo") String activo,
            @Param("idSucursal") Integer idSucursal,
            @Param("habilitadoVenta") Boolean habilitadoVenta);

    @Query("""
            SELECT c
            FROM ComprobanteConfig c
            WHERE c.deletedAt IS NULL
              AND (:term IS NULL
                   OR UPPER(c.tipoComprobante) LIKE CONCAT('%', UPPER(:term), '%')
                   OR UPPER(c.serie) LIKE CONCAT('%', UPPER(:term), '%')
                   OR UPPER(c.sucursal.nombre) LIKE CONCAT('%', UPPER(:term), '%'))
              AND (:activo IS NULL OR c.activo = :activo)
              AND (:idSucursal IS NULL OR c.sucursal.idSucursal = :idSucursal)
              AND (:habilitadoVenta IS NULL OR c.habilitadoVenta = :habilitadoVenta)
            ORDER BY c.idComprobante ASC
            """)
    Page<ComprobanteConfig> buscarPaginado(
            @Param("term") String term,
            @Param("activo") String activo,
            @Param("idSucursal") Integer idSucursal,
            @Param("habilitadoVenta") Boolean habilitadoVenta,
            Pageable pageable);

    Optional<ComprobanteConfig> findByIdComprobanteAndDeletedAtIsNull(Integer idComprobante);

    Optional<ComprobanteConfig> findBySucursal_IdSucursalAndTipoComprobante(Integer idSucursal, String tipoComprobante);

    Optional<ComprobanteConfig> findBySucursal_IdSucursalAndTipoComprobanteAndDeletedAtIsNull(
            Integer idSucursal,
            String tipoComprobante);

    @Query(
            value = """
                    SELECT *
                    FROM comprobante_config
                    WHERE id_sucursal = :idSucursal
                      AND tipo_comprobante = :tipoComprobante
                      AND activo = 1
                      AND deleted_at IS NULL
                    LIMIT 1
                    FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<ComprobanteConfig> findActivoForUpdate(
            @Param("idSucursal") Integer idSucursal,
            @Param("tipoComprobante") String tipoComprobante);
}
