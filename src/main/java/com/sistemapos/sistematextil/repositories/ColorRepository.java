package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.Color;

@Repository
public interface ColorRepository extends JpaRepository<Color, Integer> {

    Page<Color> findByDeletedAtIsNullAndEstadoOrderByIdColorDesc(String estado, Pageable pageable);

    Page<Color> findByDeletedAtIsNullAndEstadoAndNombreContainingIgnoreCaseOrderByIdColorDesc(
            String estado,
            String nombre,
            Pageable pageable);

    Page<Color> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Optional<Color> findByNombreIgnoreCase(String nombre);

    Optional<Color> findByIdColorAndDeletedAtIsNullAndEstado(Integer idColor, String estado);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdColorNot(String nombre, Integer idColor);

    @Query("SELECT COUNT(v) > 0 FROM ProductoVariante v WHERE v.color.idColor = :idColor")
    boolean estaEnUso(Integer idColor);
}
