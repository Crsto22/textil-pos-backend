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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_payment_evidence")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappPaymentEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment_evidence")
    private Long idPaymentEvidence;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_message", unique = true)
    private CrmWhatsappMessage message;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_conversation")
    private CrmWhatsappConversation conversation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_payment_request")
    private CrmWhatsappPaymentRequest paymentRequest;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewed_by")
    private Usuario reviewedBy;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "perceptual_hash", length = 32)
    private String perceptualHash;

    @Column(name = "detected_provider", length = 40)
    private String detectedProvider;

    @Column(name = "detected_amount", precision = 12, scale = 2)
    private BigDecimal detectedAmount;

    @Column(name = "detected_currency", length = 3)
    private String detectedCurrency;

    @Column(name = "operation_code", length = 120)
    private String operationCode;

    @Column(name = "operation_at")
    private LocalDateTime operationAt;

    @Column(name = "recipient", length = 180)
    private String recipient;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "extraction_json", columnDefinition = "TEXT")
    private String extractionJson;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    @Column(name = "duplicate_of_id")
    private Long duplicateOfId;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 30)
    private CrmWhatsappPaymentEvidenceStatus validationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private CrmWhatsappPaymentProcessingStatus processingStatus;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "customer_notified_at")
    private LocalDateTime customerNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (validationStatus == null) validationStatus = CrmWhatsappPaymentEvidenceStatus.PENDIENTE_VALIDACION;
        if (processingStatus == null) processingStatus = CrmWhatsappPaymentProcessingStatus.QUEUED;
        if (attempts == null) attempts = 0;
        if (availableAt == null) availableAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
