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

    Page<Talla> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Optional<Talla> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdTallaNot(String nombre, Integer idTalla);

    @Query("SELECT COUNT(v) > 0 FROM ProductoVariante v WHERE v.talla.idTalla = :idTalla")
    boolean estaEnUso(Integer idTalla);
}
