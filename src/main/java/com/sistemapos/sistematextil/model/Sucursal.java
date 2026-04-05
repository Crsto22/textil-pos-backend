package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sistemapos.sistematextil.model.converter.EstadoActivoConverter;

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
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sucursal")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sucursal")
    private Integer idSucursal;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100)
    private String nombre;

    @Column(length = 100)
    private String ciudad;

    @Column(length = 255)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Email(message = "Correo no valido")
    @Column(length = 150)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private SucursalTipo tipo = SucursalTipo.VENTA;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String estado = "ACTIVO";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_empresa", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Empresa empresa;

    @Transient
    private String descripcion;

    @Transient
    private String ubigeo;

    @Transient
    private String departamento;

    @Transient
    private String provincia;

    @Transient
    private String distrito;

    @Transient
    private String codigoEstablecimientoSunat;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.fechaCreacion == null) {
            this.fechaCreacion = now;
        }
        this.updatedAt = now;
        if (this.estado == null || this.estado.isBlank()) {
            this.estado = "ACTIVO";
        }
        if (this.tipo == null) {
            this.tipo = SucursalTipo.VENTA;
        }
        this.deletedAt = null;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescripcion() {
        if (this.descripcion != null && !this.descripcion.isBlank()) {
            return this.descripcion;
        }
        return this.ciudad;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = normalizarCompat(descripcion);
    }

    public String getUbigeo() {
        if (this.ubigeo != null && !this.ubigeo.isBlank()) {
            return this.ubigeo;
        }
        return this.empresa != null ? this.empresa.getUbigeo() : null;
    }

    public void setUbigeo(String ubigeo) {
        this.ubigeo = normalizarCompat(ubigeo);
    }

    public String getDepartamento() {
        if (this.departamento != null && !this.departamento.isBlank()) {
            return this.departamento;
        }
        return this.empresa != null ? this.empresa.getDepartamento() : null;
    }

    public void setDepartamento(String departamento) {
        this.departamento = normalizarCompat(departamento);
    }

    public String getProvincia() {
        if (this.provincia != null && !this.provincia.isBlank()) {
            return this.provincia;
        }
        return this.empresa != null ? this.empresa.getProvincia() : null;
    }

    public void setProvincia(String provincia) {
        this.provincia = normalizarCompat(provincia);
    }

    public String getDistrito() {
        if (this.distrito != null && !this.distrito.isBlank()) {
            return this.distrito;
        }
        return this.empresa != null ? this.empresa.getDistrito() : null;
    }

    public void setDistrito(String distrito) {
        this.distrito = normalizarCompat(distrito);
    }

    public String getCodigoEstablecimientoSunat() {
        if (this.codigoEstablecimientoSunat != null && !this.codigoEstablecimientoSunat.isBlank()) {
            return this.codigoEstablecimientoSunat;
        }
        return this.empresa != null ? this.empresa.getCodigoEstablecimientoSunat() : null;
    }

    public void setCodigoEstablecimientoSunat(String codigoEstablecimientoSunat) {
        this.codigoEstablecimientoSunat = normalizarCompat(codigoEstablecimientoSunat);
    }

    private String normalizarCompat(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }
}
