package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public record VentaResponse(
        Integer idVenta,
        LocalDateTime fecha,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        String moneda,
        String formaPago,
        BigDecimal igvPorcentaje,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        String tipoDescuento,
        BigDecimal igv,
        BigDecimal total,
        String estado,
        SunatEstado sunatEstado,
        String sunatCodigo,
        String sunatMensaje,
        String sunatHash,
        String sunatTicket,
        String sunatXmlNombre,
        String sunatZipNombre,
        String sunatCdrNombre,
        LocalDateTime sunatEnviadoAt,
        LocalDateTime sunatRespondidoAt,
        Integer idCliente,
        String nombreCliente,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal,
        List<VentaDetalleResponse> detalles,
        List<VentaPagoResponse> pagos
) {
}
