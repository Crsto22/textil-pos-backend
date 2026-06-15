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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "sucursal_metodo_pago_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sucursal_metodo_pago",
                columnNames = { "id_sucursal", "id_metodo_pago" }))
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SucursalMetodoPagoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sucursal_metodo_pago_config")
    private Integer idSucursalMetodoPagoConfig;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_metodo_pago", nullable = false)
    private MetodoPagoConfig metodoPago;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String estado = "ACTIVO";

    @Column(name = "requiere_codigo_operacion", nullable = false)
    private Boolean requiereCodigoOperacion = false;

    @Column(name = "requiere_fecha_pago", nullable = false)
    private Boolean requiereFechaPago = false;

    @Column(name = "requiere_hora_pago", nullable = false)
    private Boolean requiereHoraPago = false;

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
        if (this.estado == null || this.estado.isBlank()) {
            this.estado = "ACTIVO";
        }
        if (this.requiereCodigoOperacion == null) {
            this.requiereCodigoOperacion = false;
        }
        if (this.requiereFechaPago == null) {
            this.requiereFechaPago = false;
        }
        if (this.requiereHoraPago == null) {
            this.requiereHoraPago = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.requiereCodigoOperacion == null) {
            this.requiereCodigoOperacion = false;
        }
        if (this.requiereFechaPago == null) {
            this.requiereFechaPago = false;
        }
        if (this.requiereHoraPago == null) {
            this.requiereHoraPago = false;
        }
    }
}
