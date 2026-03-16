package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
    @Size(min = 3, max = 60, message = "El nombre debe tener entre 3 y 60 caracteres")
    private String nombre;

    @Size(max = 150, message = "El nombre comercial no debe superar 150 caracteres")
    @Column(name = "nombre_comercial")
    private String nombreComercial;

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "\\d{11}", message = "El RUC debe contener exactamente 11 digitos")
    @Column(unique = true, length = 11)
    private String ruc;

    @NotBlank(message = "La razon social es obligatoria")
    @Size(min = 5, max = 120, message = "La razon social debe tener entre 5 y 120 caracteres")
    @Column(name = "razon_social")
    private String razonSocial;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato valido")
    private String correo;

    @Pattern(regexp = "^$|\\d{7,15}", message = "El telefono debe tener entre 7 y 15 digitos")
    private String telefono;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "genera_facturacion_electronica", nullable = false)
    private Boolean generaFacturacionElectronica;

    @Column(name = "created_at")
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
        if (this.generaFacturacionElectronica == null) {
            this.generaFacturacionElectronica = Boolean.FALSE;
        }
        if ((this.nombreComercial == null || this.nombreComercial.isBlank()) && this.nombre != null) {
            this.nombreComercial = this.nombre.trim();
        }
    }
}
