package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Table(name = "cotizacion")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Cotizacion {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cotizacion")
    private Integer idCotizacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(length = 10)
    private String serie;

    private Integer correlativo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuentoTotal;

    @Column(name = "tipo_descuento", length = 10)
    private String tipoDescuento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(length = 500)
    private String observacion;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String activo;

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
        if (this.subtotal == null) {
            this.subtotal = BigDecimal.ZERO;
        }
        if (this.descuentoTotal == null) {
            this.descuentoTotal = BigDecimal.ZERO;
        }
        if (this.total == null) {
            this.total = BigDecimal.ZERO;
        }
        if (this.estado == null || this.estado.isBlank()) {
            this.estado = "ACTIVA";
        }
        if (this.activo == null || this.activo.isBlank()) {
            this.activo = "ACTIVO";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getIgv() {
        BigDecimal subtotalActual = this.subtotal == null ? BigDecimal.ZERO : this.subtotal;
        BigDecimal totalActual = this.total == null ? BigDecimal.ZERO : this.total;
        BigDecimal igvCalculado = totalActual.subtract(subtotalActual);
        if (igvCalculado.compareTo(BigDecimal.ZERO) < 0) {
            igvCalculado = BigDecimal.ZERO;
        }
        return igvCalculado.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getIgvPorcentaje() {
        BigDecimal subtotalActual = this.subtotal == null ? BigDecimal.ZERO : this.subtotal;
        if (subtotalActual.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return getIgv()
                .multiply(CIEN)
                .divide(subtotalActual, 2, RoundingMode.HALF_UP);
    }
}
