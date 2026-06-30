package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoColorGroupProjection;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteDisponibleExcelRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteStockSucursalRow;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Integer> {

    @Query("""
            SELECT v
            FROM ProductoVariante v
            JOIN v.producto p
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            ORDER BY
              CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM SucursalStock ssStock
                    WHERE ssStock.productoVariante = v
                      AND ssStock.cantidad > 0
                ) THEN 0
                ELSE 1
              END ASC,
              v.createdAt DESC,
              v.idProductoVariante DESC
            """)
    List<ProductoVariante> findByDeletedAtIsNullOrderByStockDescCreatedAtDescIdProductoVarianteDesc();

    @Query("""
            SELECT DISTINCT v
            FROM ProductoVariante v
            JOIN SucursalStock ss ON ss.productoVariante = v
            WHERE v.deletedAt IS NULL
              AND v.producto.deletedAt IS NULL
              AND ss.sucursal.idSucursal = :idSucursal
            ORDER BY
              CASE WHEN ss.cantidad > 0 THEN 0 ELSE 1 END ASC,
              v.createdAt DESC,
              v.idProductoVariante DESC
            """)
    List<ProductoVariante> findByDeletedAtIsNullAndSucursal_IdSucursal(@Param("idSucursal") Integer idSucursal);

    List<ProductoVariante> findByProductoIdProducto(Integer idProducto);

    List<ProductoVariante> findByProductoIdProductoAndDeletedAtIsNull(Integer idProducto);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            JOIN v.producto p
            WHERE p.idProducto = :idProducto
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            ORDER BY
              CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM SucursalStock ssStock
                    WHERE ssStock.productoVariante = v
                      AND ssStock.cantidad > 0
                ) THEN 0
                ELSE 1
              END ASC,
              v.createdAt DESC,
              v.idProductoVariante DESC
            """)
    List<ProductoVariante> findByProductoIdProductoAndDeletedAtIsNullOrderByStockDescCreatedAtDescIdProductoVarianteDesc(
            @Param("idProducto") Integer idProducto);

    @Query("""
            SELECT DISTINCT v
            FROM ProductoVariante v
            JOIN SucursalStock ss ON ss.productoVariante = v
            WHERE v.producto.idProducto = :idProducto
              AND v.deletedAt IS NULL
              AND v.producto.deletedAt IS NULL
              AND ss.sucursal.idSucursal = :idSucursal
            ORDER BY
              CASE WHEN ss.cantidad > 0 THEN 0 ELSE 1 END ASC,
              v.createdAt DESC,
              v.idProductoVariante DESC
            """)
    List<ProductoVariante> findByProductoIdProductoAndDeletedAtIsNullAndSucursal_IdSucursal(
            @Param("idProducto") Integer idProducto,
            @Param("idSucursal") Integer idSucursal);

    Optional<ProductoVariante> findByIdProductoVariante(Integer idProductoVariante);

    Optional<ProductoVariante> findByIdProductoVarianteAndDeletedAtIsNull(Integer idProductoVariante);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            JOIN v.producto p
            WHERE v.idProductoVariante = :idProductoVariante
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM SucursalStock ss
                    WHERE ss.productoVariante = v
                      AND ss.sucursal.idSucursal = :idSucursal
              )
            """)
    Optional<ProductoVariante> findByIdProductoVarianteAndDeletedAtIsNullAndSucursal_IdSucursal(
            @Param("idProductoVariante") Integer idProductoVariante,
            @Param("idSucursal") Integer idSucursal);

    List<ProductoVariante> findByIdProductoVarianteInAndDeletedAtIsNull(List<Integer> idsProductoVariante);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            WHERE v.codigoBarras = :codigoBarras
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            """)
    Optional<ProductoVariante> findEscaneableByCodigoBarras(@Param("codigoBarras") String codigoBarras);

    Optional<ProductoVariante> findByProductoIdProductoAndTallaIdTallaAndColorIdColor(
            Integer idProducto,
            Integer idTalla,
            Integer idColor);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            WHERE v.producto.idProducto = :idProducto
              AND v.talla.idTalla = :idTalla
              AND v.color.idColor = :idColor
            """)
    Optional<ProductoVariante> findByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
            @Param("idProducto") Integer idProducto,
            @Param("idTalla") Integer idTalla,
            @Param("idColor") Integer idColor,
            @Param("idSucursal") Integer idSucursal);

    @Query(
            value = """
                    SELECT *
                    FROM producto_variante
                    WHERE id_producto_variante = :idProductoVariante
                    FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<ProductoVariante> findByIdProductoVarianteForUpdate(
            @Param("idProductoVariante") Integer idProductoVariante);

    void deleteByProductoIdProducto(Integer idProducto);

    Optional<ProductoVariante> findFirstByProductoIdProductoOrderByIdProductoVarianteAsc(Integer idProducto);

    Optional<ProductoVariante> findFirstByProductoIdProductoAndDeletedAtIsNullOrderByIdProductoVarianteAsc(Integer idProducto);

    List<ProductoVariante> findByPrecioOfertaIsNotNullAndOfertaFinLessThanEqualAndDeletedAtIsNull(LocalDateTime fechaHora);

    Page<ProductoVariante> findByPrecioOfertaIsNotNullAndDeletedAtIsNull(Pageable pageable);

    @Query(
            value = """
                    SELECT
                        p.producto_id AS productoId,
                        p.nombre AS productoNombre,
                        p.slug AS productoSlug,
                        p.descripcion AS productoDescripcion,
                        p.estado AS productoEstado,
                        p.created_at AS fechaCreacion,
                        p.imagen_global_url AS imagenGlobalUrl,
                        p.imagen_global_thumb_url AS imagenGlobalThumbUrl,
                        cat.id_categoria AS categoriaId,
                        cat.nombre_categoria AS categoriaNombre,
                        c.color_id AS colorId,
                        c.nombre AS colorNombre,
                        c.codigo AS colorHex,
                        CAST(COALESCE(SUM(CASE WHEN ss.id_sucursal = :idSucursal THEN ss.cantidad ELSE 0 END), 0) AS SIGNED) AS stockTotalColor,
                        CAST(COUNT(DISTINCT v.id_producto_variante) AS SIGNED) AS totalVariantes,
                        CAST(COUNT(DISTINCT CASE WHEN ss.id_sucursal = :idSucursal AND ss.cantidad > 0 THEN v.id_producto_variante END) AS SIGNED) AS variantesConStock
                    FROM producto_variante v
                    JOIN producto p ON p.producto_id = v.producto_id
                    JOIN categoria cat ON cat.id_categoria = p.categoria_id
                    JOIN colores c ON c.color_id = v.color_id
                    JOIN tallas t ON t.talla_id = v.talla_id
                    LEFT JOIN sucursal_stock ss ON ss.id_producto_variante = v.id_producto_variante
                    LEFT JOIN producto_variante_oferta_sucursal sov
                      ON sov.id_producto_variante = v.id_producto_variante
                     AND sov.id_sucursal = :idSucursal
                     AND sov.deleted_at IS NULL
                    WHERE p.publicar_ecommerce = 1
                      AND p.deleted_at IS NULL
                      AND p.activo = 1
                      AND p.estado = 'ACTIVO'
                      AND cat.deleted_at IS NULL
                      AND cat.activo = 1
                      AND v.deleted_at IS NULL
                      AND v.activo = 1
                      AND c.deleted_at IS NULL
                      AND c.activo = 1
                      AND t.deleted_at IS NULL
                      AND t.activo = 1
                      AND (:idCategoria IS NULL OR cat.id_categoria = :idCategoria)
                      AND (:idColor IS NULL OR c.color_id = :idColor)
                      AND (:tallasCsv IS NULL OR FIND_IN_SET(UPPER(t.nombre), :tallasCsv) > 0)
                      AND (
                            :precioMax IS NULL
                            OR COALESCE(
                                CASE
                                  WHEN sov.precio_oferta IS NOT NULL
                                   AND sov.precio_oferta > 0
                                   AND sov.precio_oferta < v.precio
                                   AND (
                                        (sov.oferta_inicio IS NULL AND sov.oferta_fin IS NULL)
                                        OR (sov.oferta_inicio IS NOT NULL AND sov.oferta_fin IS NOT NULL AND NOW() BETWEEN sov.oferta_inicio AND sov.oferta_fin)
                                   )
                                  THEN sov.precio_oferta
                                END,
                                CASE
                                  WHEN v.precio_oferta IS NOT NULL
                                   AND v.precio_oferta > 0
                                   AND v.precio_oferta < v.precio
                                   AND (
                                        (v.oferta_inicio IS NULL AND v.oferta_fin IS NULL)
                                        OR (v.oferta_inicio IS NOT NULL AND v.oferta_fin IS NOT NULL AND NOW() BETWEEN v.oferta_inicio AND v.oferta_fin)
                                   )
                                  THEN v.precio_oferta
                                END,
                                v.precio
                            ) <= :precioMax
                      )
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                            OR v.codigo_barras LIKE CONCAT(:term, '%')
                            OR (
                                (:token1 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token1, '%')))
                                AND (:token2 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token2, '%')))
                                AND (:token3 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token3, '%')))
                                AND (:token4 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token4, '%')))
                                AND (:token5 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token5, '%')))
                                AND (:token6 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token6, '%')))
                            )
                      )
                    GROUP BY
                        p.producto_id,
                        p.nombre,
                        p.slug,
                        p.descripcion,
                        p.estado,
                        p.created_at,
                        p.imagen_global_url,
                        p.imagen_global_thumb_url,
                        cat.id_categoria,
                        cat.nombre_categoria,
                        c.color_id,
                        c.nombre,
                        c.codigo
                    HAVING (
                        :soloDisponibles = 0
                        OR COALESCE(SUM(CASE WHEN ss.id_sucursal = :idSucursal THEN ss.cantidad ELSE 0 END), 0) > 0
                    )
                    ORDER BY
                        CASE WHEN COALESCE(SUM(CASE WHEN ss.id_sucursal = :idSucursal THEN ss.cantidad ELSE 0 END), 0) > 0 THEN 0 ELSE 1 END ASC,
                        p.created_at DESC,
                        p.producto_id DESC,
                        c.nombre ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT p.producto_id, c.color_id
                        FROM producto_variante v
                        JOIN producto p ON p.producto_id = v.producto_id
                        JOIN categoria cat ON cat.id_categoria = p.categoria_id
                        JOIN colores c ON c.color_id = v.color_id
                        JOIN tallas t ON t.talla_id = v.talla_id
                        LEFT JOIN sucursal_stock ss ON ss.id_producto_variante = v.id_producto_variante
                        LEFT JOIN producto_variante_oferta_sucursal sov
                          ON sov.id_producto_variante = v.id_producto_variante
                         AND sov.id_sucursal = :idSucursal
                         AND sov.deleted_at IS NULL
                        WHERE p.publicar_ecommerce = 1
                          AND p.deleted_at IS NULL
                          AND p.activo = 1
                          AND p.estado = 'ACTIVO'
                          AND cat.deleted_at IS NULL
                          AND cat.activo = 1
                          AND v.deleted_at IS NULL
                          AND v.activo = 1
                          AND c.deleted_at IS NULL
                          AND c.activo = 1
                          AND t.deleted_at IS NULL
                          AND t.activo = 1
                          AND (:idCategoria IS NULL OR cat.id_categoria = :idCategoria)
                          AND (:idColor IS NULL OR c.color_id = :idColor)
                          AND (:tallasCsv IS NULL OR FIND_IN_SET(UPPER(t.nombre), :tallasCsv) > 0)
                          AND (
                                :precioMax IS NULL
                                OR COALESCE(
                                    CASE
                                      WHEN sov.precio_oferta IS NOT NULL
                                       AND sov.precio_oferta > 0
                                       AND sov.precio_oferta < v.precio
                                       AND (
                                            (sov.oferta_inicio IS NULL AND sov.oferta_fin IS NULL)
                                            OR (sov.oferta_inicio IS NOT NULL AND sov.oferta_fin IS NOT NULL AND NOW() BETWEEN sov.oferta_inicio AND sov.oferta_fin)
                                       )
                                      THEN sov.precio_oferta
                                    END,
                                    CASE
                                      WHEN v.precio_oferta IS NOT NULL
                                       AND v.precio_oferta > 0
                                       AND v.precio_oferta < v.precio
                                       AND (
                                            (v.oferta_inicio IS NULL AND v.oferta_fin IS NULL)
                                            OR (v.oferta_inicio IS NOT NULL AND v.oferta_fin IS NOT NULL AND NOW() BETWEEN v.oferta_inicio AND v.oferta_fin)
                                       )
                                      THEN v.precio_oferta
                                    END,
                                    v.precio
                                ) <= :precioMax
                          )
                          AND (
                                :term IS NULL
                                OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                                OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                                OR v.sku LIKE CONCAT(:term, '%')
                                OR v.codigo_barras LIKE CONCAT(:term, '%')
                                OR (
                                    (:token1 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token1, '%')))
                                    AND (:token2 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token2, '%')))
                                    AND (:token3 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token3, '%')))
                                    AND (:token4 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token4, '%')))
                                    AND (:token5 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token5, '%')))
                                    AND (:token6 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigo_barras, ''))) LIKE LOWER(CONCAT('%', :token6, '%')))
                                )
                          )
                        GROUP BY p.producto_id, c.color_id
                        HAVING (
                            :soloDisponibles = 0
                            OR COALESCE(SUM(CASE WHEN ss.id_sucursal = :idSucursal THEN ss.cantidad ELSE 0 END), 0) > 0
                        )
                    ) grupos
                    """,
            nativeQuery = true)
    Page<EcommerceProductoColorGroupProjection> listarGruposEcommerce(
            @Param("term") String term,
            @Param("token1") String token1,
            @Param("token2") String token2,
            @Param("token3") String token3,
            @Param("token4") String token4,
            @Param("token5") String token5,
            @Param("token6") String token6,
            @Param("idSucursal") Integer idSucursal,
            @Param("idCategoria") Integer idCategoria,
            @Param("idColor") Integer idColor,
            @Param("tallasCsv") String tallasCsv,
            @Param("precioMax") Double precioMax,
            @Param("soloDisponibles") boolean soloDisponibles,
            Pageable pageable);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            JOIN FETCH v.producto p
            JOIN FETCH p.categoria cat
            JOIN FETCH v.color c
            JOIN FETCH v.talla t
            WHERE p.publicarEcommerce = true
              AND p.deletedAt IS NULL
              AND p.activo = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND cat.deletedAt IS NULL
              AND cat.estado = 'ACTIVO'
              AND v.deletedAt IS NULL
              AND v.activo = 'ACTIVO'
              AND c.deletedAt IS NULL
              AND c.estado = 'ACTIVO'
              AND t.deletedAt IS NULL
              AND t.estado = 'ACTIVO'
              AND p.idProducto IN :productoIds
              AND c.idColor IN :colorIds
            ORDER BY p.idProducto ASC, c.nombre ASC, t.nombre ASC, v.idProductoVariante ASC
            """)
    List<ProductoVariante> listarVariantesEcommercePorProductosYColores(
            @Param("productoIds") List<Integer> productoIds,
            @Param("colorIds") List<Integer> colorIds);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            JOIN FETCH v.producto p
            JOIN FETCH p.categoria cat
            JOIN FETCH v.color c
            JOIN FETCH v.talla t
            WHERE p.publicarEcommerce = true
              AND p.deletedAt IS NULL
              AND p.activo = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND cat.deletedAt IS NULL
              AND cat.estado = 'ACTIVO'
              AND v.deletedAt IS NULL
              AND v.activo = 'ACTIVO'
              AND c.deletedAt IS NULL
              AND c.estado = 'ACTIVO'
              AND t.deletedAt IS NULL
              AND t.estado = 'ACTIVO'
              AND p.idProducto = :idProducto
            ORDER BY c.nombre ASC, t.nombre ASC, v.idProductoVariante ASC
            """)
    List<ProductoVariante> listarVariantesEcommercePorProducto(@Param("idProducto") Integer idProducto);

    @Query(
            value = """
                    SELECT
                        p.producto_id AS productoId,
                        p.nombre AS productoNombre,
                        p.slug AS productoSlug,
                        p.descripcion AS productoDescripcion,
                        p.estado AS productoEstado,
                        p.created_at AS fechaCreacion,
                        p.imagen_global_url AS imagenGlobalUrl,
                        p.imagen_global_thumb_url AS imagenGlobalThumbUrl,
                        cat.id_categoria AS categoriaId,
                        cat.nombre_categoria AS categoriaNombre,
                        c.color_id AS colorId,
                        c.nombre AS colorNombre,
                        c.codigo AS colorHex,
                        CAST(COALESCE(SUM(ss.cantidad), 0) AS SIGNED) AS stockTotalColor,
                        CAST(COUNT(DISTINCT v.id_producto_variante) AS SIGNED) AS totalVariantes,
                        CAST(COUNT(DISTINCT CASE WHEN ss.cantidad > 0 THEN v.id_producto_variante END) AS SIGNED) AS variantesConStock
                    FROM producto_variante v
                    JOIN producto p ON p.producto_id = v.producto_id
                    JOIN categoria cat ON cat.id_categoria = p.categoria_id
                    JOIN colores c ON c.color_id = v.color_id
                    JOIN tallas t ON t.talla_id = v.talla_id
                    JOIN sucursal_stock ss ON ss.id_producto_variante = v.id_producto_variante AND ss.id_sucursal = :idSucursal
                    WHERE p.publicar_ecommerce = 1
                      AND p.deleted_at IS NULL
                      AND p.activo = 1
                      AND p.estado = 'ACTIVO'
                      AND cat.deleted_at IS NULL
                      AND cat.activo = 1
                      AND v.deleted_at IS NULL
                      AND v.activo = 1
                      AND c.deleted_at IS NULL
                      AND c.activo = 1
                      AND t.deleted_at IS NULL
                      AND t.activo = 1
                    GROUP BY
                        p.producto_id,
                        p.nombre,
                        p.slug,
                        p.descripcion,
                        p.estado,
                        p.created_at,
                        p.imagen_global_url,
                        p.imagen_global_thumb_url,
                        cat.id_categoria,
                        cat.nombre_categoria,
                        c.color_id,
                        c.nombre,
                        c.codigo
                    HAVING COALESCE(SUM(ss.cantidad), 0) > 0
                    ORDER BY RAND()
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<EcommerceProductoColorGroupProjection> listarAleatoriosEcommerce(
            @Param("idSucursal") Integer idSucursal,
            @Param("limit") int limit);

    @Query(
            value = """
                    SELECT
                        p.producto_id AS productoId,
                        p.nombre AS productoNombre,
                        p.slug AS productoSlug,
                        p.descripcion AS productoDescripcion,
                        p.estado AS productoEstado,
                        p.created_at AS fechaCreacion,
                        p.imagen_global_url AS imagenGlobalUrl,
                        p.imagen_global_thumb_url AS imagenGlobalThumbUrl,
                        cat.id_categoria AS categoriaId,
                        cat.nombre_categoria AS categoriaNombre,
                        c.color_id AS colorId,
                        c.nombre AS colorNombre,
                        c.codigo AS colorHex,
                        CAST(COALESCE(SUM(ss.cantidad), 0) AS SIGNED) AS stockTotalColor,
                        CAST(COUNT(DISTINCT v.id_producto_variante) AS SIGNED) AS totalVariantes,
                        CAST(COUNT(DISTINCT CASE WHEN ss.cantidad > 0 THEN v.id_producto_variante END) AS SIGNED) AS variantesConStock
                    FROM producto_variante v
                    JOIN producto p ON p.producto_id = v.producto_id
                    JOIN categoria cat ON cat.id_categoria = p.categoria_id
                    JOIN colores c ON c.color_id = v.color_id
                    JOIN tallas t ON t.talla_id = v.talla_id
                    JOIN sucursal_stock ss ON ss.id_producto_variante = v.id_producto_variante AND ss.id_sucursal = :idSucursal
                    WHERE p.publicar_ecommerce = 1
                      AND p.producto_id <> :idProducto
                      AND p.deleted_at IS NULL
                      AND p.activo = 1
                      AND p.estado = 'ACTIVO'
                      AND cat.deleted_at IS NULL
                      AND cat.activo = 1
                      AND v.deleted_at IS NULL
                      AND v.activo = 1
                      AND c.deleted_at IS NULL
                      AND c.activo = 1
                      AND t.deleted_at IS NULL
                      AND t.activo = 1
                    GROUP BY
                        p.producto_id,
                        p.nombre,
                        p.slug,
                        p.descripcion,
                        p.estado,
                        p.created_at,
                        p.imagen_global_url,
                        p.imagen_global_thumb_url,
                        cat.id_categoria,
                        cat.nombre_categoria,
                        c.color_id,
                        c.nombre,
                        c.codigo
                    HAVING COALESCE(SUM(ss.cantidad), 0) > 0
                    ORDER BY RAND()
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<EcommerceProductoColorGroupProjection> listarRecomendadosEcommerce(
            @Param("idSucursal") Integer idSucursal,
            @Param("idProducto") Integer idProducto,
            @Param("limit") int limit);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            WHERE v.deletedAt IS NULL
              AND (
                    v.precioOferta IS NOT NULL
                    OR EXISTS (
                        SELECT 1
                        FROM ProductoVarianteOfertaSucursal vos
                        WHERE vos.productoVariante = v
                          AND vos.sucursal.idSucursal = :idSucursal
                          AND vos.deletedAt IS NULL
                    )
              )
              AND EXISTS (
                    SELECT 1
                    FROM SucursalStock ss
                    WHERE ss.productoVariante = v
                      AND ss.sucursal.idSucursal = :idSucursal
              )
            """)
    Page<ProductoVariante> findConOfertaConfiguradaPorSucursal(
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);

    @Query("""
            SELECT v
            FROM ProductoVariante v
            WHERE v.precioOferta IS NOT NULL
              AND v.deletedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM SucursalStock ss
                    WHERE ss.productoVariante = v
                      AND ss.sucursal.idSucursal = :idSucursal
              )
            """)
    Page<ProductoVariante> findByPrecioOfertaIsNotNullAndDeletedAtIsNullAndSucursal_IdSucursal(
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);

    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColor(
            Integer idProducto,
            Integer idTalla,
            Integer idColor);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdProductoVarianteNot(
            String sku,
            Integer idProductoVariante);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.sku = :sku
            """)
    boolean existsBySucursalIdSucursalAndSku(@Param("idSucursal") Integer idSucursal, @Param("sku") String sku);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.sku = :sku
              AND v.idProductoVariante <> :idProductoVariante
            """)
    boolean existsBySucursalIdSucursalAndSkuAndIdProductoVarianteNot(
            @Param("idSucursal") Integer idSucursal,
            @Param("sku") String sku,
            @Param("idProductoVariante") Integer idProductoVariante);

    boolean existsByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarrasAndIdProductoVarianteNot(
            String codigoBarras,
            Integer idProductoVariante);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.codigoBarras = :codigoBarras
            """)
    boolean existsBySucursalIdSucursalAndCodigoBarras(
            @Param("idSucursal") Integer idSucursal,
            @Param("codigoBarras") String codigoBarras);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.codigoBarras = :codigoBarras
              AND v.idProductoVariante <> :idProductoVariante
            """)
    boolean existsBySucursalIdSucursalAndCodigoBarrasAndIdProductoVarianteNot(
            @Param("idSucursal") Integer idSucursal,
            @Param("codigoBarras") String codigoBarras,
            @Param("idProductoVariante") Integer idProductoVariante);

    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndIdProductoVarianteNot(
            Integer idProducto,
            Integer idTalla,
            Integer idColor,
            Integer idProductoVariante);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.producto.idProducto = :idProducto
              AND v.talla.idTalla = :idTalla
              AND v.color.idColor = :idColor
              AND v.idProductoVariante <> :idProductoVariante
            """)
    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursalAndIdProductoVarianteNot(
            @Param("idProducto") Integer idProducto,
            @Param("idTalla") Integer idTalla,
            @Param("idColor") Integer idColor,
            @Param("idSucursal") Integer idSucursal,
            @Param("idProductoVariante") Integer idProductoVariante);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.sku = :sku
              AND (:idProductoExcluir IS NULL OR v.producto.idProducto <> :idProductoExcluir)
            """)
    boolean existsSkuParaOtroProducto(
            @Param("sku") String sku,
            @Param("idProductoExcluir") Integer idProductoExcluir);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.sku = :sku
              AND (:idProductoExcluir IS NULL OR v.producto.idProducto <> :idProductoExcluir)
            """)
    boolean existsSkuEnSucursalParaOtroProducto(
            @Param("idSucursal") Integer idSucursal,
            @Param("sku") String sku,
            @Param("idProductoExcluir") Integer idProductoExcluir);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.codigoBarras = :codigoBarras
              AND (:idProductoExcluir IS NULL OR v.producto.idProducto <> :idProductoExcluir)
            """)
    boolean existsCodigoBarrasParaOtroProducto(
            @Param("idSucursal") Integer idSucursal,
            @Param("codigoBarras") String codigoBarras,
            @Param("idProductoExcluir") Integer idProductoExcluir);

    default boolean existsCodigoBarrasParaOtroProducto(
            String codigoBarras,
            Integer idProductoExcluir) {
        return existsCodigoBarrasParaOtroProducto(null, codigoBarras, idProductoExcluir);
    }

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.producto.idProducto = :idProducto
              AND v.color.idColor = :idColor
              AND v.deletedAt IS NULL
              AND v.producto.deletedAt IS NULL
            """)
    boolean existsVarianteActivaPorProductoYColor(
            @Param("idProducto") Integer idProducto,
            @Param("idColor") Integer idColor);

    @Query("""
            SELECT COUNT(v)
            FROM ProductoVariante v
            JOIN v.producto p
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            """)
    long contarVariantesActivasParaReporte();

    @Query("""
            SELECT COUNT(DISTINCT v.idProductoVariante)
            FROM ProductoVariante v
            JOIN v.producto p
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND (
                    :idSucursal IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM SucursalStock ss
                        WHERE ss.productoVariante = v
                          AND ss.sucursal.idSucursal = :idSucursal
                    )
              )
            """)
    long contarVariantesActivasParaReportePorSucursal(@Param("idSucursal") Integer idSucursal);

    default long contarVariantesActivasParaReporte(Integer idSucursal) {
        return contarVariantesActivasParaReportePorSucursal(idSucursal);
    }

    default long contarVariantesSinStockParaReporte(Integer idSucursal) {
        return contarVariantesAgotadas(idSucursal);
    }

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow(
                v.producto.idProducto,
                v.idProductoVariante,
                v.sku,
                v.codigoBarras,
                v.color.idColor,
                v.color.nombre,
                v.color.codigo,
                v.talla.idTalla,
                v.talla.nombre,
                v.precio,
                v.precioMayor,
                v.precioOferta,
                v.ofertaInicio,
                v.ofertaFin,
                COALESCE(SUM(ss.cantidad), 0),
                v.estado
            )
            FROM ProductoVariante v
            LEFT JOIN SucursalStock ss ON ss.productoVariante = v
            WHERE v.producto.idProducto IN :productoIds
              AND v.deletedAt IS NULL
              AND v.producto.deletedAt IS NULL
            GROUP BY
                v.producto.idProducto,
                v.idProductoVariante,
                v.sku,
                v.codigoBarras,
                v.color.idColor,
                v.color.nombre,
                v.color.codigo,
                v.talla.idTalla,
                v.talla.nombre,
                v.precio,
                v.precioMayor,
                v.precioOferta,
                v.ofertaInicio,
                v.ofertaFin,
                v.estado
            """)
    List<ProductoVarianteResumenRow> obtenerResumenPorProductos(@Param("productoIds") List<Integer> productoIds);

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow(
                v.producto.idProducto,
                v.idProductoVariante,
                v.sku,
                v.codigoBarras,
                v.color.idColor,
                v.color.nombre,
                v.color.codigo,
                v.talla.idTalla,
                v.talla.nombre,
                v.precio,
                v.precioMayor,
                v.precioOferta,
                v.ofertaInicio,
                v.ofertaFin,
                COALESCE(SUM(
                    CASE
                        WHEN :idSucursal IS NOT NULL AND ss.sucursal.idSucursal = :idSucursal THEN ss.cantidad
                        WHEN :idSucursal IS NULL AND (:tipoSucursal IS NULL OR ss.sucursal.tipo = :tipoSucursal) THEN ss.cantidad
                        ELSE 0
                    END
                ), 0),
                v.estado
            )
            FROM ProductoVariante v
            LEFT JOIN SucursalStock ss ON ss.productoVariante = v
            WHERE v.producto.idProducto IN :productoIds
              AND v.deletedAt IS NULL
              AND v.producto.deletedAt IS NULL
            GROUP BY
                v.producto.idProducto,
                v.idProductoVariante,
                v.sku,
                v.codigoBarras,
                v.color.idColor,
                v.color.nombre,
                v.color.codigo,
                v.talla.idTalla,
                v.talla.nombre,
                v.precio,
                v.precioMayor,
                v.precioOferta,
                v.ofertaInicio,
                v.ofertaFin,
                v.estado
            HAVING (
                :soloDisponibles IS NULL
                OR :soloDisponibles = false
                OR COALESCE(SUM(
                    CASE
                        WHEN :idSucursal IS NOT NULL AND ss.sucursal.idSucursal = :idSucursal THEN ss.cantidad
                        WHEN :idSucursal IS NULL AND (:tipoSucursal IS NULL OR ss.sucursal.tipo = :tipoSucursal) THEN ss.cantidad
                        ELSE 0
                    END
                ), 0) > 0
            )
            """)
    List<ProductoVarianteResumenRow> obtenerResumenCatalogoPorProductos(
            @Param("productoIds") List<Integer> productoIds,
            @Param("idSucursal") Integer idSucursal,
            @Param("tipoSucursal") SucursalTipo tipoSucursal,
            @Param("soloDisponibles") Boolean soloDisponibles);

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteStockSucursalRow(
                v.idProductoVariante,
                s.idSucursal,
                s.nombre,
                COALESCE(SUM(ss.cantidad), 0)
            )
            FROM SucursalStock ss
            JOIN ss.productoVariante v
            JOIN ss.sucursal s
            WHERE v.idProductoVariante IN :idsProductoVariante
              AND (
                    (:idSucursal IS NOT NULL AND s.idSucursal = :idSucursal)
                    OR (:idSucursal IS NULL AND (:tipoSucursal IS NULL OR s.tipo = :tipoSucursal))
              )
            GROUP BY v.idProductoVariante, s.idSucursal, s.nombre
            ORDER BY v.idProductoVariante ASC, s.idSucursal ASC
            """)
    List<ProductoVarianteStockSucursalRow> obtenerStocksCatalogoPorVariantes(
            @Param("idsProductoVariante") List<Integer> idsProductoVariante,
            @Param("idSucursal") Integer idSucursal,
            @Param("tipoSucursal") SucursalTipo tipoSucursal);

    @Query(
            value = """
                    SELECT DISTINCT v
                    FROM ProductoVariante v
                    JOIN FETCH v.producto p
                    LEFT JOIN FETCH p.categoria
                    LEFT JOIN FETCH v.color c
                    LEFT JOIN FETCH v.talla t
                    WHERE v.deletedAt IS NULL
                      AND p.deletedAt IS NULL
                      AND (:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria)
                      AND (:idColor IS NULL OR v.color.idColor = :idColor)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                            OR v.codigoBarras LIKE CONCAT(:term, '%')
                            OR (
                                (:token1 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token1, '%')))
                                AND (:token2 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token2, '%')))
                                AND (:token3 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token3, '%')))
                                AND (:token4 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token4, '%')))
                                AND (:token5 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token5, '%')))
                                AND (:token6 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token6, '%')))
                            )
                      )
                      AND (
                            :conOferta IS NULL
                            OR (
                                :conOferta = true AND (
                                    (
                                        v.precioOferta IS NOT NULL
                                        AND (v.ofertaInicio IS NULL OR v.ofertaInicio <= CURRENT_TIMESTAMP)
                                        AND (v.ofertaFin IS NULL OR v.ofertaFin >= CURRENT_TIMESTAMP)
                                    )
                                    OR (
                                        :idSucursal IS NOT NULL
                                        AND EXISTS (
                                            SELECT 1
                                            FROM ProductoVarianteOfertaSucursal vos
                                            WHERE vos.productoVariante = v
                                              AND vos.sucursal.idSucursal = :idSucursal
                                              AND vos.deletedAt IS NULL
                                              AND vos.precioOferta IS NOT NULL
                                              AND (vos.ofertaInicio IS NULL OR vos.ofertaInicio <= CURRENT_TIMESTAMP)
                                              AND (vos.ofertaFin IS NULL OR vos.ofertaFin >= CURRENT_TIMESTAMP)
                                        )
                                    )
                                )
                            )
                      )
                      AND (
                            :soloDisponibles IS NULL
                            OR :soloDisponibles = false
                            OR EXISTS (
                                SELECT 1
                                FROM SucursalStock ssDisponible
                                JOIN ssDisponible.sucursal sDisponible
                                WHERE ssDisponible.productoVariante = v
                                  AND ssDisponible.cantidad > 0
                                  AND (
                                        (:idSucursal IS NOT NULL AND sDisponible.idSucursal = :idSucursal)
                                        OR (:idSucursal IS NULL AND (:tipoSucursal IS NULL OR sDisponible.tipo = :tipoSucursal))
                                  )
                            )
                      )
                    ORDER BY
                      CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM SucursalStock ssStock
                            JOIN ssStock.sucursal sStock
                            WHERE ssStock.productoVariante = v
                              AND ssStock.cantidad > 0
                              AND (
                                    (:idSucursal IS NOT NULL AND sStock.idSucursal = :idSucursal)
                                    OR (:idSucursal IS NULL AND (:tipoSucursal IS NULL OR sStock.tipo = :tipoSucursal))
                              )
                        ) THEN 0
                        ELSE 1
                      END ASC,
                      v.createdAt DESC,
                      v.idProductoVariante DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT v.idProductoVariante)
                    FROM ProductoVariante v
                    JOIN v.producto p
                    LEFT JOIN v.color c
                    LEFT JOIN v.talla t
                    WHERE v.deletedAt IS NULL
                      AND p.deletedAt IS NULL
                      AND (:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria)
                      AND (:idColor IS NULL OR v.color.idColor = :idColor)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                            OR v.codigoBarras LIKE CONCAT(:term, '%')
                            OR (
                                (:token1 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token1, '%')))
                                AND (:token2 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token2, '%')))
                                AND (:token3 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token3, '%')))
                                AND (:token4 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token4, '%')))
                                AND (:token5 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token5, '%')))
                                AND (:token6 IS NULL OR LOWER(CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(v.sku, ''), ' ', COALESCE(v.codigoBarras, ''), ' ', COALESCE(c.nombre, ''), ' ', COALESCE(t.nombre, ''))) LIKE LOWER(CONCAT('%', :token6, '%')))
                            )
                      )
                      AND (
                            :conOferta IS NULL
                            OR (
                                :conOferta = true AND (
                                    (
                                        v.precioOferta IS NOT NULL
                                        AND (v.ofertaInicio IS NULL OR v.ofertaInicio <= CURRENT_TIMESTAMP)
                                        AND (v.ofertaFin IS NULL OR v.ofertaFin >= CURRENT_TIMESTAMP)
                                    )
                                    OR (
                                        :idSucursal IS NOT NULL
                                        AND EXISTS (
                                            SELECT 1
                                            FROM ProductoVarianteOfertaSucursal vos
                                            WHERE vos.productoVariante = v
                                              AND vos.sucursal.idSucursal = :idSucursal
                                              AND vos.deletedAt IS NULL
                                              AND vos.precioOferta IS NOT NULL
                                              AND (vos.ofertaInicio IS NULL OR vos.ofertaInicio <= CURRENT_TIMESTAMP)
                                              AND (vos.ofertaFin IS NULL OR vos.ofertaFin >= CURRENT_TIMESTAMP)
                                        )
                                    )
                                )
                            )
                      )
                      AND (
                            :soloDisponibles IS NULL
                            OR :soloDisponibles = false
                            OR EXISTS (
                                SELECT 1
                                FROM SucursalStock ssDisponible
                                JOIN ssDisponible.sucursal sDisponible
                                WHERE ssDisponible.productoVariante = v
                                  AND ssDisponible.cantidad > 0
                                  AND (
                                        (:idSucursal IS NOT NULL AND sDisponible.idSucursal = :idSucursal)
                                        OR (:idSucursal IS NULL AND (:tipoSucursal IS NULL OR sDisponible.tipo = :tipoSucursal))
                                  )
                            )
                      )
                    """)
    Page<ProductoVariante> buscarResumenPaginado(
            @Param("term") String term,
            @Param("token1") String token1,
            @Param("token2") String token2,
            @Param("token3") String token3,
            @Param("token4") String token4,
            @Param("token5") String token5,
            @Param("token6") String token6,
            @Param("idSucursal") Integer idSucursal,
            @Param("idCategoria") Integer idCategoria,
            @Param("idColor") Integer idColor,
            @Param("conOferta") Boolean conOferta,
            @Param("tipoSucursal") SucursalTipo tipoSucursal,
            @Param("soloDisponibles") Boolean soloDisponibles,
            Pageable pageable);

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteDisponibleExcelRow(
                v.idProductoVariante,
                v.sku,
                v.codigoBarras,
                p.nombre,
                p.categoria.nombreCategoria,
                s.nombre,
                v.color.nombre,
                v.talla.nombre,
                ss.cantidad,
                v.precio,
                v.precioMayor,
                v.precioOferta,
                v.estado,
                v.ofertaInicio,
                v.ofertaFin
            )
            FROM SucursalStock ss
            JOIN ss.productoVariante v
            JOIN v.producto p
            JOIN ss.sucursal s
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND ss.cantidad > 0
              AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<ProductoVarianteDisponibleExcelRow> listarDisponiblesParaReporte(@Param("idSucursal") Integer idSucursal);

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteDisponibleExcelRow(
                v.idProductoVariante,
                v.sku,
                v.codigoBarras,
                p.nombre,
                p.categoria.nombreCategoria,
                COALESCE(MAX(s.nombre), 'MULTISUCURSAL'),
                v.color.nombre,
                v.talla.nombre,
                COALESCE(SUM(
                    CASE
                        WHEN :idSucursal IS NULL OR s.idSucursal = :idSucursal THEN ss.cantidad
                        ELSE 0
                    END
                ), 0),
                v.precio,
                v.precioMayor,
                v.precioOferta,
                v.estado,
                v.ofertaInicio,
                v.ofertaFin
            )
            FROM ProductoVariante v
            JOIN v.producto p
            LEFT JOIN SucursalStock ss ON ss.productoVariante = v
            LEFT JOIN ss.sucursal s
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            GROUP BY
                v.idProductoVariante,
                v.sku,
                v.codigoBarras,
                p.nombre,
                p.categoria.nombreCategoria,
                v.color.nombre,
                v.talla.nombre,
                v.precio,
                v.precioMayor,
                v.precioOferta,
                v.estado,
                v.ofertaInicio,
                v.ofertaFin
            HAVING COALESCE(SUM(
                CASE
                    WHEN :idSucursal IS NULL OR s.idSucursal = :idSucursal THEN ss.cantidad
                    ELSE 0
                END
            ), 0) <= 0
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<ProductoVarianteDisponibleExcelRow> listarSinStockParaReporte(@Param("idSucursal") Integer idSucursal);

    @Query("""
            SELECT COUNT(v.idProductoVariante)
            FROM ProductoVariante v
            WHERE v.deletedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM HistorialStock hs
                    WHERE hs.productoVariante = v
                      AND (:idSucursal IS NULL OR hs.sucursal.idSucursal = :idSucursal)
              )
              AND EXISTS (
                    SELECT 1
                    FROM SucursalStock ss
                    WHERE ss.productoVariante = v
                    GROUP BY ss.productoVariante.idProductoVariante
                    HAVING COALESCE(SUM(
                        CASE
                            WHEN :idSucursal IS NULL OR ss.sucursal.idSucursal = :idSucursal THEN ss.cantidad
                            ELSE 0
                        END
                    ), 0) <= 0
              )
            """)
    long contarVariantesAgotadas(@Param("idSucursal") Integer idSucursal);

    @Query("""
            SELECT COUNT(v.idProductoVariante)
            FROM ProductoVariante v
            WHERE v.deletedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM HistorialStock hs
                    WHERE hs.productoVariante = v
                      AND (:idSucursal IS NULL OR hs.sucursal.idSucursal = :idSucursal)
              )
              AND EXISTS (
                    SELECT 1
                    FROM SucursalStock ss
                    WHERE ss.productoVariante = v
                    GROUP BY ss.productoVariante.idProductoVariante
                    HAVING COALESCE(SUM(
                        CASE
                            WHEN :idSucursal IS NULL OR ss.sucursal.idSucursal = :idSucursal THEN ss.cantidad
                            ELSE 0
                        END
                    ), 0) BETWEEN 1 AND :umbral
              )
            """)
    long contarStockBajo(@Param("idSucursal") Integer idSucursal, @Param("umbral") Integer umbral);

    @Query("""
            SELECT COALESCE(SUM(
                CASE
                    WHEN :idSucursal IS NULL OR ss.sucursal.idSucursal = :idSucursal THEN ss.cantidad
                    ELSE 0
                END
            ), 0)
            FROM SucursalStock ss
            """)
    long sumarStockTotal(@Param("idSucursal") Integer idSucursal);

    @Query("""
            SELECT COUNT(v.idProductoVariante)
            FROM ProductoVariante v
            WHERE v.deletedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM SucursalStock ss
                    WHERE ss.productoVariante = v
                    GROUP BY ss.productoVariante.idProductoVariante
                    HAVING COALESCE(SUM(
                        CASE
                            WHEN :idSucursal IS NULL OR ss.sucursal.idSucursal = :idSucursal THEN ss.cantidad
                            ELSE 0
                        END
                    ), 0) > 0
              )
            """)
    long contarVariantesDisponibles(@Param("idSucursal") Integer idSucursal);

    @Query("""
            SELECT
                v.idProductoVariante,
                p.nombre,
                v.color.nombre,
                v.talla.nombre,
                COALESCE(SUM(
                    CASE
                        WHEN :idSucursal IS NULL OR s.idSucursal = :idSucursal THEN ss.cantidad
                        ELSE 0
                    END
                ), 0),
                v.sku,
                p.idProducto,
                v.color.idColor
            FROM ProductoVariante v
            JOIN v.producto p
            LEFT JOIN SucursalStock ss ON ss.productoVariante = v
            LEFT JOIN ss.sucursal s
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM HistorialStock hs
                    WHERE hs.productoVariante = v
                      AND (:idSucursal IS NULL OR hs.sucursal.idSucursal = :idSucursal)
              )
            GROUP BY v.idProductoVariante, p.nombre, v.color.nombre, v.talla.nombre, v.sku, p.idProducto, v.color.idColor
            HAVING COALESCE(SUM(
                CASE
                    WHEN :idSucursal IS NULL OR s.idSucursal = :idSucursal THEN ss.cantidad
                    ELSE 0
                END
            ), 0) <= 0
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<Object[]> listarReposicionUrgente(@Param("idSucursal") Integer idSucursal);

    @Query("""
            SELECT
                v.idProductoVariante,
                p.nombre,
                v.color.nombre,
                v.talla.nombre,
                COALESCE(SUM(
                    CASE
                        WHEN :idSucursal IS NULL OR s.idSucursal = :idSucursal THEN ss.cantidad
                        ELSE 0
                    END
                ), 0),
                v.sku,
                p.idProducto,
                v.color.idColor
            FROM ProductoVariante v
            JOIN v.producto p
            LEFT JOIN SucursalStock ss ON ss.productoVariante = v
            LEFT JOIN ss.sucursal s
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND EXISTS (
                    SELECT 1
                    FROM HistorialStock hs
                    WHERE hs.productoVariante = v
                      AND (:idSucursal IS NULL OR hs.sucursal.idSucursal = :idSucursal)
              )
            GROUP BY v.idProductoVariante, p.nombre, v.color.nombre, v.talla.nombre, v.sku, p.idProducto, v.color.idColor
            HAVING COALESCE(SUM(
                CASE
                    WHEN :idSucursal IS NULL OR s.idSucursal = :idSucursal THEN ss.cantidad
                    ELSE 0
                END
            ), 0) BETWEEN 1 AND :umbral
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<Object[]> listarStockBajoResumen(
            @Param("idSucursal") Integer idSucursal,
            @Param("umbral") Integer umbral);
}
