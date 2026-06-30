package com.sistemapos.sistematextil.model;

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
import lombok.Setter;

@Entity
@Table(name = "ecommerce_portada")
@Getter
@Setter
public class EcommercePortada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ecommerce_portada")
    private Integer idEcommercePortada;

    @Column(name = "desktop_url", nullable = false, length = 600)
    private String desktopUrl;

    @Column(name = "desktop_thumb_url", length = 600)
    private String desktopThumbUrl;

    @Column(name = "mobile_url", nullable = false, length = 600)
    private String mobileUrl;

    @Column(name = "mobile_thumb_url", length = 600)
    private String mobileThumbUrl;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (estado == null || estado.isBlank()) {
            estado = "ACTIVO";
        }
        if (orden == null) {
            orden = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
