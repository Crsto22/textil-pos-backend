package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "traslado")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Traslado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_traslado")
    private Integer idTraslado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal_origen", nullable = false)
    @NotNull(message = "La sucursal origen es obligatoria")
    private Sucursal sucursalOrigen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal_destino", nullable = false)
    @NotNull(message = "La sucursal destino es obligatoria")
    private Sucursal sucursalDestino;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto_variante", nullable = false)
    @NotNull(message = "La variante es obligatoria")
    private ProductoVariante productoVariante;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer cantidad;

    @Size(max = 255, message = "El motivo no debe superar 255 caracteres")
    private String motivo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    @NotNull(message = "El usuario es obligatorio")
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.fecha = this.fecha == null ? now : this.fecha;
        this.createdAt = this.createdAt == null ? now : this.createdAt;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
