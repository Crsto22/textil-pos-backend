package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_film_addon")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappFilmAddon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_film_addon") private Long idFilmAddon;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_agent_session", nullable = false, unique = true)
    private CrmWhatsappAgentSession agentSession;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fulfillment", nullable = false, unique = true)
    private CrmWhatsappFulfillment fulfillment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false)
    private CrmWhatsappConversation conversation;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_metodo_pago_cuenta") private MetodoPagoCuenta paymentAccount;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_venta") private Venta venta;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CrmWhatsappFilmStatus status;
    @Column(name = "offered_at", nullable = false) private LocalDateTime offeredAt;
    @Column(name = "payment_started_at") private LocalDateTime paymentStartedAt;
    @Column(name = "expires_at") private LocalDateTime expiresAt;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    @Column(name = "final_message_sent_at") private LocalDateTime finalMessageSentAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (amount == null) amount = new BigDecimal("2.00");
        if (status == null) status = CrmWhatsappFilmStatus.OFFERED;
        if (offeredAt == null) offeredAt = now;
        createdAt = updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
