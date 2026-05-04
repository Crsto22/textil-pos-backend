package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.sistemapos.sistematextil.model.converter.EstadoActivoConverter;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.OrderBy;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "metodo_pago_config")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MetodoPagoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo_pago")
    private Integer idMetodoPago;

    @NotBlank(message = "El nombre del metodo de pago es obligatorio")
    @Column(unique = true, length = 50)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String estado = "ACTIVO";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "metodoPago", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idMetodoPagoCuenta ASC")
    private List<MetodoPagoCuenta> cuentas = new ArrayList<>();

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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setCuentas(List<MetodoPagoCuenta> cuentas) {
        this.cuentas.clear();
        if (cuentas == null) {
            return;
        }
        cuentas.forEach(this::addCuenta);
    }

    public void addCuenta(MetodoPagoCuenta cuenta) {
        if (cuenta == null) {
            return;
        }
        cuenta.setMetodoPago(this);
        this.cuentas.add(cuenta);
    }
}
