package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_film_evidence")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappFilmEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_film_evidence") private Long idFilmEvidence;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_film_addon", nullable = false) private CrmWhatsappFilmAddon filmAddon;
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_message", nullable = false, unique = true) private CrmWhatsappMessage message;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "reviewed_by") private Usuario reviewedBy;
    @Column(name = "storage_path", nullable = false, length = 500) private String storagePath;
    @Column(name = "mime_type", nullable = false, length = 120) private String mimeType;
    @Column(name = "file_name", length = 255) private String fileName;
    @Column(name = "sha256", nullable = false, length = 64) private String sha256;
    @Column(name = "detected_provider", length = 40) private String detectedProvider;
    @Column(name = "detected_amount", precision = 12, scale = 2) private BigDecimal detectedAmount;
    @Column(name = "detected_currency", length = 3) private String detectedCurrency;
    @Column(name = "operation_code", length = 120) private String operationCode;
    @Column(name = "operation_at") private LocalDateTime operationAt;
    @Column(name = "recipient", length = 180) private String recipient;
    @Column(name = "confidence") private Integer confidence;
    @Column(name = "extracted_text", columnDefinition = "TEXT") private String extractedText;
    @Column(name = "extraction_json", columnDefinition = "TEXT") private String extractionJson;
    @Column(name = "warnings_json", columnDefinition = "TEXT") private String warningsJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 30)
    private CrmWhatsappPaymentEvidenceStatus validationStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private CrmWhatsappPaymentProcessingStatus processingStatus;
    @Column(name = "attempts", nullable = false) private Integer attempts;
    @Column(name = "available_at", nullable = false) private LocalDateTime availableAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "review_note", length = 500) private String reviewNote;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (validationStatus == null) validationStatus = CrmWhatsappPaymentEvidenceStatus.PENDIENTE_VALIDACION;
        if (processingStatus == null) processingStatus = CrmWhatsappPaymentProcessingStatus.QUEUED;
        if (attempts == null) attempts = 0;
        if (availableAt == null) availableAt = now;
        createdAt = updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
