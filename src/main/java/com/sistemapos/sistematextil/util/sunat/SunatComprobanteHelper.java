package com.sistemapos.sistematextil.util.sunat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import com.sistemapos.sistematextil.model.Cliente;
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
        return "R-" + construirBaseNombreArchivo(venta) + ".xml";
    }

    public static String carpetaTipoComprobante(Venta venta) {
        if (venta == null || venta.getTipoComprobante() == null) {
            return "comprobante";
        }
        return switch (venta.getTipoComprobante().trim().toUpperCase(Locale.ROOT)) {
            case "FACTURA" -> "factura";
            case "BOLETA" -> "boleta";
            default -> "comprobante";
        };
    }

    private static String construirBaseNombreArchivo(Venta venta) {
        String ruc = venta.getSucursal() != null && venta.getSucursal().getEmpresa() != null
                ? valorTexto(venta.getSucursal().getEmpresa().getRuc())
                : "SINRUC";
        return ruc + "-"
                + codigoTipoComprobante(venta.getTipoComprobante()) + "-"
                + numeroComprobante(venta);
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

    private static String valorTexto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static Integer ordenDetalle(VentaDetalle detalle) {
        return detalle.getIdVentaDetalle() == null ? Integer.MAX_VALUE : detalle.getIdVentaDetalle();
    }
}
