package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.EcommercePortada;

public interface EcommercePortadaRepository extends JpaRepository<EcommercePortada, Integer> {

    List<EcommercePortada> findByDeletedAtIsNullOrderByOrdenAscIdEcommercePortadaAsc();

    List<EcommercePortada> findByEstadoAndDeletedAtIsNullOrderByOrdenAscIdEcommercePortadaAsc(String estado);
}
