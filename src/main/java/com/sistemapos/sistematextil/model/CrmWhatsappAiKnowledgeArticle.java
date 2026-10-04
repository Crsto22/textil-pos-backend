package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_knowledge_article")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappAiKnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_knowledge_article")
    private Long idKnowledgeArticle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_connection", nullable = false)
    private CrmWhatsappConnection connection;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private CrmWhatsappAiKnowledgeCategory category;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "keywords", length = 500)
    private String keywords;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CrmWhatsappAiKnowledgeStatus status;

    @Column(name = "active_version", nullable = false)
    private Integer activeVersion;

    @Column(name = "pending_version", nullable = false)
    private Integer pendingVersion;

    @Column(name = "source_key", length = 80)
    private String sourceKey;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = CrmWhatsappAiKnowledgeStatus.BORRADOR;
        if (activeVersion == null) activeVersion = 0;
        if (pendingVersion == null) pendingVersion = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
