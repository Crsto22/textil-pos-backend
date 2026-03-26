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

                                @Query(
                                                                                                value = """
                                                                                                                                                                SELECT mp.nombre AS metodo_pago, COALESCE(SUM(p.monto), 0) AS monto
                                                                                                                                                                FROM pago p
                                                                                                                                                                JOIN venta v ON v.id_venta = p.id_venta
                                                                                                                                                                JOIN metodo_pago_config mp ON mp.id_metodo_pago = p.id_metodo_pago
                                                                                                                                                                WHERE p.deleted_at IS NULL
                                                                                                                                                                        AND v.deleted_at IS NULL
                                                                                                                                                                        AND v.estado = 'EMITIDA'
                                                                                                                                                                        AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
                                                                                                                                                                        AND (:idUsuario IS NULL OR v.id_usuario = :idUsuario)
                                                                                                                                                                        AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
                                                                                                                                                                        AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
                                                                                                                                                                GROUP BY mp.nombre
                                                                                                                                                                ORDER BY monto DESC
                                                                                                                                                                """,
                                                                                                nativeQuery = true)
                                List<Object[]> obtenerIngresosPorMetodoPago(
                                                                                                @Param("idSucursal") Integer idSucursal,
                                                                                                @Param("idUsuario") Integer idUsuario,
                                                                                                @Param("fechaInicio") LocalDateTime fechaInicio,
                                                                                                @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

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
                          AND (:estadoVenta IS NULL OR UPPER(v.estado) = :estadoVenta)
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
                        @Param("estadoVenta") String estadoVenta,
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
                          AND (:estadoVenta IS NULL OR UPPER(v.estado) = :estadoVenta)
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
                        @Param("estadoVenta") String estadoVenta,
                        @Param("fechaInicio") LocalDateTime fechaInicio,
                        @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

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

        Optional<Pago> findByIdPagoAndDeletedAtIsNull(Integer idPago);
}
