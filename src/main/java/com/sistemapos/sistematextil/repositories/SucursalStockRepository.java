package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Categoria;

public interface SucursalStockRepository extends JpaRepository<SucursalStock, Integer> {

    @Query("""
            SELECT COALESCE(SUM(ss.cantidad), 0)
            FROM SucursalStock ss
            JOIN ss.productoVariante v
            JOIN v.producto p
            WHERE ss.sucursal.idSucursal = :idSucursal
              AND p.idProducto = :idProducto
              AND ss.cantidad > 0
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND v.activo = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.activo = 'ACTIVO'
              AND p.publicarEcommerce = true
            """)
    long sumarStockEcommercePorProducto(
            @Param("idSucursal") Integer idSucursal,
            @Param("idProducto") Integer idProducto);

    Optional<SucursalStock> findBySucursalIdSucursalAndProductoVarianteIdProductoVariante(
            Integer idSucursal,
            Integer idProductoVariante);

    @Query(
            value = """
                    SELECT *
                    FROM sucursal_stock
                    WHERE id_sucursal = :idSucursal
                      AND id_producto_variante = :idProductoVariante
                    FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<SucursalStock> findBySucursalIdSucursalAndProductoVarianteIdProductoVarianteForUpdate(
            @Param("idSucursal") Integer idSucursal,
            @Param("idProductoVariante") Integer idProductoVariante);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.sucursal s
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            WHERE s.idSucursal = :idSucursal
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND (
                    :term IS NULL
                    OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR v.sku LIKE CONCAT(:term, '%')
                    OR v.codigoBarras LIKE CONCAT(:term, '%')
              )
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<SucursalStock> buscarPorSucursal(
            @Param("idSucursal") Integer idSucursal,
            @Param("term") String term);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.sucursal s
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH v.color c
            LEFT JOIN FETCH v.talla t
            WHERE s.idSucursal = :idSucursal
              AND ss.cantidad > 0
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.publicarEcommerce = true
              AND (:term IS NULL
                   OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(c.nombre, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(t.nombre, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(v.codigoBarras, '')) LIKE LOWER(CONCAT('%', :term, '%')))
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<SucursalStock> buscarDisponiblesParaIa(
            @Param("idSucursal") Integer idSucursal,
            @Param("term") String term,
            Pageable pageable);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.sucursal s
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH v.color c
            LEFT JOIN FETCH v.talla t
            WHERE s.idSucursal = :idSucursal
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.publicarEcommerce = true
              AND (
                    LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(COALESCE(c.nombre, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(COALESCE(t.nombre, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(COALESCE(v.codigoBarras, '')) LIKE LOWER(CONCAT('%', :term, '%')))
            ORDER BY CASE WHEN ss.cantidad > 0 THEN 0 ELSE 1 END, p.nombre ASC, v.idProductoVariante ASC
            """)
    List<SucursalStock> buscarCoincidenciasParaIa(
            @Param("idSucursal") Integer idSucursal,
            @Param("term") String term,
            Pageable pageable);

    @Query("""
            SELECT p.idProducto
            FROM SucursalStock ss
            JOIN ss.productoVariante v
            JOIN v.producto p
            WHERE ss.sucursal.idSucursal = :idSucursal
              AND ss.cantidad > 0
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.publicarEcommerce = true
            GROUP BY p.idProducto, p.nombre
            ORDER BY p.nombre ASC
            """)
    List<Integer> listarIdsProductosEcommerceDisponiblesParaIa(
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);

    @Query("""
            SELECT p.idProducto AS productId, p.nombre AS productName
            FROM SucursalStock ss
            JOIN ss.productoVariante v
            JOIN v.producto p
            WHERE ss.sucursal.idSucursal = :idSucursal
              AND ss.cantidad > 0
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.publicarEcommerce = true
            GROUP BY p.idProducto, p.nombre
            ORDER BY p.nombre ASC
            """)
    List<EcommerceProductNameView> listarNombresProductosEcommerceDisponiblesParaIa(
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);

    @Query("""
            SELECT p.idProducto
            FROM SucursalStock ss
            JOIN ss.productoVariante v
            JOIN v.producto p
            LEFT JOIN v.color c
            LEFT JOIN v.talla t
            WHERE ss.sucursal.idSucursal = :idSucursal
              AND ss.cantidad > 0
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.publicarEcommerce = true
              AND (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(:term) LIKE LOWER(CONCAT('%', p.nombre, '%'))
                   OR LOWER(COALESCE(c.nombre, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(t.nombre, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(v.codigoBarras, '')) LIKE LOWER(CONCAT('%', :term, '%')))
            GROUP BY p.idProducto, p.nombre
            ORDER BY p.nombre ASC
            """)
    List<Integer> buscarIdsProductosEcommerceDisponiblesParaIa(
            @Param("idSucursal") Integer idSucursal,
            @Param("term") String term,
            Pageable pageable);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.sucursal s
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH v.color c
            LEFT JOIN FETCH v.talla t
            WHERE s.idSucursal = :idSucursal
              AND p.idProducto IN :productIds
              AND ss.cantidad > 0
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.publicarEcommerce = true
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<SucursalStock> listarVariantesEcommerceDisponiblesParaIa(
            @Param("idSucursal") Integer idSucursal,
            @Param("productIds") List<Integer> productIds);

    @Query("""
            SELECT DISTINCT categoria
            FROM SucursalStock ss
            JOIN ss.productoVariante v
            JOIN v.producto p
            JOIN p.categoria categoria
            WHERE ss.sucursal.idSucursal = :idSucursal
              AND ss.cantidad > 0
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND categoria.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND categoria.estado = 'ACTIVO'
            ORDER BY categoria.nombreCategoria ASC
            """)
    List<Categoria> listarCategoriasDisponiblesParaIa(
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);

    interface EcommerceProductNameView {
        Integer getProductId();
        String getProductName();
    }

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.sucursal s
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            WHERE v.idProductoVariante = :idProductoVariante
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            ORDER BY s.idSucursal ASC
            """)
    List<SucursalStock> listarPorVariante(@Param("idProductoVariante") Integer idProductoVariante);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.sucursal s
            JOIN FETCH ss.productoVariante v
            WHERE v.idProductoVariante IN :idsProductoVariante
            ORDER BY v.idProductoVariante ASC, s.idSucursal ASC
            """)
    List<SucursalStock> listarPorVariantes(@Param("idsProductoVariante") List<Integer> idsProductoVariante);

    @Query(
            value = """
                    SELECT
                        pv.id_producto_variante,
                        p.nombre AS producto,
                        COALESCE(c.nombre, '-') AS color,
                        COALESCE(t.nombre, '-') AS talla,
                        ss.cantidad
                    FROM sucursal_stock ss
                    JOIN producto_variante pv ON pv.id_producto_variante = ss.id_producto_variante
                    JOIN producto p ON p.producto_id = pv.producto_id
                    LEFT JOIN colores c ON c.color_id = pv.color_id
                    LEFT JOIN tallas t ON t.talla_id = pv.talla_id
                    WHERE ss.id_sucursal = :idSucursal
                      AND ss.cantidad > 0
                      AND pv.deleted_at IS NULL
                      AND p.deleted_at IS NULL
                    ORDER BY ss.cantidad DESC, p.nombre ASC, pv.id_producto_variante ASC
                    LIMIT 5
                    """,
            nativeQuery = true)
    List<Object[]> obtenerTopStockActual(@Param("idSucursal") Integer idSucursal);
}
