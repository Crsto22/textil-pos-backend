package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    @Query("""
            SELECT p
            FROM Pago p
            JOIN p.venta v
            LEFT JOIN v.cliente c
            JOIN p.metodoPago mp
            WHERE p.deletedAt IS NULL
              AND v.deletedAt IS NULL
              AND (:idVenta IS NULL OR v.idVenta = :idVenta)
              AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
              AND (:idMetodoPago IS NULL OR mp.idMetodoPago = :idMetodoPago)
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:fechaInicio IS NULL OR p.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR p.fecha < :fechaFinExclusive)
              AND (
                    :term IS NULL
                    OR LOWER(CAST(mp.nombre AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(CAST(COALESCE(p.codigoOperacion, '') AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(CAST(COALESCE(c.nombres, '') AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR TRIM(str(v.idVenta)) LIKE CONCAT(:term, '%')
                    OR TRIM(str(v.correlativo)) LIKE CONCAT(:term, '%')
                    OR LOWER(CAST(
                            CONCAT(
                                    CONCAT(COALESCE(v.serie, ''), '-'),
                                    COALESCE(TRIM(str(v.correlativo)), '')
                            )
                    AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            """)
    Page<Pago> buscarConFiltros(
            @Param("term") String term,
            @Param("idVenta") Integer idVenta,
            @Param("idUsuario") Integer idUsuario,
            @Param("idMetodoPago") Integer idMetodoPago,
            @Param("idSucursal") Integer idSucursal,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive,
            Pageable pageable);

    @Query("""
            SELECT p
            FROM Pago p
            JOIN p.venta v
            LEFT JOIN v.cliente c
            JOIN p.metodoPago mp
            WHERE p.deletedAt IS NULL
              AND v.deletedAt IS NULL
              AND (:idVenta IS NULL OR v.idVenta = :idVenta)
              AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
              AND (:idMetodoPago IS NULL OR mp.idMetodoPago = :idMetodoPago)
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:fechaInicio IS NULL OR p.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR p.fecha < :fechaFinExclusive)
              AND (
                    :term IS NULL
                    OR LOWER(CAST(mp.nombre AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(CAST(COALESCE(p.codigoOperacion, '') AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(CAST(COALESCE(c.nombres, '') AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR TRIM(str(v.idVenta)) LIKE CONCAT(:term, '%')
                    OR TRIM(str(v.correlativo)) LIKE CONCAT(:term, '%')
                    OR LOWER(CAST(
                            CONCAT(
                                    CONCAT(COALESCE(v.serie, ''), '-'),
                                    COALESCE(TRIM(str(v.correlativo)), '')
                            )
                    AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            ORDER BY p.fecha DESC, p.idPago DESC
            """)
    List<Pago> buscarReporteConFiltros(
            @Param("term") String term,
            @Param("idVenta") Integer idVenta,
            @Param("idUsuario") Integer idUsuario,
            @Param("idMetodoPago") Integer idMetodoPago,
            @Param("idSucursal") Integer idSucursal,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);
    Optional<Pago> findByIdPagoAndDeletedAtIsNull(Integer idPago);

    List<Pago> findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(Integer idVenta);

    @Query("""
            SELECT p
            FROM Pago p
            WHERE p.deletedAt IS NULL
              AND p.venta.idVenta IN :ventaIds
            ORDER BY p.venta.idVenta ASC, p.idPago ASC
            """)
    List<Pago> findActivosByVentaIds(@Param("ventaIds") List<Integer> ventaIds);

    long countByVenta_IdVentaAndDeletedAtIsNull(Integer idVenta);
}
