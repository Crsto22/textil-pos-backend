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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_fulfillment")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappFulfillment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fulfillment")
    private Long idFulfillment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agent_session")
    private CrmWhatsappAgentSession agentSession;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_conversation")
    private CrmWhatsappConversation conversation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_payment_request")
    private CrmWhatsappPaymentRequest paymentRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_venta")
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_pickup_branch")
    private Sucursal pickupBranch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_shalom_agency")
    private ShalomAgency shalomAgencyReference;

    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private CrmWhatsappFulfillmentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CrmWhatsappFulfillmentStatus status;

    @Column(name = "recipient_name", length = 180)
    private String recipientName;

    @Column(name = "document_type", length = 10)
    private String documentType;

    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "shalom_agency", length = 180)
    private String shalomAgency;

    @Column(name = "shalom_destination", length = 180)
    private String shalomDestination;

    @Column(name = "shalom_agency_address", length = 500)
    private String shalomAgencyAddress;

    @Column(name = "shalom_candidate_ids", length = 500)
    private String shalomCandidateIds;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) status = CrmWhatsappFulfillmentStatus.DRAFT;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
