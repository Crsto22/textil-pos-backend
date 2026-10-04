package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

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
@Table(name = "crm_whatsapp_ai_run")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappAiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_run")
    private Long idAiRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ai_job", nullable = false)
    private CrmWhatsappAiJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_message", nullable = false)
    private CrmWhatsappMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false)
    private CrmWhatsappConversation conversation;

    @OneToOne(mappedBy = "run", fetch = FetchType.LAZY)
    private CrmWhatsappAiFeedback feedback;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private CrmWhatsappAiRunOutcome outcome;

    @Column(name = "intent", length = 50)
    private String intent;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "requires_human", nullable = false)
    private Boolean requiresHuman;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "tool_trace_json", columnDefinition = "TEXT")
    private String toolTraceJson;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "draft_response", columnDefinition = "TEXT")
    private String draftResponse;

    @Column(name = "suggested_media_json", columnDefinition = "TEXT")
    private String suggestedMediaJson;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "input_cost_per_million_usd", precision = 12, scale = 6)
    private BigDecimal inputCostPerMillionUsd;

    @Column(name = "output_cost_per_million_usd", precision = 12, scale = 6)
    private BigDecimal outputCostPerMillionUsd;

    @Column(name = "estimated_cost_usd", precision = 14, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "error_detail", length = 1000)
    private String errorDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (requiresHuman == null) requiresHuman = false;
        if (provider == null || provider.isBlank()) provider = "GEMINI";
        if (promptVersion == null || promptVersion.isBlank()) promptVersion = "v1";
        if (promptVersion.length() > 100) promptVersion = promptVersion.substring(0, 100);
        createdAt = LocalDateTime.now();
    }
}
