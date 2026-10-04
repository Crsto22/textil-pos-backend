package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_audit_event")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappAiAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_audit_event") private Long idAuditEvent;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_connection", nullable = false) private CrmWhatsappConnection connection;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conversation") private CrmWhatsappConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ai_run") private CrmWhatsappAiRun run;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_actor") private Usuario actor;
    @Column(name = "event_type", nullable = false, length = 50) private String eventType;
    @Column(nullable = false, length = 20) private String severity;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "metadata_json", columnDefinition = "TEXT") private String metadataJson;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
