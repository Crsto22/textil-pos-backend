package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Empresa;

import jakarta.persistence.LockModeType;

public interface EmpresaRepository extends JpaRepository <Empresa,Integer> {

    Optional<Empresa> findTopByOrderByIdEmpresaAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Empresa e WHERE e.idEmpresa = :idEmpresa")
    Optional<Empresa> findForUpdate(@Param("idEmpresa") Integer idEmpresa);

}
