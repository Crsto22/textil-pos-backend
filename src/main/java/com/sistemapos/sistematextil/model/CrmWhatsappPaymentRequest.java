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
@Table(name = "crm_whatsapp_payment_request")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappPaymentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_payment_request")
    private Long idPaymentRequest;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_conversation")
    private CrmWhatsappConversation conversation;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_connection", nullable = false)
    private CrmWhatsappConnection connection;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_metodo_pago", nullable = false)
    private MetodoPagoConfig metodoPago;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_metodo_pago_cuenta")
    private MetodoPagoCuenta cuenta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta")
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "sale_request_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String saleRequestJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ai_sale_draft")
    private CrmWhatsappAiSaleDraft aiSaleDraft;

    @Column(name = "ai_sale_draft_version")
    private Integer aiSaleDraftVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false, length = 20)
    private CrmWhatsappPaymentReservationStatus reservationStatus;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "review_expires_at")
    private LocalDateTime reviewExpiresAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CrmWhatsappPaymentRequestStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE;
        if (reservationStatus == null) reservationStatus = CrmWhatsappPaymentReservationStatus.LEGACY_NONE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
