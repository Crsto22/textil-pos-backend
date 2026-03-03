package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.MetodoPagoConfig;

@Repository
public interface MetodoPagoConfigRepository extends JpaRepository<MetodoPagoConfig, Integer> {

    List<MetodoPagoConfig> findByEstadoOrderByNombreAsc(String estado);
}
