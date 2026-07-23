package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sistemapos.sistematextil.model.Trabajador;

public interface TrabajadorRepository
        extends JpaRepository<Trabajador, Integer>, JpaSpecificationExecutor<Trabajador> {

    Optional<Trabajador> findByIdTrabajadorAndDeletedAtIsNull(Integer idTrabajador);

    Optional<Trabajador> findByCodigoZktecoAndDeletedAtIsNull(String codigoZkteco);

    boolean existsByCodigoZktecoAndIdTrabajadorNot(String codigoZkteco, Integer idTrabajador);

    boolean existsByDniAndIdTrabajadorNot(String dni, Integer idTrabajador);

    boolean existsByUsuario_IdUsuarioAndIdTrabajadorNot(Integer idUsuario, Integer idTrabajador);
}
