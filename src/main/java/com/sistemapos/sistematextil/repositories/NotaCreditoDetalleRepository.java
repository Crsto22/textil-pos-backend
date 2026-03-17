package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.NotaCreditoDetalle;

public interface NotaCreditoDetalleRepository extends JpaRepository<NotaCreditoDetalle, Integer> {

    List<NotaCreditoDetalle> findByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(Integer idNotaCredito);
}
