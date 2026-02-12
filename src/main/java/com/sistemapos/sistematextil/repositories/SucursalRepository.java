package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.Sucursal;

public interface SucursalRepository extends JpaRepository<Sucursal, Integer> {
    Page<Sucursal> findByDeletedAtIsNull(Pageable pageable);
    Page<Sucursal> findByDeletedAtIsNullAndNombreStartingWithIgnoreCase(String nombre, Pageable pageable);
    Optional<Sucursal> findByIdSucursalAndDeletedAtIsNull(Integer idSucursal);
    Optional<Sucursal> findByEmpresa_IdEmpresaAndNombreIgnoreCaseAndDeletedAtIsNull(Integer idEmpresa, String nombre);
}
