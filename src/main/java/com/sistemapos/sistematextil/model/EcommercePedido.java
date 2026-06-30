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
@Table(name = "ecommerce_pedido")
@Getter
@Setter
public class EcommercePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ecommerce_pedido")
    private Integer idEcommercePedido;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_metodo_pago", nullable = false)
    private MetodoPagoConfig metodoPago;

    @Column(nullable = false, length = 40)
    private String estado;

    @Column(name = "cliente_dni", nullable = false, length = 20)
    private String clienteDni;

    @Column(name = "cliente_nombres", nullable = false, length = 100)
    private String clienteNombres;

    @Column(name = "cliente_apellidos", nullable = false, length = 100)
    private String clienteApellidos;

    @Column(name = "cliente_correo", nullable = false, length = 150)
    private String clienteCorreo;

    @Column(name = "cliente_telefono", nullable = false, length = 20)
    private String clienteTelefono;

    @Column(name = "desea_factura", nullable = false)
    private Boolean deseaFactura;

    @Column(name = "factura_ruc", length = 11)
    private String facturaRuc;

    @Column(name = "envio_tipo", nullable = false, length = 20)
    private String envioTipo;

    @Column(name = "envio_direccion", length = 255)
    private String envioDireccion;

    @Column(name = "envio_referencia", length = 255)
    private String envioReferencia;

    @Column(name = "envio_departamento", length = 100)
    private String envioDepartamento;

    @Column(name = "envio_provincia", length = 100)
    private String envioProvincia;

    @Column(name = "envio_distrito", length = 100)
    private String envioDistrito;

    @Column(name = "envio_tarifa", length = 40)
    private String envioTarifa;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "reserva_expira_at", nullable = false)
    private LocalDateTime reservaExpiraAt;

    @Column(name = "comprobante_url", length = 600)
    private String comprobanteUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_venta")
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_aceptacion")
    private Usuario usuarioAceptacion;

    @Column(name = "aceptado_at")
    private LocalDateTime aceptadoAt;

    @Column(name = "comprobante_token_hash", nullable = false, unique = true, length = 64)
    private String comprobanteTokenHash;

    @Column(name = "comprobante_token_expira_at", nullable = false)
    private LocalDateTime comprobanteTokenExpiraAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EcommercePedidoDetalle> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (fecha == null) {
            fecha = now;
        }
        updatedAt = now;
        if (deseaFactura == null) {
            deseaFactura = false;
        }
        if (subtotal == null) {
            subtotal = BigDecimal.ZERO;
        }
        if (total == null) {
            total = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addDetalle(EcommercePedidoDetalle detalle) {
        detalle.setPedido(this);
        detalles.add(detalle);
    }
}
