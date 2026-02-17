package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Page<Producto> findAllByOrderByIdProductoAsc(Pageable pageable);

    Page<Producto> findBySucursal_IdSucursalOrderByIdProductoAsc(Integer idSucursal, Pageable pageable);

    @Query("""
            SELECT p
            FROM Producto p
            LEFT JOIN p.sucursal s
            WHERE (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
              AND (
                    :term IS NULL
                    OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR p.sku LIKE CONCAT(:term, '%')
                    OR (p.codigoExterno IS NOT NULL AND p.codigoExterno LIKE CONCAT(:term, '%'))
              )
            """)
    Page<Producto> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);

    Optional<Producto> findByIdProducto(Integer idProducto);

    Optional<Producto> findByIdProductoAndSucursal_IdSucursal(Integer idProducto, Integer idSucursal);

    boolean existsBySkuAndSucursalIdSucursal(String sku, Integer idSucursal);

    boolean existsBySkuAndSucursalIdSucursalAndIdProductoNot(String sku, Integer idSucursal, Integer idProducto);

    boolean existsByCodigoExterno(String codigoExterno);

    boolean existsByCodigoExternoAndIdProductoNot(String codigoExterno, Integer idProducto);

    @Query("SELECT COUNT(v) > 0 FROM ProductoVariante v WHERE v.producto.idProducto = :idProducto")
    boolean estaEnUso(Integer idProducto);
}
