package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.MetodoPagoCuenta;

@Repository
public interface MetodoPagoCuentaRepository extends JpaRepository<MetodoPagoCuenta, Integer> {
    List<MetodoPagoCuenta> findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(
            Integer paymentMethodId);
}
