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
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "producto_variante")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductoVariante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto_variante")
    private Integer idProductoVariante;

    @NotNull(message = "El stock es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    @NotNull(message = "El precio es obligatorio")
    @Min(value = 0, message = "El precio no puede ser negativo")
    private Double precio;

    @DecimalMin(value = "0.0", inclusive = false, message = "El precio por mayor debe ser mayor a 0")
    @Column(name = "precio_mayor")
    private Double precioMayor;

    @Column(name = "precio_oferta")
    private Double precioOferta;

    @Column(name = "oferta_inicio")
    private LocalDateTime ofertaInicio;

    @Column(name = "oferta_fin")
    private LocalDateTime ofertaFin;

    @Column(nullable = false)
    private String estado = "ACTIVO";

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String activo = "ACTIVO";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @NotBlank(message = "El SKU es obligatorio")
    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Size(max = 100, message = "El codigo de barras no debe superar 100 caracteres")
    @Column(name = "codigo_barras", length = 100)
    private String codigoBarras;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    @JsonIgnoreProperties({"categoria", "sucursal"}) 
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "talla_id", nullable = false)
    private Talla talla;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = false)
    @JsonIgnoreProperties({"empresa", "hibernateLazyInitializer", "handler"})
    private Sucursal sucursal;
}
