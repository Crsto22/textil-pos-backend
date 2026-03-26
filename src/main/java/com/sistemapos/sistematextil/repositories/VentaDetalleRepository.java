package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.VentaDetalle;

public interface VentaDetalleRepository extends JpaRepository<VentaDetalle, Integer> {

    @Query("""
      SELECT COALESCE(SUM(vd.cantidad), 0)
      FROM VentaDetalle vd
      JOIN vd.venta v
      WHERE vd.deletedAt IS NULL
        AND v.deletedAt IS NULL
        AND v.estado = 'EMITIDA'
        AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
        AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
        AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
        AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
      """)
    long sumarCantidadVendida(
      @Param("idSucursal") Integer idSucursal,
      @Param("idUsuario") Integer idUsuario,
      @Param("fechaInicio") LocalDateTime fechaInicio,
      @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query("""
      SELECT COUNT(DISTINCT vd.productoVariante.idProductoVariante)
      FROM VentaDetalle vd
      JOIN vd.venta v
      WHERE vd.deletedAt IS NULL
        AND v.deletedAt IS NULL
        AND v.estado = 'EMITIDA'
        AND (:idSucursal IS NULL OR v.sucursal.idSucursal = :idSucursal)
        AND (:idUsuario IS NULL OR v.usuario.idUsuario = :idUsuario)
        AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
        AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
      """)
    long contarVariantesVendidas(
      @Param("idSucursal") Integer idSucursal,
      @Param("idUsuario") Integer idUsuario,
      @Param("fechaInicio") LocalDateTime fechaInicio,
      @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(
      value = """
        SELECT
          pv.id_producto_variante,
          p.nombre AS producto,
          c.nombre AS color,
          t.nombre AS talla,
          SUM(vd.cantidad) AS cantidad_vendida
        FROM venta_detalle vd
        JOIN venta v ON v.id_venta = vd.id_venta
        JOIN producto_variante pv ON pv.id_producto_variante = vd.id_producto_variante
        JOIN producto p ON p.producto_id = pv.producto_id
        JOIN colores c ON c.color_id = pv.color_id
        JOIN tallas t ON t.talla_id = pv.talla_id
        WHERE vd.deleted_at IS NULL
          AND v.deleted_at IS NULL
          AND v.estado = 'EMITIDA'
          AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
          AND (:idUsuario IS NULL OR v.id_usuario = :idUsuario)
          AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
          AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
        GROUP BY pv.id_producto_variante, p.nombre, c.nombre, t.nombre
        ORDER BY cantidad_vendida DESC
        LIMIT 5
        """,
      nativeQuery = true)
    List<Object[]> obtenerTopProductosVendidos(
      @Param("idSucursal") Integer idSucursal,
      @Param("idUsuario") Integer idUsuario,
      @Param("fechaInicio") LocalDateTime fechaInicio,
      @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(
      value = """
        SELECT
          p.producto_id,
          p.nombre AS producto,
          pv.id_producto_variante,
          c.nombre AS color,
          t.nombre AS talla,
          s.id_sucursal,
          s.nombre AS sucursal,
          SUM(vd.cantidad) AS unidades_vendidas,
          COALESCE(SUM(vd.total_detalle), 0) AS monto_vendido
        FROM venta_detalle vd
        JOIN venta v ON v.id_venta = vd.id_venta
        JOIN producto_variante pv ON pv.id_producto_variante = vd.id_producto_variante
        JOIN producto p ON p.producto_id = pv.producto_id
        LEFT JOIN colores c ON c.color_id = pv.color_id
        LEFT JOIN tallas t ON t.talla_id = pv.talla_id
        JOIN sucursal s ON s.id_sucursal = v.id_sucursal
        WHERE vd.deleted_at IS NULL
          AND v.deleted_at IS NULL
          AND v.estado = 'EMITIDA'
          AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
          AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
          AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
        GROUP BY p.producto_id, p.nombre, pv.id_producto_variante, c.nombre, t.nombre, s.id_sucursal, s.nombre
        ORDER BY monto_vendido DESC, unidades_vendidas DESC, p.nombre ASC, pv.id_producto_variante ASC
        LIMIT 10
        """,
      nativeQuery = true)
    List<Object[]> obtenerTopProductosPorMonto(
      @Param("idSucursal") Integer idSucursal,
      @Param("fechaInicio") LocalDateTime fechaInicio,
      @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(
      value = """
        SELECT
          p.producto_id,
          p.nombre AS producto,
          pv.id_producto_variante,
          c.nombre AS color,
          t.nombre AS talla,
          s.id_sucursal,
          s.nombre AS sucursal,
          SUM(vd.cantidad) AS unidades_vendidas,
          COALESCE(SUM(vd.total_detalle), 0) AS monto_vendido
        FROM venta_detalle vd
        JOIN venta v ON v.id_venta = vd.id_venta
        JOIN producto_variante pv ON pv.id_producto_variante = vd.id_producto_variante
        JOIN producto p ON p.producto_id = pv.producto_id
        LEFT JOIN colores c ON c.color_id = pv.color_id
        LEFT JOIN tallas t ON t.talla_id = pv.talla_id
        JOIN sucursal s ON s.id_sucursal = v.id_sucursal
        WHERE vd.deleted_at IS NULL
          AND v.deleted_at IS NULL
          AND v.estado = 'EMITIDA'
          AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
          AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
          AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
        GROUP BY p.producto_id, p.nombre, pv.id_producto_variante, c.nombre, t.nombre, s.id_sucursal, s.nombre
        ORDER BY unidades_vendidas DESC, monto_vendido DESC, p.nombre ASC, pv.id_producto_variante ASC
        LIMIT 10
        """,
      nativeQuery = true)
    List<Object[]> obtenerTopProductosPorUnidades(
      @Param("idSucursal") Integer idSucursal,
      @Param("fechaInicio") LocalDateTime fechaInicio,
      @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(
      value = """
        SELECT
          c.color_id,
          c.nombre AS color,
          c.codigo AS codigo_color,
          t.talla_id,
          t.nombre AS talla,
          SUM(vd.cantidad) AS unidades_vendidas,
          COALESCE(SUM(vd.total_detalle), 0) AS monto_vendido
        FROM venta_detalle vd
        JOIN venta v ON v.id_venta = vd.id_venta
        JOIN producto_variante pv ON pv.id_producto_variante = vd.id_producto_variante
        JOIN colores c ON c.color_id = pv.color_id
        JOIN tallas t ON t.talla_id = pv.talla_id
        WHERE vd.deleted_at IS NULL
          AND v.deleted_at IS NULL
          AND v.estado = 'EMITIDA'
          AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
          AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
          AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
        GROUP BY c.color_id, c.nombre, c.codigo, t.talla_id, t.nombre
        ORDER BY c.nombre ASC, t.nombre ASC
        """,
      nativeQuery = true)
    List<Object[]> obtenerHeatmapVentasPorTallaColor(
      @Param("idSucursal") Integer idSucursal,
      @Param("fechaInicio") LocalDateTime fechaInicio,
      @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(
      value = """
        SELECT
          cat.id_categoria,
          cat.nombre_categoria AS categoria,
          SUM(vd.cantidad) AS unidades_vendidas,
          COALESCE(SUM(vd.total_detalle), 0) AS monto_vendido
        FROM venta_detalle vd
        JOIN venta v ON v.id_venta = vd.id_venta
        JOIN producto_variante pv ON pv.id_producto_variante = vd.id_producto_variante
        JOIN producto p ON p.producto_id = pv.producto_id
        JOIN categoria cat ON cat.id_categoria = p.categoria_id
        WHERE vd.deleted_at IS NULL
          AND v.deleted_at IS NULL
          AND v.estado = 'EMITIDA'
          AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
          AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
          AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
        GROUP BY cat.id_categoria, cat.nombre_categoria
        ORDER BY monto_vendido DESC, unidades_vendidas DESC, cat.nombre_categoria ASC
        """,
      nativeQuery = true)
    List<Object[]> obtenerVentasPorCategoria(
      @Param("idSucursal") Integer idSucursal,
      @Param("fechaInicio") LocalDateTime fechaInicio,
      @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    List<VentaDetalle> findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(Integer idVenta);

    @Query("""
            SELECT vd
            FROM VentaDetalle vd
            WHERE vd.deletedAt IS NULL
              AND vd.venta.idVenta IN :ventaIds
            ORDER BY vd.venta.idVenta ASC, vd.idVentaDetalle ASC
            """)
    List<VentaDetalle> findActivosByVentaIds(@Param("ventaIds") List<Integer> ventaIds);

    long countByVenta_IdVentaAndDeletedAtIsNull(Integer idVenta);
}
