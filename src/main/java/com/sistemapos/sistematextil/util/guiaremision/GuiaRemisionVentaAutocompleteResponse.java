package com.sistemapos.sistematextil.util.guiaremision;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public record GuiaRemisionVentaAutocompleteResponse(
        Integer idVenta,
        String tipoComprobante,
        String tipoDocumentoSunat,
        String serie,
        Integer correlativo,
        String numeroDocumento,
        LocalDateTime fecha,
        String estado,
        SunatEstado sunatEstado,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        ClienteResumen cliente,
        SucursalResumen sucursalPartida,
        GuiaRemisionDocumentoRelacionadoRequest documentoRelacionado,
        List<ItemVenta> itemsVenta,
        List<GuiaRemisionDetalleCreateItem> detalles,
        GuiaSugerida guiaSugerida) {

    public record ClienteResumen(
            Integer idCliente,
            String tipoDocumento,
            String tipoDocumentoSunat,
            String nroDocumento,
            String nombres,
            String direccion) {
    }

    public record SucursalResumen(
            Integer idSucursal,
            String nombre,
            String ubigeo,
            String direccion,
            String codigoEstablecimientoSunat) {
    }

    public record ItemVenta(
            Integer idVentaDetalle,
            Integer idProductoVariante,
            String sku,
            String codigoBarras,
            String producto,
            String color,
            String talla,
            String descripcion,
            Integer cantidad,
            String unidadMedida,
            BigDecimal precioUnitario,
            BigDecimal descuento,
            BigDecimal igvDetalle,
            BigDecimal subtotal,
            BigDecimal totalDetalle) {
    }

    public record GuiaSugerida(
            String motivoTraslado,
            Integer idSucursalPartida,
            String ubigeoPartida,
            String direccionPartida,
            String ubigeoLlegada,
            String direccionLlegada,
            String destinatarioTipoDoc,
            String destinatarioNroDoc,
            String destinatarioRazonSocial,
            List<GuiaRemisionDocumentoRelacionadoRequest> documentosRelacionados,
            List<GuiaRemisionDetalleCreateItem> detalles) {
    }
}
