package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CotizacionDetalle;

public interface CotizacionDetalleRepository extends JpaRepository<CotizacionDetalle, Integer> {

    List<CotizacionDetalle> findByCotizacion_IdCotizacionAndDeletedAtIsNullOrderByIdCotizacionDetalleAsc(Integer idCotizacion);

    long countByCotizacion_IdCotizacionAndDeletedAtIsNull(Integer idCotizacion);
}
