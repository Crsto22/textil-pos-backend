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
@Table(name = "nota_credito")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota_credito")
    private Integer idNotaCredito;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta_referencia", nullable = false)
    private Venta ventaReferencia;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private String tipoComprobante;

    @Column(nullable = false, length = 10)
    private String serie;

    @Column(nullable = false)
    private Integer correlativo;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(name = "codigo_motivo", nullable = false, length = 5)
    private String codigoMotivo;

    @Column(name = "descripcion_motivo", nullable = false, length = 255)
    private String descripcionMotivo;

    @Column(name = "tipo_documento_ref", nullable = false, length = 2)
    private String tipoDocumentoRef;

    @Column(name = "serie_ref", nullable = false, length = 10)
    private String serieRef;

    @Column(name = "correlativo_ref", nullable = false)
    private Integer correlativoRef;

    @Column(name = "igv_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal igvPorcentaje;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuentoTotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 20)
    private String estado;

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

    @Column(name = "sunat_zip_key", length = 600)
    private String sunatZipKey;

    @Column(name = "sunat_cdr_nombre", length = 180)
    private String sunatCdrNombre;

    @Column(name = "sunat_cdr_key", length = 600)
    private String sunatCdrKey;

    @Column(name = "sunat_enviado_at")
    private LocalDateTime sunatEnviadoAt;

    @Column(name = "sunat_respondido_at")
    private LocalDateTime sunatRespondidoAt;

    @Column(name = "stock_devuelto", nullable = false)
    private Boolean stockDevuelto;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String activo;

    @Column(nullable = false)
    private LocalDateTime fecha;

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
        if (this.fecha == null) {
            this.fecha = now;
        }
        if (this.estado == null || this.estado.isBlank()) {
            this.estado = "EMITIDA";
        }
        if (this.sunatEstado == null) {
            this.sunatEstado = SunatEstado.PENDIENTE;
        }
        if (this.stockDevuelto == null) {
            this.stockDevuelto = false;
        }
        if (this.moneda == null || this.moneda.isBlank()) {
            this.moneda = "PEN";
        }
        if (this.descuentoTotal == null) {
            this.descuentoTotal = BigDecimal.ZERO;
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
