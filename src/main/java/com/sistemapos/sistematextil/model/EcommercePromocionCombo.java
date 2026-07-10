package com.sistemapos.sistematextil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ecommerce_promocion_combo")
@Getter
@Setter
public class EcommercePromocionCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ecommerce_promocion_combo")
    private Integer idEcommercePromocionCombo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "precio_combo", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioCombo;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_creacion")
    private Usuario usuarioCreacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "promocion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EcommercePromocionComboItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (estado == null || estado.isBlank()) {
            estado = "ACTIVO";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addItem(EcommercePromocionComboItem item) {
        item.setPromocion(this);
        items.add(item);
    }
}
