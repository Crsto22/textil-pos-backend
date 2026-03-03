package com.sistemapos.sistematextil.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.ImportacionProductoHistorial;

public interface ImportacionProductoHistorialRepository extends JpaRepository<ImportacionProductoHistorial, Integer> {

    Page<ImportacionProductoHistorial> findByDeletedAtIsNull(Pageable pageable);

    Page<ImportacionProductoHistorial> findByDeletedAtIsNullAndSucursal_IdSucursal(Integer idSucursal, Pageable pageable);
}
