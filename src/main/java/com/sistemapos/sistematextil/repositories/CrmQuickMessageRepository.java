package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.sistemapos.sistematextil.model.CrmQuickMessage;

public interface CrmQuickMessageRepository extends JpaRepository<CrmQuickMessage, Long> {

    @EntityGraph(attributePaths = { "blocks", "createdBy", "empresa" })
    List<CrmQuickMessage> findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByKeywordAsc(Integer idEmpresa);

    @EntityGraph(attributePaths = { "blocks", "createdBy", "empresa" })
    Optional<CrmQuickMessage> findByIdQuickMessageAndEmpresa_IdEmpresaAndDeletedAtIsNull(Long id, Integer idEmpresa);

    Optional<CrmQuickMessage> findFirstByEmpresa_IdEmpresaAndKeywordIgnoreCaseAndDeletedAtIsNull(
            Integer idEmpresa, String keyword);
}
