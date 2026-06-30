package com.sistemapos.sistematextil.util.ecommerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EcommercePedidoAdminResponse(
        Integer idEcommercePedido,
        String codigo,
        String estado,
        LocalDateTime fecha,
        LocalDateTime reservaExpiraAt,
        BigDecimal total,
        String metodoPago,
        String comprobanteUrl,
        Cliente cliente,
        Envio envio,
        Integer idSucursal,
        String nombreSucursal,
        Integer idVenta,
        String ventaNumero,
        Integer idUsuarioAceptacion,
        String usuarioAceptacionNombre,
        LocalDateTime aceptadoAt,
        List<EcommercePedidoResponse.Detalle> detalles) {

    public record Cliente(
            String dni,
            String nombres,
            String apellidos,
            String correo,
            String telefono,
            Boolean deseaFactura,
            String ruc) {
    }

    public record Envio(
            String tipo,
            String direccion,
            String referencia,
            String departamento,
            String provincia,
            String distrito,
            String tarifa) {
    }
}
