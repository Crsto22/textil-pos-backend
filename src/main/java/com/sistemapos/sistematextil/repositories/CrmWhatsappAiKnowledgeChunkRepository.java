package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeChunk;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeStatus;

public interface CrmWhatsappAiKnowledgeChunkRepository extends JpaRepository<CrmWhatsappAiKnowledgeChunk, Long> {

    @Query("""
            select c from CrmWhatsappAiKnowledgeChunk c
            join fetch c.article a
            where a.connection.idConnection = :connectionId
              and a.deletedAt is null
              and a.activeVersion > 0
              and c.articleVersion = a.activeVersion
              and a.status in :statuses
            order by a.updatedAt desc, c.chunkOrder asc
            """)
    List<CrmWhatsappAiKnowledgeChunk> findSearchable(
            @Param("connectionId") Long connectionId,
            @Param("statuses") List<CrmWhatsappAiKnowledgeStatus> statuses);

    @Modifying
    @Query("delete from CrmWhatsappAiKnowledgeChunk c where c.article.idKnowledgeArticle = :articleId and c.articleVersion <> :version")
    void deleteOtherVersions(@Param("articleId") Long articleId, @Param("version") Integer version);

    @Modifying
    @Query("delete from CrmWhatsappAiKnowledgeChunk c where c.article.idKnowledgeArticle = :articleId and c.articleVersion = :version")
    void deleteVersion(@Param("articleId") Long articleId, @Param("version") Integer version);
}
