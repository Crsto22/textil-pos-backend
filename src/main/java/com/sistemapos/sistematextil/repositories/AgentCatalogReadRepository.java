package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.SucursalStock;

/** Read-only inventory view used by the conversational agent. */
public interface AgentCatalogReadRepository extends Repository<SucursalStock, Integer> {

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            WHERE ss.sucursal.idSucursal = :branchId
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND v.activo = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.activo = 'ACTIVO'
              AND p.publicarEcommerce = true
            ORDER BY p.nombre ASC, v.idProductoVariante ASC
            """)
    List<SucursalStock> findCatalogByBranch(@Param("branchId") Integer branchId);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            WHERE ss.sucursal.idSucursal = :branchId
              AND p.idProducto = :productId
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND v.activo = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.activo = 'ACTIVO'
              AND p.publicarEcommerce = true
            ORDER BY v.idProductoVariante ASC
            """)
    List<SucursalStock> findProductByBranch(
            @Param("branchId") Integer branchId,
            @Param("productId") Integer productId);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            WHERE ss.sucursal.idSucursal = :branchId
              AND v.idProductoVariante = :variantId
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND v.activo = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.activo = 'ACTIVO'
              AND p.publicarEcommerce = true
            """)
    List<SucursalStock> findVariantByBranch(
            @Param("branchId") Integer branchId,
            @Param("variantId") Integer variantId);

    @Query("""
            SELECT ss
            FROM SucursalStock ss
            JOIN FETCH ss.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            WHERE ss.sucursal.idSucursal = :branchId
              AND v.idProductoVariante IN :variantIds
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND v.estado = 'ACTIVO'
              AND v.activo = 'ACTIVO'
              AND p.estado = 'ACTIVO'
              AND p.activo = 'ACTIVO'
              AND p.publicarEcommerce = true
            ORDER BY v.idProductoVariante ASC
            """)
    List<SucursalStock> findVariantsByBranch(
            @Param("branchId") Integer branchId,
            @Param("variantIds") List<Integer> variantIds);
}
