package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.Color;

@Repository
public interface ColorRepository extends JpaRepository<Color, Integer> {
    
    // Para el formulario de productos
    List<Color> findByEstado(String estado);

    // Para evitar borrar colores que ya tienen productos vinculados
    @Query("SELECT COUNT(v) > 0 FROM ProductoVariante v WHERE v.color.idColor = :idColor")
    boolean estaEnUso(Integer idColor);
}