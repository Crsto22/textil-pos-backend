package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.HistorialStock;

@Repository
public interface HistorialStockRepository extends JpaRepository<HistorialStock, Integer> {

    Page<HistorialStock> findAllByOrderByFechaDesc(Pageable pageable);

    Page<HistorialStock> findBySucursalIdSucursalOrderByFechaDesc(Integer idSucursal, Pageable pageable);

    List<HistorialStock> findByProductoVarianteProductoIdProductoOrderByFechaDesc(Integer idProducto);

    List<HistorialStock> findByProductoVarianteProductoIdProductoAndSucursalIdSucursalOrderByFechaDesc(
            Integer idProducto,
            Integer idSucursal);

    List<HistorialStock> findByProductoVarianteIdProductoVarianteOrderByFechaDesc(Integer idProductoVariante);

    List<HistorialStock> findByProductoVarianteIdProductoVarianteAndSucursalIdSucursalOrderByFechaDesc(
            Integer idProductoVariante,
            Integer idSucursal);

    List<HistorialStock> findBySucursalIdSucursalOrderByFechaDesc(Integer idSucursal);

    List<HistorialStock> findTop10BySucursalIdSucursalOrderByFechaDesc(Integer idSucursal);

    @Query(
            value = """
                    SELECT
                        COUNT(*) AS total_movimientos,
                        COALESCE(SUM(CASE WHEN tipo_movimiento IN ('ENTRADA', 'DEVOLUCION') THEN cantidad ELSE 0 END), 0) AS unidades_entrada,
                        COALESCE(SUM(CASE WHEN tipo_movimiento IN ('SALIDA', 'VENTA') THEN cantidad ELSE 0 END), 0) AS unidades_salida,
                        COALESCE(SUM(CASE WHEN tipo_movimiento = 'AJUSTE' THEN ABS(cantidad) ELSE 0 END), 0) AS unidades_ajuste,
                        COALESCE(SUM(CASE WHEN tipo_movimiento = 'RESERVA' THEN cantidad ELSE 0 END), 0) AS unidades_reserva,
                        COALESCE(SUM(CASE WHEN tipo_movimiento = 'LIBERACION' THEN cantidad ELSE 0 END), 0) AS unidades_liberacion
                    FROM historial_stock
                    WHERE id_sucursal = :idSucursal
                    """,
            nativeQuery = true)
    Object[] obtenerResumenMovimientos(@Param("idSucursal") Integer idSucursal);

    @Query(
            value = """
                    SELECT
                        hs.id_producto_variante,
                        p.nombre AS producto,
                        COALESCE(c.nombre, '-') AS color,
                        COALESCE(t.nombre, '-') AS talla,
                        COALESCE(SUM(hs.cantidad), 0) AS cantidad_salida
                    FROM historial_stock hs
                    JOIN producto_variante pv ON pv.id_producto_variante = hs.id_producto_variante
                    JOIN producto p ON p.producto_id = pv.producto_id
                    LEFT JOIN colores c ON c.color_id = pv.color_id
                    LEFT JOIN tallas t ON t.talla_id = pv.talla_id
                    WHERE hs.id_sucursal = :idSucursal
                      AND hs.tipo_movimiento IN ('SALIDA', 'VENTA')
                      AND pv.deleted_at IS NULL
                      AND p.deleted_at IS NULL
                    GROUP BY hs.id_producto_variante, p.nombre, c.nombre, t.nombre
                    ORDER BY cantidad_salida DESC, p.nombre ASC, hs.id_producto_variante ASC
                    LIMIT 5
                    """,
            nativeQuery = true)
    List<Object[]> obtenerTopProductosConSalida(@Param("idSucursal") Integer idSucursal);
}
