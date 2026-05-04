package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    Page<Categoria> findByDeletedAtIsNullAndEstadoOrderByIdCategoriaDesc(String estado, Pageable pageable);

    default Page<Categoria> findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoOrderByIdCategoriaDesc(
            Integer idSucursal,
            String estado,
            Pageable pageable) {
        return findByDeletedAtIsNullAndEstadoOrderByIdCategoriaDesc(estado, pageable);
    }

    Page<Categoria> findByDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaDesc(
            String estado,
            String nombreCategoria,
            Pageable pageable);

    default Page<Categoria> findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaDesc(
            Integer idSucursal,
            String estado,
            String nombreCategoria,
            Pageable pageable) {
        return findByDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaDesc(
                estado,
                nombreCategoria,
                pageable);
    }

    Optional<Categoria> findByIdCategoria(Integer idCategoria);

    Optional<Categoria> findByIdCategoriaAndDeletedAtIsNullAndEstado(Integer idCategoria, String estado);

    default Optional<Categoria> findByIdCategoriaAndSucursal_IdSucursal(
            Integer idCategoria,
            Integer idSucursal) {
        return findByIdCategoria(idCategoria);
    }

    default Optional<Categoria> findByIdCategoriaAndSucursal_IdSucursalAndDeletedAtIsNullAndEstado(
            Integer idCategoria,
            Integer idSucursal,
            String estado) {
        return findByIdCategoriaAndDeletedAtIsNullAndEstado(idCategoria, estado);
    }

    Optional<Categoria> findByNombreCategoriaIgnoreCase(String nombreCategoria);

    default Optional<Categoria> findBySucursal_IdSucursalAndNombreCategoriaIgnoreCase(
            Integer idSucursal,
            String nombreCategoria) {
        return findByNombreCategoriaIgnoreCase(nombreCategoria);
    }

    default List<Categoria> findBySucursal_IdSucursal(Integer idSucursal) {
        return findAll(Sort.by("idCategoria").descending());
    }

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.categoria.idCategoria = :idCategoria")
    boolean estaEnUso(Integer idCategoria);
}
