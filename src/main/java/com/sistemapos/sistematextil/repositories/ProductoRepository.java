package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    Optional<Producto> findBySkuAndSucursalIdSucursal(String sku, Integer idSucursal);
}
