package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Integer> {

    Page<Venta> findByDeletedAtIsNullOrderByIdVentaDesc(Pageable pageable);

    Page<Venta> findByDeletedAtIsNullAndSucursal_IdSucursalOrderByIdVentaDesc(Integer idSucursal, Pageable pageable);

    Optional<Venta> findByIdVentaAndDeletedAtIsNull(Integer idVenta);

    Optional<Venta> findByIdVentaAndDeletedAtIsNullAndSucursal_IdSucursal(Integer idVenta, Integer idSucursal);
}
