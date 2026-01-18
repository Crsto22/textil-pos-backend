package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.ProductoVariante;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Integer> {
    List<ProductoVariante> findByProductoIdProducto(Integer idProducto);

    // Método optimizado para verificar duplicados sin traer toda la lista a memoria
    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
        Integer idProducto, Integer idTalla, Integer idColor, Integer idSucursal
    );
}