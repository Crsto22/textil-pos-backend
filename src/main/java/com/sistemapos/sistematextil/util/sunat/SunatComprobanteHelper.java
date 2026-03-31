package com.sistemapos.sistematextil.util.sunat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

public final class SunatComprobanteHelper {

    private SunatComprobanteHelper() {
    }

    public static String construirContenidoQr(Venta venta) {
        Cliente cliente = venta.getCliente();
        String rucEmpresa = venta.getSucursal() != null && venta.getSucursal().getEmpresa() != null
                ? valorTexto(venta.getSucursal().getEmpresa().getRuc())
                : "";
        TipoDocumento tipoDocumento = cliente != null ? cliente.getTipoDocumento() : null;
        String nroCliente = cliente != null ? valorTexto(cliente.getNroDocumento()) : "";
        String fecha = venta.getFecha() == null ? "" : venta.getFecha().toLocalDate().toString();

        return String.join("|",
                rucEmpresa,
                codigoTipoComprobante(venta.getTipoComprobante()),
                valorTexto(venta.getSerie()),
                venta.getCorrelativo() == null ? "" : String.valueOf(venta.getCorrelativo()),
                valorTexto(venta.getIgv()),
                valorTexto(venta.getTotal()),
                fecha,
                codigoTipoDocumento(tipoDocumento),
                nroCliente);
    }

    public static String construirContenidoQr(NotaCredito notaCredito) {
        Cliente cliente = notaCredito.getCliente();
        String rucEmpresa = notaCredito.getSucursal() != null && notaCredito.getSucursal().getEmpresa() != null
                ? valorTexto(notaCredito.getSucursal().getEmpresa().getRuc())
                : "";
        TipoDocumento tipoDocumento = cliente != null ? cliente.getTipoDocumento() : null;
        String nroCliente = cliente != null ? valorTexto(cliente.getNroDocumento()) : "";
        String fecha = notaCredito.getFecha() == null ? "" : notaCredito.getFecha().toLocalDate().toString();

        return String.join("|",
                rucEmpresa,
                codigoTipoComprobante(notaCredito.getTipoComprobante()),
                valorTexto(notaCredito.getSerie()),
                notaCredito.getCorrelativo() == null ? "" : String.valueOf(notaCredito.getCorrelativo()),
                valorTexto(notaCredito.getIgv()),
                valorTexto(notaCredito.getTotal()),
                fecha,
                codigoTipoDocumento(tipoDocumento),
                nroCliente);
    }

    public static String construirCadenaResumen(Venta venta, List<VentaDetalle> detalles) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(construirContenidoQr(venta));

        detalles.stream()
                .sorted(Comparator.comparing(SunatComprobanteHelper::ordenDetalle))
                .forEach(detalle -> joiner.add(String.join(":",
                        detalle.getProductoVariante() != null ? valorTexto(detalle.getProductoVariante().getSku()) : "",
                        detalle.getCantidad() == null ? "" : String.valueOf(detalle.getCantidad()),
                        valorTexto(detalle.getPrecioUnitario()),
                        valorTexto(detalle.getSubtotal()))));

