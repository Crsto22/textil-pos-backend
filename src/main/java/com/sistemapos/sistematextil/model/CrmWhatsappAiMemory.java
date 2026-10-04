package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_memory")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappAiMemory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_memory")
    private Long idAiMemory;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false, unique = true)
    private CrmWhatsappConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "attention_state", nullable = false, length = 20)
    private CrmWhatsappAiAttentionState attentionState;

    @Column(name = "current_intent", length = 50) private String currentIntent;
    @Column(name = "id_product") private Integer productId;
    @Column(name = "id_variant") private Integer variantId;
    @Column(name = "product_name", length = 180) private String productName;
    @Column(name = "color", length = 100) private String color;
    @Column(name = "size", length = 60) private String size;
    @Column(name = "quantity") private Integer quantity;
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_question", nullable = false, length = 30)
    private CrmWhatsappAiPendingQuestion pendingQuestion;
    @Column(name = "last_pending_reply_message_id") private Long lastPendingReplyMessageId;
    @Column(name = "last_pending_reply_intent", length = 50) private String lastPendingReplyIntent;
    @Column(name = "last_pending_reply_response", length = 2000) private String lastPendingReplyResponse;
    @Column(name = "last_incoming_message_id") private Long lastIncomingMessageId;
    @Column(name = "last_ai_message_id") private Long lastAiMessageId;
    @Column(name = "greeting_sent_at") private LocalDateTime greetingSentAt;
    @Column(name = "consecutive_auto_responses", nullable = false) private Integer consecutiveAutoResponses;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "memory", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idAiMemoryItem ASC")
    private List<CrmWhatsappAiMemoryItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (attentionState == null) attentionState = CrmWhatsappAiAttentionState.AUTOMATICA; if (pendingQuestion == null) pendingQuestion = CrmWhatsappAiPendingQuestion.NONE; if (consecutiveAutoResponses == null) consecutiveAutoResponses = 0; if (expiresAt == null) expiresAt = now.plusHours(24); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
