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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_payment_review_audit")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappPaymentReviewAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_review_audit")
    private Long idReviewAudit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_payment_evidence", nullable = false, unique = true)
    private CrmWhatsappPaymentEvidence paymentEvidence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_payment_request", nullable = false)
    private CrmWhatsappPaymentRequest paymentRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agent_session")
    private CrmWhatsappAgentSession agentSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conversation")
    private CrmWhatsappConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reviewed_by", nullable = false)
    private Usuario reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private CrmWhatsappPaymentReviewAction action;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "detected_amount", precision = 12, scale = 2)
    private BigDecimal detectedAmount;

    @Column(name = "operation_code", length = 120)
    private String operationCode;

    @Column(name = "sale_status_before", nullable = false, length = 20)
    private String saleStatusBefore;

    @Column(name = "sale_status_after", nullable = false, length = 20)
    private String saleStatusAfter;

    @Column(name = "agent_state_before", nullable = false, length = 50)
    private String agentStateBefore;

    @Column(name = "agent_state_after", nullable = false, length = 50)
    private String agentStateAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
