package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.GuiaRemisionDetalle;

public interface GuiaRemisionDetalleRepository extends JpaRepository<GuiaRemisionDetalle, Integer> {

    List<GuiaRemisionDetalle> findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaRemisionDetalleAsc(
            Integer idGuiaRemision);
}
