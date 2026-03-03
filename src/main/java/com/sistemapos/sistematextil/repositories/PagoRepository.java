package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(Integer idVenta);

    long countByVenta_IdVentaAndDeletedAtIsNull(Integer idVenta);
}
