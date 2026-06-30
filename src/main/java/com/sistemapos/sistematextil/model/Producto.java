package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "producto")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "producto_id")
    private Integer idProducto;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 180, unique = true)
    private String slug;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "imagen_global_url", length = 600)
    private String imagenGlobalUrl;

    @Column(name = "imagen_global_thumb_url", length = 600)
    private String imagenGlobalThumbUrl;

    @Column(name = "guia_tallas_url", length = 600)
    private String guiaTallasUrl;

    @Column(name = "guia_tallas_thumb_url", length = 600)
    private String guiaTallasThumbUrl;

    @Column(name = "publicar_ecommerce", nullable = false)
    private Boolean publicarEcommerce = Boolean.FALSE;

    @Column(nullable = false)
    private String estado;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String activo = "ACTIVO";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", nullable = false)
    @JsonIgnoreProperties({"productos"})
    private Categoria categoria;

    @Transient
    private Sucursal sucursal;

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
        if (this.activo == null || this.activo.isBlank()) {
            this.activo = "ACTIVO";
        }
        if (this.publicarEcommerce == null) {
            this.publicarEcommerce = Boolean.FALSE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
