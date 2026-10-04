package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_business_hours", uniqueConstraints =
        @UniqueConstraint(name = "uk_crm_whatsapp_business_hours_day", columnNames = {"id_connection", "day_of_week"}))
@Getter @Setter @NoArgsConstructor
public class CrmWhatsappBusinessHours {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_business_hours")
    private Long idBusinessHours;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_connection", nullable = false)
    private CrmWhatsappConnection connection;

    @Column(name = "day_of_week", nullable = false, length = 12)
    private String dayOfWeek;

    @Column(name = "closed", nullable = false)
    private Boolean closed;

    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (closed == null) closed = false; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
