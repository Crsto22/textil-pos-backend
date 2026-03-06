package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Integer> {

    @Query("""
            SELECT v
            FROM Venta v
            LEFT JOIN v.cliente c
            WHERE v.deletedAt IS NULL
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
              AND (:tipoComprobante IS NULL OR v.tipoComprobante = :tipoComprobante)
              AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
              AND (
                    :term IS NULL
                    OR LOWER(CAST(c.nombres AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR TRIM(str(v.correlativo)) LIKE CONCAT(:term, '%')
                    OR LOWER(CAST(
                            CONCAT(
                                    CONCAT(COALESCE(v.serie, ''), '-'),
                                    COALESCE(TRIM(str(v.correlativo)), '')
                            )
                    AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            """)
    Page<Venta> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("idUsuario") Integer idUsuario,
            @Param("tipoComprobante") String tipoComprobante,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive,
            Pageable pageable);

    Page<Venta> findByDeletedAtIsNullOrderByIdVentaDesc(Pageable pageable);

    Page<Venta> findByDeletedAtIsNullAndSucursal_IdSucursalOrderByIdVentaDesc(Integer idSucursal, Pageable pageable);

    Optional<Venta> findByIdVentaAndDeletedAtIsNull(Integer idVenta);

    Optional<Venta> findByIdVentaAndDeletedAtIsNullAndSucursal_IdSucursal(Integer idVenta, Integer idSucursal);

    List<Venta> findByDeletedAtIsNullAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
            LocalDateTime fechaInicio,
            LocalDateTime fechaFinExclusive);

    List<Venta> findByDeletedAtIsNullAndSucursal_IdSucursalAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
            Integer idSucursal,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFinExclusive);

    List<Venta> findByDeletedAtIsNullAndEstadoAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
            String estado,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFinExclusive);

    List<Venta> findByDeletedAtIsNullAndSucursal_IdSucursalAndEstadoAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
            Integer idSucursal,
            String estado,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFinExclusive);

    @Query("""
            SELECT COALESCE(MAX(v.correlativo), 0)
            FROM Venta v
            WHERE v.deletedAt IS NULL
              AND v.sucursal.idSucursal = :idSucursal
              AND v.tipoComprobante = :tipoComprobante
              AND v.serie = :serie
            """)
    Integer obtenerMaxCorrelativoPorDocumento(
            @Param("idSucursal") Integer idSucursal,
            @Param("tipoComprobante") String tipoComprobante,
            @Param("serie") String serie);
}
