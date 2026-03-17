package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.ComunicacionBajaDetalle;

public interface ComunicacionBajaDetalleRepository extends JpaRepository<ComunicacionBajaDetalle, Integer> {

    List<ComunicacionBajaDetalle> findByComunicacionBaja_IdBajaAndDeletedAtIsNull(Integer idBaja);
}
