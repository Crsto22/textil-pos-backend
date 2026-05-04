package com.sistemapos.sistematextil.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ProductoVarianteOfertaSucursal;

public interface ProductoVarianteOfertaSucursalRepository extends JpaRepository<ProductoVarianteOfertaSucursal, Integer> {

    Optional<ProductoVarianteOfertaSucursal> findByProductoVarianteIdProductoVarianteAndSucursalIdSucursalAndDeletedAtIsNull(
            Integer idProductoVariante,
            Integer idSucursal);

    Optional<ProductoVarianteOfertaSucursal> findByProductoVarianteIdProductoVarianteAndSucursalIdSucursal(
            Integer idProductoVariante,
            Integer idSucursal);

    List<ProductoVarianteOfertaSucursal> findByProductoVarianteIdProductoVarianteInAndSucursalIdSucursalAndDeletedAtIsNull(
            Collection<Integer> idsProductoVariante,
            Integer idSucursal);

    @Query("""
            SELECT vos
            FROM ProductoVarianteOfertaSucursal vos
            JOIN vos.productoVariante v
            JOIN v.producto p
            JOIN vos.sucursal s
            WHERE vos.deletedAt IS NULL
              AND vos.sucursal.idSucursal = :idSucursal
              AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL
            """)
    Page<ProductoVarianteOfertaSucursal> findActivasPorSucursal(
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);

    List<ProductoVarianteOfertaSucursal> findByPrecioOfertaIsNotNullAndOfertaFinLessThanEqualAndDeletedAtIsNull(
            LocalDateTime fechaHora);
}
