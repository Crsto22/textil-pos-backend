package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.SucursalStock;

public interface SucursalStockRepository extends JpaRepository<SucursalStock, Integer> {

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
}
