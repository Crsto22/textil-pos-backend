package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sistemapos.sistematextil.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    Page<Categoria> findAllByOrderByIdCategoriaAsc(Pageable pageable);

    Page<Categoria> findBySucursal_IdSucursalOrderByIdCategoriaAsc(Integer idSucursal, Pageable pageable);

    Page<Categoria> findByNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(String nombreCategoria, Pageable pageable);

    Page<Categoria> findBySucursal_IdSucursalAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(
            Integer idSucursal,
            String nombreCategoria,
            Pageable pageable);

    Optional<Categoria> findByIdCategoria(Integer idCategoria);

    Optional<Categoria> findByIdCategoriaAndSucursal_IdSucursal(Integer idCategoria, Integer idSucursal);

    boolean existsBySucursal_IdSucursalAndNombreCategoriaIgnoreCase(Integer idSucursal, String nombreCategoria);

    boolean existsBySucursal_IdSucursalAndNombreCategoriaIgnoreCaseAndIdCategoriaNot(
            Integer idSucursal,
            String nombreCategoria,
            Integer idCategoria);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.categoria.idCategoria = :idCategoria")
    boolean estaEnUso(Integer idCategoria);
}
