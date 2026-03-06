package com.sistemapos.sistematextil.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.awt.Color;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.Pago;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaDetalleResponse;
import com.sistemapos.sistematextil.util.venta.VentaListItemResponse;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaPagoResponse;
import com.sistemapos.sistematextil.util.venta.VentaReporteResponse;
import com.sistemapos.sistematextil.util.venta.VentaResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VentaService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal CERO_MONETARIO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter FECHA_HORA_EXCEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final PagoRepository pagoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final MetodoPagoConfigRepository metodoPagoConfigRepository;
    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final ClienteRepository clienteRepository;
    private final HistorialStockRepository historialStockRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<VentaListItemResponse> listarPaginado(
            String term,
            Integer idUsuario,
            String tipoComprobante,
            String periodo,
            LocalDate fecha,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            int page,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);
        String termNormalizado = normalizarTerminoBusqueda(term);
        Integer idUsuarioFiltro = normalizarIdUsuarioFiltro(idUsuario);
        String tipoComprobanteFiltro = normalizarTipoComprobanteFiltro(tipoComprobante);
        RangoFechas rangoFechasFiltro = resolverRangoFechasListado(periodo, fecha, desde, hasta);
        LocalDateTime fechaInicioFiltro = rangoFechasFiltro == null ? null : rangoFechasFiltro.desde().atStartOfDay();
        LocalDateTime fechaFinExclusiveFiltro = rangoFechasFiltro == null
                ? null
                : rangoFechasFiltro.hasta().plusDays(1).atStartOfDay();

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idVenta").descending());
        Integer idSucursalFiltro = resolverIdSucursalListado(usuarioAutenticado, idSucursal);

        Page<Venta> ventas = ventaRepository.buscarConFiltros(
                termNormalizado,
                idSucursalFiltro,
                idUsuarioFiltro,
                tipoComprobanteFiltro,
                fechaInicioFiltro,
                fechaFinExclusiveFiltro,
                pageable);

        return PagedResponse.fromPage(ventas.map(this::toListItemResponse));
    }

    public VentaReporteResponse obtenerReporteVentas(
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            boolean incluirAnuladas,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        FiltroReporteVentas filtro = resolverFiltroReporteVentas(
                usuarioAutenticado,
                agrupar,
                periodo,
                desde,
                hasta,
                idSucursal);
        List<Venta> ventas = buscarVentasParaReporte(filtro, incluirAnuladas);

        return construirReporteVentas(ventas, filtro, incluirAnuladas);
    }

    public byte[] exportarReporteVentasExcel(
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            boolean incluirAnuladas,
            String correoUsuarioAutenticado) {
        VentaReporteResponse reporte = obtenerReporteVentas(
                agrupar,
                periodo,
                desde,
                hasta,
                idSucursal,
                incluirAnuladas,
                correoUsuarioAutenticado);
        return construirExcelReporteVentas(reporte);
    }

    public VentaResponse obtenerDetalle(Integer idVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        List<Pago> pagos = pagoRepository.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(venta.getIdVenta());

        return toResponse(venta, detalles, pagos);
    }

    public byte[] generarComprobantePdfA4(Integer idVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        List<Pago> pagos = pagoRepository.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(venta.getIdVenta());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 26f, 26f, 22f, 22f);
            PdfWriter.getInstance(document, output);
            document.open();

            agregarCabeceraComprobantePdf(document, venta);
            document.add(Chunk.NEWLINE);
            agregarDatosClienteComprobantePdf(document, venta);
            document.add(Chunk.NEWLINE);
            agregarDetalleComprobantePdf(document, detalles);
            document.add(Chunk.NEWLINE);
            agregarResumenComprobantePdf(document, venta, pagos);
            document.add(Chunk.NEWLINE);
            agregarPieComprobantePdf(document);

            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("No se pudo generar el comprobante PDF");
        }
    }

    private void agregarCabeceraComprobantePdf(Document document, Venta venta) throws DocumentException {
        Sucursal sucursal = venta.getSucursal();
        String nombreEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getNombre())
                : "";
        String razonSocial = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getRazonSocial())
                : "";
        String ruc = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getRuc())
                : "";

        com.lowagie.text.Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        com.lowagie.text.Font fontSubTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        com.lowagie.text.Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 8);

        PdfPTable header = new PdfPTable(new float[] { 2.5f, 3.5f, 2.8f });
        header.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setPaddingRight(8f);
        Image logo = cargarLogoEmpresaParaPdf(venta);
        if (logo != null) {
            logoCell.addElement(logo);
        } else {
            Paragraph logoTexto = new Paragraph(nombreEmpresa, fontSubTitulo);
            logoTexto.setAlignment(Element.ALIGN_LEFT);
            logoCell.addElement(logoTexto);
        }
        header.addCell(logoCell);

        PdfPCell empresaCell = new PdfPCell();
        empresaCell.setBorder(Rectangle.NO_BORDER);
        empresaCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        empresaCell.setPadding(4f);

        Paragraph razon = new Paragraph(
                razonSocial.isBlank() ? nombreEmpresa : razonSocial,
                fontTitulo);
        razon.setAlignment(Element.ALIGN_CENTER);
        empresaCell.addElement(razon);

        if (!nombreEmpresa.isBlank() && !razonSocial.equals(nombreEmpresa)) {
            Paragraph nombre = new Paragraph(nombreEmpresa, fontSubTitulo);
            nombre.setAlignment(Element.ALIGN_CENTER);
            empresaCell.addElement(nombre);
        }

        Paragraph direccion = new Paragraph(
                "Direccion: " + (sucursal != null ? valorTexto(sucursal.getDireccion()) : ""),
                fontNormal);
        direccion.setAlignment(Element.ALIGN_CENTER);
        empresaCell.addElement(direccion);

        Paragraph contacto = new Paragraph(
                "Tel: " + (sucursal != null ? valorTexto(sucursal.getTelefono()) : "")
                        + "  Email: " + (sucursal != null ? valorTexto(sucursal.getCorreo()) : ""),
                fontNormal);
        contacto.setAlignment(Element.ALIGN_CENTER);
        empresaCell.addElement(contacto);

        header.addCell(empresaCell);

        PdfPCell tipoCell = new PdfPCell();
        tipoCell.setBorder(Rectangle.BOX);
        tipoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tipoCell.setPadding(10f);

        Paragraph pRuc = new Paragraph("RUC: " + ruc, fontSubTitulo);
        pRuc.setAlignment(Element.ALIGN_CENTER);
        tipoCell.addElement(pRuc);

        Paragraph pTipo = new Paragraph(tituloComprobanteParaPdf(venta.getTipoComprobante()), fontSubTitulo);
        pTipo.setAlignment(Element.ALIGN_CENTER);
        tipoCell.addElement(pTipo);

        Paragraph pNumero = new Paragraph("Nro " + numeroComprobanteParaPdf(venta), fontSubTitulo);
        pNumero.setAlignment(Element.ALIGN_CENTER);
        tipoCell.addElement(pNumero);

        header.addCell(tipoCell);
        document.add(header);
    }

    private void agregarDatosClienteComprobantePdf(Document document, Venta venta) throws DocumentException {
        Cliente cliente = venta.getCliente();
        com.lowagie.text.Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        com.lowagie.text.Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        com.lowagie.text.Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 8);

        PdfPTable tabla = new PdfPTable(new float[] { 1.1f, 3.7f, 1.2f, 2.0f });
        tabla.setWidthPercentage(100);

        PdfPCell titulo = new PdfPCell(new Phrase("DATOS DEL CLIENTE", fontHeader));
        titulo.setColspan(4);
        titulo.setHorizontalAlignment(Element.ALIGN_LEFT);
        titulo.setBackgroundColor(new Color(240, 240, 240));
        titulo.setPadding(6f);
        tabla.addCell(titulo);

        agregarFilaLabelValor(tabla, "Cliente", cliente != null ? valorTexto(cliente.getNombres()) : "CLIENTE VARIOS", fontLabel, fontValue);
        agregarFilaLabelValor(tabla, "Fecha emision", venta.getFecha() == null ? "" : venta.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), fontLabel, fontValue);

        String tipoDoc = cliente != null && cliente.getTipoDocumento() != null
                ? cliente.getTipoDocumento().name()
                : "SIN_DOC";
        agregarFilaLabelValor(tabla, "Tipo doc", tipoDoc, fontLabel, fontValue);
        agregarFilaLabelValor(tabla, "Vendedor", nombreUsuario(venta.getUsuario()), fontLabel, fontValue);

        agregarFilaLabelValor(tabla, "Nro doc", cliente != null ? valorTexto(cliente.getNroDocumento()) : "", fontLabel, fontValue);
        agregarFilaLabelValor(tabla, "Comprobante", numeroComprobanteParaPdf(venta), fontLabel, fontValue);

        agregarFilaLabelValor(tabla, "Direccion", cliente != null ? valorTexto(cliente.getDireccion()) : "", fontLabel, fontValue);
        agregarFilaLabelValor(tabla, "Estado", valorTexto(venta.getEstado()), fontLabel, fontValue);

        document.add(tabla);
    }

    private void agregarDetalleComprobantePdf(Document document, List<VentaDetalle> detalles) throws DocumentException {
        com.lowagie.text.Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        com.lowagie.text.Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 8);

        PdfPTable tabla = new PdfPTable(new float[] { 0.8f, 0.9f, 4.6f, 1.2f, 1.2f, 1.3f });
        tabla.setWidthPercentage(100);

        agregarHeaderDetalle(tabla, "CANT.", fontHeader);
        agregarHeaderDetalle(tabla, "UND.", fontHeader);
        agregarHeaderDetalle(tabla, "DESCRIPCION", fontHeader);
        agregarHeaderDetalle(tabla, "P.UNIT", fontHeader);
        agregarHeaderDetalle(tabla, "DSCTO", fontHeader);
        agregarHeaderDetalle(tabla, "IMPORTE", fontHeader);

        int filas = 0;
        for (VentaDetalle detalle : detalles) {
            filas++;
            tabla.addCell(celdaContenidoPdf(String.valueOf(valorEntero(detalle.getCantidad())), fontBody, Element.ALIGN_CENTER));
            tabla.addCell(celdaContenidoPdf("UND", fontBody, Element.ALIGN_CENTER));
            tabla.addCell(celdaContenidoPdf(descripcionDetalleParaPdf(detalle), fontBody, Element.ALIGN_LEFT));
            tabla.addCell(celdaContenidoPdf(formatearMonedaPdf(detalle.getPrecioUnitario()), fontBody, Element.ALIGN_RIGHT));
            tabla.addCell(celdaContenidoPdf(formatearMonedaPdf(detalle.getDescuento()), fontBody, Element.ALIGN_RIGHT));
            tabla.addCell(celdaContenidoPdf(formatearMonedaPdf(detalle.getSubtotal()), fontBody, Element.ALIGN_RIGHT));
        }

        int filasMinimas = 10;
        while (filas < filasMinimas) {
            filas++;
            for (int i = 0; i < 6; i++) {
                PdfPCell vacia = celdaContenidoPdf(" ", fontBody, Element.ALIGN_LEFT);
                vacia.setFixedHeight(20f);
                tabla.addCell(vacia);
            }
        }

        document.add(tabla);
    }

    private void agregarResumenComprobantePdf(Document document, Venta venta, List<Pago> pagos) throws DocumentException {
        com.lowagie.text.Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        com.lowagie.text.Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 8);
        com.lowagie.text.Font fontTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

        PdfPTable contenedor = new PdfPTable(new float[] { 5.7f, 2.3f });
        contenedor.setWidthPercentage(100);

        PdfPCell izquierda = new PdfPCell();
        izquierda.setBorder(Rectangle.NO_BORDER);
        izquierda.setPaddingRight(8f);
        izquierda.addElement(new Paragraph("SON: " + montoEnLetras(venta.getTotal()), fontLabel));

        Paragraph pagosTitulo = new Paragraph("Metodos de pago:", fontLabel);
        pagosTitulo.setSpacingBefore(6f);
        izquierda.addElement(pagosTitulo);
        if (pagos.isEmpty()) {
            izquierda.addElement(new Paragraph("- Sin pagos registrados", fontValue));
        } else {
            for (Pago pago : pagos) {
                String metodo = pago.getMetodoPago() != null ? valorTexto(pago.getMetodoPago().getNombre()) : "N/D";
                String linea = "- " + metodo + ": S/ " + formatearMonedaPdf(pago.getMonto());
                if (pago.getReferencia() != null && !pago.getReferencia().isBlank()) {
                    linea = linea + " (" + pago.getReferencia().trim() + ")";
                }
                izquierda.addElement(new Paragraph(linea, fontValue));
            }
        }
        contenedor.addCell(izquierda);

        PdfPCell derecha = new PdfPCell();
        derecha.setBorder(Rectangle.NO_BORDER);
        PdfPTable totales = new PdfPTable(new float[] { 1.5f, 1f });
        totales.setWidthPercentage(100);

        agregarFilaTotalPdf(totales, "Subtotal (S/)", formatearMonedaPdf(venta.getSubtotal()), fontLabel, fontValue);
        agregarFilaTotalPdf(totales, "Descuento (S/)", formatearMonedaPdf(venta.getDescuentoTotal()), fontLabel, fontValue);
        String etiquetaIgv = "IGV " + formatearMonedaPdf(venta.getIgvPorcentaje()) + "% (S/)";
        agregarFilaTotalPdf(totales, etiquetaIgv, formatearMonedaPdf(venta.getIgv()), fontLabel, fontValue);
        agregarFilaTotalPdf(totales, "IMPORTE TOTAL (S/)", formatearMonedaPdf(venta.getTotal()), fontTotal, fontTotal);

        derecha.addElement(totales);
        contenedor.addCell(derecha);

        document.add(contenedor);
    }

    private void agregarPieComprobantePdf(Document document) throws DocumentException {
        com.lowagie.text.Font pie = FontFactory.getFont(FontFactory.HELVETICA, 7);
        Paragraph p = new Paragraph(
                "Representacion impresa de comprobante electronico. "
                        + "Fecha de impresion: "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                pie);
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
    }

    private PdfPCell celdaContenidoPdf(String texto, com.lowagie.text.Font font, int alineacion) {
        PdfPCell cell = new PdfPCell(new Phrase(valorTexto(texto), font));
        cell.setHorizontalAlignment(alineacion);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4f);
        return cell;
    }

    private void agregarHeaderDetalle(PdfPTable tabla, String texto, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(new Color(235, 235, 235));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f);
        tabla.addCell(cell);
    }

    private void agregarFilaLabelValor(
            PdfPTable tabla,
            String label,
            String value,
            com.lowagie.text.Font fontLabel,
            com.lowagie.text.Font fontValue) {
        PdfPCell cLabel = new PdfPCell(new Phrase(label, fontLabel));
        cLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        cLabel.setPadding(4f);
        tabla.addCell(cLabel);

        PdfPCell cValue = new PdfPCell(new Phrase(valorTexto(value), fontValue));
        cValue.setHorizontalAlignment(Element.ALIGN_LEFT);
        cValue.setPadding(4f);
        tabla.addCell(cValue);
    }

    private void agregarFilaTotalPdf(
            PdfPTable tabla,
            String label,
            String value,
            com.lowagie.text.Font fontLabel,
            com.lowagie.text.Font fontValue) {
        PdfPCell cLabel = new PdfPCell(new Phrase(label, fontLabel));
        cLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        cLabel.setPadding(4f);
        tabla.addCell(cLabel);

        PdfPCell cValue = new PdfPCell(new Phrase(value, fontValue));
        cValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cValue.setPadding(4f);
        tabla.addCell(cValue);
    }

    private String tituloComprobanteParaPdf(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return "COMPROBANTE";
        }
        return switch (tipoComprobante.trim().toUpperCase(Locale.ROOT)) {
            case "BOLETA" -> "BOLETA DE VENTA ELECTRONICA";
            case "FACTURA" -> "FACTURA ELECTRONICA";
            case "NOTA DE VENTA" -> "NOTA DE VENTA";
            default -> tipoComprobante.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String numeroComprobanteParaPdf(Venta venta) {
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

    private String descripcionDetalleParaPdf(VentaDetalle detalle) {
        ProductoVariante variante = detalle.getProductoVariante();
        if (variante == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        if (variante.getProducto() != null && variante.getProducto().getNombre() != null) {
            sb.append(variante.getProducto().getNombre().trim());
        }
        if (variante.getColor() != null && variante.getColor().getNombre() != null) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("Color: ").append(variante.getColor().getNombre().trim());
        }
        if (variante.getTalla() != null && variante.getTalla().getNombre() != null) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("Talla: ").append(variante.getTalla().getNombre().trim());
        }
        if (variante.getSku() != null && !variante.getSku().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("SKU: ").append(variante.getSku().trim());
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    private String formatearMonedaPdf(BigDecimal monto) {
        BigDecimal normalizado = moneda(monto);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat("0.00", symbols);
        return format.format(normalizado);
    }

    private Image cargarLogoEmpresaParaPdf(Venta venta) {
        String logoUrl = venta != null
                && venta.getSucursal() != null
                && venta.getSucursal().getEmpresa() != null
                        ? venta.getSucursal().getEmpresa().getLogoUrl()
                        : null;
        if (logoUrl == null || logoUrl.isBlank()) {
            return null;
        }

        try (InputStream stream = URI.create(logoUrl).toURL().openStream()) {
            byte[] bytes = stream.readAllBytes();
            Image image = Image.getInstance(bytes);
            image.scaleToFit(160f, 75f);
            image.setAlignment(Element.ALIGN_LEFT);
            return image;
        } catch (Exception ex) {
            return null;
        }
    }

    private String montoEnLetras(BigDecimal monto) {
        BigDecimal valor = monto == null ? BigDecimal.ZERO : monto.abs().setScale(2, RoundingMode.HALF_UP);
        long entero = valor.longValue();
        int decimales = valor.remainder(BigDecimal.ONE)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return convertirNumeroALetras(entero) + " CON " + String.format(Locale.ROOT, "%02d/100", decimales) + " SOLES";
    }

    private String convertirNumeroALetras(long numero) {
        if (numero == 0) {
            return "CERO";
        }
        if (numero < 0) {
            return "MENOS " + convertirNumeroALetras(Math.abs(numero));
        }
        if (numero < 30) {
            String[] unidades = {
                    "CERO", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
                    "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE",
                    "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES",
                    "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"
            };
            return unidades[(int) numero];
        }
        if (numero < 100) {
            String[] decenas = {
                    "", "", "VEINTE", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA",
                    "SETENTA", "OCHENTA", "NOVENTA"
            };
            long unidad = numero % 10;
            long decena = numero / 10;
            if (unidad == 0) {
                return decenas[(int) decena];
            }
            return decenas[(int) decena] + " Y " + convertirNumeroALetras(unidad);
        }
        if (numero == 100) {
            return "CIEN";
        }
        if (numero < 1000) {
            String[] centenas = {
                    "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
                    "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"
            };
            long resto = numero % 100;
            long centena = numero / 100;
            if (resto == 0) {
                return centenas[(int) centena];
            }
            return centenas[(int) centena] + " " + convertirNumeroALetras(resto);
        }
        if (numero < 1_000_000) {
            long miles = numero / 1000;
            long resto = numero % 1000;
            String prefijo = miles == 1 ? "MIL" : convertirNumeroALetras(miles) + " MIL";
            if (resto == 0) {
                return prefijo;
            }
            return prefijo + " " + convertirNumeroALetras(resto);
        }
        if (numero < 1_000_000_000) {
            long millones = numero / 1_000_000;
            long resto = numero % 1_000_000;
            String prefijo = millones == 1
                    ? "UN MILLON"
                    : convertirNumeroALetras(millones) + " MILLONES";
            if (resto == 0) {
                return prefijo;
            }
            return prefijo + " " + convertirNumeroALetras(resto);
        }
        return String.valueOf(numero);
    }

    @Transactional
    public VentaResponse registrarVenta(VentaCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolVenta(usuarioAutenticado);

        Sucursal sucursalVenta = resolverSucursalParaVenta(request.idSucursal(), usuarioAutenticado);
        Cliente cliente = resolverCliente(request.idCliente(), sucursalVenta.getIdSucursal());

        List<DetalleCalculado> detallesCalculados = calcularDetalles(
                request.detalles(),
                sucursalVenta.getIdSucursal());
        TotalesVenta totales = calcularTotales(
                detallesCalculados,
                request.descuentoTotal(),
                request.tipoDescuento(),
                request.igvPorcentaje());
        List<PagoCalculado> pagosCalculados = calcularPagos(request.pagos(), totales.total());
        String tipoComprobante = normalizarTipoComprobante(request.tipoComprobante());
        NumeroComprobante numeroComprobante = asignarNumeroComprobante(
                sucursalVenta.getIdSucursal(),
                tipoComprobante);

        Venta venta = new Venta();
        venta.setSucursal(sucursalVenta);
        venta.setUsuario(usuarioAutenticado);
        venta.setCliente(cliente);
        venta.setTipoComprobante(tipoComprobante);
        venta.setSerie(numeroComprobante.serie());
        venta.setCorrelativo(numeroComprobante.correlativo());
        venta.setIgvPorcentaje(totales.igvPorcentaje());
        venta.setSubtotal(totales.subtotal());
        venta.setDescuentoTotal(totales.descuentoAplicado());
        venta.setTipoDescuento(totales.tipoDescuento());
        venta.setIgv(totales.igv());
        venta.setTotal(totales.total());
        venta.setEstado("EMITIDA");
        venta.setActivo("ACTIVO");

        Venta ventaGuardada = ventaRepository.save(venta);

        List<VentaDetalle> detallesGuardar = new ArrayList<>();
        for (DetalleCalculado detalleCalculado : detallesCalculados) {
            VentaDetalle detalle = new VentaDetalle();
            detalle.setVenta(ventaGuardada);
            detalle.setProductoVariante(detalleCalculado.variante());
            detalle.setCantidad(detalleCalculado.cantidad());
            detalle.setPrecioUnitario(detalleCalculado.precioUnitario());
            detalle.setDescuento(detalleCalculado.descuento());
            detalle.setSubtotal(detalleCalculado.subtotal());
            detalle.setActivo("ACTIVO");
            detallesGuardar.add(detalle);
        }
        List<VentaDetalle> detallesGuardados = ventaDetalleRepository.saveAll(detallesGuardar);

        List<Pago> pagosGuardar = new ArrayList<>();
        for (PagoCalculado pagoCalculado : pagosCalculados) {
            Pago pago = new Pago();
            pago.setVenta(ventaGuardada);
            pago.setMetodoPago(pagoCalculado.metodoPago());
            pago.setMonto(pagoCalculado.monto());
            pago.setReferencia(pagoCalculado.referencia());
            pago.setActivo("ACTIVO");
            pagosGuardar.add(pago);
        }
        List<Pago> pagosGuardados = pagoRepository.saveAll(pagosGuardar);

        List<HistorialStock> historial = new ArrayList<>();
        for (DetalleCalculado detalleCalculado : detallesCalculados) {
            ProductoVariante variante = detalleCalculado.variante();
            int stockAnterior = valorEntero(variante.getStock());
            int stockNuevo = stockAnterior - detalleCalculado.cantidad();
            variante.setStock(stockNuevo);
            variante.setEstado(stockNuevo <= 0 ? "AGOTADO" : "ACTIVO");

            HistorialStock movimiento = new HistorialStock();
            movimiento.setTipoMovimiento(HistorialStock.TipoMovimiento.VENTA);
            movimiento.setMotivo("VENTA #" + ventaGuardada.getIdVenta());
            movimiento.setProductoVariante(variante);
            movimiento.setSucursal(sucursalVenta);
            movimiento.setUsuario(usuarioAutenticado);
            movimiento.setCantidad(detalleCalculado.cantidad());
            movimiento.setStockAnterior(stockAnterior);
            movimiento.setStockNuevo(stockNuevo);
            historial.add(movimiento);
        }
        productoVarianteRepository.saveAll(detallesCalculados.stream().map(DetalleCalculado::variante).toList());
        historialStockRepository.saveAll(historial);

        return toResponse(ventaGuardada, detallesGuardados, pagosGuardados);
    }

    private List<DetalleCalculado> calcularDetalles(List<VentaDetalleCreateItem> detalles, Integer idSucursalVenta) {
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un detalle de venta");
        }

        Set<Integer> variantesUnicas = new HashSet<>();
        List<DetalleCalculado> calculados = new ArrayList<>();

        for (VentaDetalleCreateItem item : detalles) {
            Integer idProductoVariante = item.idProductoVariante();
            if (idProductoVariante == null) {
                throw new RuntimeException("Cada detalle debe incluir idProductoVariante");
            }
            if (!variantesUnicas.add(idProductoVariante)) {
                throw new RuntimeException("No puede repetir la misma variante en el detalle de venta");
            }

            ProductoVariante variante = productoVarianteRepository.findByIdProductoVarianteForUpdate(idProductoVariante)
                    .orElseThrow(() -> new RuntimeException(
                            "La variante con ID " + idProductoVariante + " no existe"));

            if (variante.getSucursal() == null
                    || variante.getSucursal().getIdSucursal() == null
                    || !idSucursalVenta.equals(variante.getSucursal().getIdSucursal())) {
                throw new RuntimeException("La variante con ID " + idProductoVariante + " no pertenece a la sucursal de la venta");
            }

            if (!"ACTIVO".equalsIgnoreCase(variante.getEstado())) {
                throw new RuntimeException("La variante con SKU '" + variante.getSku() + "' no esta ACTIVA");
            }

            int cantidad = item.cantidad();
            int stockActual = valorEntero(variante.getStock());
            if (stockActual < cantidad) {
                throw new RuntimeException("Stock insuficiente para SKU '" + variante.getSku()
                        + "'. Disponible: " + stockActual + ", solicitado: " + cantidad);
            }

            BigDecimal precioUnitario = item.precioUnitario() == null
                    ? decimalDesdeDouble(precioVigenteVariante(variante))
                    : decimalPositivo(item.precioUnitario(), "precioUnitario");
            BigDecimal descuento = item.descuento() == null
                    ? BigDecimal.ZERO
                    : decimalNoNegativo(item.descuento(), "descuento");

            BigDecimal totalLinea = precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
            if (descuento.compareTo(totalLinea) > 0) {
                throw new RuntimeException("El descuento de la linea no puede superar el total de la linea para SKU '"
                        + variante.getSku() + "'");
            }
            BigDecimal subtotal = totalLinea.subtract(descuento).setScale(2, RoundingMode.HALF_UP);

            calculados.add(new DetalleCalculado(variante, cantidad, precioUnitario, descuento, subtotal));
        }

        return calculados;
    }

    private TotalesVenta calcularTotales(
            List<DetalleCalculado> detalles,
            Double descuentoTotalInput,
            String tipoDescuentoInput,
            Double igvPorcentajeInput) {
        BigDecimal subtotal = detalles.stream()
                .map(DetalleCalculado::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal descuentoInput = descuentoTotalInput == null
                ? BigDecimal.ZERO
                : decimalNoNegativo(descuentoTotalInput, "descuentoTotal");
        String tipoDescuento = normalizarTipoDescuento(tipoDescuentoInput, descuentoInput);

        BigDecimal descuentoAplicado = BigDecimal.ZERO;
        if (descuentoInput.compareTo(BigDecimal.ZERO) > 0) {
            if ("MONTO".equals(tipoDescuento)) {
                descuentoAplicado = descuentoInput;
            } else {
                descuentoAplicado = subtotal
                        .multiply(descuentoInput)
                        .divide(CIEN, 2, RoundingMode.HALF_UP);
            }
        }

        if (descuentoAplicado.compareTo(subtotal) > 0) {
            throw new RuntimeException("El descuento total no puede superar el subtotal");
        }

        BigDecimal subtotalConDescuento = subtotal.subtract(descuentoAplicado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igvPorcentaje = normalizarIgv(igvPorcentajeInput);
        BigDecimal igv = subtotalConDescuento
                .multiply(igvPorcentaje)
                .divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotalConDescuento.add(igv).setScale(2, RoundingMode.HALF_UP);

        return new TotalesVenta(
                igvPorcentaje,
                subtotalConDescuento,
                descuentoAplicado.setScale(2, RoundingMode.HALF_UP),
                tipoDescuento,
                igv,
                total);
    }

    private List<PagoCalculado> calcularPagos(List<VentaPagoCreateItem> pagos, BigDecimal totalVenta) {
        if (pagos == null || pagos.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un pago");
        }

        List<PagoCalculado> calculados = new ArrayList<>();
        BigDecimal sumaPagos = BigDecimal.ZERO;

        for (VentaPagoCreateItem item : pagos) {
            Integer idMetodoPago = item.idMetodoPago();
            if (idMetodoPago == null) {
                throw new RuntimeException("Cada pago debe incluir idMetodoPago");
            }

            MetodoPagoConfig metodoPago = metodoPagoConfigRepository.findById(idMetodoPago)
                    .orElseThrow(() -> new RuntimeException("Metodo de pago con ID " + idMetodoPago + " no encontrado"));
            if (!"ACTIVO".equalsIgnoreCase(metodoPago.getEstado())) {
                throw new RuntimeException("El metodo de pago '" + metodoPago.getNombre() + "' esta INACTIVO");
            }

            BigDecimal monto = decimalPositivo(item.monto(), "monto");
            String referencia = normalizarTexto(item.referencia(), 100);
            sumaPagos = sumaPagos.add(monto).setScale(2, RoundingMode.HALF_UP);

            calculados.add(new PagoCalculado(metodoPago, monto, referencia));
        }

        if (sumaPagos.compareTo(totalVenta) != 0) {
            throw new RuntimeException("La suma de pagos (" + sumaPagos
                    + ") debe ser igual al total de la venta (" + totalVenta + ")");
        }

        return calculados;
    }

    private Venta obtenerVentaConAlcance(Integer idVenta, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return ventaRepository.findByIdVentaAndDeletedAtIsNull(idVenta)
                    .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return ventaRepository.findByIdVentaAndDeletedAtIsNullAndSucursal_IdSucursal(idVenta, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
    }

    private Sucursal resolverSucursalParaVenta(Integer idSucursalRequest, Usuario usuarioAutenticado) {
        Integer idSucursalDestino = esAdministrador(usuarioAutenticado)
                ? idSucursalRequeridaParaAdmin(idSucursalRequest)
                : obtenerIdSucursalUsuario(usuarioAutenticado);
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalDestino)
                .filter(s -> "ACTIVO".equalsIgnoreCase(s.getEstado()))
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada o inactiva"));
    }

    private Cliente resolverCliente(Integer idCliente, Integer idSucursal) {
        if (idCliente == null) {
            return null;
        }
        return clienteRepository.findByIdClienteAndDeletedAtIsNullAndSucursal_IdSucursal(idCliente, idSucursal)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado para la sucursal"));
    }

    private NumeroComprobante asignarNumeroComprobante(Integer idSucursal, String tipoComprobante) {
        ComprobanteConfig config = comprobanteConfigRepository.findActivoForUpdate(idSucursal, tipoComprobante)
                .orElseThrow(() -> new RuntimeException(
                        "No existe configuracion activa de comprobante para la sucursal y tipo"));

        String serie = normalizarTexto(config.getSerie(), 10);
        if (serie == null) {
            throw new RuntimeException("La configuracion del comprobante no tiene serie valida");
        }

        int ultimoConfig = valorEntero(config.getUltimoCorrelativo());
        int maxVenta = valorEntero(ventaRepository
                .obtenerMaxCorrelativoPorDocumento(idSucursal, tipoComprobante, serie));
        int base = Math.max(ultimoConfig, maxVenta);
        int nuevoCorrelativo = base + 1;

        config.setUltimoCorrelativo(nuevoCorrelativo);
        comprobanteConfigRepository.save(config);

        return new NumeroComprobante(serie, nuevoCorrelativo);
    }

    private VentaListItemResponse toListItemResponse(Venta venta) {
        String nombreCliente = venta.getCliente() != null ? venta.getCliente().getNombres() : null;
        String nombreUsuario = nombreUsuario(venta.getUsuario());
        Integer idSucursal = venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null;
        String nombreSucursal = venta.getSucursal() != null ? venta.getSucursal().getNombre() : null;
        long items = ventaDetalleRepository.countByVenta_IdVentaAndDeletedAtIsNull(venta.getIdVenta());
        long pagos = pagoRepository.countByVenta_IdVentaAndDeletedAtIsNull(venta.getIdVenta());

        return new VentaListItemResponse(
                venta.getIdVenta(),
                venta.getFecha(),
                venta.getTipoComprobante(),
                venta.getSerie(),
                venta.getCorrelativo(),
                venta.getTotal(),
                venta.getEstado(),
                venta.getCliente() != null ? venta.getCliente().getIdCliente() : null,
                nombreCliente,
                venta.getUsuario() != null ? venta.getUsuario().getIdUsuario() : null,
                nombreUsuario,
                idSucursal,
                nombreSucursal,
                items,
                pagos);
    }

    private VentaResponse toResponse(Venta venta, List<VentaDetalle> detalles, List<Pago> pagos) {
        List<VentaDetalleResponse> detalleResponses = detalles.stream()
                .map(this::toDetalleResponse)
                .toList();
        List<VentaPagoResponse> pagoResponses = pagos.stream()
                .map(this::toPagoResponse)
                .toList();

        return new VentaResponse(
                venta.getIdVenta(),
                venta.getFecha(),
                venta.getTipoComprobante(),
                venta.getSerie(),
                venta.getCorrelativo(),
                venta.getIgvPorcentaje(),
                venta.getSubtotal(),
                venta.getDescuentoTotal(),
                venta.getTipoDescuento(),
                venta.getIgv(),
                venta.getTotal(),
                venta.getEstado(),
                venta.getCliente() != null ? venta.getCliente().getIdCliente() : null,
                venta.getCliente() != null ? venta.getCliente().getNombres() : null,
                venta.getUsuario() != null ? venta.getUsuario().getIdUsuario() : null,
                nombreUsuario(venta.getUsuario()),
                venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null,
                venta.getSucursal() != null ? venta.getSucursal().getNombre() : null,
                detalleResponses,
                pagoResponses);
    }

    private VentaDetalleResponse toDetalleResponse(VentaDetalle detalle) {
        ProductoVariante variante = detalle.getProductoVariante();
        return new VentaDetalleResponse(
                detalle.getIdVentaDetalle(),
                variante != null ? variante.getIdProductoVariante() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getIdProducto() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getNombre() : null,
                variante != null ? variante.getSku() : null,
                variante != null ? variante.getPrecioOferta() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getIdColor() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getIdTalla() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getDescuento(),
                detalle.getSubtotal());
    }

    private VentaPagoResponse toPagoResponse(Pago pago) {
        MetodoPagoConfig metodo = pago.getMetodoPago();
        return new VentaPagoResponse(
                pago.getIdPago(),
                metodo != null ? metodo.getIdMetodoPago() : null,
                metodo != null ? metodo.getNombre() : null,
                pago.getMonto(),
                pago.getReferencia(),
                pago.getFecha());
    }

    private FiltroReporteVentas resolverFiltroReporteVentas(
            Usuario usuarioAutenticado,
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursalRequest) {
        AgrupacionReporte agrupacion = normalizarAgrupacionReporte(agrupar);
        PeriodoFiltro periodoFiltro = normalizarPeriodoFiltro(periodo);
        RangoFechas rango = resolverRangoFechas(periodoFiltro, desde, hasta);

        Integer idSucursalFiltro;
        String nombreSucursalFiltro;
        if (esAdministrador(usuarioAutenticado)) {
            if (idSucursalRequest == null) {
                idSucursalFiltro = null;
                nombreSucursalFiltro = "TODAS";
            } else {
                Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalRequest)
                        .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
                idSucursalFiltro = sucursal.getIdSucursal();
                nombreSucursalFiltro = sucursal.getNombre();
            }
        } else {
            Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
            Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalUsuario)
                    .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
            idSucursalFiltro = sucursal.getIdSucursal();
            nombreSucursalFiltro = sucursal.getNombre();
        }

        return new FiltroReporteVentas(
                agrupacion,
                periodoFiltro,
                rango.desde(),
                rango.hasta(),
                idSucursalFiltro,
                nombreSucursalFiltro);
    }

    private AgrupacionReporte normalizarAgrupacionReporte(String agrupar) {
        if (agrupar == null || agrupar.isBlank()) {
            return AgrupacionReporte.DIA;
        }
        String valor = agrupar.trim().toUpperCase(Locale.ROOT);
        return switch (valor) {
            case "DIA" -> AgrupacionReporte.DIA;
            case "SEMANA" -> AgrupacionReporte.SEMANA;
            case "MES" -> AgrupacionReporte.MES;
            default -> throw new RuntimeException("agrupar permitido: DIA, SEMANA o MES");
        };
    }

    private PeriodoFiltro normalizarPeriodoFiltro(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            return PeriodoFiltro.MES;
        }
        String valor = periodo.trim().toUpperCase(Locale.ROOT);
        return switch (valor) {
            case "HOY" -> PeriodoFiltro.HOY;
            case "AYER" -> PeriodoFiltro.AYER;
            case "SEMANA" -> PeriodoFiltro.SEMANA;
            case "MES" -> PeriodoFiltro.MES;
            case "RANGO" -> PeriodoFiltro.RANGO;
            default -> throw new RuntimeException(
                    "periodo permitido: HOY, AYER, SEMANA, MES o RANGO");
        };
    }

    private RangoFechas resolverRangoFechas(PeriodoFiltro periodoFiltro, LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now();
        return switch (periodoFiltro) {
            case HOY -> new RangoFechas(hoy, hoy);
            case AYER -> {
                LocalDate ayer = hoy.minusDays(1);
                yield new RangoFechas(ayer, ayer);
            }
            case SEMANA -> {
                LocalDate inicio = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate fin = inicio.plusDays(6);
                yield new RangoFechas(inicio, fin);
            }
            case MES -> {
                YearMonth mesActual = YearMonth.now();
                yield new RangoFechas(mesActual.atDay(1), mesActual.atEndOfMonth());
            }
            case RANGO -> resolverRangoPersonalizado(desde, hasta);
        };
    }

    private RangoFechas resolverRangoPersonalizado(LocalDate desde, LocalDate hasta) {
        if (desde == null) {
            throw new RuntimeException("Para periodo RANGO, envie 'desde'");
        }
        LocalDate desdeNormalizado = desde;
        LocalDate hastaNormalizado = hasta == null ? desde : hasta;

        if (desdeNormalizado.isAfter(hastaNormalizado)) {
            throw new RuntimeException("La fecha 'desde' no puede ser mayor a 'hasta'");
        }

        return new RangoFechas(desdeNormalizado, hastaNormalizado);
    }

    private List<Venta> buscarVentasParaReporte(FiltroReporteVentas filtro, boolean incluirAnuladas) {
        LocalDateTime fechaInicio = filtro.desde().atStartOfDay();
        LocalDateTime fechaFinExclusive = filtro.hasta().plusDays(1).atStartOfDay();

        if (incluirAnuladas) {
            if (filtro.idSucursal() == null) {
                return ventaRepository.findByDeletedAtIsNullAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
                        fechaInicio,
                        fechaFinExclusive);
            }
            return ventaRepository
                    .findByDeletedAtIsNullAndSucursal_IdSucursalAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
                            filtro.idSucursal(),
                            fechaInicio,
                            fechaFinExclusive);
        }

        if (filtro.idSucursal() == null) {
            return ventaRepository.findByDeletedAtIsNullAndEstadoAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
                    "EMITIDA",
                    fechaInicio,
                    fechaFinExclusive);
        }
        return ventaRepository
                .findByDeletedAtIsNullAndSucursal_IdSucursalAndEstadoAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAsc(
                        filtro.idSucursal(),
                        "EMITIDA",
                        fechaInicio,
                        fechaFinExclusive);
    }

    private VentaReporteResponse construirReporteVentas(
            List<Venta> ventas,
            FiltroReporteVentas filtro,
            boolean incluirAnuladas) {
        List<Venta> ventasOrdenadas = ventas.stream()
                .sorted(Comparator.comparing(Venta::getFecha))
                .toList();

        BigDecimal montoTotal = ventasOrdenadas.stream()
                .map(Venta::getTotal)
                .map(this::moneda)
                .reduce(CERO_MONETARIO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long cantidadVentas = ventasOrdenadas.size();
        BigDecimal ticketPromedio = promedio(montoTotal, cantidadVentas);

        Map<LocalDate, AcumuladoPeriodo> acumuladoPorPeriodo = new TreeMap<>();
        Map<String, AcumuladoCliente> acumuladoPorCliente = new LinkedHashMap<>();
        List<VentaReporteResponse.DetalleItem> detalleVentas = new ArrayList<>();

        for (Venta venta : ventasOrdenadas) {
            LocalDate fecha = venta.getFecha().toLocalDate();
            LocalDate inicioPeriodo = inicioPeriodo(fecha, filtro.agrupacion());
            String etiquetaPeriodo = etiquetaPeriodo(fecha, filtro.agrupacion());
            BigDecimal totalVenta = moneda(venta.getTotal());

            AcumuladoPeriodo periodoActual = acumuladoPorPeriodo.get(inicioPeriodo);
            if (periodoActual == null) {
                acumuladoPorPeriodo.put(inicioPeriodo, new AcumuladoPeriodo(etiquetaPeriodo, 1L, totalVenta));
            } else {
                acumuladoPorPeriodo.put(
                        inicioPeriodo,
                        new AcumuladoPeriodo(
                                etiquetaPeriodo,
                                periodoActual.cantidadVentas() + 1,
                                periodoActual.montoTotal().add(totalVenta).setScale(2, RoundingMode.HALF_UP)));
            }

            Integer idCliente = venta.getCliente() != null ? venta.getCliente().getIdCliente() : null;
            String nombreCliente = venta.getCliente() != null ? venta.getCliente().getNombres() : "SIN CLIENTE";
            String claveCliente = idCliente == null ? "SIN_CLIENTE" : "ID_" + idCliente;
            AcumuladoCliente clienteActual = acumuladoPorCliente.get(claveCliente);
            if (clienteActual == null) {
                acumuladoPorCliente.put(claveCliente, new AcumuladoCliente(idCliente, nombreCliente, 1L, totalVenta));
            } else {
                acumuladoPorCliente.put(
                        claveCliente,
                        new AcumuladoCliente(
                                clienteActual.idCliente(),
                                clienteActual.nombreCliente(),
                                clienteActual.cantidadVentas() + 1,
                                clienteActual.montoTotal().add(totalVenta).setScale(2, RoundingMode.HALF_UP)));
            }

            detalleVentas.add(new VentaReporteResponse.DetalleItem(
                    venta.getIdVenta(),
                    venta.getFecha(),
                    venta.getTipoComprobante(),
                    venta.getSerie(),
                    venta.getCorrelativo(),
                    venta.getEstado(),
                    idCliente,
                    nombreCliente,
                    venta.getUsuario() != null ? venta.getUsuario().getIdUsuario() : null,
                    nombreUsuario(venta.getUsuario()),
                    venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null,
                    venta.getSucursal() != null ? venta.getSucursal().getNombre() : null,
                    moneda(venta.getSubtotal()),
                    moneda(venta.getDescuentoTotal()),
                    moneda(venta.getIgv()),
                    totalVenta));
        }

        List<VentaReporteResponse.PeriodoItem> periodos = acumuladoPorPeriodo.values().stream()
                .map(item -> new VentaReporteResponse.PeriodoItem(
                        item.etiqueta(),
                        item.cantidadVentas(),
                        item.montoTotal(),
                        promedio(item.montoTotal(), item.cantidadVentas())))
                .toList();

        List<VentaReporteResponse.ClienteItem> clientes = acumuladoPorCliente.values().stream()
                .sorted(Comparator.comparing(AcumuladoCliente::montoTotal).reversed())
                .map(item -> new VentaReporteResponse.ClienteItem(
                        item.idCliente(),
                        item.nombreCliente(),
                        item.cantidadVentas(),
                        item.montoTotal(),
                        promedio(item.montoTotal(), item.cantidadVentas())))
                .toList();

        List<VentaReporteResponse.DetalleItem> detalleDesc = detalleVentas.stream()
                .sorted(Comparator.comparing(VentaReporteResponse.DetalleItem::fecha).reversed())
                .toList();

        return new VentaReporteResponse(
                filtro.agrupacion().name(),
                filtro.periodoFiltro().name(),
                filtro.desde(),
                filtro.hasta(),
                filtro.idSucursal(),
                filtro.nombreSucursal(),
                incluirAnuladas,
                montoTotal,
                cantidadVentas,
                ticketPromedio,
                periodos,
                detalleDesc,
                clientes);
    }

    private LocalDate inicioPeriodo(LocalDate fecha, AgrupacionReporte agrupacion) {
        return switch (agrupacion) {
            case DIA -> fecha;
            case SEMANA -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> fecha.withDayOfMonth(1);
        };
    }

    private String etiquetaPeriodo(LocalDate fecha, AgrupacionReporte agrupacion) {
        return switch (agrupacion) {
            case DIA -> fecha.toString();
            case SEMANA -> {
                WeekFields weekFields = WeekFields.ISO;
                int week = fecha.get(weekFields.weekOfWeekBasedYear());
                int year = fecha.get(weekFields.weekBasedYear());
                yield String.format("%d-W%02d", year, week);
            }
            case MES -> YearMonth.from(fecha).toString();
        };
    }

    private byte[] construirExcelReporteVentas(VentaReporteResponse reporte) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = crearEstiloHeader(workbook);
            CellStyle moneyStyle = crearEstiloMoneda(workbook);

            List<Integer> ventaIds = reporte.detalleVentas().stream()
                    .map(VentaReporteResponse.DetalleItem::idVenta)
                    .filter(id -> id != null)
                    .toList();
            List<VentaDetalle> detallesVenta = ventaIds.isEmpty()
                    ? List.of()
                    : ventaDetalleRepository.findActivosByVentaIds(ventaIds);
            List<Pago> pagosVenta = ventaIds.isEmpty()
                    ? List.of()
                    : pagoRepository.findActivosByVentaIds(ventaIds);

            Map<Integer, List<VentaDetalle>> detallesPorVenta = agruparDetallesPorVenta(detallesVenta);
            Map<Integer, List<Pago>> pagosPorVenta = agruparPagosPorVenta(pagosVenta);

            construirHojaResumen(workbook, reporte, headerStyle, moneyStyle);
            construirHojaDetalle(workbook, reporte, detallesPorVenta, pagosPorVenta, headerStyle, moneyStyle);
            construirHojaDetalleItems(workbook, reporte, detallesPorVenta, headerStyle, moneyStyle);
            construirHojaPagos(workbook, reporte, pagosPorVenta, headerStyle, moneyStyle);
            construirHojaClientes(workbook, reporte, headerStyle, moneyStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el Excel del reporte de ventas");
        }
    }

    private void construirHojaResumen(
            Workbook workbook,
            VentaReporteResponse reporte,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Resumen");
        int rowIdx = 0;

        Row titulo = sheet.createRow(rowIdx++);
        titulo.createCell(0).setCellValue("Reporte de Ventas");

        Row generado = sheet.createRow(rowIdx++);
        generado.createCell(0).setCellValue("Generado");
        generado.createCell(1).setCellValue(LocalDateTime.now().format(FECHA_HORA_EXCEL));

        Row agrupacion = sheet.createRow(rowIdx++);
        agrupacion.createCell(0).setCellValue("Agrupacion");
        agrupacion.createCell(1).setCellValue(reporte.agrupacion());

        Row periodo = sheet.createRow(rowIdx++);
        periodo.createCell(0).setCellValue("Periodo filtro");
        periodo.createCell(1).setCellValue(reporte.periodoFiltro());

        Row rango = sheet.createRow(rowIdx++);
        rango.createCell(0).setCellValue("Rango");
        rango.createCell(1).setCellValue(reporte.desde() + " a " + reporte.hasta());

        Row sucursal = sheet.createRow(rowIdx++);
        sucursal.createCell(0).setCellValue("Sucursal");
        sucursal.createCell(1).setCellValue(reporte.nombreSucursal());

        Row estadoFiltro = sheet.createRow(rowIdx++);
        estadoFiltro.createCell(0).setCellValue("Incluye anuladas");
        estadoFiltro.createCell(1).setCellValue(reporte.incluirAnuladas() ? "SI" : "NO");

        rowIdx++;
        Row h = sheet.createRow(rowIdx++);
        h.createCell(0).setCellValue("Indicador");
        h.createCell(1).setCellValue("Valor");
        h.getCell(0).setCellStyle(headerStyle);
        h.getCell(1).setCellStyle(headerStyle);

        Row total = sheet.createRow(rowIdx++);
        total.createCell(0).setCellValue("Monto total");
        total.createCell(1).setCellValue(reporte.montoTotal().doubleValue());
        total.getCell(1).setCellStyle(moneyStyle);

        Row cantidad = sheet.createRow(rowIdx++);
        cantidad.createCell(0).setCellValue("Cantidad ventas");
        cantidad.createCell(1).setCellValue(reporte.cantidadVentas());

        Row ticket = sheet.createRow(rowIdx++);
        ticket.createCell(0).setCellValue("Ticket promedio");
        ticket.createCell(1).setCellValue(reporte.ticketPromedio().doubleValue());
        ticket.getCell(1).setCellStyle(moneyStyle);

        rowIdx++;
        Row hp = sheet.createRow(rowIdx++);
        hp.createCell(0).setCellValue("Periodo");
        hp.createCell(1).setCellValue("Cantidad Ventas");
        hp.createCell(2).setCellValue("Monto Total");
        hp.createCell(3).setCellValue("Ticket Promedio");
        hp.getCell(0).setCellStyle(headerStyle);
        hp.getCell(1).setCellStyle(headerStyle);
        hp.getCell(2).setCellStyle(headerStyle);
        hp.getCell(3).setCellStyle(headerStyle);

        for (VentaReporteResponse.PeriodoItem item : reporte.periodos()) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(item.periodo());
            r.createCell(1).setCellValue(item.cantidadVentas());
            r.createCell(2).setCellValue(item.montoTotal().doubleValue());
            r.createCell(3).setCellValue(item.ticketPromedio().doubleValue());
            r.getCell(2).setCellStyle(moneyStyle);
            r.getCell(3).setCellStyle(moneyStyle);
        }

        for (int i = 0; i <= 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void construirHojaDetalle(
            Workbook workbook,
            VentaReporteResponse reporte,
            Map<Integer, List<VentaDetalle>> detallesPorVenta,
            Map<Integer, List<Pago>> pagosPorVenta,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Detalle Ventas");
        int rowIdx = 0;

        Row h = sheet.createRow(rowIdx++);
        String[] headers = {
                "Fecha",
                "Comprobante",
                "Serie",
                "Correlativo",
                "Estado",
                "Cliente",
                "Vendedor",
                "Sucursal",
                "Items",
                "Pagos",
                "Subtotal",
                "Descuento",
                "IGV",
                "Total"
        };
        for (int i = 0; i < headers.length; i++) {
            h.createCell(i).setCellValue(headers[i]);
            h.getCell(i).setCellStyle(headerStyle);
        }

        for (VentaReporteResponse.DetalleItem item : reporte.detalleVentas()) {
            Row r = sheet.createRow(rowIdx++);
            int cantidadItems = item.idVenta() == null
                    ? 0
                    : detallesPorVenta.getOrDefault(item.idVenta(), List.of()).size();
            int cantidadPagos = item.idVenta() == null
                    ? 0
                    : pagosPorVenta.getOrDefault(item.idVenta(), List.of()).size();

            r.createCell(0).setCellValue(item.fecha() == null ? "" : item.fecha().format(FECHA_HORA_EXCEL));
            r.createCell(1).setCellValue(valorTexto(item.tipoComprobante()));
            r.createCell(2).setCellValue(valorTexto(item.serie()));
            r.createCell(3).setCellValue(item.correlativo() == null ? 0 : item.correlativo());
            r.createCell(4).setCellValue(valorTexto(item.estado()));
            r.createCell(5).setCellValue(valorTexto(item.nombreCliente()));
            r.createCell(6).setCellValue(valorTexto(item.nombreUsuario()));
            r.createCell(7).setCellValue(valorTexto(item.nombreSucursal()));
            r.createCell(8).setCellValue(cantidadItems);
            r.createCell(9).setCellValue(cantidadPagos);
            r.createCell(10).setCellValue(moneda(item.subtotal()).doubleValue());
            r.createCell(11).setCellValue(moneda(item.descuentoTotal()).doubleValue());
            r.createCell(12).setCellValue(moneda(item.igv()).doubleValue());
            r.createCell(13).setCellValue(moneda(item.total()).doubleValue());
            r.getCell(10).setCellStyle(moneyStyle);
            r.getCell(11).setCellStyle(moneyStyle);
            r.getCell(12).setCellStyle(moneyStyle);
            r.getCell(13).setCellStyle(moneyStyle);
        }

        Row total = sheet.createRow(rowIdx);
        total.createCell(0).setCellValue("TOTAL");
        total.createCell(13).setCellValue(moneda(reporte.montoTotal()).doubleValue());
        total.getCell(13).setCellStyle(moneyStyle);

        for (int i = 0; i <= 13; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void construirHojaDetalleItems(
            Workbook workbook,
            VentaReporteResponse reporte,
            Map<Integer, List<VentaDetalle>> detallesPorVenta,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Items Venta");
        int rowIdx = 0;

        Row h = sheet.createRow(rowIdx++);
        String[] headers = {
                "Comprobante",
                "FechaVenta",
                "EstadoVenta",
                "Cliente",
                "Vendedor",
                "Sucursal",
                "Producto",
                "SKU",
                "Color",
                "Talla",
                "Cantidad",
                "PrecioUnitario",
                "DescuentoLinea",
                "SubtotalLinea"
        };
        for (int i = 0; i < headers.length; i++) {
            h.createCell(i).setCellValue(headers[i]);
            h.getCell(i).setCellStyle(headerStyle);
        }

        BigDecimal totalItems = CERO_MONETARIO;
        for (VentaReporteResponse.DetalleItem venta : reporte.detalleVentas()) {
            Integer idVenta = venta.idVenta();
            if (idVenta == null) {
                continue;
            }
            List<VentaDetalle> itemsVenta = detallesPorVenta.getOrDefault(idVenta, List.of());
            for (VentaDetalle detalle : itemsVenta) {
                ProductoVariante variante = detalle.getProductoVariante();
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(comprobanteTexto(venta));
                r.createCell(1).setCellValue(venta.fecha() == null ? "" : venta.fecha().format(FECHA_HORA_EXCEL));
                r.createCell(2).setCellValue(valorTexto(venta.estado()));
                r.createCell(3).setCellValue(valorTexto(venta.nombreCliente()));
                r.createCell(4).setCellValue(valorTexto(venta.nombreUsuario()));
                r.createCell(5).setCellValue(valorTexto(venta.nombreSucursal()));
                r.createCell(6).setCellValue(variante != null && variante.getProducto() != null
                        ? valorTexto(variante.getProducto().getNombre())
                        : "");
                r.createCell(7).setCellValue(variante != null ? valorTexto(variante.getSku()) : "");
                r.createCell(8).setCellValue(variante != null && variante.getColor() != null
                        ? valorTexto(variante.getColor().getNombre())
                        : "");
                r.createCell(9).setCellValue(variante != null && variante.getTalla() != null
                        ? valorTexto(variante.getTalla().getNombre())
                        : "");
                r.createCell(10).setCellValue(detalle.getCantidad() == null ? 0 : detalle.getCantidad());
                r.createCell(11).setCellValue(moneda(detalle.getPrecioUnitario()).doubleValue());
                r.createCell(12).setCellValue(moneda(detalle.getDescuento()).doubleValue());
                r.createCell(13).setCellValue(moneda(detalle.getSubtotal()).doubleValue());
                r.getCell(11).setCellStyle(moneyStyle);
                r.getCell(12).setCellStyle(moneyStyle);
                r.getCell(13).setCellStyle(moneyStyle);
                totalItems = totalItems.add(moneda(detalle.getSubtotal())).setScale(2, RoundingMode.HALF_UP);
            }
        }

        Row total = sheet.createRow(rowIdx);
        total.createCell(0).setCellValue("TOTAL SUBTOTAL ITEMS");
        total.createCell(13).setCellValue(totalItems.doubleValue());
        total.getCell(13).setCellStyle(moneyStyle);

        for (int i = 0; i <= 13; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void construirHojaPagos(
            Workbook workbook,
            VentaReporteResponse reporte,
            Map<Integer, List<Pago>> pagosPorVenta,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Pagos Venta");
        int rowIdx = 0;

        Row h = sheet.createRow(rowIdx++);
        String[] headers = {
                "Comprobante",
                "FechaVenta",
                "EstadoVenta",
                "Cliente",
                "Vendedor",
                "Sucursal",
                "MetodoPago",
                "Referencia",
                "FechaPago",
                "Monto"
        };
        for (int i = 0; i < headers.length; i++) {
            h.createCell(i).setCellValue(headers[i]);
            h.getCell(i).setCellStyle(headerStyle);
        }

        BigDecimal totalPagos = CERO_MONETARIO;
        for (VentaReporteResponse.DetalleItem venta : reporte.detalleVentas()) {
            Integer idVenta = venta.idVenta();
            if (idVenta == null) {
                continue;
            }
            List<Pago> pagosVenta = pagosPorVenta.getOrDefault(idVenta, List.of());
            for (Pago pago : pagosVenta) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(comprobanteTexto(venta));
                r.createCell(1).setCellValue(venta.fecha() == null ? "" : venta.fecha().format(FECHA_HORA_EXCEL));
                r.createCell(2).setCellValue(valorTexto(venta.estado()));
                r.createCell(3).setCellValue(valorTexto(venta.nombreCliente()));
                r.createCell(4).setCellValue(valorTexto(venta.nombreUsuario()));
                r.createCell(5).setCellValue(valorTexto(venta.nombreSucursal()));
                r.createCell(6).setCellValue(pago.getMetodoPago() != null ? valorTexto(pago.getMetodoPago().getNombre()) : "");
                r.createCell(7).setCellValue(valorTexto(pago.getReferencia()));
                r.createCell(8).setCellValue(pago.getFecha() == null ? "" : pago.getFecha().format(FECHA_HORA_EXCEL));
                r.createCell(9).setCellValue(moneda(pago.getMonto()).doubleValue());
                r.getCell(9).setCellStyle(moneyStyle);
                totalPagos = totalPagos.add(moneda(pago.getMonto())).setScale(2, RoundingMode.HALF_UP);
            }
        }

        Row total = sheet.createRow(rowIdx);
        total.createCell(0).setCellValue("TOTAL PAGOS");
        total.createCell(9).setCellValue(totalPagos.doubleValue());
        total.getCell(9).setCellStyle(moneyStyle);

        for (int i = 0; i <= 9; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void construirHojaClientes(
            Workbook workbook,
            VentaReporteResponse reporte,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Resumen Clientes");
        int rowIdx = 0;

        Row h = sheet.createRow(rowIdx++);
        h.createCell(0).setCellValue("Cliente");
        h.createCell(1).setCellValue("Cantidad Ventas");
        h.createCell(2).setCellValue("Monto Total");
        h.createCell(3).setCellValue("Ticket Promedio");
        h.getCell(0).setCellStyle(headerStyle);
        h.getCell(1).setCellStyle(headerStyle);
        h.getCell(2).setCellStyle(headerStyle);
        h.getCell(3).setCellStyle(headerStyle);

        for (VentaReporteResponse.ClienteItem item : reporte.clientes()) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(valorTexto(item.nombreCliente()));
            r.createCell(1).setCellValue(item.cantidadVentas());
            r.createCell(2).setCellValue(moneda(item.montoTotal()).doubleValue());
            r.createCell(3).setCellValue(moneda(item.ticketPromedio()).doubleValue());
            r.getCell(2).setCellStyle(moneyStyle);
            r.getCell(3).setCellStyle(moneyStyle);
        }

        Row total = sheet.createRow(rowIdx);
        total.createCell(0).setCellValue("TOTAL");
        total.createCell(2).setCellValue(moneda(reporte.montoTotal()).doubleValue());
        total.getCell(2).setCellStyle(moneyStyle);

        for (int i = 0; i <= 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private Map<Integer, List<VentaDetalle>> agruparDetallesPorVenta(List<VentaDetalle> detallesVenta) {
        Map<Integer, List<VentaDetalle>> resultado = new HashMap<>();
        for (VentaDetalle detalle : detallesVenta) {
            Integer idVenta = detalle.getVenta() != null ? detalle.getVenta().getIdVenta() : null;
            if (idVenta == null) {
                continue;
            }
            resultado.computeIfAbsent(idVenta, key -> new ArrayList<>()).add(detalle);
        }
        return resultado;
    }

    private Map<Integer, List<Pago>> agruparPagosPorVenta(List<Pago> pagosVenta) {
        Map<Integer, List<Pago>> resultado = new HashMap<>();
        for (Pago pago : pagosVenta) {
            Integer idVenta = pago.getVenta() != null ? pago.getVenta().getIdVenta() : null;
            if (idVenta == null) {
                continue;
            }
            resultado.computeIfAbsent(idVenta, key -> new ArrayList<>()).add(pago);
        }
        return resultado;
    }

    private CellStyle crearEstiloHeader(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle crearEstiloMoneda(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("#,##0.00"));
        return style;
    }

    private BigDecimal promedio(BigDecimal montoTotal, long cantidadVentas) {
        if (cantidadVentas <= 0) {
            return CERO_MONETARIO;
        }
        return moneda(montoTotal)
                .divide(BigDecimal.valueOf(cantidadVentas), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal moneda(BigDecimal valor) {
        if (valor == null) {
            return CERO_MONETARIO;
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String valorTexto(String value) {
        return value == null ? "" : value;
    }

    private String comprobanteTexto(VentaReporteResponse.DetalleItem venta) {
        String tipo = valorTexto(venta.tipoComprobante());
        String serie = valorTexto(venta.serie());
        String correlativo = venta.correlativo() == null ? "" : String.valueOf(venta.correlativo());
        String documento = "";
        if (!serie.isBlank() && !correlativo.isBlank()) {
            documento = serie + "-" + correlativo;
        } else if (!serie.isBlank()) {
            documento = serie;
        } else if (!correlativo.isBlank()) {
            documento = correlativo;
        }
        if (tipo.isBlank()) {
            return documento;
        }
        if (documento.isBlank()) {
            return tipo;
        }
        return tipo + " " + documento;
    }

    private String nombreUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String normalizarTipoComprobante(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return "NOTA DE VENTA";
        }
        String normalized = tipoComprobante.trim().toUpperCase();
        validarTipoComprobantePermitido(normalized);
        return normalized;
    }

    private String normalizarTipoDescuento(String tipoDescuento, BigDecimal descuentoInput) {
        if (descuentoInput == null || descuentoInput.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (tipoDescuento == null || tipoDescuento.isBlank()) {
            throw new RuntimeException("tipoDescuento es obligatorio cuando descuentoTotal es mayor a 0");
        }
        String normalized = tipoDescuento.trim().toUpperCase();
        if (!"MONTO".equals(normalized) && !"PORCENTAJE".equals(normalized)) {
            throw new RuntimeException("tipoDescuento permitido: MONTO o PORCENTAJE");
        }
        if ("PORCENTAJE".equals(normalized) && descuentoInput.compareTo(CIEN) > 0) {
            throw new RuntimeException("descuentoTotal no puede superar 100 cuando tipoDescuento es PORCENTAJE");
        }
        return normalized;
    }

    private BigDecimal normalizarIgv(Double igvPorcentaje) {
        BigDecimal igv = igvPorcentaje == null
                ? BigDecimal.valueOf(18)
                : decimalNoNegativo(igvPorcentaje, "igvPorcentaje");
        if (igv.compareTo(CIEN) > 0) {
            throw new RuntimeException("igvPorcentaje no puede ser mayor a 100");
        }
        return igv.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer idSucursalRequeridaParaAdmin(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            throw new RuntimeException("idSucursal es obligatorio para ADMINISTRADOR");
        }
        return idSucursalRequest;
    }

    private BigDecimal decimalDesdeDouble(Double value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private Double precioVigenteVariante(ProductoVariante variante) {
        if (variante == null) {
            return 0d;
        }
        Double precio = variante.getPrecio();
        Double precioOferta = variante.getPrecioOferta();
        if (precioOferta != null && precio != null && precioOferta > 0 && precioOferta < precio) {
            return precioOferta;
        }
        return precio;
    }

    private BigDecimal decimalPositivo(Double value, String field) {
        BigDecimal decimal = decimalDesdeDouble(value);
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El campo '" + field + "' debe ser mayor a 0");
        }
        return decimal;
    }

    private BigDecimal decimalNoNegativo(Double value, String field) {
        BigDecimal decimal = decimalDesdeDouble(value);
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El campo '" + field + "' no puede ser negativo");
        }
        return decimal;
    }

    private String normalizarTexto(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizarTerminoBusqueda(String term) {
        if (term == null) {
            return null;
        }
        String normalizado = term.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private Integer normalizarIdUsuarioFiltro(Integer idUsuario) {
        if (idUsuario == null) {
            return null;
        }
        if (idUsuario <= 0) {
            throw new RuntimeException("idUsuario debe ser mayor a 0");
        }
        return idUsuario;
    }

    private Integer resolverIdSucursalListado(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        if (esAdministrador(usuarioAutenticado)) {
            if (idSucursalRequest == null) {
                return null;
            }
            if (idSucursalRequest <= 0) {
                throw new RuntimeException("idSucursal debe ser mayor a 0");
            }
            return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalRequest)
                    .map(Sucursal::getIdSucursal)
                    .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para filtrar por otra sucursal");
        }
        return idSucursalUsuario;
    }

    private String normalizarTipoComprobanteFiltro(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return null;
        }
        String normalized = tipoComprobante.trim().toUpperCase();
        validarTipoComprobantePermitido(normalized);
        return normalized;
    }

    private RangoFechas resolverRangoFechasListado(
            String periodo,
            LocalDate fecha,
            LocalDate desde,
            LocalDate hasta) {
        String periodoNormalizado = normalizarPeriodoListado(periodo);

        if (periodoNormalizado == null) {
            if (fecha != null) {
                return new RangoFechas(fecha, fecha);
            }
            if (desde != null || hasta != null) {
                return resolverRangoPersonalizadoListado(desde, hasta);
            }
            return null;
        }

        LocalDate hoy = LocalDate.now();
        return switch (periodoNormalizado) {
            case "HOY" -> new RangoFechas(hoy, hoy);
            case "AYER" -> {
                LocalDate ayer = hoy.minusDays(1);
                yield new RangoFechas(ayer, ayer);
            }
            case "SEMANA", "SEMANA_ACTUAL" -> {
                LocalDate inicio = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate fin = inicio.plusDays(6);
                yield new RangoFechas(inicio, fin);
            }
            case "MES", "MES_ACTUAL" -> {
                YearMonth mesActual = YearMonth.now();
                yield new RangoFechas(mesActual.atDay(1), mesActual.atEndOfMonth());
            }
            case "FECHA", "FECHA_ESPECIFICA" -> {
                if (fecha == null) {
                    throw new RuntimeException("Para periodo FECHA, envie 'fecha'");
                }
                yield new RangoFechas(fecha, fecha);
            }
            case "RANGO" -> resolverRangoPersonalizadoListado(desde, hasta);
            default -> throw new RuntimeException(
                    "periodo permitido: HOY, AYER, SEMANA, MES, FECHA o RANGO");
        };
    }

    private String normalizarPeriodoListado(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            return null;
        }
        return periodo.trim().toUpperCase(Locale.ROOT);
    }

    private RangoFechas resolverRangoPersonalizadoListado(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            throw new RuntimeException("Para periodo RANGO, envie 'desde' y/o 'hasta'");
        }
        LocalDate desdeNormalizado = desde == null ? hasta : desde;
        LocalDate hastaNormalizado = hasta == null ? desde : hasta;

        if (desdeNormalizado.isAfter(hastaNormalizado)) {
            throw new RuntimeException("La fecha 'desde' no puede ser mayor a 'hasta'");
        }

        return new RangoFechas(desdeNormalizado, hastaNormalizado);
    }

    private void validarTipoComprobantePermitido(String tipoComprobante) {
        if (!"NOTA DE VENTA".equals(tipoComprobante)
                && !"BOLETA".equals(tipoComprobante)
                && !"FACTURA".equals(tipoComprobante)) {
            throw new RuntimeException("tipoComprobante permitido: NOTA DE VENTA, BOLETA o FACTURA");
        }
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro 'page' no puede ser negativo");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolLectura(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar ventas");
        }
    }

    private void validarRolVenta(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para registrar ventas");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private enum AgrupacionReporte {
        DIA,
        SEMANA,
        MES
    }

    private enum PeriodoFiltro {
        HOY,
        AYER,
        SEMANA,
        MES,
        RANGO
    }

    private record RangoFechas(
            LocalDate desde,
            LocalDate hasta) {
    }

    private record FiltroReporteVentas(
            AgrupacionReporte agrupacion,
            PeriodoFiltro periodoFiltro,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            String nombreSucursal) {
    }

    private record AcumuladoPeriodo(
            String etiqueta,
            long cantidadVentas,
            BigDecimal montoTotal) {
    }

    private record AcumuladoCliente(
            Integer idCliente,
            String nombreCliente,
            long cantidadVentas,
            BigDecimal montoTotal) {
    }

    private record DetalleCalculado(
            ProductoVariante variante,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal descuento,
            BigDecimal subtotal) {
    }

    private record PagoCalculado(
            MetodoPagoConfig metodoPago,
            BigDecimal monto,
            String referencia) {
    }

    private record TotalesVenta(
            BigDecimal igvPorcentaje,
            BigDecimal subtotal,
            BigDecimal descuentoAplicado,
            String tipoDescuento,
            BigDecimal igv,
            BigDecimal total) {
    }

    private record NumeroComprobante(
            String serie,
            Integer correlativo) {
    }
}
