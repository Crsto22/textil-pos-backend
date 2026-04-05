package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    Page<Categoria> findByDeletedAtIsNullAndEstadoOrderByIdCategoriaAsc(String estado, Pageable pageable);

    @Query("""
            SELECT c
            FROM Categoria c
            WHERE c.deletedAt IS NULL
              AND c.estado = :estado
            ORDER BY c.idCategoria ASC
            """)
    Page<Categoria> findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoOrderByIdCategoriaAsc(
            @Param("idSucursal") Integer idSucursal,
            @Param("estado") String estado,
            Pageable pageable);

    Page<Categoria> findByDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(
            String estado,
            String nombreCategoria,
            Pageable pageable);

    @Query("""
            SELECT c
            FROM Categoria c
            WHERE c.deletedAt IS NULL
              AND c.estado = :estado
              AND LOWER(c.nombreCategoria) LIKE LOWER(CONCAT('%', :nombreCategoria, '%'))
            ORDER BY c.idCategoria ASC
            """)
    Page<Categoria> findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(
            @Param("idSucursal") Integer idSucursal,
            @Param("estado") String estado,
            @Param("nombreCategoria") String nombreCategoria,
            Pageable pageable);

    Optional<Categoria> findByIdCategoria(Integer idCategoria);

    Optional<Categoria> findByIdCategoriaAndDeletedAtIsNullAndEstado(Integer idCategoria, String estado);

    @Query("""
            SELECT c
            FROM Categoria c
            WHERE c.idCategoria = :idCategoria
            """)
    Optional<Categoria> findByIdCategoriaAndSucursal_IdSucursal(
            @Param("idCategoria") Integer idCategoria,
            @Param("idSucursal") Integer idSucursal);

    @Query("""
            SELECT c
            FROM Categoria c
            WHERE c.idCategoria = :idCategoria
              AND c.deletedAt IS NULL
              AND c.estado = :estado
            """)
    Optional<Categoria> findByIdCategoriaAndSucursal_IdSucursalAndDeletedAtIsNullAndEstado(
            @Param("idCategoria") Integer idCategoria,
            @Param("idSucursal") Integer idSucursal,
            @Param("estado") String estado);

    @Query("""
            SELECT c
            FROM Categoria c
            WHERE LOWER(c.nombreCategoria) = LOWER(:nombreCategoria)
            """)
    Optional<Categoria> findBySucursal_IdSucursalAndNombreCategoriaIgnoreCase(
            @Param("idSucursal") Integer idSucursal,
            @Param("nombreCategoria") String nombreCategoria);

    @Query("""
            SELECT c
            FROM Categoria c
            ORDER BY c.idCategoria ASC
            """)
    List<Categoria> findBySucursal_IdSucursal(@Param("idSucursal") Integer idSucursal);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.categoria.idCategoria = :idCategoria")
    boolean estaEnUso(Integer idCategoria);
}
