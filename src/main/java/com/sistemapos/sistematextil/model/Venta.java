package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
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
@Table(name = "venta")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Integer idVenta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private String tipoComprobante;

    @Column(length = 10)
    private String serie;

    private Integer correlativo;

    @Column(name = "igv_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal igvPorcentaje;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuentoTotal;

    @Column(name = "tipo_descuento", length = 10)
    private String tipoDescuento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 10)
    private String estado;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String activo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.fecha == null) {
            this.fecha = now;
        }
        this.updatedAt = now;
        if (this.tipoComprobante == null || this.tipoComprobante.isBlank()) {
            this.tipoComprobante = "NOTA DE VENTA";
        }
        if (this.igvPorcentaje == null) {
            this.igvPorcentaje = BigDecimal.valueOf(18);
        }
        if (this.subtotal == null) {
            this.subtotal = BigDecimal.ZERO;
        }
        if (this.descuentoTotal == null) {
            this.descuentoTotal = BigDecimal.ZERO;
        }
        if (this.igv == null) {
            this.igv = BigDecimal.ZERO;
        }
        if (this.total == null) {
            this.total = BigDecimal.ZERO;
        }
        if (this.estado == null || this.estado.isBlank()) {
            this.estado = "EMITIDA";
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
