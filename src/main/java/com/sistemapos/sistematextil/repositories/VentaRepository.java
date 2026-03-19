package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.math.BigDecimal;
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
            SELECT COALESCE(SUM(v.total), 0)
            FROM Venta v
            WHERE v.deletedAt IS NULL
              AND v.estado = 'EMITIDA'
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
              AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
            """)
    BigDecimal sumarTotalEmitido(
            @Param("idSucursal") Integer idSucursal,
            @Param("idUsuario") Integer idUsuario,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query("""
            SELECT COUNT(v)
            FROM Venta v
            WHERE v.deletedAt IS NULL
              AND v.estado = 'EMITIDA'
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
              AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
            """)
    long contarTicketsEmitidos(
            @Param("idSucursal") Integer idSucursal,
            @Param("idUsuario") Integer idUsuario,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query("""
            SELECT COALESCE(AVG(v.total), 0)
            FROM Venta v
            WHERE v.deletedAt IS NULL
              AND v.estado = 'EMITIDA'
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
              AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
            """)
    BigDecimal promedioVentaEmitida(
            @Param("idSucursal") Integer idSucursal,
            @Param("idUsuario") Integer idUsuario,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(
            value = """
                    SELECT DATE(v.fecha) AS fecha, COALESCE(SUM(v.total), 0) AS monto
                    FROM venta v
                    WHERE v.deleted_at IS NULL
                      AND v.estado = 'EMITIDA'
                      AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
                      AND (:idUsuario IS NULL OR v.id_usuario = :idUsuario)
                      AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
                      AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
                    GROUP BY DATE(v.fecha)
                    ORDER BY DATE(v.fecha)
                    """,
            nativeQuery = true)
    List<Object[]> obtenerVentasPorFecha(
            @Param("idSucursal") Integer idSucursal,
            @Param("idUsuario") Integer idUsuario,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query("""
            SELECT v
            FROM Venta v
            LEFT JOIN v.cliente c
            WHERE v.deletedAt IS NULL
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
              AND (:idCliente IS NULL OR c.idCliente = :idCliente)
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
            @Param("idCliente") Integer idCliente,
            @Param("tipoComprobante") String tipoComprobante,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive,
            Pageable pageable);

    @Query("""
            SELECT v
            FROM Venta v
            LEFT JOIN v.cliente c
            WHERE v.deletedAt IS NULL
              AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
              AND (:idCliente IS NULL OR c.idCliente = :idCliente)
              AND (:estado IS NULL OR v.estado = :estado)
              AND v.fecha >= :fechaInicio
              AND v.fecha < :fechaFinExclusive
            ORDER BY v.fecha ASC
            """)
    List<Venta> buscarParaReporte(
            @Param("idSucursal") Integer idSucursal,
            @Param("idCliente") Integer idCliente,
            @Param("estado") String estado,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    long countByClienteIdClienteAndDeletedAtIsNullAndEstado(Integer idCliente, String estado);

    @Query("""
            SELECT COALESCE(SUM(v.total), 0)
            FROM Venta v
            WHERE v.deletedAt IS NULL
              AND v.estado = :estado
              AND v.cliente.idCliente = :idCliente
            """)
    BigDecimal sumarTotalPorClienteYEstado(
            @Param("idCliente") Integer idCliente,
            @Param("estado") String estado);

    List<Venta> findTop3ByClienteIdClienteAndDeletedAtIsNullAndEstadoOrderByFechaDesc(Integer idCliente, String estado);

    Optional<Venta> findByIdVentaAndDeletedAtIsNull(Integer idVenta);

    Optional<Venta> findByIdVentaAndDeletedAtIsNullAndSucursal_IdSucursal(Integer idVenta, Integer idSucursal);

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
