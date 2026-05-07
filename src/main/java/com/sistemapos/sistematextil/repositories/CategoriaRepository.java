package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sistemapos.sistematextil.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    Page<Categoria> findByDeletedAtIsNullAndEstadoOrderByIdCategoriaDesc(String estado, Pageable pageable);

    Page<Categoria> findByDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaDesc(
            String estado,
            String nombreCategoria,
            Pageable pageable);

    Optional<Categoria> findByIdCategoria(Integer idCategoria);

    Optional<Categoria> findByIdCategoriaAndDeletedAtIsNullAndEstado(Integer idCategoria, String estado);

    Optional<Categoria> findByNombreCategoriaIgnoreCase(String nombreCategoria);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.categoria.idCategoria = :idCategoria")
    boolean estaEnUso(Integer idCategoria);
}
