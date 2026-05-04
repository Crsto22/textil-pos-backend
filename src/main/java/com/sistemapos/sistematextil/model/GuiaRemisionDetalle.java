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
@Table(name = "guia_remision_detalle")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GuiaRemisionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_guia_remision_detalle")
    private Integer idGuiaRemisionDetalle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_guia_remision", nullable = false)
    private GuiaRemision guiaRemision;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto_variante", nullable = false)
    private ProductoVariante productoVariante;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "unidad_medida", nullable = false, length = 3)
    private String unidadMedida;

    @Column(name = "codigo_producto", length = 30)
    private String codigoProducto;

    @Column(name = "peso_unitario", precision = 12, scale = 3)
    private BigDecimal pesoUnitario;

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
        if (this.unidadMedida == null || this.unidadMedida.isBlank()) {
            this.unidadMedida = "NIU";
        }
        if (this.activo == null || this.activo.isBlank()) {
            this.activo = "ACTIVO";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.unidadMedida == null || this.unidadMedida.isBlank()) {
            this.unidadMedida = "NIU";
        }
    }
}
