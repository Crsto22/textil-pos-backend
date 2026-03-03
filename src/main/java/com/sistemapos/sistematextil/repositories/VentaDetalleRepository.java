package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.VentaDetalle;

public interface VentaDetalleRepository extends JpaRepository<VentaDetalle, Integer> {

    List<VentaDetalle> findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(Integer idVenta);

    long countByVenta_IdVentaAndDeletedAtIsNull(Integer idVenta);
}
