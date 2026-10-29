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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_message_reaction", uniqueConstraints =
        @UniqueConstraint(name = "uk_crm_message_reaction_direction", columnNames = {"id_message", "direction"}))
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappMessageReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reaction")
    private Long idReaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_message", nullable = false)
    private CrmWhatsappMessage message;

    @Column(name = "direction", nullable = false, length = 20)
    private String direction;

    @Column(name = "emoji", nullable = false, length = 32)
    private String emoji;

    @Column(name = "whatsapp_reaction_id", length = 120)
    private String whatsappReactionId;

    @Column(name = "event_at")
    private LocalDateTime eventAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