        return joiner.toString();
    }

    public static String construirCadenaResumen(NotaCredito notaCredito, List<com.sistemapos.sistematextil.model.NotaCreditoDetalle> detalles) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(construirContenidoQr(notaCredito));

        detalles.stream()
                .sorted(Comparator.comparing(SunatComprobanteHelper::ordenDetalle))
                .forEach(detalle -> joiner.add(String.join(":",
                        detalle.getProductoVariante() != null ? valorTexto(detalle.getProductoVariante().getSku()) : "",
                        detalle.getCantidad() == null ? "" : String.valueOf(detalle.getCantidad()),
                        valorTexto(detalle.getPrecioUnitario()),
                        valorTexto(detalle.getSubtotal()))));

        return joiner.toString();
    }

    public static String generarHashBase64(String contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contenido.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            return contenido;
        }
    }

    public static String codigoTipoComprobante(String tipoComprobante) {
        if (tipoComprobante == null) {
            return "";
        }
        return switch (tipoComprobante.trim().toUpperCase(Locale.ROOT)) {
            case "FACTURA" -> "01";
            case "BOLETA" -> "03";
            case "NOTA_CREDITO_BOLETA", "NOTA_CREDITO_FACTURA", "NOTA DE CREDITO", "NOTA DE CRÉDITO" -> "07";
            default -> "00";
        };
    }

    public static String codigoTipoDocumento(TipoDocumento tipoDocumento) {
        if (tipoDocumento == null) {
            return "0";
        }
        return switch (tipoDocumento) {
            case DNI -> "1";
            case RUC -> "6";
            case CE -> "4";
            case SIN_DOC -> "0";
        };
    }

    public static String construirNombreArchivoXml(Venta venta) {
        return construirBaseNombreArchivo(venta) + ".xml";
    }

    public static String construirNombreArchivoZip(Venta venta) {
        return construirBaseNombreArchivo(venta) + ".zip";
    }

    public static String construirNombreArchivoCdrZip(Venta venta) {
        return "R-" + construirBaseNombreArchivo(venta) + ".zip";
    }

    public static String construirNombreArchivoCdrXml(Venta venta) {
        return "R-" + construirBaseNombreArchivo(venta) + ".xml";
    }

    public static String construirNombreArchivoXml(NotaCredito notaCredito) {
        return construirBaseNombreArchivo(notaCredito) + ".xml";
    }

    public static String construirNombreArchivoZip(NotaCredito notaCredito) {
        return construirBaseNombreArchivo(notaCredito) + ".zip";
    }

    public static String construirNombreArchivoCdrZip(NotaCredito notaCredito) {
        return "R-" + construirBaseNombreArchivo(notaCredito) + ".zip";
    }

    public static String construirNombreArchivoCdrXml(NotaCredito notaCredito) {
        return "R-" + construirBaseNombreArchivo(notaCredito) + ".xml";
    }

    public static String carpetaTipoComprobante(Venta venta) {
        if (venta == null || venta.getTipoComprobante() == null) {
            return "comprobante";
        }
        return switch (venta.getTipoComprobante().trim().toUpperCase(Locale.ROOT)) {
            case "FACTURA" -> "facturas";
            case "BOLETA" -> "boletas";
            default -> "comprobante";
        };
    }

    public static String carpetaTipoComprobante(NotaCredito notaCredito) {
        if (notaCredito == null) {
            return "notas-credito";
        }
        return "notas-credito";
    }

    private static String construirBaseNombreArchivo(Venta venta) {
        String ruc = venta.getSucursal() != null && venta.getSucursal().getEmpresa() != null
                ? valorTexto(venta.getSucursal().getEmpresa().getRuc())
                : "SINRUC";
        return ruc + "-"
                + codigoTipoComprobante(venta.getTipoComprobante()) + "-"
                + numeroComprobante(venta);
    }

    private static String construirBaseNombreArchivo(NotaCredito notaCredito) {
        String ruc = notaCredito.getSucursal() != null && notaCredito.getSucursal().getEmpresa() != null
                ? valorTexto(notaCredito.getSucursal().getEmpresa().getRuc())
                : "SINRUC";
        return ruc + "-07-" + numeroComprobante(notaCredito);
    }

    public static String numeroComprobante(Venta venta) {
        String serie = venta.getSerie() == null ? "" : venta.getSerie().trim();
        String correlativo = venta.getCorrelativo() == null
                ? ""
                : String.format(Locale.ROOT, "%08d", venta.getCorrelativo());
        if (serie.isBlank()) {
            return correlativo;
        }
        if (correlativo.isBlank()) {
            return serie;
        }
        return serie + "-" + correlativo;
    }

    public static String numeroComprobante(NotaCredito notaCredito) {
        String serie = notaCredito.getSerie() == null ? "" : notaCredito.getSerie().trim();
        String correlativo = notaCredito.getCorrelativo() == null
                ? ""
                : String.format(Locale.ROOT, "%08d", notaCredito.getCorrelativo());
        if (serie.isBlank()) {
            return correlativo;
        }
        if (correlativo.isBlank()) {
            return serie;
        }
        return serie + "-" + correlativo;
    }

    private static String valorTexto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static Integer ordenDetalle(VentaDetalle detalle) {
        return detalle.getIdVentaDetalle() == null ? Integer.MAX_VALUE : detalle.getIdVentaDetalle();
    }

    private static Integer ordenDetalle(com.sistemapos.sistematextil.model.NotaCreditoDetalle detalle) {
        return detalle.getIdNotaCreditoDetalle() == null ? Integer.MAX_VALUE : detalle.getIdNotaCreditoDetalle();
    }
}
