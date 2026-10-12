package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_config")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappAiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_config")
    private Long idAiConfig;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_connection", nullable = false, unique = true)
    private CrmWhatsappConnection connection;

    @Column(name = "gemini_api_key_ciphertext", columnDefinition = "TEXT")
    private String geminiApiKeyCiphertext;

    @Column(name = "gemini_api_key_nonce", length = 64)
    private String geminiApiKeyNonce;

    @Column(name = "gemini_model", length = 100)
    private String geminiModel;

    @Column(name = "agent_enabled", nullable = false)
    private Boolean agentEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "activation_stage", nullable = false, length = 30)
    private CrmWhatsappAgentActivationStage activationStage;

    @Column(name = "test_phone_numbers", length = 1000)
    private String testPhoneNumbers;

    @Column(name = "limited_conversations_per_hour", nullable = false)
    private Integer limitedConversationsPerHour;

    @Column(name = "message_grouping_ms", nullable = false)
    private Integer messageGroupingMs;

    @Column(name = "payment_reservation_seconds", nullable = false)
    private Integer paymentReservationSeconds;

    @Column(name = "receipt_minimum_confidence", nullable = false)
    private Integer receiptMinimumConfidence;

    @Column(name = "advisor_attention_enabled", nullable = false)
    private Boolean advisorAttentionEnabled;

    @Column(name = "job_max_attempts", nullable = false)
    private Integer jobMaxAttempts;

    @Column(name = "outbox_max_attempts", nullable = false)
    private Integer outboxMaxAttempts;

    @Column(name = "max_messages_per_batch", nullable = false)
    private Integer maxMessagesPerBatch;

    @Column(name = "max_tool_calls_per_run", nullable = false)
    private Integer maxToolCallsPerRun;

    @Column(name = "max_output_messages_per_run", nullable = false)
    private Integer maxOutputMessagesPerRun;

    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    @Column(name = "agent_behavior_instructions", columnDefinition = "TEXT")
    private String agentBehaviorInstructions;

    @Column(name = "api_key_updated_at")
    private LocalDateTime apiKeyUpdatedAt;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "api_key_updated_by")
    private Usuario apiKeyUpdatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        applyAgentDefaults();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        applyAgentDefaults();
    }

    private void applyAgentDefaults() {
        if (agentEnabled == null) agentEnabled = false;
        if (activationStage == null) activationStage = CrmWhatsappAgentActivationStage.LOCAL_TEST;
        if (limitedConversationsPerHour == null) limitedConversationsPerHour = 10;
        if (messageGroupingMs == null) messageGroupingMs = 5000;
        if (paymentReservationSeconds == null) paymentReservationSeconds = 240;
        if (receiptMinimumConfidence == null) receiptMinimumConfidence = 85;
        if (advisorAttentionEnabled == null) advisorAttentionEnabled = false;
        if (jobMaxAttempts == null) jobMaxAttempts = 3;
        if (outboxMaxAttempts == null) outboxMaxAttempts = 5;
        if (maxMessagesPerBatch == null) maxMessagesPerBatch = 15;
        if (maxToolCallsPerRun == null) maxToolCallsPerRun = 12;
        if (maxOutputMessagesPerRun == null) maxOutputMessagesPerRun = 6;
    }
}
