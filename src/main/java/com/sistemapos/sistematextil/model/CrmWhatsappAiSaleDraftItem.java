package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_sale_draft_item")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappAiSaleDraftItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_sale_draft_item") private Long idAiSaleDraftItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ai_sale_draft", nullable = false) private CrmWhatsappAiSaleDraft draft;
    @Column(name = "id_product", nullable = false) private Integer productId;
    @Column(name = "id_variant", nullable = false) private Integer variantId;
    @Column(name = "product_name", nullable = false, length = 180) private String productName;
    @Column(name = "sku", length = 100) private String sku;
    @Column(name = "color", length = 100) private String color;
    @Column(name = "size", length = 60) private String size;
    @Column(name = "quantity", nullable = false) private Integer quantity;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
    @Column(name = "regular_unit_price", precision = 12, scale = 2) private BigDecimal regularUnitPrice;
    @Column(name = "stock_snapshot", nullable = false) private Integer stockSnapshot;
    @Column(name = "image_url", length = 500) private String imageUrl;
    @Column(name = "preventa", nullable = false) private Boolean preventa = Boolean.FALSE;
    @Column(name = "fecha_envio_preventa") private LocalDate fechaEnvioPreventa;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
