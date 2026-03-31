package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Page<Producto> findByDeletedAtIsNullOrderByIdProductoAsc(Pageable pageable);

    Page<Producto> findBySucursal_IdSucursalAndDeletedAtIsNullOrderByIdProductoAsc(
            Integer idSucursal,
            Pageable pageable);

    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM Producto p
                    LEFT JOIN p.sucursal s
                    LEFT JOIN ProductoVariante v ON v.producto = p AND v.deletedAt IS NULL
                    WHERE p.deletedAt IS NULL
                      AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
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
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.idProducto)
                    FROM Producto p
                    LEFT JOIN p.sucursal s
                    LEFT JOIN ProductoVariante v ON v.producto = p AND v.deletedAt IS NULL
                    WHERE p.deletedAt IS NULL
                      AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
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
                    """)
    Page<Producto> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("idCategoria") Integer idCategoria,
            @Param("idColor") Integer idColor,
            @Param("conOferta") Boolean conOferta,
            Pageable pageable);

    Optional<Producto> findFirstBySucursal_IdSucursalAndCategoria_IdCategoriaAndNombreIgnoreCaseAndDeletedAtIsNullOrderByIdProductoAsc(
            Integer idSucursal,
            Integer idCategoria,
            String nombre);

    Optional<Producto> findByIdProductoAndDeletedAtIsNull(Integer idProducto);

    Optional<Producto> findByIdProductoAndSucursal_IdSucursalAndDeletedAtIsNull(
            Integer idProducto,
            Integer idSucursal);

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
              AND (:idSucursal IS NULL OR p.sucursal.idSucursal = :idSucursal)
            """)
    long contarActivosParaReporte(@Param("idSucursal") Integer idSucursal);
}
