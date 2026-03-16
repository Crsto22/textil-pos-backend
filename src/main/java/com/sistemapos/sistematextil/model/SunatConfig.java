package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.model.converter.EstadoActivoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sunat_config")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SunatConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sunat_config")
    private Integer idSunatConfig;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_empresa", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 20)
    private String ambiente;

    @Column(name = "usuario_sol", nullable = false, length = 50)
    private String usuarioSol;

    @Column(name = "clave_sol", nullable = false, length = 255)
    private String claveSol;

    @Column(name = "url_bill_service", length = 255)
    private String urlBillService;

    @Column(name = "certificado_url", length = 600)
    private String certificadoUrl;

    @Column(name = "certificado_password", length = 255)
    private String certificadoPassword;

    @Column(name = "client_id", length = 255)
    private String clientId;

    @Column(name = "client_secret", length = 255)
    private String clientSecret;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String activo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.ambiente == null || this.ambiente.isBlank()) {
            this.ambiente = "BETA";
        }
        if (this.activo == null || this.activo.isBlank()) {
            this.activo = "ACTIVO";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
