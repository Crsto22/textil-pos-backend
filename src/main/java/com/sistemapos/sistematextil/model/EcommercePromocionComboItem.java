package com.sistemapos.sistematextil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ecommerce_promocion_combo_item")
@Getter
@Setter
public class EcommercePromocionComboItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ecommerce_promocion_combo_item")
    private Integer idEcommercePromocionComboItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ecommerce_promocion_combo", nullable = false)
    private EcommercePromocionCombo promocion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad_requerida", nullable = false)
    private Integer cantidadRequerida;
}
