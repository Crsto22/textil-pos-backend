package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query("""
            update CrmWhatsappAiKnowledgeArticle a
               set a.deletedAt = :deletedAt,
                   a.status = :draftStatus,
                   a.pendingVersion = a.activeVersion
             where a.idKnowledgeArticle = :articleId
               and a.connection.idConnection = :connectionId
               and a.deletedAt is null
            """)
    int softDelete(
            @Param("articleId") Long articleId,
            @Param("connectionId") Long connectionId,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("draftStatus") CrmWhatsappAiKnowledgeStatus draftStatus);

    @Modifying
    @Query("""
            update CrmWhatsappAiKnowledgeArticle a
               set a.activeVersion = :version,
                   a.status = :activeStatus,
                   a.indexedAt = :indexedAt,
                   a.lastError = null
             where a.idKnowledgeArticle = :articleId
               and a.deletedAt is null
               and a.status = :indexingStatus
               and a.pendingVersion = :version
            """)
    int activateIndexedVersion(
            @Param("articleId") Long articleId,
            @Param("version") Integer version,
            @Param("indexedAt") LocalDateTime indexedAt,
            @Param("indexingStatus") CrmWhatsappAiKnowledgeStatus indexingStatus,
            @Param("activeStatus") CrmWhatsappAiKnowledgeStatus activeStatus);

    @Modifying
    @Query("""
            update CrmWhatsappAiKnowledgeArticle a
               set a.status = :errorStatus,
                   a.lastError = :lastError
             where a.idKnowledgeArticle = :articleId
               and a.deletedAt is null
               and a.status = :indexingStatus
               and a.pendingVersion = :version
            """)
    int markIndexError(
            @Param("articleId") Long articleId,
            @Param("version") Integer version,
            @Param("lastError") String lastError,
            @Param("indexingStatus") CrmWhatsappAiKnowledgeStatus indexingStatus,
            @Param("errorStatus") CrmWhatsappAiKnowledgeStatus errorStatus);
}
