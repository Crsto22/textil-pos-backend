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
@Table(name = "nota_credito_detalle")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NotaCreditoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota_credito_detalle")
    private Integer idNotaCreditoDetalle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_nota_credito", nullable = false)
    private NotaCredito notaCredito;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto_variante", nullable = false)
    private ProductoVariante productoVariante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta_detalle_ref")
    private VentaDetalle ventaDetalleReferencia;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "unidad_medida", nullable = false, length = 3)
    private String unidadMedida;

    @Column(name = "codigo_tipo_afectacion_igv", nullable = false, length = 2)
    private String codigoTipoAfectacionIgv;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal descuento;

    @Column(name = "igv_detalle", nullable = false, precision = 10, scale = 2)
    private BigDecimal igvDetalle;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_detalle", precision = 10, scale = 2)
    private BigDecimal totalDetalle;

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
        if (this.descuento == null) {
            this.descuento = BigDecimal.ZERO;
        }
        if (this.unidadMedida == null || this.unidadMedida.isBlank()) {
            this.unidadMedida = "NIU";
        }
        if (this.codigoTipoAfectacionIgv == null || this.codigoTipoAfectacionIgv.isBlank()) {
            this.codigoTipoAfectacionIgv = "10";
        }
        if (this.igvDetalle == null) {
            this.igvDetalle = BigDecimal.ZERO;
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
