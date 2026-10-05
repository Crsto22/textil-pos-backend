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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_message")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_message")
    private Long idMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conversation", nullable = false)
    private CrmWhatsappConversation conversation;

    @Column(name = "direction", nullable = false, length = 20)
    private String direction;

    @Column(name = "origin", nullable = false, length = 30)
    private String origin;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "whatsapp_message_id", unique = true, length = 120)
    private String whatsappMessageId;

    @Column(name = "message_status", nullable = false, length = 20)
    private String messageStatus;

    @Column(name = "media_mime_type", length = 120)
    private String mediaMimeType;

    @Column(name = "media_file_name", length = 255)
    private String mediaFileName;

    @Column(name = "media_storage_path", length = 500)
    private String mediaStoragePath;

    @Column(name = "media_duration_seconds")
    private Integer mediaDurationSeconds;

    @Column(name = "audio_transcription", columnDefinition = "TEXT")
    private String audioTranscription;

    @Column(name = "audio_transcription_status", length = 20)
    private String audioTranscriptionStatus;

    @Column(name = "audio_transcription_language", length = 20)
    private String audioTranscriptionLanguage;

    @Column(name = "audio_transcription_confidence")
    private Integer audioTranscriptionConfidence;

    @Column(name = "message_key_json", columnDefinition = "TEXT")
    private String messageKeyJson;

    @Column(name = "baileys_message_json", columnDefinition = "MEDIUMTEXT")
    private String baileysMessageJson;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Column(name = "related_sale_id")
    private Integer relatedSaleId;

    @Column(name = "receipt_format", length = 10)
    private String receiptFormat;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (messageType == null || messageType.isBlank()) {
            messageType = "TEXT";
        }
        if (origin == null || origin.isBlank()) {
            origin = "EXTERNAL";
        }
        if (messageStatus == null || messageStatus.isBlank()) {
            messageStatus = "sent";
        }
    }
}
