package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmQuickMessageBlock;

public interface CrmQuickMessageBlockRepository extends JpaRepository<CrmQuickMessageBlock, Long> {

    List<CrmQuickMessageBlock> findByQuickMessage_IdQuickMessageAndDeletedAtIsNullOrderByPositionAscIdBlockAsc(Long commandId);

    Optional<CrmQuickMessageBlock> findByIdBlockAndQuickMessage_IdQuickMessageAndQuickMessage_Empresa_IdEmpresaAndDeletedAtIsNull(
            Long blockId, Long commandId, Integer idEmpresa);
}
