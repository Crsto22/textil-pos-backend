package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatJobEstado;
import com.sistemapos.sistematextil.util.sunat.SunatJobTipoDocumento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sunat_job")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SunatJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sunat_job")
    private Long idSunatJob;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 30)
    private SunatJobTipoDocumento tipoDocumento;

    @Column(name = "documento_id", nullable = false)
    private Integer documentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SunatJobEstado estado;

    @Column(nullable = false)
    private Integer intentos;

    @Column(name = "max_intentos", nullable = false)
    private Integer maxIntentos;

    @Column(name = "ultimo_error", length = 1000)
    private String ultimoError;

    @Column(name = "ultimo_codigo", length = 40)
    private String ultimoCodigo;

    @Column(name = "ticket_sunat", length = 120)
    private String ticketSunat;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = SunatJobEstado.PENDIENTE_ENVIO;
        }
        if (this.intentos == null) {
            this.intentos = 0;
        }
        if (this.maxIntentos == null || this.maxIntentos < 1) {
            this.maxIntentos = 10;
        }
        if (this.nextRetryAt == null) {
            this.nextRetryAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
