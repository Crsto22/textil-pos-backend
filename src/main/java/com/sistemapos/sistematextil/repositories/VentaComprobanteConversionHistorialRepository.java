package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.VentaComprobanteConversionHistorial;

public interface VentaComprobanteConversionHistorialRepository
        extends JpaRepository<VentaComprobanteConversionHistorial, Long> {

    Optional<VentaComprobanteConversionHistorial> findByVenta_IdVenta(Integer idVenta);

    boolean existsByVenta_IdVenta(Integer idVenta);
}
