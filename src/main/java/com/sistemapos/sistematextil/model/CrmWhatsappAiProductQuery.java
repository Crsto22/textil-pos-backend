package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_product_query", uniqueConstraints =
        @UniqueConstraint(name = "uk_crm_ai_product_query_run_product", columnNames = {"id_ai_run", "id_producto"}))
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappAiProductQuery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_product_query") private Long idProductQuery;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ai_run", nullable = false) private CrmWhatsappAiRun run;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_conversation", nullable = false) private CrmWhatsappConversation conversation;
    @Column(name = "id_producto", nullable = false) private Integer productId;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
