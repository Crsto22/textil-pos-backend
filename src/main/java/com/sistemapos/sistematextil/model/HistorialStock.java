package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "historial_stock")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialStock {

    public enum TipoMovimiento {
        ENTRADA,
        SALIDA,
        AJUSTE,
        VENTA,
        DEVOLUCION,
        RESERVA,
        LIBERACION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer idHistorial;

    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false)
    @NotNull(message = "El tipo de movimiento es obligatorio")
    private TipoMovimiento tipoMovimiento;

    @Column(name = "motivo", length = 150)
    private String motivo;

    @ManyToOne
    @JoinColumn(name = "id_producto_variante", nullable = false)
    @NotNull(message = "La variante de producto es obligatoria")
    private ProductoVariante productoVariante;

    @ManyToOne
    @JoinColumn(name = "id_sucursal", nullable = false)
    @NotNull(message = "La sucursal es obligatoria")
    private Sucursal sucursal;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    @NotNull(message = "El usuario que registra es obligatorio")
    private Usuario usuario;

    @Column(name = "cantidad", nullable = false)
    @NotNull(message = "La cantidad es obligatoria")
    private Integer cantidad;

    @Column(name = "stock_anterior", nullable = false)
    @NotNull(message = "El stock anterior es obligatorio")
    private Integer stockAnterior;

    @Column(name = "stock_nuevo", nullable = false)
    @NotNull(message = "El stock nuevo es obligatorio")
    private Integer stockNuevo;

    @PrePersist
    protected void onCreate() {
        this.fecha = LocalDateTime.now();
    }
}
