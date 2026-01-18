package com.sistemapos.sistematextil.repositories;

import java.util.List; // Importante importar List

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.Talla;

@Repository
public interface TallaRepository extends JpaRepository<Talla, Integer> {
    
    // Esto genera automáticamente: SELECT * FROM tallas WHERE estado = ?
    List<Talla> findByEstado(String estado);

    @Query("SELECT COUNT(v) > 0 FROM ProductoVariante v WHERE v.talla.idTalla = :idTalla")
boolean estaEnUso(Integer idTalla);

}