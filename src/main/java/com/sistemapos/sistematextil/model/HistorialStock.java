package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer idHistorial;

    // Relación con el Producto (o ProductoVariante según tu lógica de Kardex)
    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    @NotNull(message = "El producto es obligatorio")
    private Producto producto;

    @Column(nullable = false)
    private Integer stock; // Cantidad del movimiento (+ o -)

    @Column(nullable = false, length = 100)
    private String motiva; // "VENTA", "REPOSICIÓN", "AJUSTE"

    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDateTime fecha;

    // CORREGIDO: Ahora usa tu clase 'Usuario' en singular
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    @NotNull(message = "El usuario que registra es obligatorio")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "id_sucursal", nullable = false)
    @NotNull(message = "La sucursal es obligatoria")
    private Sucursal sucursal;

    @PrePersist
    protected void onCreate() {
        this.fecha = LocalDateTime.now();
    }
}