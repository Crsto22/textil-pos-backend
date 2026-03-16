package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.SunatConfig;

public interface SunatConfigRepository extends JpaRepository<SunatConfig, Integer> {

    List<SunatConfig> findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSunatConfigAsc(Integer idEmpresa);

    Optional<SunatConfig> findByIdSunatConfigAndDeletedAtIsNull(Integer idSunatConfig);
}
