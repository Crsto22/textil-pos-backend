package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.Empresa;

public interface EmpresaRepository extends JpaRepository <Empresa,Integer> {

    Optional<Empresa> findTopByOrderByIdEmpresaAsc();

}
