package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_quick_message_block")
@Getter
@Setter
@NoArgsConstructor
public class CrmQuickMessageBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_block")
    private Long idBlock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_quick_message", nullable = false)
    private CrmQuickMessage quickMessage;

    @Column(name = "block_type", nullable = false, length = 10)
    private String blockType;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "media_storage_path", length = 500)
    private String mediaStoragePath;

    @Column(name = "media_file_name", length = 255)
    private String mediaFileName;

    @Column(name = "media_mime_type", length = 120)
    private String mediaMimeType;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
