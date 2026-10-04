package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeArticle;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeStatus;

public interface CrmWhatsappAiKnowledgeArticleRepository
        extends JpaRepository<CrmWhatsappAiKnowledgeArticle, Long> {
    List<CrmWhatsappAiKnowledgeArticle> findByConnection_IdConnectionAndDeletedAtIsNullOrderByUpdatedAtDesc(
            Long connectionId);
    Optional<CrmWhatsappAiKnowledgeArticle> findByIdKnowledgeArticleAndConnection_IdConnectionAndDeletedAtIsNull(
            Long articleId, Long connectionId);
    List<CrmWhatsappAiKnowledgeArticle> findByStatusAndDeletedAtIsNullOrderByUpdatedAtAsc(
            CrmWhatsappAiKnowledgeStatus status, Pageable pageable);
}
