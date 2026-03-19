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
@Table(name = "comprobante_config")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ComprobanteConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Integer idComprobante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private String tipoComprobante;

    @Column(name = "serie", nullable = false, length = 10)
    private String serie;

    @Column(name = "ultimo_correlativo", nullable = false)
    private Integer ultimoCorrelativo;

    @Column(name = "habilitado_venta", nullable = false)
    private Boolean habilitadoVenta;

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
        if (this.ultimoCorrelativo == null || this.ultimoCorrelativo < 0) {
            this.ultimoCorrelativo = 0;
        }
        if (this.activo == null || this.activo.isBlank()) {
            this.activo = "ACTIVO";
        }
        if (this.habilitadoVenta == null) {
            this.habilitadoVenta = "NOTA DE VENTA".equals(this.tipoComprobante)
                    || "BOLETA".equals(this.tipoComprobante)
                    || "FACTURA".equals(this.tipoComprobante);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
