package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shalom_agency")
@Getter
@Setter
@NoArgsConstructor
public class ShalomAgency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_shalom_agency")
    private Long idShalomAgency;

    @Column(name = "ter_id", nullable = false, unique = true)
    private Integer externalId;
    @Column(name = "abbreviation", length = 30) private String abbreviation;
    @Column(name = "name", nullable = false, length = 300) private String name;
    @Column(name = "department", length = 120) private String department;
    @Column(name = "province", length = 120) private String province;
    @Column(name = "zone", length = 180) private String zone;
    @Column(name = "place", length = 180) private String place;
    @Column(name = "address", length = 500) private String address;
    @Column(name = "phone", length = 100) private String phone;
    @Column(name = "attention_hours", length = 300) private String attentionHours;
    @Column(name = "latitude", precision = 12, scale = 8) private BigDecimal latitude;
    @Column(name = "longitude", precision = 12, scale = 8) private BigDecimal longitude;
    @Column(name = "ubigeo_id") private Integer ubigeoId;
    @Column(name = "origin_enabled", nullable = false) private boolean originEnabled;
    @Column(name = "destination_enabled", nullable = false) private boolean destinationEnabled;
    @Column(name = "delivery_enabled", nullable = false) private boolean deliveryEnabled;
    @Column(name = "principal", nullable = false) private boolean principal;
    @Column(name = "service_status", length = 120) private String serviceStatus;
    @Column(name = "normalized_search", nullable = false, columnDefinition = "TEXT") private String normalizedSearch;
    @Column(name = "raw_payload", nullable = false, columnDefinition = "JSON") private String rawPayload;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "last_seen_at", nullable = false) private LocalDateTime lastSeenAt;
    @Column(name = "synced_at", nullable = false) private LocalDateTime syncedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist
    void create() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void update() { updatedAt = LocalDateTime.now(); }
}
