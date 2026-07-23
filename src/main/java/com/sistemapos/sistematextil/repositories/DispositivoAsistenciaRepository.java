package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sistemapos.sistematextil.model.DispositivoAsistencia;

public interface DispositivoAsistenciaRepository
        extends JpaRepository<DispositivoAsistencia, Integer>, JpaSpecificationExecutor<DispositivoAsistencia> {

    Optional<DispositivoAsistencia> findByNumeroSerieIgnoreCase(String numeroSerie);

    boolean existsByNumeroSerieIgnoreCaseAndIdDispositivoNot(String numeroSerie, Integer idDispositivo);
}
