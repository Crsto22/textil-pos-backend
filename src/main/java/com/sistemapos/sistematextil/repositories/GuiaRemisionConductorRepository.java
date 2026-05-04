package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.GuiaRemisionConductor;

public interface GuiaRemisionConductorRepository extends JpaRepository<GuiaRemisionConductor, Integer> {

    List<GuiaRemisionConductor> findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
            Integer idGuiaRemision);

    Optional<GuiaRemisionConductor> findByIdGuiaConductorAndGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(
            Integer idGuiaConductor,
            Integer idGuiaRemision);
}
