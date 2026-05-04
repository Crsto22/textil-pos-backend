package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
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
@Table(name = "producto_variante_oferta_sucursal")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductoVarianteOfertaSucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto_variante_oferta_sucursal")
    private Integer idProductoVarianteOfertaSucursal;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_producto_variante", nullable = false)
    private ProductoVariante productoVariante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @Column(name = "precio_oferta")
    private Double precioOferta;

    @Column(name = "oferta_inicio")
    private LocalDateTime ofertaInicio;

    @Column(name = "oferta_fin")
    private LocalDateTime ofertaFin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_creacion")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "sucursal", "turno"})
    private Usuario usuarioCreacion;

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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
