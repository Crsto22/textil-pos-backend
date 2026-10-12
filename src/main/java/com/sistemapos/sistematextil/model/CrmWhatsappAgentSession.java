package com.sistemapos.sistematextil.model;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_agent_session")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappAgentSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_agent_session")
    private Long idAgentSession;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false)
    private CrmWhatsappConversation conversation;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_connection", nullable = false)
    private CrmWhatsappConnection connection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "selected_payment_method_id")
    private MetodoPagoConfig selectedPaymentMethod;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "selected_payment_account_id")
    private MetodoPagoCuenta selectedPaymentAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_customer_message_id")
    private CrmWhatsappMessage lastCustomerMessage;

    @Column(name = "session_key", nullable = false, length = 64, unique = true)
    private String sessionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private CrmWhatsappAgentState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "resume_state", length = 50)
    private CrmWhatsappAgentState resumeState;

    @Column(name = "active_slot")
    private Boolean activeSlot;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_event", length = 60)
    private CrmWhatsappAgentEvent lastEvent;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "state_changed_at", nullable = false)
    private LocalDateTime stateChangedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (state == null) state = CrmWhatsappAgentState.IDLE;
        if (activeSlot == null) activeSlot = Boolean.TRUE;
        if (lockVersion == null) lockVersion = 0L;
        if (startedAt == null) startedAt = now;
        if (lastActivityAt == null) lastActivityAt = now;
        if (stateChangedAt == null) stateChangedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
