package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_memory_item")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappAiMemoryItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_memory_item") private Long idAiMemoryItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ai_memory", nullable = false) private CrmWhatsappAiMemory memory;
    @Column(name = "id_product") private Integer productId;
    @Column(name = "id_variant") private Integer variantId;
    @Column(name = "product_name", nullable = false, length = 180) private String productName;
    @Column(name = "color", length = 100) private String color;
    @Column(name = "size", length = 60) private String size;
    @Column(name = "quantity", nullable = false) private Integer quantity;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (quantity == null || quantity < 1) quantity = 1; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
