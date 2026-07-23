package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sistemapos.sistematextil.model.CargoTrabajador;

public interface CargoTrabajadorRepository
        extends JpaRepository<CargoTrabajador, Integer>, JpaSpecificationExecutor<CargoTrabajador> {

    Optional<CargoTrabajador> findByIdCargo(Integer idCargo);

    boolean existsByNombreIgnoreCaseAndIdCargoNot(String nombre, Integer idCargo);
}
