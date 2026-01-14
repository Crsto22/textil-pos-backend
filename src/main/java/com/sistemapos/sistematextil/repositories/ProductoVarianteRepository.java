package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.ProductoVariante;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Integer> {
    List<ProductoVariante> findByProductoIdProducto(Integer idProducto);
}