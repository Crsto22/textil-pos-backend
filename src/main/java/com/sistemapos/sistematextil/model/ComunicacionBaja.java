package com.sistemapos.sistematextil.model;

import java.time.LocalDate;
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
@Table(name = "comunicacion_baja")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ComunicacionBaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_baja")
    private Integer idBaja;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_solicita", nullable = false)
    private Usuario usuarioSolicita;

    @Column(name = "motivo_baja", nullable = false, length = 255)
    private String motivoBaja;

    @Column(name = "identificador_baja", nullable = false, length = 50)
    private String identificadorBaja;

    @Column(name = "fecha_emision_original", nullable = false)
    private LocalDate fechaEmisionOriginal;

    @Column(name = "fecha_generacion_baja", nullable = false)
    private LocalDateTime fechaGeneracionBaja;

    @Column(name = "sunat_ticket", length = 120)
    private String sunatTicket;

    @Column(name = "sunat_estado", nullable = false, length = 20)
    private String sunatEstado;

    @Column(name = "sunat_codigo", length = 20)
    private String sunatCodigo;

    @Column(name = "sunat_mensaje", length = 500)
    private String sunatMensaje;

    @Column(name = "sunat_xml_nombre", length = 180)
    private String sunatXmlNombre;

    @Column(name = "sunat_xml_key", length = 600)
    private String sunatXmlKey;

    @Column(name = "sunat_cdr_nombre", length = 180)
    private String sunatCdrNombre;

    @Column(name = "sunat_cdr_key", length = 600)
    private String sunatCdrKey;

    @Column(name = "stock_devuelto", nullable = false)
    private Boolean stockDevuelto;

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
        if (this.sunatEstado == null || this.sunatEstado.isBlank()) {
            this.sunatEstado = "PENDIENTE";
        }
        if (this.stockDevuelto == null) {
            this.stockDevuelto = false;
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
