package com.sistemapos.sistematextil.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sistemapos.sistematextil.util.sunat.SunatBajaEstado;
import com.sistemapos.sistematextil.util.sunat.SunatBajaTipo;

import jakarta.persistence.Column;
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
@Table(name = "sunat_baja_lote")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SunatBajaLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sunat_baja_lote")
    private Integer idSunatBajaLote;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_empresa", nullable = false)
    private Empresa empresa;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_envio", nullable = false, length = 10)
    private SunatBajaTipo tipoEnvio;

    @Column(name = "fecha_documento", nullable = false)
    private LocalDate fechaDocumento;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDate fechaGeneracion;

    @Column(nullable = false)
    private Integer correlativo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SunatBajaEstado estado;

    @Column(length = 20)
    private String codigo;

    @Column(length = 500)
    private String mensaje;

    @Column(name = "ticket_sunat", length = 120)
    private String ticketSunat;

    @Column(name = "sunat_hash", length = 120)
    private String sunatHash;

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
        if (this.fechaGeneracion == null) {
            this.fechaGeneracion = now.toLocalDate();
        }
        if (this.estado == null) {
            this.estado = SunatBajaEstado.PENDIENTE_ENVIO;
        }
        if (this.correlativo == null || this.correlativo < 1) {
            this.correlativo = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
