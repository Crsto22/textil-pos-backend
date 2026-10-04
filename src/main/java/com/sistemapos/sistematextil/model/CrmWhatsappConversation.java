package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_conversation")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conversation")
    private Long idConversation;

    @Column(name = "phone", nullable = false, unique = true, length = 80)
    private String phone;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "whatsapp_username", length = 120)
    private String whatsappUsername;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "last_message", columnDefinition = "TEXT")
    private String lastMessage;

    @Column(name = "last_message_type", length = 20)
    private String lastMessageType;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_attention_mode", nullable = false, length = 20)
    private CrmWhatsappAiAttentionMode aiAttentionMode;

    @Column(name = "ai_attention_mode_explicit", nullable = false)
    private Boolean aiAttentionModeExplicit;

    @Enumerated(EnumType.STRING)
    @Column(name = "attention_queue", nullable = false, length = 30)
    private CrmWhatsappAttentionQueue attentionQueue;

    @Enumerated(EnumType.STRING)
    @Column(name = "waiting_reason", length = 30)
    private CrmWhatsappWaitingReason waitingReason;

    @ManyToOne(optional = true)
    @JoinColumn(name = "id_cliente", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = jakarta.persistence.FetchType.EAGER, optional = true)
    @JoinColumn(name = "id_connection", nullable = true)
    private CrmWhatsappConnection connection;

    @ManyToOne(optional = true)
    @JoinColumn(name = "assigned_user_id", nullable = true)
    private Usuario assignedUser;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "ESPERA";
        }
        if (unreadCount == null) {
            unreadCount = 0;
        }
        if (aiAttentionMode == null) {
            aiAttentionMode = CrmWhatsappAiAttentionMode.AUTOMATICA;
        }
        if (aiAttentionModeExplicit == null) {
            aiAttentionModeExplicit = false;
        }
        normalizeAttentionQueue();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeAttentionQueue();
    }

    private void normalizeAttentionQueue() {
        if ("RESUELTO".equals(status)) {
            attentionQueue = CrmWhatsappAttentionQueue.RESOLVED;
            waitingReason = null;
        } else if (assignedUser != null || "ATENDIDO".equals(status)) {
            attentionQueue = CrmWhatsappAttentionQueue.HUMAN_ACTIVE;
            waitingReason = null;
        } else if (waitingReason == CrmWhatsappWaitingReason.PAYMENT_VERIFICATION) {
            attentionQueue = CrmWhatsappAttentionQueue.PAYMENT_VERIFICATION;
        } else if (waitingReason == CrmWhatsappWaitingReason.AI_DISABLED
                || waitingReason == CrmWhatsappWaitingReason.ADVISOR_REQUIRED
                || aiAttentionMode == CrmWhatsappAiAttentionMode.HUMANA) {
            attentionQueue = CrmWhatsappAttentionQueue.ADVISOR_REQUIRED;
            if (waitingReason == null) {
                waitingReason = CrmWhatsappWaitingReason.ADVISOR_REQUIRED;
            }
        } else {
            attentionQueue = CrmWhatsappAttentionQueue.AI_ACTIVE;
            waitingReason = null;
        }
    }
}
