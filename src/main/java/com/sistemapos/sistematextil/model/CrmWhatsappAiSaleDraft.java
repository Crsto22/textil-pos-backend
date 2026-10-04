package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_sale_draft")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappAiSaleDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_sale_draft") private Long idAiSaleDraft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false) private CrmWhatsappConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_connection", nullable = false) private CrmWhatsappConnection connection;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sucursal", nullable = false) private Sucursal sucursal;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_metodo_pago") private MetodoPagoConfig metodoPago;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_venta") private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private CrmWhatsappAiSaleDraftStatus status;
    @Column(nullable = false) private Integer version;
    @Column(name = "confirmed_version") private Integer confirmedVersion;
    @Column(name = "customer_confirmed_at") private LocalDateTime customerConfirmedAt;
    @Column(name = "confirmation_message_id") private Long confirmationMessageId;
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2) private BigDecimal subtotal;
    @Column(name = "promotion_discount", nullable = false, precision = 12, scale = 2) private BigDecimal promotionDiscount;
    @Column(name = "total", nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @Column(name = "pending_customer_name", length = 150) private String pendingCustomerName;
    @Column(name = "pending_customer_phone", length = 9) private String pendingCustomerPhone;
    @Column(name = "customer_name_current", length = 150) private String customerNameCurrent;
    @Column(name = "customer_name_suggested", length = 150) private String customerNameSuggested;
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_name_suggestion_status", length = 20)
    private CrmWhatsappAiCustomerNameSuggestionStatus customerNameSuggestionStatus;
    @Column(name = "pending_promotion_id") private Integer pendingPromotionId;
    @Column(name = "last_ecommerce_message_id") private Long lastEcommerceMessageId;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idAiSaleDraftItem ASC")
    private List<CrmWhatsappAiSaleDraftItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idAiSaleDraftPromotion ASC")
    private List<CrmWhatsappAiSaleDraftPromotion> promotions = new ArrayList<>();

    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now; updatedAt = now;
        if (status == null) status = CrmWhatsappAiSaleDraftStatus.BUILDING;
        if (version == null) version = 1;
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (promotionDiscount == null) promotionDiscount = BigDecimal.ZERO;
        if (total == null) total = BigDecimal.ZERO;
        if (expiresAt == null) expiresAt = now.plusHours(24);
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
