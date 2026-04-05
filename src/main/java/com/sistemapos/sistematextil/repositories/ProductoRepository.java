package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

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
                    WHERE p.deletedAt IS NULL
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
                    SELECT COUNT(DISTINCT p.idProducto)
                    FROM Producto p
                    LEFT JOIN ProductoVariante v ON v.producto = p AND v.deletedAt IS NULL
                    WHERE p.deletedAt IS NULL
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
    Page<Producto> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("idCategoria") Integer idCategoria,
            @Param("idColor") Integer idColor,
            @Param("conOferta") Boolean conOferta,
            @Param("tipoSucursal") SucursalTipo tipoSucursal,
            @Param("soloDisponibles") Boolean soloDisponibles,
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
