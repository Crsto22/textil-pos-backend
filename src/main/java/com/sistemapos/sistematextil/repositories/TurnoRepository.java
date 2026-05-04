package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.Turno;

public interface TurnoRepository extends JpaRepository<Turno, Integer> {

    Page<Turno> findByDeletedAtIsNull(Pageable pageable);

    Page<Turno> findByDeletedAtIsNullAndNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Optional<Turno> findByIdTurnoAndDeletedAtIsNull(Integer idTurno);

    Optional<Turno> findByNombreIgnoreCaseAndDeletedAtIsNull(String nombre);

    Optional<Turno> findByNombreIgnoreCase(String nombre);
}
