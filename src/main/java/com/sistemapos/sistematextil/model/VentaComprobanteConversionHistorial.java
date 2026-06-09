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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "venta_comprobante_conversion_historial",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_venta_conversion_historial_venta",
                columnNames = "id_venta"))
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class VentaComprobanteConversionHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conversion")
    private Long idConversion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @Column(name = "tipo_comprobante_origen", nullable = false, length = 20)
    private String tipoComprobanteOrigen;

    @Column(name = "serie_origen", nullable = false, length = 10)
    private String serieOrigen;

    @Column(name = "correlativo_origen", nullable = false)
    private Integer correlativoOrigen;

    @Column(name = "tipo_comprobante_destino", nullable = false, length = 20)
    private String tipoComprobanteDestino;

    @Column(name = "serie_destino", nullable = false, length = 10)
    private String serieDestino;

    @Column(name = "correlativo_destino", nullable = false)
    private Integer correlativoDestino;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente_origen")
    private Cliente clienteOrigen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente_destino")
    private Cliente clienteDestino;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_conversion", nullable = false)
    private Usuario usuarioConversion;

    @Column(name = "convertido_at", nullable = false, updatable = false)
    private LocalDateTime convertidoAt;

    @PrePersist
    protected void onCreate() {
        if (this.convertidoAt == null) {
            this.convertidoAt = LocalDateTime.now();
        }
    }
}
