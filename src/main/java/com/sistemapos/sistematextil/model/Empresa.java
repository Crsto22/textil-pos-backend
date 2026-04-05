package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "empresa")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empresa")
    private Integer idEmpresa;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    private String nombre;

    @Size(max = 150, message = "El nombre comercial no debe superar 150 caracteres")
    @Column(name = "nombre_comercial")
    private String nombreComercial;

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "\\d{11}", message = "El RUC debe contener exactamente 11 digitos")
    @Column(unique = true, length = 11)
    private String ruc;

    @NotBlank(message = "La razon social es obligatoria")
    @Size(min = 5, max = 150, message = "La razon social debe tener entre 5 y 150 caracteres")
    @Column(name = "razon_social")
    private String razonSocial;

    @Email(message = "El correo no tiene un formato valido")
    @Column(length = 150)
    private String correo;

    @Column(length = 20)
    private String telefono;

    @Column(length = 255)
    private String direccion;

    @Column(length = 6)
    private String ubigeo;

    @Column(length = 100)
    private String departamento;

    @Column(length = 100)
    private String provincia;

    @Column(length = 100)
    private String distrito;

    @Column(name = "codigo_establecimiento_sunat", length = 4)
    private String codigoEstablecimientoSunat;

    @Column(name = "logo_url", length = 600)
    private String logoUrl;

    @Column(name = "genera_facturacion_electronica", nullable = false)
    private Boolean generaFacturacionElectronica;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.fechaCreacion == null) {
            this.fechaCreacion = now;
        }
        this.updatedAt = now;
        if (this.generaFacturacionElectronica == null) {
            this.generaFacturacionElectronica = Boolean.FALSE;
        }
        if (this.activo == null) {
            this.activo = Boolean.TRUE;
        }
        if ((this.nombreComercial == null || this.nombreComercial.isBlank()) && this.nombre != null) {
            this.nombreComercial = this.nombre.trim();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
