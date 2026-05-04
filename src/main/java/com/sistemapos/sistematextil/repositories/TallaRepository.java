package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.Talla;

@Repository
public interface TallaRepository extends JpaRepository<Talla, Integer> {

    Page<Talla> findByDeletedAtIsNullAndEstadoOrderByIdTallaDesc(String estado, Pageable pageable);

    Page<Talla> findByDeletedAtIsNullAndEstadoAndNombreContainingIgnoreCaseOrderByIdTallaDesc(
            String estado,
            String nombre,
            Pageable pageable);

    Page<Talla> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Optional<Talla> findByNombreIgnoreCase(String nombre);

    Optional<Talla> findByIdTallaAndDeletedAtIsNullAndEstado(Integer idTalla, String estado);

    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdTallaNot(String nombre, Integer idTalla);

    @Query("SELECT COUNT(v) > 0 FROM ProductoVariante v WHERE v.talla.idTalla = :idTalla")
    boolean estaEnUso(Integer idTalla);
}
