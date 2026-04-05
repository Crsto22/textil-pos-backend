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
import com.sistemapos.sistematextil.util.producto.ProductoVarianteDisponibleExcelRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteStockSucursalRow;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Integer> {

    List<ProductoVariante> findByDeletedAtIsNull();

    @Query("""
            SELECT DISTINCT v
            FROM ProductoVariante v
            JOIN SucursalStock ss ON ss.productoVariante = v
            WHERE v.deletedAt IS NULL
              AND v.producto.deletedAt IS NULL
              AND ss.sucursal.idSucursal = :idSucursal
            ORDER BY v.idProductoVariante ASC
            """)
    List<ProductoVariante> findByDeletedAtIsNullAndSucursal_IdSucursal(@Param("idSucursal") Integer idSucursal);

    List<ProductoVariante> findByProductoIdProducto(Integer idProducto);

    List<ProductoVariante> findByProductoIdProductoAndDeletedAtIsNull(Integer idProducto);

    @Query("""
            SELECT DISTINCT v
            FROM ProductoVariante v
            JOIN SucursalStock ss ON ss.productoVariante = v
            WHERE v.producto.idProducto = :idProducto
              AND v.deletedAt IS NULL
              AND v.producto.deletedAt IS NULL
              AND ss.sucursal.idSucursal = :idSucursal
            ORDER BY v.idProductoVariante ASC
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
            @Param("codigoBarras") String codigoBarras,
            @Param("idProductoExcluir") Integer idProductoExcluir);

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
                    LEFT JOIN FETCH v.color
                    LEFT JOIN FETCH v.talla
                    WHERE v.deletedAt IS NULL
                      AND p.deletedAt IS NULL
                      AND (:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria)
                      AND (:idColor IS NULL OR v.color.idColor = :idColor)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                            OR v.codigoBarras LIKE CONCAT(:term, '%')
                      )
                      AND (
                            :conOferta IS NULL
                            OR (:conOferta = true AND v.precioOferta IS NOT NULL AND (v.ofertaInicio IS NULL OR v.ofertaInicio <= CURRENT_TIMESTAMP) AND (v.ofertaFin IS NULL OR v.ofertaFin >= CURRENT_TIMESTAMP))
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
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT v.idProductoVariante)
                    FROM ProductoVariante v
                    JOIN v.producto p
                    WHERE v.deletedAt IS NULL
                      AND p.deletedAt IS NULL
                      AND (:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria)
                      AND (:idColor IS NULL OR v.color.idColor = :idColor)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                            OR v.codigoBarras LIKE CONCAT(:term, '%')
                      )
                      AND (
                            :conOferta IS NULL
                            OR (:conOferta = true AND v.precioOferta IS NOT NULL AND (v.ofertaInicio IS NULL OR v.ofertaInicio <= CURRENT_TIMESTAMP) AND (v.ofertaFin IS NULL OR v.ofertaFin >= CURRENT_TIMESTAMP))
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
                COALESCE(MAX(s.nombre), 'MULTISUCURSAL')
            FROM ProductoVariante v
            JOIN v.producto p
            LEFT JOIN SucursalStock ss ON ss.productoVariante = v
            LEFT JOIN ss.sucursal s
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            GROUP BY v.idProductoVariante, p.nombre, v.color.nombre, v.talla.nombre
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
                COALESCE(MAX(s.nombre), 'MULTISUCURSAL')
            FROM ProductoVariante v
            JOIN v.producto p
            LEFT JOIN SucursalStock ss ON ss.productoVariante = v
            LEFT JOIN ss.sucursal s
            WHERE v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            GROUP BY v.idProductoVariante, p.nombre, v.color.nombre, v.talla.nombre
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
