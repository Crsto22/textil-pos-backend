package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.SucursalTipo;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Page<Producto> findByDeletedAtIsNullOrderByIdProductoAsc(Pageable pageable);

    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM Producto p
                    LEFT JOIN ProductoVariante v ON v.producto = p AND v.deletedAt IS NULL
                    LEFT JOIN v.color c
                    LEFT JOIN v.talla t
                    WHERE p.deletedAt IS NULL
                      AND (:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria)
                      AND (:idColor IS NULL OR v.color.idColor = :idColor)
                      AND (:publicarEcommerce IS NULL OR p.publicarEcommerce = :publicarEcommerce)
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
                            FROM ProductoVariante vStock
                            JOIN SucursalStock ssStock ON ssStock.productoVariante = vStock
                            JOIN ssStock.sucursal sStock
                            WHERE vStock.producto = p
                              AND vStock.deletedAt IS NULL
                              AND ssStock.cantidad > 0
                              AND (
                                    (:idSucursal IS NOT NULL AND sStock.idSucursal = :idSucursal)
                                    OR (:idSucursal IS NULL AND (:tipoSucursal IS NULL OR sStock.tipo = :tipoSucursal))
                              )
                        ) THEN 0
                        ELSE 1
                      END ASC,
                      p.fechaCreacion DESC,
                      p.idProducto DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.idProducto)
                    FROM Producto p
                    LEFT JOIN ProductoVariante v ON v.producto = p AND v.deletedAt IS NULL
                    LEFT JOIN v.color c
                    LEFT JOIN v.talla t
                    WHERE p.deletedAt IS NULL
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
    Page<Producto> buscarConFiltros(
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
            @Param("publicarEcommerce") Boolean publicarEcommerce,
            Pageable pageable);

    Optional<Producto> findFirstByCategoria_IdCategoriaAndNombreIgnoreCaseAndDeletedAtIsNullOrderByIdProductoAsc(
            Integer idCategoria,
            String nombre);

    @Query(
            value = """
                    SELECT p.*
                    FROM producto p
                    WHERE p.categoria_id = :idCategoria
                      AND LOWER(p.nombre) = LOWER(:nombre)
                      AND p.deleted_at IS NULL
                    ORDER BY p.producto_id ASC
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<Producto> findFirstBySucursal_IdSucursalAndCategoria_IdCategoriaAndNombreIgnoreCaseAndDeletedAtIsNullOrderByIdProductoAsc(
            @Param("idSucursal") Integer idSucursal,
            @Param("idCategoria") Integer idCategoria,
            @Param("nombre") String nombre);

    Optional<Producto> findByIdProductoAndDeletedAtIsNull(Integer idProducto);

    Optional<Producto> findBySlugAndDeletedAtIsNull(String slug);

    @Query("""
            SELECT p
            FROM Producto p
            WHERE p.deletedAt IS NULL
              AND p.publicarEcommerce = true
              AND p.estado = 'ACTIVO'
              AND p.slug IS NOT NULL
              AND p.slug <> ''
              AND (
                    (p.imagenGlobalUrl IS NOT NULL AND p.imagenGlobalUrl <> '')
                    OR (p.imagenGlobalThumbUrl IS NOT NULL AND p.imagenGlobalThumbUrl <> '')
              )
            ORDER BY p.fechaCreacion DESC, p.idProducto DESC
            """)
    List<Producto> listarImagenesInicioEcommerce(Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdProductoNot(String slug, Integer idProducto);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.producto.idProducto = :idProducto
              AND v.deletedAt IS NULL
            """)
    boolean estaEnUso(Integer idProducto);

    @Query("""
            SELECT COUNT(p)
            FROM Producto p
            WHERE p.deletedAt IS NULL
            """)
    long contarActivosParaReporte();

    @Query("""
            SELECT COUNT(DISTINCT p.idProducto)
            FROM Producto p
            WHERE p.deletedAt IS NULL
              AND (
                    :idSucursal IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM ProductoVariante v
                        JOIN SucursalStock ss ON ss.productoVariante = v
                        WHERE v.producto = p
                          AND v.deletedAt IS NULL
                          AND ss.sucursal.idSucursal = :idSucursal
                    )
              )
            """)
    long contarActivosParaReportePorSucursal(@Param("idSucursal") Integer idSucursal);

    default long contarActivosParaReporte(Integer idSucursal) {
        return contarActivosParaReportePorSucursal(idSucursal);
    }
}
