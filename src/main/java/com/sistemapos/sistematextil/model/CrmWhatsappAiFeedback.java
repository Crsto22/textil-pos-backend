package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_feedback")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappAiFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_feedback")
    private Long idAiFeedback;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ai_run", nullable = false, unique = true)
    private CrmWhatsappAiRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false)
    private CrmWhatsappConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reviewer", nullable = false)
    private Usuario reviewer;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_outgoing_message", unique = true)
    private CrmWhatsappMessage outgoingMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private CrmWhatsappAiFeedbackDecision decision;

    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "final_text", columnDefinition = "TEXT")
    private String finalText;

    @Column(name = "similarity_percentage", precision = 5, scale = 2)
    private BigDecimal similarityPercentage;

    @Column(name = "changed_characters")
    private Integer changedCharacters;

    @Column(name = "discard_reason", length = 300)
    private String discardReason;

    @Column(name = "send_status", nullable = false, length = 20)
    private String sendStatus;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (sendStatus == null || sendStatus.isBlank()) sendStatus = "NOT_SENT";
        if (reviewedAt == null) reviewedAt = now;
        createdAt = now;
    }
}
