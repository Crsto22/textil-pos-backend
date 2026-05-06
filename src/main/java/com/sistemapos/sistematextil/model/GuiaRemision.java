package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/**
 * Guia de Remision electronica - Motivo 04 exclusivo:
 * Traslado entre establecimientos de la misma empresa.
 */
@Entity
@Table(name = "guia_remision")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GuiaRemision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_guia_remision")
    private Integer idGuiaRemision;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false)
    private Integer correlativo;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "fecha_inicio_traslado", nullable = false)
    private LocalDate fechaInicioTraslado;

    @Column(name = "motivo_traslado", nullable = false, length = 2)
    private String motivoTraslado;

    @Column(name = "descripcion_motivo", length = 255)
    private String descripcionMotivo;

    @Column(name = "modalidad_transporte", nullable = false, length = 2)
    private String modalidadTransporte;

    @Column(name = "peso_bruto_total", nullable = false, precision = 12, scale = 3)
    private BigDecimal pesoBrutoTotal;

    @Column(name = "unidad_peso", nullable = false, length = 3)
    private String unidadPeso;

    @Column(name = "numero_bultos")
    private Integer numeroBultos;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "ubigeo_partida", nullable = false, length = 6)
    private String ubigeoPartida;

    @Column(name = "direccion_partida", nullable = false, length = 255)
    private String direccionPartida;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal_partida")
    private Sucursal sucursalPartida;

    @Column(name = "ubigeo_llegada", nullable = false, length = 6)
    private String ubigeoLlegada;

    @Column(name = "direccion_llegada", nullable = false, length = 255)
    private String direccionLlegada;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal_llegada")
    private Sucursal sucursalLlegada;

    @Column(name = "destinatario_tipo_doc", nullable = false, length = 1)
    private String destinatarioTipoDoc;

    @Column(name = "destinatario_nro_doc", nullable = false, length = 20)
    private String destinatarioNroDoc;

    @Column(name = "destinatario_razon_social", nullable = false, length = 255)
    private String destinatarioRazonSocial;

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

    @Column(name = "sunat_cdr_nombre", length = 180)
    private String sunatCdrNombre;

    @Column(name = "sunat_cdr_key", length = 600)
    private String sunatCdrKey;

    @Column(name = "sunat_pdf_nombre", length = 180)
    private String sunatPdfNombre;

    @Column(name = "sunat_pdf_key", length = 600)
    private String sunatPdfKey;

    @Column(name = "sunat_enviado_at")
    private LocalDateTime sunatEnviadoAt;

    @Column(name = "sunat_respondido_at")
    private LocalDateTime sunatRespondidoAt;

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
        if (this.fechaEmision == null) this.fechaEmision = now;
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
        if (this.estado == null || this.estado.isBlank()) this.estado = "BORRADOR";
        if (this.sunatEstado == null) this.sunatEstado = SunatEstado.PENDIENTE_ENVIO;
        if (this.unidadPeso == null || this.unidadPeso.isBlank()) this.unidadPeso = "KGM";
        if (this.motivoTraslado == null || this.motivoTraslado.isBlank()) this.motivoTraslado = "04";
        if (this.activo == null || this.activo.isBlank()) this.activo = "ACTIVO";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.unidadPeso == null || this.unidadPeso.isBlank()) this.unidadPeso = "KGM";
    }
}
