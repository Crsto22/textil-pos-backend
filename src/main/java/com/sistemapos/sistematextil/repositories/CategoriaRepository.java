package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sistemapos.sistematextil.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    Page<Categoria> findByDeletedAtIsNullAndEstadoOrderByIdCategoriaAsc(String estado, Pageable pageable);

    Page<Categoria> findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoOrderByIdCategoriaAsc(
            Integer idSucursal,
            String estado,
            Pageable pageable);

    Page<Categoria> findByDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(
            String estado,
            String nombreCategoria,
            Pageable pageable);

    Page<Categoria> findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(
            Integer idSucursal,
            String estado,
            String nombreCategoria,
            Pageable pageable);

    Optional<Categoria> findByIdCategoria(Integer idCategoria);

    Optional<Categoria> findByIdCategoriaAndDeletedAtIsNullAndEstado(Integer idCategoria, String estado);

    Optional<Categoria> findByIdCategoriaAndSucursal_IdSucursal(Integer idCategoria, Integer idSucursal);

    Optional<Categoria> findByIdCategoriaAndSucursal_IdSucursalAndDeletedAtIsNullAndEstado(
            Integer idCategoria,
            Integer idSucursal,
            String estado);

    Optional<Categoria> findBySucursal_IdSucursalAndNombreCategoriaIgnoreCase(Integer idSucursal, String nombreCategoria);

    List<Categoria> findBySucursal_IdSucursal(Integer idSucursal);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.categoria.idCategoria = :idCategoria")
    boolean estaEnUso(Integer idCategoria);
}
