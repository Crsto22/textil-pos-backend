package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sistemapos.sistematextil.model.converter.EstadoActivoConverter;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @JoinColumn(name = "id_canal_venta")
    private CanalVenta canalVenta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private String tipoComprobante;

    @Column(length = 10)
    private String serie;

    private Integer correlativo;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(name = "forma_pago", nullable = false, length = 10)
    private String formaPago;

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

    @Column(name = "tipo_anulacion", length = 20)
    private String tipoAnulacion;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @Column(name = "anulado_at")
    private LocalDateTime anuladoAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_anulacion")
    private Usuario usuarioAnulacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "sunat_estado", nullable = false, length = 20)
    private SunatEstado sunatEstado;

    @Column(name = "sunat_codigo", length = 20)
    private String sunatCodigo;

    @Column(name = "sunat_mensaje", length = 500)
    private String sunatMensaje;

    @Column(name = "sunat_hash", length = 120)
    private String sunatHash;

    @Column(name = "sunat_ticket", length = 120)
    private String sunatTicket;

    @Column(name = "sunat_xml_nombre", length = 180)
    private String sunatXmlNombre;

    @Column(name = "sunat_xml_key", length = 600)
    private String sunatXmlKey;

    @Column(name = "sunat_zip_nombre", length = 180)
    private String sunatZipNombre;

    @Column(name = "sunat_cdr_nombre", length = 180)
    private String sunatCdrNombre;

    @Column(name = "sunat_cdr_key", length = 600)
    private String sunatCdrKey;

    @Column(name = "sunat_enviado_at")
    private LocalDateTime sunatEnviadoAt;

    @Column(name = "sunat_respondido_at")
    private LocalDateTime sunatRespondidoAt;

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
        if (this.moneda == null || this.moneda.isBlank()) {
            this.moneda = "PEN";
        }
        if (this.formaPago == null || this.formaPago.isBlank()) {
            this.formaPago = "CONTADO";
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
        if (this.sunatEstado == null) {
            this.sunatEstado = requiereComprobanteElectronico() ? SunatEstado.PENDIENTE : SunatEstado.NO_APLICA;
        }
        if (this.activo == null || this.activo.isBlank()) {
            this.activo = "ACTIVO";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private boolean requiereComprobanteElectronico() {
        if (this.tipoComprobante == null) {
            return false;
        }
        return "BOLETA".equalsIgnoreCase(this.tipoComprobante)
                || "FACTURA".equalsIgnoreCase(this.tipoComprobante);
    }
}
