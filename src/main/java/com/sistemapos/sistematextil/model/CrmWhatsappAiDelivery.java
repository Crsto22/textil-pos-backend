package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_delivery")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappAiDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_delivery") private Long idAiDelivery;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ai_run", unique = true) private CrmWhatsappAiRun run;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false) private CrmWhatsappConversation conversation;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_outgoing_message") private CrmWhatsappMessage outgoingMessage;
    @Column(name = "source_message_id") private Long sourceMessageId;
    @Column(name = "text_body", length = 2000) private String textBody;
    @Column(name = "idempotency_key", length = 160, unique = true) private String idempotencyKey;
    @Column(name = "media_reference", length = 1000) private String mediaReference;
    @Column(name = "media_mime_type", length = 120) private String mediaMimeType;
    @Column(name = "media_file_name", length = 255) private String mediaFileName;
    @Column(name = "media_caption", length = 1000) private String mediaCaption;
    @Column(name = "secondary_media_reference", length = 1000) private String secondaryMediaReference;
    @Column(name = "secondary_media_mime_type", length = 120) private String secondaryMediaMimeType;
    @Column(name = "secondary_media_file_name", length = 255) private String secondaryMediaFileName;
    @Column(name = "guide_outgoing_message_id") private Long guideOutgoingMessageId;
    @Column(name = "prelude_outgoing_message_id") private Long preludeOutgoingMessageId;
    @Column(name = "initial_conversation_response", nullable = false)
    private Boolean initialConversationResponse;
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 30) private CrmWhatsappAiDeliveryType deliveryType;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) private CrmWhatsappAiDeliveryStatus status;
    @Column(name = "attempts", nullable = false) private Integer attempts;
    @Column(name = "available_at", nullable = false) private LocalDateTime availableAt;
    @Column(name = "locked_at") private LocalDateTime lockedAt;
    @Column(name = "sent_at") private LocalDateTime sentAt;
    @Column(name = "failure_reason", length = 1000) private String failureReason;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (deliveryType == null) deliveryType = CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE; if (status == null) status = CrmWhatsappAiDeliveryStatus.PENDING; if (attempts == null) attempts = 0; if (availableAt == null) availableAt = now; if (initialConversationResponse == null) initialConversationResponse = false; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
