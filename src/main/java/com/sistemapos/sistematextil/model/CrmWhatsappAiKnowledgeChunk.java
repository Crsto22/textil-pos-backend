package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_knowledge_chunk")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappAiKnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_knowledge_chunk")
    private Long idKnowledgeChunk;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_knowledge_article", nullable = false)
    private CrmWhatsappAiKnowledgeArticle article;

    @Column(name = "article_version", nullable = false)
    private Integer articleVersion;

    @Column(name = "chunk_order", nullable = false)
    private Integer chunkOrder;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "embedding_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String embeddingJson;

    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
