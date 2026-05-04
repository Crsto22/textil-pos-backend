package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.GuiaRemisionVehiculo;

public interface GuiaRemisionVehiculoRepository extends JpaRepository<GuiaRemisionVehiculo, Integer> {

    List<GuiaRemisionVehiculo> findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
            Integer idGuiaRemision);

    Optional<GuiaRemisionVehiculo> findByIdGuiaVehiculoAndGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(
            Integer idGuiaVehiculo,
            Integer idGuiaRemision);
}
