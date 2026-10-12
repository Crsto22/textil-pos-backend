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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_shalom_profile")
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappShalomProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_shalom_profile") private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "id_shalom_agency", nullable = false)
    private ShalomAgency agency;
    @Column(name = "recipient_name", nullable = false, length = 180) private String recipientName;
    @Column(name = "document_number", nullable = false, length = 20) private String documentNumber;
    @Column(name = "phone", nullable = false, length = 30) private String phone;
    @Column(name = "agency_name_snapshot", nullable = false, length = 300) private String agencyNameSnapshot;
    @Column(name = "agency_address_snapshot", length = 500) private String agencyAddressSnapshot;
    @Column(name = "confirmed_at", nullable = false) private LocalDateTime confirmedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { LocalDateTime now=LocalDateTime.now(); if(createdAt==null)createdAt=now; updatedAt=now; }
    @PreUpdate void update() { updatedAt=LocalDateTime.now(); }
}
