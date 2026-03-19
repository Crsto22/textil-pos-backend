package com.sistemapos.sistematextil.services;

import java.awt.Color;
import java.awt.image.BufferedImage;
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

import javax.imageio.ImageIO;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sistemapos.sistematextil.events.VentaRegistradaEvent;
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
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;
import com.sistemapos.sistematextil.util.sunat.SunatEmissionResult;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;
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
    private static final String CODIGO_IGV_GRAVADO = "10";
    private static final String CODIGO_IGV_INAFECTO = "30";
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
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SunatEmissionService sunatEmissionService;
    private final SunatDocumentStorageService sunatDocumentStorageService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<VentaListItemResponse> listarPaginado(
            String term,
            Integer idUsuario,
            Integer idCliente,
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
        Integer idUsuarioFiltro = resolverIdUsuarioListado(usuarioAutenticado, idUsuario);
        String tipoComprobanteFiltro = normalizarTipoComprobanteFiltro(tipoComprobante);
        RangoFechas rangoFechasFiltro = resolverRangoFechasListado(periodo, fecha, desde, hasta);
        LocalDateTime fechaInicioFiltro = rangoFechasFiltro == null ? null : rangoFechasFiltro.desde().atStartOfDay();
        LocalDateTime fechaFinExclusiveFiltro = rangoFechasFiltro == null
                ? null
                : rangoFechasFiltro.hasta().plusDays(1).atStartOfDay();

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idVenta").descending());
        Integer idSucursalFiltro = resolverIdSucursalListado(usuarioAutenticado, idSucursal);
        Integer idClienteFiltro = resolverIdClienteFiltro(usuarioAutenticado, idCliente, idSucursalFiltro);

        Page<Venta> ventas = ventaRepository.buscarConFiltros(
                termNormalizado,
                idSucursalFiltro,
                idUsuarioFiltro,
                idClienteFiltro,
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
            Integer idCliente,
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
                idSucursal,
                idCliente);
        List<Venta> ventas = buscarVentasParaReporte(filtro, incluirAnuladas);

        return construirReporteVentas(ventas, filtro, incluirAnuladas);
    }

    public byte[] exportarReporteVentasExcel(
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            Integer idCliente,
            boolean incluirAnuladas,
            String correoUsuarioAutenticado) {
        VentaReporteResponse reporte = obtenerReporteVentas(
                agrupar,
                periodo,
                desde,
                hasta,
                idSucursal,
                idCliente,
                incluirAnuladas,
                correoUsuarioAutenticado);
        return construirExcelReporteVentas(reporte);
    }

    public byte[] exportarReportePdfVentas(
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            Integer idCliente,
            boolean incluirAnuladas,
            String correoUsuarioAutenticado) {
        VentaReporteResponse reporte = obtenerReporteVentas(
                agrupar,
                periodo,
                desde,
                hasta,
                idSucursal,
                idCliente,
                incluirAnuladas,
                correoUsuarioAutenticado);
        List<Integer> ventaIds = reporte.detalleVentas().stream()
                .map(VentaReporteResponse.DetalleItem::idVenta)
                .filter(id -> id != null)
                .toList();
        List<VentaDetalle> detallesVenta = ventaIds.isEmpty()
                ? List.of()
                : ventaDetalleRepository.findActivosByVentaIds(ventaIds);
        Map<Integer, List<VentaDetalle>> detallesPorVenta = agruparDetallesPorVenta(detallesVenta);
        return construirPdfReporteVentas(reporte, detallesPorVenta);
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
            Document document = new Document(PageSize.A4, 40f, 40f, 28f, 28f);
            PdfWriter.getInstance(document, output);
            document.open();

            agregarCabeceraComprobantePdf(document, venta);
            document.add(new Paragraph(" "));
            agregarDatosClienteComprobantePdf(document, venta, pagos);
            document.add(new Paragraph(" "));
            agregarDetalleComprobantePdf(document, detalles);
            agregarResumenComprobantePdf(document, venta, pagos);
            document.add(new Paragraph(" "));
            agregarPieComprobantePdf(document, venta, pagos);

            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("No se pudo generar el comprobante PDF");
        }
    }

    public byte[] generarTicket80mm(Integer idVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        List<Pago> pagos = pagoRepository.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(venta.getIdVenta());

        float anchoTicket = 226f; // 80mm en puntos (1mm ≈ 2.83pt)
        float margen = 8f;

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(new com.lowagie.text.Rectangle(anchoTicket, 1000f), margen, margen, 8f, 8f);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            document.open();

            agregarCabeceraTicket(document, venta);
            agregarSeparadorTicket(document);
            agregarDatosClienteTicket(document, venta, pagos);
            agregarSeparadorTicket(document);
            agregarDetalleTicket(document, detalles);
            agregarSeparadorTicket(document);
            agregarResumenTicket(document, venta);
            agregarSeparadorTicket(document);
            agregarPieTicket(document, venta);

            // Recortar la altura del documento al contenido real
            float alturaContenido = writer.getVerticalPosition(true);
            document.close();

            // Regenerar con altura ajustada
            float alturaReal = 1000f - alturaContenido + 20f;
            if (alturaReal < 200f) alturaReal = 200f;

            ByteArrayOutputStream outputFinal = new ByteArrayOutputStream();
            Document docFinal = new Document(new com.lowagie.text.Rectangle(anchoTicket, alturaReal), margen, margen, 8f, 8f);
            PdfWriter.getInstance(docFinal, outputFinal);
            docFinal.open();

            agregarCabeceraTicket(docFinal, venta);
            agregarSeparadorTicket(docFinal);
            agregarDatosClienteTicket(docFinal, venta, pagos);
            agregarSeparadorTicket(docFinal);
            agregarDetalleTicket(docFinal, detalles);
            agregarSeparadorTicket(docFinal);
            agregarResumenTicket(docFinal, venta);
            agregarSeparadorTicket(docFinal);
            agregarPieTicket(docFinal, venta);

            docFinal.close();
            return outputFinal.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("No se pudo generar el ticket");
        }
    }

    private void agregarSeparadorTicket(Document document) throws DocumentException {
        Paragraph sep = new Paragraph("- - - - - - - - - - - - - - - - - - - - - - - - - - - -",
                fuentePdf(false, 6f, new Color(150, 150, 150)));
        sep.setAlignment(Element.ALIGN_CENTER);
        sep.setSpacingBefore(2f);
        sep.setSpacingAfter(2f);
        document.add(sep);
    }

    private void agregarCabeceraTicket(Document document, Venta venta) throws DocumentException {
        Sucursal sucursal = venta.getSucursal();
        String nombreEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getNombre()) : "";
        String razonSocial = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getRazonSocial()) : "";
        String ruc = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getRuc()) : "";
        String direccion = sucursal != null ? valorTexto(sucursal.getDireccion()) : "";
        String distrito = sucursal != null ? valorTexto(sucursal.getDistrito()) : "";
        String provincia = sucursal != null ? valorTexto(sucursal.getProvincia()) : "";
        String departamento = sucursal != null ? valorTexto(sucursal.getDepartamento()) : "";
        String telefono = sucursal != null ? valorTexto(sucursal.getTelefono()) : "";

        // Logo o nombre empresa
        Image logo = cargarLogoEmpresaParaPdf(venta);
        if (logo != null) {
            logo.scaleToFit(100f, 40f);
            logo.setAlignment(Element.ALIGN_CENTER);
            document.add(logo);
        } else {
            String nombreMostrar = !nombreEmpresa.isBlank() ? nombreEmpresa : razonSocial;
            if (!nombreMostrar.isBlank()) {
                Paragraph pNombre = new Paragraph(nombreMostrar, fuentePdf(true, 10f));
                pNombre.setAlignment(Element.ALIGN_CENTER);
                document.add(pNombre);
            }
        }

        if (!razonSocial.isBlank() && !razonSocial.equalsIgnoreCase(nombreEmpresa)) {
            Paragraph pRazon = new Paragraph(razonSocial, fuentePdf(false, 6.5f));
            pRazon.setAlignment(Element.ALIGN_CENTER);
            document.add(pRazon);
        }

        if (!ruc.isBlank()) {
            Paragraph pRuc = new Paragraph("RUC: " + ruc, fuentePdf(true, 7.5f));
            pRuc.setAlignment(Element.ALIGN_CENTER);
            pRuc.setSpacingBefore(2f);
            document.add(pRuc);
        }

        if (!direccion.isBlank()) {
            String dirCompleta = direccion;
            String ubicacion = construirUbicacionPdf(distrito, provincia, departamento);
            if (!ubicacion.isBlank()) dirCompleta += " - " + ubicacion;
            Paragraph pDir = new Paragraph(dirCompleta, fuentePdf(false, 6f));
            pDir.setAlignment(Element.ALIGN_CENTER);
            pDir.setSpacingBefore(1f);
            document.add(pDir);
        }

        if (!telefono.isBlank()) {
            Paragraph pTel = new Paragraph("Tel: " + telefono, fuentePdf(false, 6f));
            pTel.setAlignment(Element.ALIGN_CENTER);
            document.add(pTel);
        }

        // Tipo y numero de comprobante
        Paragraph pTipo = new Paragraph(tituloComprobanteParaPdf(venta.getTipoComprobante()), fuentePdf(true, 8f));
        pTipo.setAlignment(Element.ALIGN_CENTER);
        pTipo.setSpacingBefore(4f);
        document.add(pTipo);

        Paragraph pNumero = new Paragraph(numeroComprobanteParaPdf(venta), fuentePdf(true, 9f));
        pNumero.setAlignment(Element.ALIGN_CENTER);
        pNumero.setSpacingBefore(1f);
        document.add(pNumero);
    }

    private void agregarDatosClienteTicket(Document document, Venta venta, List<Pago> pagos) throws DocumentException {
        Cliente cliente = venta.getCliente();
        String nombreCliente = cliente != null && !valorTexto(cliente.getNombres()).isBlank()
                ? valorTexto(cliente.getNombres()) : "CLIENTES VARIOS";
        String tipoDoc = etiquetaTipoDocumentoPdf(cliente);
        String nroDocumento = cliente != null && !valorTexto(cliente.getNroDocumento()).isBlank()
                ? valorTexto(cliente.getNroDocumento()) : "-";
        String fechaEmision = venta.getFecha() == null ? ""
                : venta.getFecha().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String horaEmision = venta.getFecha() == null ? ""
                : venta.getFecha().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formaPago = venta.getFormaPago() != null ? venta.getFormaPago() : "CONTADO";
        String metodoPago = pagos.stream()
                .map(p -> p.getMetodoPago() != null ? p.getMetodoPago().getNombre() : "")
                .filter(n -> !n.isBlank()).distinct()
                .reduce((a, b) -> a + ", " + b).orElse("-");
        String moneda = normalizarMonedaPdf(venta.getMoneda());

        com.lowagie.text.Font fValor = fuentePdf(false, 6.5f);

        agregarLineaTicket(document, "F. Emision: " + fechaEmision + "  " + horaEmision, fValor);
        agregarLineaTicket(document, "Cliente: " + nombreCliente, fValor);
        agregarLineaTicket(document, tipoDoc + ": " + nroDocumento, fValor);
        agregarLineaTicket(document, "Moneda: " + moneda, fValor);
        agregarLineaTicket(document, "F. Pago: " + formaPago + "  |  M. Pago: " + metodoPago, fValor);

        String direccionCliente = cliente != null && !valorTexto(cliente.getDireccion()).isBlank()
                ? valorTexto(cliente.getDireccion()) : "";
        if (!direccionCliente.isBlank()) {
            agregarLineaTicket(document, "Dir: " + direccionCliente, fValor);
        }
    }

    private void agregarLineaTicket(Document document, String texto, com.lowagie.text.Font font) throws DocumentException {
        Paragraph p = new Paragraph(texto, font);
        p.setAlignment(Element.ALIGN_LEFT);
        p.setSpacingBefore(1f);
        document.add(p);
    }

    private void agregarDetalleTicket(Document document, List<VentaDetalle> detalles) throws DocumentException {
        com.lowagie.text.Font fHeader = fuentePdf(true, 6.5f);
        com.lowagie.text.Font fItem = fuentePdf(false, 6.5f);

        // Encabezado: CANT  DESCRIPCION  P.UNIT  DSCTO  IMPORTE
        PdfPTable header = new PdfPTable(new float[] { 1f, 4.1f, 1.3f, 1.3f, 1.5f });
        header.setWidthPercentage(100);
        header.addCell(crearCeldaTicket("CANT", Element.ALIGN_CENTER, fHeader));
        header.addCell(crearCeldaTicket("DESCRIPCION", Element.ALIGN_LEFT, fHeader));
        header.addCell(crearCeldaTicket("P.UNIT", Element.ALIGN_RIGHT, fHeader));
        header.addCell(crearCeldaTicket("DSCTO", Element.ALIGN_RIGHT, fHeader));
        header.addCell(crearCeldaTicket("IMPORTE", Element.ALIGN_RIGHT, fHeader));
        document.add(header);

        for (VentaDetalle detalle : detalles) {
            PdfPTable fila = new PdfPTable(new float[] { 1f, 4.1f, 1.3f, 1.3f, 1.5f });
            fila.setWidthPercentage(100);

            fila.addCell(crearCeldaTicket(String.valueOf(valorEntero(detalle.getCantidad())), Element.ALIGN_CENTER, fItem));
            fila.addCell(crearCeldaTicket(descripcionDetalleParaPdf(detalle), Element.ALIGN_LEFT, fItem));
            fila.addCell(crearCeldaTicket(formatearMonedaPdf(detalle.getPrecioUnitario()), Element.ALIGN_RIGHT, fItem));
            BigDecimal descuento = detalle.getDescuento() != null ? detalle.getDescuento() : BigDecimal.ZERO;
            fila.addCell(crearCeldaTicket(formatearMonedaPdf(descuento), Element.ALIGN_RIGHT, fItem));
            BigDecimal importe = detalle.getTotalDetalle() != null ? detalle.getTotalDetalle() : detalle.getSubtotal();
            fila.addCell(crearCeldaTicket(formatearMonedaPdf(importe), Element.ALIGN_RIGHT, fItem));

            document.add(fila);
        }
    }

    private PdfPCell crearCeldaTicket(String texto, int alineacion, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(1.5f);
        cell.setHorizontalAlignment(alineacion);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph p = new Paragraph(valorTexto(texto), font);
        p.setAlignment(alineacion);
        cell.addElement(p);
        return cell;
    }

    private void agregarResumenTicket(Document document, Venta venta) throws DocumentException {
        String simbolo = simboloMonedaPdf(venta.getMoneda());
        com.lowagie.text.Font fNormal = fuentePdf(false, 7f);
        com.lowagie.text.Font fTotal = fuentePdf(true, 9f);

        agregarFilaTotalTicket(document, "Subtotal " + simbolo, formatearMonedaPdf(venta.getSubtotal()), fNormal);
        if (venta.getDescuentoTotal() != null && venta.getDescuentoTotal().compareTo(BigDecimal.ZERO) > 0) {
            agregarFilaTotalTicket(document, "Descuento Total " + simbolo, "-" + formatearMonedaPdf(venta.getDescuentoTotal()), fNormal);
        }
        if (aplicaIgvSegunTipoComprobante(venta.getTipoComprobante())) {
            agregarFilaTotalTicket(document, "IGV (" + formatearDecimalPdf(venta.getIgvPorcentaje()) + "%) " + simbolo,
                    formatearMonedaPdf(venta.getIgv()), fNormal);
        }
        agregarFilaTotalTicket(document, "TOTAL " + simbolo, formatearMonedaPdf(venta.getTotal()), fTotal);

        String monedaTexto = "PEN".equalsIgnoreCase(valorTexto(venta.getMoneda()).isBlank() ? "PEN" : venta.getMoneda())
                ? "SOLES" : "DOLARES AMERICANOS";
        Paragraph son = new Paragraph("Son: " + montoEnLetrasConMoneda(venta.getTotal(), monedaTexto), fuentePdf(false, 6f));
        son.setSpacingBefore(3f);
        document.add(son);
    }

    private void agregarFilaTotalTicket(Document document, String label, String valor, com.lowagie.text.Font font) throws DocumentException {
        PdfPTable fila = new PdfPTable(new float[] { 5f, 3f });
        fila.setWidthPercentage(100);

        PdfPCell cLabel = new PdfPCell();
        cLabel.setBorder(Rectangle.NO_BORDER);
        cLabel.setPadding(1f);
        Paragraph pLabel = new Paragraph(label, font);
        pLabel.setAlignment(Element.ALIGN_RIGHT);
        cLabel.addElement(pLabel);
        fila.addCell(cLabel);

        PdfPCell cValor = new PdfPCell();
        cValor.setBorder(Rectangle.NO_BORDER);
        cValor.setPadding(1f);
        Paragraph pValor = new Paragraph(valor, font);
        pValor.setAlignment(Element.ALIGN_RIGHT);
        cValor.addElement(pValor);
        fila.addCell(cValor);

        document.add(fila);
    }

    private void agregarPieTicket(Document document, Venta venta) throws DocumentException {
        Color colorGris = new Color(80, 80, 80);
        boolean esElectronica = requiereComprobanteElectronico(venta.getTipoComprobante());

        // QR centrado
        Image qr = generarQrComprobantePdf(venta);
        if (qr != null) {
            qr.scaleToFit(90f, 90f);
            qr.setAlignment(Element.ALIGN_CENTER);
            document.add(qr);
        }

        // Hash
        String hashCode = generarHashComprobantePdf(venta);
        Paragraph hashP = new Paragraph("Hash: " + hashCode, fuentePdf(false, 5.5f, colorGris));
        hashP.setAlignment(Element.ALIGN_CENTER);
        hashP.setSpacingBefore(2f);
        document.add(hashP);

        if (esElectronica) {
            String tipoTexto = esFacturaPdf(venta) ? "FACTURA ELECTRONICA" : "BOLETA DE VENTA ELECTRONICA";
            Paragraph rep = new Paragraph("Representacion impresa de la " + tipoTexto,
                    fuentePdf(true, 5.5f, colorGris));
            rep.setAlignment(Element.ALIGN_CENTER);
            rep.setSpacingBefore(3f);
            document.add(rep);

            Paragraph consulta = new Paragraph("Consulte en: https://e-factura.sunat.gob.pe/",
                    fuentePdf(false, 5f, colorGris));
            consulta.setAlignment(Element.ALIGN_CENTER);
            consulta.setSpacingBefore(1f);
            document.add(consulta);
        }

        Paragraph autorizado = new Paragraph("Autorizado mediante Res. de Intendencia",
                fuentePdf(false, 5f, colorGris));
        autorizado.setAlignment(Element.ALIGN_CENTER);
        autorizado.setSpacingBefore(2f);
        document.add(autorizado);

        Paragraph gracias = new Paragraph("*** GRACIAS POR SU COMPRA ***", fuentePdf(true, 7f));
        gracias.setAlignment(Element.ALIGN_CENTER);
        gracias.setSpacingBefore(6f);
        document.add(gracias);
    }

    public ArchivoDescargable descargarSunatXml(Integer idVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        if (!requiereComprobanteElectronico(venta.getTipoComprobante())) {
            throw new RuntimeException("La venta no requiere archivo XML SUNAT");
        }
        if (venta.getSunatXmlKey() == null || venta.getSunatXmlKey().isBlank()) {
            throw new RuntimeException("La venta aun no tiene XML SUNAT disponible");
        }

        byte[] contenido = sunatDocumentStorageService.download(venta.getSunatXmlKey());
        return new ArchivoDescargable(
                venta.getSunatXmlNombre() != null && !venta.getSunatXmlNombre().isBlank()
                        ? venta.getSunatXmlNombre()
                        : SunatComprobanteHelper.construirNombreArchivoXml(venta),
                MediaType.APPLICATION_XML_VALUE,
                contenido);
    }

    public ArchivoDescargable descargarSunatCdr(Integer idVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        if (!requiereComprobanteElectronico(venta.getTipoComprobante())) {
            throw new RuntimeException("La venta no requiere archivo CDR SUNAT");
        }
        if (venta.getSunatCdrKey() == null || venta.getSunatCdrKey().isBlank()) {
            throw new RuntimeException("La venta aun no tiene CDR SUNAT disponible");
        }

        byte[] contenido = sunatDocumentStorageService.download(venta.getSunatCdrKey());
        return new ArchivoDescargable(
                venta.getSunatCdrNombre() != null && !venta.getSunatCdrNombre().isBlank()
                        ? venta.getSunatCdrNombre()
                        : SunatComprobanteHelper.construirNombreArchivoCdrZip(venta),
                "application/xml",
                contenido);
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
        String direccion = sucursal != null ? valorTexto(sucursal.getDireccion()) : "";
        String distrito = sucursal != null ? valorTexto(sucursal.getDistrito()) : "";
        String provincia = sucursal != null ? valorTexto(sucursal.getProvincia()) : "";
        String departamento = sucursal != null ? valorTexto(sucursal.getDepartamento()) : "";
        String telefono = sucursal != null ? valorTexto(sucursal.getTelefono()) : "";
        String correo = sucursal != null && !valorTexto(sucursal.getCorreo()).isBlank()
                ? valorTexto(sucursal.getCorreo())
                : sucursal != null && sucursal.getEmpresa() != null
                        ? valorTexto(sucursal.getEmpresa().getCorreo())
                        : "";
        String direccionCompleta = direccion;
        String ubicacion = construirUbicacionPdf(distrito, provincia, departamento);
        if (!direccionCompleta.isBlank() && !ubicacion.isBlank()) {
            direccionCompleta += " - " + ubicacion;
        }

        Color colorPrimario = new Color(60, 76, 102);

        PdfPTable header = new PdfPTable(new float[] { 6.4f, 3.6f });
        header.setWidthPercentage(100);

        PdfPCell empresaCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
        empresaCell.setPaddingRight(18f);
        Image logo = cargarLogoEmpresaParaPdf(venta);
        if (logo != null) {
            PdfPTable empresaWrap = new PdfPTable(new float[] { 2.2f, 4.2f });
            empresaWrap.setWidthPercentage(100);

            PdfPCell logoCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setPaddingRight(8f);
            logo.scaleToFit(120f, 60f);
            logo.setAlignment(Element.ALIGN_CENTER);
            logoCell.addElement(logo);
            empresaWrap.addCell(logoCell);

            PdfPCell datosEmpresaCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
            datosEmpresaCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            datosEmpresaCell.setPaddingTop(4f);
            agregarDatosEmpresaComprobantePdf(
                    datosEmpresaCell,
                    nombreEmpresa,
                    razonSocial,
                    ruc,
                    direccionCompleta,
                    telefono,
                    correo,
                    colorPrimario);
            empresaWrap.addCell(datosEmpresaCell);

            empresaCell.addElement(empresaWrap);
        } else {
            agregarDatosEmpresaComprobantePdf(
                    empresaCell,
                    nombreEmpresa,
                    razonSocial,
                    ruc,
                    direccionCompleta,
                    telefono,
                    correo,
                    colorPrimario);
        }
        header.addCell(empresaCell);

        PdfPCell tipoCell = crearCeldaBase(Rectangle.BOX, 12f);
        tipoCell.setBorderColor(colorPrimario);
        tipoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tipoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph pTipo = new Paragraph(tituloComprobanteParaPdf(venta.getTipoComprobante()), fuentePdf(true, 15f, colorPrimario));
        pTipo.setAlignment(Element.ALIGN_CENTER);
        tipoCell.addElement(pTipo);

        Paragraph pNumero = new Paragraph(numeroComprobanteParaPdf(venta), fuentePdf(true, 13f));
        pNumero.setAlignment(Element.ALIGN_CENTER);
        pNumero.setSpacingBefore(6f);
        tipoCell.addElement(pNumero);

        header.addCell(tipoCell);
        document.add(header);
    }

    private void agregarDatosEmpresaComprobantePdf(
            PdfPCell cell,
            String nombreEmpresa,
            String razonSocial,
            String ruc,
            String direccion,
            String telefono,
            String correo,
            Color colorPrimario) {
        String nombreMostrar = !razonSocial.isBlank() ? razonSocial : nombreEmpresa;
        if (!nombreMostrar.isBlank()) {
            Paragraph empresa = new Paragraph(nombreMostrar, fuentePdf(true, 14f, colorPrimario));
            empresa.setAlignment(Element.ALIGN_LEFT);
            cell.addElement(empresa);
        }
        if (!nombreEmpresa.isBlank() && !nombreEmpresa.equalsIgnoreCase(razonSocial)) {
            Paragraph nombre = new Paragraph(nombreEmpresa, fuentePdf(false, 10f, new Color(70, 70, 70)));
            nombre.setAlignment(Element.ALIGN_LEFT);
            nombre.setSpacingBefore(2f);
            cell.addElement(nombre);
        }
        if (!ruc.isBlank()) {
            Paragraph rucP = new Paragraph("RUC: " + ruc, fuentePdf(false, 10f));
            rucP.setAlignment(Element.ALIGN_LEFT);
            rucP.setSpacingBefore(2f);
            cell.addElement(rucP);
        }
        if (!direccion.isBlank()) {
            Paragraph direccionP = new Paragraph("Direccion: " + direccion, fuentePdf(false, 9.5f));
            direccionP.setAlignment(Element.ALIGN_LEFT);
            direccionP.setSpacingBefore(2f);
            cell.addElement(direccionP);
        }
        if (!telefono.isBlank()) {
            Paragraph telefonoP = new Paragraph("Telefono: " + telefono, fuentePdf(false, 9.5f));
            telefonoP.setAlignment(Element.ALIGN_LEFT);
            telefonoP.setSpacingBefore(1f);
            cell.addElement(telefonoP);
        }
        if (!correo.isBlank()) {
            Paragraph correoP = new Paragraph("Correo: " + correo, fuentePdf(false, 9.5f));
            correoP.setAlignment(Element.ALIGN_LEFT);
            correoP.setSpacingBefore(1f);
            cell.addElement(correoP);
        }
    }

    private void agregarDatosClienteComprobantePdf(Document document, Venta venta, List<Pago> pagos) throws DocumentException {
        Cliente cliente = venta.getCliente();
        String nombreCliente = cliente != null && !valorTexto(cliente.getNombres()).isBlank()
                ? valorTexto(cliente.getNombres())
                : "CLIENTES VARIOS";
        String tipoDoc = etiquetaTipoDocumentoPdf(cliente);
        String nroDocumento = cliente != null && !valorTexto(cliente.getNroDocumento()).isBlank()
                ? valorTexto(cliente.getNroDocumento())
                : "-";
        String direccionCliente = cliente != null && !valorTexto(cliente.getDireccion()).isBlank()
                ? valorTexto(cliente.getDireccion())
                : "";
        String fechaEmision = venta.getFecha() == null
                ? ""
                : venta.getFecha().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String horaEmision = venta.getFecha() == null
                ? ""
                : venta.getFecha().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String moneda = normalizarMonedaPdf(venta.getMoneda());
        String formaPago = venta.getFormaPago() != null ? venta.getFormaPago() : "CONTADO";
        String estado = valorTexto(venta.getEstado());
        String metodoPago = pagos.stream()
                .map(p -> p.getMetodoPago() != null ? p.getMetodoPago().getNombre() : "")
                .filter(n -> !n.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("-");
        String codigoOperacion = construirCodigosOperacionPagoPdf(pagos);
        String numeroComprobante = numeroComprobanteParaPdf(venta);

        Color colorFondoInfo = new Color(245, 247, 250);

        PdfPTable tabla = new PdfPTable(new float[] { 2.2f, 4.3f, 2f, 2.3f });
        tabla.setWidthPercentage(100);

        agregarFilaDatosComprobantePdf(tabla, "Cliente", nombreCliente, "Fecha", fechaEmision, colorFondoInfo);
        agregarFilaDatosComprobantePdf(tabla, tipoDoc, nroDocumento, "Hora", horaEmision, colorFondoInfo);
        agregarFilaDatosComprobantePdf(
                tabla,
                "Direccion",
                direccionCliente.isBlank() ? "-" : direccionCliente,
                "Comprobante",
                numeroComprobante,
                colorFondoInfo);
        agregarFilaDatosComprobantePdf(tabla, "Moneda", moneda, "Forma de pago", formaPago, colorFondoInfo);
        agregarFilaDatosComprobantePdf(tabla, "Metodo de pago", metodoPago, "Estado", estado.isBlank() ? "-" : estado, colorFondoInfo);
        if (esFacturaPdf(venta) && !codigoOperacion.isBlank()) {
            agregarFilaDatosComprobantePdf(tabla, "Codigo de operacion", codigoOperacion, "", "", colorFondoInfo);
        }

        document.add(tabla);
    }

    private void agregarDetalleComprobantePdf(Document document, List<VentaDetalle> detalles) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 0.8f, 5.7f, 1.2f, 1.6f, 1.5f, 1.7f });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        tabla.setSpacingBefore(4f);

        Color colorHeaderBg = new Color(60, 76, 102);

        agregarHeaderDetalle(tabla, "Item", Element.ALIGN_CENTER, colorHeaderBg);
        agregarHeaderDetalle(tabla, "Descripcion", Element.ALIGN_LEFT, colorHeaderBg);
        agregarHeaderDetalle(tabla, "Cant.", Element.ALIGN_CENTER, colorHeaderBg);
        agregarHeaderDetalle(tabla, "P. Unit.", Element.ALIGN_RIGHT, colorHeaderBg);
        agregarHeaderDetalle(tabla, "Dscto.", Element.ALIGN_RIGHT, colorHeaderBg);
        agregarHeaderDetalle(tabla, "Importe", Element.ALIGN_RIGHT, colorHeaderBg);

        int fila = 1;
        for (VentaDetalle detalle : detalles) {
            Color bgFila = fila % 2 == 0 ? new Color(247, 249, 252) : null;
            tabla.addCell(crearCeldaDetallePdf(String.valueOf(fila), Element.ALIGN_CENTER, bgFila));
            tabla.addCell(crearCeldaDetallePdf(descripcionDetalleParaPdf(detalle), Element.ALIGN_LEFT, bgFila));
            tabla.addCell(crearCeldaDetallePdf(String.valueOf(valorEntero(detalle.getCantidad())), Element.ALIGN_CENTER, bgFila));
            tabla.addCell(crearCeldaDetallePdf(formatearMonedaPdf(detalle.getPrecioUnitario()), Element.ALIGN_RIGHT, bgFila));
            BigDecimal descuento = detalle.getDescuento() != null ? detalle.getDescuento() : BigDecimal.ZERO;
            tabla.addCell(crearCeldaDetallePdf(formatearMonedaPdf(descuento), Element.ALIGN_RIGHT, bgFila));
            tabla.addCell(crearCeldaDetallePdf(
                    formatearMonedaPdf(detalle.getTotalDetalle() != null ? detalle.getTotalDetalle() : detalle.getSubtotal()),
                    Element.ALIGN_RIGHT, bgFila));
            fila++;
        }

        document.add(tabla);
    }

    private void agregarResumenComprobantePdf(Document document, Venta venta, List<Pago> pagos) throws DocumentException {
        PdfPTable totales = new PdfPTable(new float[] { 6.5f, 2.5f });
        totales.setWidthPercentage(42);
        totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totales.setSpacingBefore(6f);

        agregarFilaTotalComprobantePdf(totales, "Subtotal", formatearMonedaPdf(venta.getSubtotal()));
        if (venta.getDescuentoTotal() != null && venta.getDescuentoTotal().compareTo(BigDecimal.ZERO) > 0) {
            agregarFilaTotalComprobantePdf(totales, "Descuento", "-" + formatearMonedaPdf(venta.getDescuentoTotal()));
        }
        if (aplicaIgvSegunTipoComprobante(venta.getTipoComprobante())) {
            agregarFilaTotalComprobantePdf(totales, "IGV (" + formatearDecimalPdf(venta.getIgvPorcentaje()) + "%)",
                    formatearMonedaPdf(venta.getIgv()));
        }
        agregarFilaTotalComprobantePdf(totales, "TOTAL", formatearMonedaPdf(venta.getTotal()), true);

        document.add(totales);

        String monedaTexto = "PEN".equalsIgnoreCase(valorTexto(venta.getMoneda()).isBlank() ? "PEN" : venta.getMoneda())
                ? "SOLES" : "DOLARES AMERICANOS";
        Paragraph son = new Paragraph(
                "Son: " + montoEnLetrasConMoneda(venta.getTotal(), monedaTexto),
                fuentePdf(true, 8.5f, new Color(60, 60, 60)));
        son.setSpacingBefore(8f);
        document.add(son);
    }

    private void agregarPieComprobantePdf(Document document, Venta venta, List<Pago> pagos) throws DocumentException {
        String metodoPago = construirTextoPagosPdf(pagos);
        Color colorGris = new Color(100, 100, 100);
        boolean esElectronica = requiereComprobanteElectronico(venta.getTipoComprobante());

        if (!metodoPago.isBlank()) {
            Paragraph formaPago = new Paragraph("Forma de pago: " + metodoPago, fuentePdf(false, 8.5f));
            formaPago.setSpacingBefore(10f);
            document.add(formaPago);
        }

        PdfPTable pie = new PdfPTable(new float[] { 3.5f, 6.5f });
        pie.setWidthPercentage(100);
        pie.setSpacingBefore(14f);

        PdfPCell qrCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
        qrCell.setVerticalAlignment(Element.ALIGN_TOP);
        Image qr = generarQrComprobantePdf(venta);
        if (qr != null) {
            qr.scaleToFit(120f, 120f);
            qr.setAlignment(Element.ALIGN_LEFT);
            qrCell.addElement(qr);
        }
        pie.addCell(qrCell);

        PdfPCell infoCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
        infoCell.setVerticalAlignment(Element.ALIGN_TOP);
        infoCell.setPaddingLeft(10f);

        String hashCode = generarHashComprobantePdf(venta);
        Paragraph hashP = new Paragraph("Hash: " + hashCode, fuentePdf(false, 7.5f, colorGris));
        hashP.setSpacingBefore(2f);
        infoCell.addElement(hashP);

        if (esElectronica) {
            String tipoTexto = esFacturaPdf(venta) ? "FACTURA ELECTRONICA" : "BOLETA DE VENTA ELECTRONICA";
            Paragraph rep = new Paragraph(
                    "Representacion impresa de la " + tipoTexto,
                    fuentePdf(true, 8f, colorGris));
            rep.setSpacingBefore(6f);
            infoCell.addElement(rep);

            Paragraph consulta = new Paragraph(
                    "Puede consultar su comprobante en: https://e-factura.sunat.gob.pe/",
                    fuentePdf(false, 7f, colorGris));
            consulta.setSpacingBefore(3f);
            infoCell.addElement(consulta);
        }

        Paragraph autorizado = new Paragraph(
                "Autorizado mediante Resolucion de Intendencia",
                fuentePdf(false, 7f, colorGris));
        autorizado.setSpacingBefore(4f);
        infoCell.addElement(autorizado);

        pie.addCell(infoCell);
        document.add(pie);
    }

    private void agregarFilaDatosComprobantePdf(
            PdfPTable tabla, String label, String valor,
            String labelDer, String valorDer, Color bgColor) {
        tabla.addCell(crearCeldaDatoPdf(label, true, bgColor));
        tabla.addCell(crearCeldaDatoPdf(valor, false, bgColor));
        tabla.addCell(crearCeldaDatoPdf(labelDer, true, bgColor));
        tabla.addCell(crearCeldaDatoPdf(valorDer, false, bgColor));
    }

    private PdfPCell crearCeldaDatoPdf(String texto, boolean bold, Color bgColor) {
        PdfPCell cell = crearCeldaBase(Rectangle.NO_BORDER, 5f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        cell.addElement(new Paragraph(valorTexto(texto), fuentePdf(bold, 9.5f)));
        return cell;
    }

    private void agregarHeaderDetalle(PdfPTable tabla, String texto, int alineacion, Color bgColor) {
        PdfPCell cell = crearCeldaBase(Rectangle.BOX, 6f);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(Color.WHITE);
        cell.setHorizontalAlignment(alineacion);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setUseAscender(true);
        Paragraph paragraph = new Paragraph(texto, fuentePdf(true, 9f, Color.WHITE));
        paragraph.setAlignment(alineacion);
        cell.addElement(paragraph);
        tabla.addCell(cell);
    }

    private PdfPCell crearCeldaDetallePdf(String texto, int alineacion, Color bgColor) {
        PdfPCell cell = crearCeldaBase(Rectangle.BOX, 5f);
        cell.setBorderColor(new Color(225, 230, 236));
        cell.setHorizontalAlignment(alineacion);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setUseAscender(true);
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        Paragraph paragraph = new Paragraph(valorTexto(texto), fuentePdf(false, 9f));
        paragraph.setAlignment(alineacion);
        cell.addElement(paragraph);
        return cell;
    }

    private String etiquetaTipoDocumentoPdf(Cliente cliente) {
        if (cliente == null || cliente.getTipoDocumento() == null) {
            return "Doc.";
        }
        return switch (cliente.getTipoDocumento()) {
            case RUC -> "RUC";
            case DNI -> "DNI";
            case CE -> "C.E.";
            case SIN_DOC -> "Doc.";
        };
    }

    private boolean esFacturaPdf(Venta venta) {
        return venta.getTipoComprobante() != null
                && "FACTURA".equalsIgnoreCase(venta.getTipoComprobante().trim());
    }

    private String simboloMonedaPdf(String moneda) {
        String m = valorTexto(moneda).toUpperCase(Locale.ROOT);
        return "USD".equals(m) ? "US$" : "S/";
    }

    private String normalizarMonedaPdf(String moneda) {
        String m = valorTexto(moneda).toUpperCase(Locale.ROOT);
        return switch (m) {
            case "USD" -> "DOLAR AMERICANO";
            case "EUR" -> "EURO";
            default -> "SOL";
        };
    }

    private String construirUbicacionPdf(String distrito, String provincia, String departamento) {
        StringBuilder sb = new StringBuilder();
        if (!distrito.isBlank()) sb.append(distrito);
        if (!provincia.isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(provincia);
        }
        if (!departamento.isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(departamento);
        }
        return sb.toString();
    }

    private String formatearDecimalPdf(BigDecimal valor) {
        if (valor == null) return "0";
        BigDecimal stripped = valor.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toPlainString() : stripped.toPlainString();
    }

    private String montoEnLetrasConMoneda(BigDecimal monto, String monedaTexto) {
        BigDecimal valor = monto == null ? BigDecimal.ZERO : monto.abs().setScale(2, RoundingMode.HALF_UP);
        long entero = valor.longValue();
        int decimales = valor.remainder(BigDecimal.ONE)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return convertirNumeroALetras(entero) + " CON " + String.format(Locale.ROOT, "%02d/100", decimales) + " " + monedaTexto;
    }

    private void agregarFilaTotalComprobantePdf(PdfPTable tabla, String label, String value) {
        agregarFilaTotalComprobantePdf(tabla, label, value, false);
    }

    private void agregarFilaTotalComprobantePdf(
            PdfPTable tabla,
            String label,
            String value,
            boolean resaltar) {
        com.lowagie.text.Font font = fuentePdf(true, resaltar ? 10.5f : 9.5f);
        Color fondo = resaltar ? new Color(236, 241, 248) : null;

        PdfPCell cLabel = crearCeldaBase(Rectangle.NO_BORDER, 5f);
        cLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (fondo != null) {
            cLabel.setBackgroundColor(fondo);
        }
        Paragraph pLabel = new Paragraph(label, font);
        pLabel.setAlignment(Element.ALIGN_RIGHT);
        cLabel.addElement(pLabel);
        tabla.addCell(cLabel);

        PdfPCell cValue = crearCeldaBase(Rectangle.NO_BORDER, 5f);
        cValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (fondo != null) {
            cValue.setBackgroundColor(fondo);
        }
        Paragraph pValue = new Paragraph(value, font);
        pValue.setAlignment(Element.ALIGN_RIGHT);
        cValue.addElement(pValue);
        tabla.addCell(cValue);
    }

    private PdfPCell crearCeldaBase(int border, float padding) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(border);
        cell.setPadding(padding);
        cell.setBorderWidth(0.8f);
        return cell;
    }

    private com.lowagie.text.Font fuentePdf(boolean bold, float size) {
        return fuentePdf(bold, size, Color.BLACK);
    }

    private com.lowagie.text.Font fuentePdf(boolean bold, float size, Color color) {
        return FontFactory.getFont(
                FontFactory.HELVETICA,
                size,
                bold ? com.lowagie.text.Font.BOLD : com.lowagie.text.Font.NORMAL,
                color);
    }

    private String tituloComprobanteParaPdf(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return "COMPROBANTE";
        }
        return switch (tipoComprobante.trim().toUpperCase(Locale.ROOT)) {
            case "BOLETA" -> "BOLETA ELECTRONICA";
            case "FACTURA" -> "FACTURA ELECTRONICA";
            case "NOTA DE VENTA" -> "NOTA DE VENTA";
            default -> tipoComprobante.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String numeroComprobanteParaPdf(Venta venta) {
        return SunatComprobanteHelper.numeroComprobante(venta);
    }

    private String descripcionDetalleParaPdf(VentaDetalle detalle) {
        if (detalle.getDescripcion() != null && !detalle.getDescripcion().isBlank()) {
            return detalle.getDescripcion().trim();
        }
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
                sb.append(" - ");
            }
            sb.append(variante.getColor().getNombre().trim());
        }
        if (variante.getTalla() != null && variante.getTalla().getNombre() != null) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append("Talla ").append(variante.getTalla().getNombre().trim());
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    private String construirContactoEmpresaPdf(Sucursal sucursal) {
        if (sucursal == null) {
            return "";
        }
        return valorTexto(sucursal.getDireccion())
                + "  |  "
                + valorTexto(sucursal.getTelefono())
                + "  |  "
                + valorTexto(sucursal.getCorreo());
    }

    private String construirTextoPagosPdf(List<Pago> pagos) {
        if (pagos == null || pagos.isEmpty()) {
            return "";
        }
        return pagos.stream()
                .map(pago -> pago.getMetodoPago() != null ? valorTexto(pago.getMetodoPago().getNombre()) : "N/D")
                .distinct()
                .reduce((a, b) -> a + " / " + b)
                .orElse("");
    }

    private String construirCodigosOperacionPagoPdf(List<Pago> pagos) {
        if (pagos == null || pagos.isEmpty()) {
            return "";
        }
        return pagos.stream()
                .map(Pago::getCodigoOperacion)
                .filter(codigo -> codigo != null && !codigo.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
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

        ImageIO.scanForPlugins();

        try (InputStream stream = URI.create(logoUrl).toURL().openStream()) {
            BufferedImage buffered = ImageIO.read(stream);
            if (buffered != null) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(buffered, "png", png);
                Image image = Image.getInstance(png.toByteArray());
                image.setAlignment(Element.ALIGN_LEFT);
                return image;
            }
        } catch (Exception ex) {
            return null;
        }

        return null;
    }

    private Image generarQrComprobantePdf(Venta venta) {
        try {
            String contenido = construirContenidoQrPdf(venta);
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter()
                    .encode(contenido, BarcodeFormat.QR_CODE, 180, 180, hints);
            BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(buffered, "png", out);
            return Image.getInstance(out.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    private String construirContenidoQrPdf(Venta venta) {
        return SunatComprobanteHelper.construirContenidoQr(venta);
    }

    private String generarHashComprobantePdf(Venta venta) {
        if (venta.getSunatHash() != null && !venta.getSunatHash().isBlank()) {
            return venta.getSunatHash();
        }
        return SunatComprobanteHelper.generarHashBase64(construirContenidoQrPdf(venta));
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
        String tipoComprobante = normalizarTipoComprobante(request.tipoComprobante());
        boolean aplicaIgv = aplicaIgvSegunTipoComprobante(tipoComprobante);
        Cliente cliente = resolverCliente(request.idCliente(), sucursalVenta.getIdSucursal());
        validarClienteParaComprobanteElectronico(tipoComprobante, cliente);
        validarEmpresaParaComprobanteElectronico(tipoComprobante, sucursalVenta);

        List<DetalleCalculado> detallesCalculados = calcularDetalles(
                request.detalles(),
                sucursalVenta.getIdSucursal(),
                aplicaIgv);
        TotalesVenta totales = calcularTotales(
                detallesCalculados,
                request.descuentoTotal(),
                request.tipoDescuento(),
                request.igvPorcentaje(),
                aplicaIgv);
        List<DetalleCalculado> detallesFinales = aplicarTributosDetalle(detallesCalculados, totales);
        List<PagoCalculado> pagosCalculados = calcularPagos(request.pagos(), totales.total());
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
        venta.setMoneda(normalizarMoneda(request.moneda()));
        venta.setFormaPago(normalizarFormaPago(request.formaPago()));
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
        for (DetalleCalculado detalleCalculado : detallesFinales) {
            VentaDetalle detalle = new VentaDetalle();
            detalle.setVenta(ventaGuardada);
            detalle.setProductoVariante(detalleCalculado.variante());
            detalle.setDescripcion(detalleCalculado.descripcion());
            detalle.setCantidad(detalleCalculado.cantidad());
            detalle.setUnidadMedida(detalleCalculado.unidadMedida());
            detalle.setCodigoTipoAfectacionIgv(detalleCalculado.codigoTipoAfectacionIgv());
            detalle.setPrecioUnitario(detalleCalculado.precioUnitario());
            detalle.setDescuento(detalleCalculado.descuento());
            detalle.setIgvDetalle(detalleCalculado.igvDetalle());
            detalle.setSubtotal(detalleCalculado.subtotal());
            detalle.setTotalDetalle(detalleCalculado.totalDetalle());
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
            pago.setCodigoOperacion(pagoCalculado.codigoOperacion());
            pago.setActivo("ACTIVO");
            pagosGuardar.add(pago);
        }
        List<Pago> pagosGuardados = pagoRepository.saveAll(pagosGuardar);

        List<HistorialStock> historial = new ArrayList<>();
        for (DetalleCalculado detalleCalculado : detallesFinales) {
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
        productoVarianteRepository.saveAll(detallesFinales.stream().map(DetalleCalculado::variante).toList());
        historialStockRepository.saveAll(historial);

        if (requiereComprobanteElectronico(tipoComprobante)) {
            applicationEventPublisher.publishEvent(new VentaRegistradaEvent(ventaGuardada.getIdVenta()));
        }

        return toResponse(ventaGuardada, detallesGuardados, pagosGuardados);
    }

    @Transactional
    public void procesarEmisionElectronica(Integer idVenta) {
        Venta venta = ventaRepository.findByIdVentaAndDeletedAtIsNull(idVenta)
                .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));

        if (!requiereComprobanteElectronico(venta.getTipoComprobante())) {
            if (venta.getSunatEstado() != SunatEstado.NO_APLICA) {
                venta.setSunatEstado(SunatEstado.NO_APLICA);
                venta.setSunatMensaje("La venta no requiere emision electronica SUNAT.");
                ventaRepository.save(venta);
            }
            return;
        }

        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());

        SunatEmissionResult resultado;
        try {
            resultado = sunatEmissionService.emitir(venta, detalles);
        } catch (RuntimeException e) {
            resultado = new SunatEmissionResult(
                    SunatEstado.ERROR,
                    "EXCEPTION",
                    e.getMessage() == null ? "Ocurrio un error al emitir el comprobante en SUNAT" : e.getMessage(),
                    venta.getSunatHash(),
                    venta.getSunatTicket(),
                    venta.getSunatXmlNombre(),
                    venta.getSunatXmlKey(),
                    venta.getSunatZipNombre(),
                    venta.getSunatZipKey(),
                    venta.getSunatCdrNombre(),
                    venta.getSunatCdrKey(),
                    venta.getSunatEnviadoAt(),
                    LocalDateTime.now());
        }
        aplicarResultadoSunat(venta, resultado);
        ventaRepository.save(venta);
    }

    public VentaResponse reintentarEmisionSunat(Integer idVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolVenta(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        if (!requiereComprobanteElectronico(venta.getTipoComprobante())) {
            throw new RuntimeException("La venta no requiere emision electronica SUNAT");
        }

        procesarEmisionElectronica(venta.getIdVenta());

        Venta ventaActualizada = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(ventaActualizada.getIdVenta());
        List<Pago> pagos = pagoRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(ventaActualizada.getIdVenta());

        return toResponse(ventaActualizada, detalles, pagos);
    }

    private List<DetalleCalculado> calcularDetalles(
            List<VentaDetalleCreateItem> detalles,
            Integer idSucursalVenta,
            boolean aplicaIgv) {
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

            String nombreProducto = descripcionDetalleVenta(null, variante);

            if (!"ACTIVO".equalsIgnoreCase(variante.getEstado())) {
                throw new RuntimeException("El producto '" + nombreProducto + "' no esta disponible");
            }

            int cantidad = item.cantidad();
            int stockActual = valorEntero(variante.getStock());
            if (stockActual < cantidad) {
                throw new RuntimeException("Stock insuficiente para '" + nombreProducto
                        + "'. Disponible: " + stockActual + ", solicitado: " + cantidad);
            }

            BigDecimal precioUnitario = item.precioUnitario() == null
                    ? decimalDesdeDouble(precioVigenteVariante(variante))
                    : decimalPositivo(item.precioUnitario(), "precioUnitario");
                BigDecimal descuentoItem = item.descuento() == null
                    ? BigDecimal.ZERO
                    : decimalNoNegativo(item.descuento(), "descuento");
                if (descuentoItem.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException("No se permite descuento por item; use descuentoTotal global");
                }
                BigDecimal descuento = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal totalLinea = precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
            if (descuento.compareTo(totalLinea) > 0) {
                throw new RuntimeException("El descuento no puede superar el total para '"
                        + nombreProducto + "'");
            }
            BigDecimal subtotal = totalLinea.subtract(descuento).setScale(2, RoundingMode.HALF_UP);
            String descripcion = descripcionDetalleVenta(item.descripcion(), variante);
            String unidadMedida = normalizarUnidadMedida(item.unidadMedida());
            String codigoTipoAfectacionIgv = normalizarCodigoTipoAfectacionIgv(
                    item.codigoTipoAfectacionIgv(),
                    aplicaIgv);

            calculados.add(new DetalleCalculado(
                    variante,
                    descripcion,
                    cantidad,
                    unidadMedida,
                    codigoTipoAfectacionIgv,
                    precioUnitario,
                    descuento,
                    subtotal,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    subtotal));
        }

        return calculados;
    }

    private List<DetalleCalculado> aplicarTributosDetalle(List<DetalleCalculado> detalles, TotalesVenta totales) {
        BigDecimal subtotalOriginal = detalles.stream()
            .map(DetalleCalculado::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal descuentoGlobalPendiente = moneda(totales.descuentoAplicado());
        BigDecimal subtotalNetoPendiente = moneda(totales.subtotal());
        BigDecimal igvPendiente = moneda(totales.igv());
        BigDecimal totalPendiente = moneda(totales.total());
        List<DetalleCalculado> resultado = new ArrayList<>();

        for (int i = 0; i < detalles.size(); i++) {
            DetalleCalculado detalle = detalles.get(i);
            BigDecimal descuentoGlobalAsignado;

            if (i == detalles.size() - 1) {
            descuentoGlobalAsignado = descuentoGlobalPendiente;
            } else if (subtotalOriginal.compareTo(BigDecimal.ZERO) == 0) {
            descuentoGlobalAsignado = CERO_MONETARIO;
            } else {
            descuentoGlobalAsignado = detalle.subtotal()
                .multiply(totales.descuentoAplicado())
                .divide(subtotalOriginal, 2, RoundingMode.HALF_UP);
            if (descuentoGlobalAsignado.compareTo(descuentoGlobalPendiente) > 0) {
                descuentoGlobalAsignado = descuentoGlobalPendiente;
            }
            }

            descuentoGlobalPendiente = descuentoGlobalPendiente.subtract(descuentoGlobalAsignado)
                .setScale(2, RoundingMode.HALF_UP);

            BigDecimal descuentoLineaTotal = detalle.descuento()
                .add(descuentoGlobalAsignado)
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalDetalle;
            BigDecimal subtotalNeto;
            BigDecimal igvDetalle;

            if (i == detalles.size() - 1) {
            totalDetalle = totalPendiente;
            subtotalNeto = subtotalNetoPendiente;
            igvDetalle = igvPendiente;
            } else {
            totalDetalle = detalle.subtotal()
                .subtract(descuentoGlobalAsignado)
                .setScale(2, RoundingMode.HALF_UP);
            subtotalNeto = calcularBaseSinIgv(
                totalDetalle,
                totales.igvPorcentaje(),
                detalle.codigoTipoAfectacionIgv());
            igvDetalle = totalDetalle.subtract(subtotalNeto).setScale(2, RoundingMode.HALF_UP);

            totalPendiente = totalPendiente.subtract(totalDetalle).setScale(2, RoundingMode.HALF_UP);
            subtotalNetoPendiente = subtotalNetoPendiente.subtract(subtotalNeto).setScale(2, RoundingMode.HALF_UP);
            igvPendiente = igvPendiente.subtract(igvDetalle).setScale(2, RoundingMode.HALF_UP);
            }

            resultado.add(new DetalleCalculado(
                    detalle.variante(),
                    detalle.descripcion(),
                    detalle.cantidad(),
                    detalle.unidadMedida(),
                    detalle.codigoTipoAfectacionIgv(),
                    detalle.precioUnitario(),
                descuentoLineaTotal,
                    subtotalNeto,
                    igvDetalle,
                    totalDetalle));
        }

        return resultado;
    }

    private TotalesVenta calcularTotales(
            List<DetalleCalculado> detalles,
            Double descuentoTotalInput,
            String tipoDescuentoInput,
            Double igvPorcentajeInput,
            boolean aplicaIgv) {
        BigDecimal totalBase = detalles.stream()
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
                descuentoAplicado = totalBase
                        .multiply(descuentoInput)
                        .divide(CIEN, 2, RoundingMode.HALF_UP);
            }
        }

        if (descuentoAplicado.compareTo(totalBase) > 0) {
            throw new RuntimeException("El descuento total no puede superar el total base de la venta");
        }

        BigDecimal igvPorcentaje = aplicaIgv ? normalizarIgv(igvPorcentajeInput) : CERO_MONETARIO;
        BigDecimal totalConDescuento = totalBase.subtract(descuentoAplicado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = aplicaIgv
                ? calcularBaseSinIgv(totalConDescuento, igvPorcentaje, CODIGO_IGV_GRAVADO)
                : totalConDescuento;
        BigDecimal igv = aplicaIgv
                ? totalConDescuento.subtract(subtotal).setScale(2, RoundingMode.HALF_UP)
                : CERO_MONETARIO;
        BigDecimal total = totalConDescuento;

        return new TotalesVenta(
                igvPorcentaje,
                subtotal,
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
            String codigoOperacion = normalizarTexto(item.codigoOperacion(), 100);
            sumaPagos = sumaPagos.add(monto).setScale(2, RoundingMode.HALF_UP);

            calculados.add(new PagoCalculado(metodoPago, monto, codigoOperacion));
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

    private void validarClienteParaComprobanteElectronico(String tipoComprobante, Cliente cliente) {
        if (!requiereComprobanteElectronico(tipoComprobante)) {
            return;
        }
        if (cliente == null) {
            throw new RuntimeException("Cliente obligatorio para emitir " + tipoComprobante + " electronica");
        }
        if (cliente.getNombres() == null || cliente.getNombres().isBlank()) {
            throw new RuntimeException("El cliente debe tener nombres para emitir " + tipoComprobante + " electronica");
        }

        TipoDocumento tipoDocumento = cliente.getTipoDocumento();
        String nroDocumento = normalizarTexto(cliente.getNroDocumento(), 20);
        if ("FACTURA".equals(tipoComprobante)) {
            if (tipoDocumento != TipoDocumento.RUC) {
                throw new RuntimeException("La FACTURA electronica requiere un cliente con RUC");
            }
            if (nroDocumento == null || nroDocumento.length() != 11) {
                throw new RuntimeException("La FACTURA electronica requiere RUC de 11 digitos");
            }
            return;
        }

        if (tipoDocumento != null
                && tipoDocumento != TipoDocumento.SIN_DOC
                && (nroDocumento == null || nroDocumento.isBlank())) {
            throw new RuntimeException("La BOLETA electronica requiere numero de documento valido para el cliente");
        }
    }

    private void validarEmpresaParaComprobanteElectronico(String tipoComprobante, Sucursal sucursal) {
        if (!requiereComprobanteElectronico(tipoComprobante)) {
            return;
        }
        if (sucursal == null || sucursal.getEmpresa() == null) {
            throw new RuntimeException("La sucursal debe estar vinculada a una empresa para emitir comprobantes SUNAT");
        }
        String rucEmpresa = normalizarTexto(sucursal.getEmpresa().getRuc(), 11);
        if (rucEmpresa == null || rucEmpresa.length() != 11) {
            throw new RuntimeException("La empresa debe tener RUC de 11 digitos para emitir comprobantes SUNAT");
        }
        if (normalizarTexto(sucursal.getEmpresa().getRazonSocial(), 150) == null) {
            throw new RuntimeException("La empresa debe tener razon social para emitir comprobantes SUNAT");
        }
    }

    private boolean requiereComprobanteElectronico(String tipoComprobante) {
        return "BOLETA".equals(tipoComprobante) || "FACTURA".equals(tipoComprobante);
    }

    private boolean aplicaIgvSegunTipoComprobante(String tipoComprobante) {
        return requiereComprobanteElectronico(normalizarAliasTipoComprobante(tipoComprobante));
    }

    private BigDecimal calcularBaseSinIgv(BigDecimal totalConIgv, BigDecimal igvPorcentaje, String codigoTipoAfectacionIgv) {
        BigDecimal total = moneda(totalConIgv);
        if (!afectaIgv(codigoTipoAfectacionIgv) || total.compareTo(BigDecimal.ZERO) == 0) {
            return total;
        }
        BigDecimal factor = BigDecimal.ONE.add(moneda(igvPorcentaje).divide(CIEN, 10, RoundingMode.HALF_UP));
        return total.divide(factor, 2, RoundingMode.HALF_UP);
    }

    private boolean afectaIgv(String codigoTipoAfectacionIgv) {
        if (codigoTipoAfectacionIgv == null || codigoTipoAfectacionIgv.isBlank()) {
            return true;
        }
        return codigoTipoAfectacionIgv.startsWith("1");
    }

    private void aplicarResultadoSunat(Venta venta, SunatEmissionResult resultado) {
        if (resultado == null) {
            venta.setSunatEstado(SunatEstado.ERROR);
            venta.setSunatCodigo("NULL");
            venta.setSunatMensaje("SUNAT no devolvio respuesta.");
            venta.setSunatRespondidoAt(LocalDateTime.now());
            return;
        }

        venta.setSunatEstado(resultado.estado());
        venta.setSunatCodigo(normalizarTexto(resultado.codigo(), 20));
        venta.setSunatMensaje(normalizarTexto(resultado.mensaje(), 500));
        venta.setSunatHash(normalizarTexto(resultado.hash(), 120));
        venta.setSunatTicket(normalizarTexto(resultado.ticket(), 120));
        venta.setSunatXmlNombre(normalizarTexto(resultado.xmlNombre(), 180));
        venta.setSunatXmlKey(normalizarTexto(resultado.xmlKey(), 600));
        venta.setSunatZipNombre(normalizarTexto(resultado.zipNombre(), 180));
        venta.setSunatZipKey(normalizarTexto(resultado.zipKey(), 600));
        venta.setSunatCdrNombre(normalizarTexto(resultado.cdrNombre(), 180));
        venta.setSunatCdrKey(normalizarTexto(resultado.cdrKey(), 600));
        venta.setSunatEnviadoAt(resultado.fechaEnvio());
        venta.setSunatRespondidoAt(resultado.fechaRespuesta());
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
                venta.getMoneda(),
                venta.getTotal(),
                venta.getEstado(),
                venta.getSunatEstado(),
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
                venta.getMoneda(),
                venta.getFormaPago(),
                venta.getIgvPorcentaje(),
                venta.getSubtotal(),
                venta.getDescuentoTotal(),
                venta.getTipoDescuento(),
                venta.getIgv(),
                venta.getTotal(),
                venta.getEstado(),
                venta.getSunatEstado(),
                venta.getSunatCodigo(),
                venta.getSunatMensaje(),
                venta.getSunatHash(),
                venta.getSunatTicket(),
                venta.getSunatXmlNombre(),
                venta.getSunatZipNombre(),
                venta.getSunatCdrNombre(),
                venta.getSunatEnviadoAt(),
                venta.getSunatRespondidoAt(),
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
                detalle.getDescripcion(),
                variante != null ? variante.getSku() : null,
                variante != null ? variante.getPrecioOferta() : null,
                variante != null ? variante.getOfertaInicio() : null,
                variante != null ? variante.getOfertaFin() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getIdColor() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getIdTalla() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                detalle.getCantidad(),
                detalle.getUnidadMedida(),
                detalle.getCodigoTipoAfectacionIgv(),
                detalle.getPrecioUnitario(),
                detalle.getDescuento(),
                detalle.getIgvDetalle(),
                detalle.getSubtotal(),
                detalle.getTotalDetalle());
    }

    private VentaPagoResponse toPagoResponse(Pago pago) {
        MetodoPagoConfig metodo = pago.getMetodoPago();
        return new VentaPagoResponse(
                pago.getIdPago(),
                metodo != null ? metodo.getIdMetodoPago() : null,
                metodo != null ? metodo.getNombre() : null,
                pago.getMonto(),
                pago.getCodigoOperacion(),
                pago.getFecha());
    }

    private FiltroReporteVentas resolverFiltroReporteVentas(
            Usuario usuarioAutenticado,
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursalRequest,
            Integer idClienteRequest) {
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

        Integer idClienteFiltro = resolverIdClienteFiltro(usuarioAutenticado, idClienteRequest, idSucursalFiltro);

        return new FiltroReporteVentas(
                agrupacion,
                periodoFiltro,
                rango.desde(),
                rango.hasta(),
                idSucursalFiltro,
                nombreSucursalFiltro,
                idClienteFiltro);
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
        String estadoFiltro = incluirAnuladas ? null : "EMITIDA";
        return ventaRepository.buscarParaReporte(
                filtro.idSucursal(),
                filtro.idCliente(),
                estadoFiltro,
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
            String telefonoCliente = venta.getCliente() != null && venta.getCliente().getTelefono() != null && !venta.getCliente().getTelefono().isBlank()
                    ? " - " + venta.getCliente().getTelefono() : "";
            String nombreCliente = (venta.getCliente() != null ? venta.getCliente().getNombres() : "SIN CLIENTE") + telefonoCliente;
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

            construirHojaDetalle(workbook, reporte, detallesPorVenta, pagosPorVenta, headerStyle, moneyStyle);
            construirHojaDetalleItems(workbook, reporte, detallesPorVenta, headerStyle, moneyStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el Excel del reporte de ventas");
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
                "Estado",
                "Cliente",
                "Producto",
                "Color",
                "Talla",
                "Cantidad",
                "PrecioUnitario",
                "Total"
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
                r.createCell(4).setCellValue(variante != null && variante.getProducto() != null
                        ? valorTexto(variante.getProducto().getNombre())
                        : valorTexto(detalle.getDescripcion()));
                r.createCell(5).setCellValue(variante != null && variante.getColor() != null
                        ? valorTexto(variante.getColor().getNombre())
                        : "");
                r.createCell(6).setCellValue(variante != null && variante.getTalla() != null
                        ? valorTexto(variante.getTalla().getNombre())
                        : "");
                r.createCell(7).setCellValue(detalle.getCantidad() == null ? 0 : detalle.getCantidad());
                r.createCell(8).setCellValue(moneda(detalle.getPrecioUnitario()).doubleValue());
                BigDecimal totalLinea = detalle.getTotalDetalle() != null
                        ? detalle.getTotalDetalle()
                        : moneda(detalle.getSubtotal());
                r.createCell(9).setCellValue(moneda(totalLinea).doubleValue());
                r.getCell(8).setCellStyle(moneyStyle);
                r.getCell(9).setCellStyle(moneyStyle);
                totalItems = totalItems.add(moneda(totalLinea)).setScale(2, RoundingMode.HALF_UP);
            }
        }

        Row total = sheet.createRow(rowIdx);
        total.createCell(0).setCellValue("TOTAL");
        total.createCell(9).setCellValue(totalItems.doubleValue());
        total.getCell(9).setCellStyle(moneyStyle);

        for (int i = 0; i <= 9; i++) {
            sheet.autoSizeColumn(i);
        }
    }



    // ─── PDF Reporte de Ventas ────────────────────────────────────────────────

    private static final Color[] COLORES_COMPROBANTE_PDF = {
            new Color(232, 240, 254),  // celeste claro
            null                       // sin color
    };

    private byte[] construirPdfReporteVentas(
            VentaReporteResponse reporte,
            Map<Integer, List<VentaDetalle>> detallesPorVenta) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 30f, 30f, 30f, 30f);
            PdfWriter.getInstance(document, output);
            document.open();

            agregarEncabezadoReportePdf(document, reporte);
            document.add(new Paragraph(" "));
            agregarTablaItemsReportePdf(document, reporte, detallesPorVenta);
            agregarPieTotalReportePdf(document, reporte);

            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("No se pudo generar el PDF del reporte de ventas");
        }
    }

    private void agregarEncabezadoReportePdf(Document document, VentaReporteResponse reporte)
            throws DocumentException {
        Color colorPrimario = new Color(60, 76, 102);

        Paragraph titulo = new Paragraph("REPORTE DE VENTAS", fuentePdf(true, 16f, colorPrimario));
        titulo.setAlignment(Element.ALIGN_CENTER);
        document.add(titulo);

        String sucursalTexto = reporte.nombreSucursal() != null && !reporte.nombreSucursal().isBlank()
                ? reporte.nombreSucursal() : "TODAS";
        Paragraph sucursal = new Paragraph("Sucursal: " + sucursalTexto, fuentePdf(true, 11f));
        sucursal.setAlignment(Element.ALIGN_CENTER);
        sucursal.setSpacingBefore(4f);
        document.add(sucursal);

        String rango = (reporte.desde() != null ? reporte.desde().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-")
                + " al "
                + (reporte.hasta() != null ? reporte.hasta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-");
        Paragraph fechas = new Paragraph("Periodo: " + rango, fuentePdf(false, 9.5f));
        fechas.setAlignment(Element.ALIGN_CENTER);
        fechas.setSpacingBefore(2f);
        document.add(fechas);

        String generado = "Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Paragraph gen = new Paragraph(generado, fuentePdf(false, 8.5f, new Color(120, 120, 120)));
        gen.setAlignment(Element.ALIGN_CENTER);
        gen.setSpacingBefore(2f);
        document.add(gen);
    }

    private void agregarTablaItemsReportePdf(
            Document document,
            VentaReporteResponse reporte,
            Map<Integer, List<VentaDetalle>> detallesPorVenta) throws DocumentException {
        Color colorHeaderBg = new Color(60, 76, 102);

        // columnas: Comprobante | FechaVenta | Estado | Cliente | Producto | Color | Talla | Cantidad | P.Unit | Total
        PdfPTable tabla = new PdfPTable(new float[] { 2.8f, 2.2f, 1.6f, 2.8f, 3.2f, 1.5f, 1.2f, 1.2f, 1.6f, 1.6f });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        tabla.setSpacingBefore(6f);

        String[] headers = { "Comprobante", "Fecha Venta", "Estado", "Cliente",
                "Producto", "Color", "Talla", "Cant.", "P. Unit.", "Total" };
        int[] aligns = { Element.ALIGN_LEFT, Element.ALIGN_CENTER, Element.ALIGN_CENTER,
                Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_CENTER,
                Element.ALIGN_CENTER, Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT };
        for (int i = 0; i < headers.length; i++) {
            agregarHeaderDetalle(tabla, headers[i], aligns[i], colorHeaderBg);
        }

        int colorIdx = 0;
        Map<Integer, Integer> colorPorVenta = new java.util.LinkedHashMap<>();

        // Primero asignar colores por comprobante (en orden de aparición)
        for (VentaReporteResponse.DetalleItem ventaItem : reporte.detalleVentas()) {
            Integer idVenta = ventaItem.idVenta();
            if (idVenta != null && !colorPorVenta.containsKey(idVenta)) {
                colorPorVenta.put(idVenta, colorIdx % COLORES_COMPROBANTE_PDF.length);
                colorIdx++;
            }
        }

        DateTimeFormatter fmtFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (VentaReporteResponse.DetalleItem ventaItem : reporte.detalleVentas()) {
            Integer idVenta = ventaItem.idVenta();
            if (idVenta == null) continue;

            List<VentaDetalle> itemsVenta = detallesPorVenta.getOrDefault(idVenta, List.of());
            int colorIndex = colorPorVenta.getOrDefault(idVenta, 0);
            Color bgFila = COLORES_COMPROBANTE_PDF[colorIndex];

            for (VentaDetalle detalle : itemsVenta) {
                ProductoVariante variante = detalle.getProductoVariante();

                String comprobante = comprobanteTexto(ventaItem);
                String fechaVenta = ventaItem.fecha() != null ? ventaItem.fecha().format(fmtFecha) : "-";
                String estado = valorTexto(ventaItem.estado());
                String cliente = valorTexto(ventaItem.nombreCliente());
                String producto = variante != null && variante.getProducto() != null
                        ? valorTexto(variante.getProducto().getNombre())
                        : valorTexto(detalle.getDescripcion());
                String color = variante != null && variante.getColor() != null
                        ? valorTexto(variante.getColor().getNombre()) : "-";
                String talla = variante != null && variante.getTalla() != null
                        ? valorTexto(variante.getTalla().getNombre()) : "-";
                String cantidad = String.valueOf(detalle.getCantidad() == null ? 0 : detalle.getCantidad());
                String precioUnit = formatearMonedaPdf(detalle.getPrecioUnitario());
                BigDecimal totalLinea = detalle.getTotalDetalle() != null
                        ? detalle.getTotalDetalle() : detalle.getSubtotal();
                String totalStr = formatearMonedaPdf(totalLinea);

                tabla.addCell(crearCeldaReportePdf(comprobante, Element.ALIGN_LEFT, bgFila));
                tabla.addCell(crearCeldaReportePdf(fechaVenta, Element.ALIGN_CENTER, bgFila));
                tabla.addCell(crearCeldaReportePdf(estado, Element.ALIGN_CENTER, bgFila));
                tabla.addCell(crearCeldaReportePdf(cliente, Element.ALIGN_LEFT, bgFila));
                tabla.addCell(crearCeldaReportePdf(producto, Element.ALIGN_LEFT, bgFila));
                tabla.addCell(crearCeldaReportePdf(color, Element.ALIGN_CENTER, bgFila));
                tabla.addCell(crearCeldaReportePdf(talla, Element.ALIGN_CENTER, bgFila));
                tabla.addCell(crearCeldaReportePdf(cantidad, Element.ALIGN_CENTER, bgFila));
                tabla.addCell(crearCeldaReportePdf(precioUnit, Element.ALIGN_RIGHT, bgFila));
                tabla.addCell(crearCeldaReportePdf(totalStr, Element.ALIGN_RIGHT, bgFila));
            }
        }

        document.add(tabla);
    }

    private void agregarPieTotalReportePdf(Document document, VentaReporteResponse reporte)
            throws DocumentException {
        Color colorPrimario = new Color(60, 76, 102);
        String totalFormateado = formatearMonedaPdf(reporte.montoTotal());
        String ventas = String.valueOf(reporte.cantidadVentas());

        Paragraph p = new Paragraph(
                String.format("Total de Ventas: S/ %s   |   Cantidad de Comprobantes: %s", totalFormateado, ventas),
                fuentePdf(true, 10.5f, colorPrimario));
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingBefore(10f);
        document.add(p);
    }

    private PdfPCell crearCeldaReportePdf(String texto, int alineacion, Color bgColor) {
        PdfPCell cell = crearCeldaBase(Rectangle.BOX, 4.5f);
        cell.setBorderColor(new Color(200, 210, 225));
        cell.setHorizontalAlignment(alineacion);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setUseAscender(true);
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        Paragraph paragraph = new Paragraph(valorTexto(texto), fuentePdf(false, 8.5f));
        paragraph.setAlignment(alineacion);
        cell.addElement(paragraph);
        return cell;
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
        String normalized = normalizarAliasTipoComprobante(tipoComprobante);
        validarTipoComprobantePermitido(normalized);
        return normalized;
    }

    private String normalizarAliasTipoComprobante(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return null;
        }
        String normalized = tipoComprobante.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TICKET", "TICKET DE VENTA" -> "NOTA DE VENTA";
            default -> normalized;
        };
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

    private String normalizarMoneda(String moneda) {
        if (moneda == null || moneda.isBlank()) {
            return "PEN";
        }
        String normalized = moneda.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new RuntimeException("moneda debe tener 3 caracteres");
        }
        return normalized;
    }

    private String normalizarFormaPago(String formaPago) {
        if (formaPago == null || formaPago.isBlank()) {
            return "CONTADO";
        }
        String normalized = formaPago.trim().toUpperCase(Locale.ROOT);
        if ("CREDITO".equals(normalized) || "CRÉDITO".equals(normalized)) {
            return "CREDITO";
        }
        return "CONTADO";
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
        if (precioOferta == null || precio == null || precioOferta <= 0 || precioOferta >= precio) {
            return precio;
        }
        LocalDateTime ofertaInicio = variante.getOfertaInicio();
        LocalDateTime ofertaFin = variante.getOfertaFin();
        if (ofertaInicio == null && ofertaFin == null) {
            return precioOferta;
        }
        if (ofertaInicio == null || ofertaFin == null) {
            return precio;
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (ahora.isBefore(ofertaInicio) || ahora.isAfter(ofertaFin)) {
            return precio;
        }
        return precioOferta;
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

    private String normalizarUnidadMedida(String unidadMedida) {
        if (unidadMedida == null || unidadMedida.isBlank()) {
            return "NIU";
        }
        String normalized = unidadMedida.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new RuntimeException("unidadMedida debe tener 3 caracteres");
        }
        return normalized;
    }

    private String normalizarCodigoTipoAfectacionIgv(String codigo, boolean aplicaIgv) {
        if (!aplicaIgv) {
            return CODIGO_IGV_INAFECTO;
        }
        if (codigo == null || codigo.isBlank()) {
            return CODIGO_IGV_GRAVADO;
        }
        String normalized = codigo.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 2) {
            throw new RuntimeException("codigoTipoAfectacionIgv debe tener 2 caracteres");
        }
        return normalized;
    }

    private String descripcionDetalleVenta(String descripcionRequest, ProductoVariante variante) {
        String descripcion = normalizarTexto(descripcionRequest, 255);
        if (descripcion != null) {
            return descripcion;
        }

        if (variante == null) {
            return "-";
        }

        StringBuilder sb = new StringBuilder();
        if (variante.getProducto() != null && variante.getProducto().getNombre() != null) {
            sb.append(variante.getProducto().getNombre().trim());
        }
        if (variante.getColor() != null && variante.getColor().getNombre() != null) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(variante.getColor().getNombre().trim());
        }
        if (variante.getTalla() != null && variante.getTalla().getNombre() != null) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append("Talla ").append(variante.getTalla().getNombre().trim());
        }
        return sb.isEmpty() ? "-" : normalizarTexto(sb.toString(), 255);
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

    private Integer resolverIdUsuarioListado(Usuario usuarioAutenticado, Integer idUsuarioRequest) {
        Integer idUsuarioFiltro = normalizarIdUsuarioFiltro(idUsuarioRequest);
        if (!esVentas(usuarioAutenticado)) {
            return idUsuarioFiltro;
        }

        Integer idUsuarioAutenticado = usuarioAutenticado.getIdUsuario();
        if (idUsuarioAutenticado == null || idUsuarioAutenticado <= 0) {
            throw new RuntimeException("El usuario autenticado no tiene identificador valido");
        }
        if (idUsuarioFiltro != null && !idUsuarioAutenticado.equals(idUsuarioFiltro)) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para filtrar por otro usuario");
        }
        return idUsuarioAutenticado;
    }

    private Integer resolverIdClienteFiltro(
            Usuario usuarioAutenticado,
            Integer idClienteRequest,
            Integer idSucursalFiltro) {
        if (idClienteRequest == null) {
            return null;
        }
        if (idClienteRequest <= 0) {
            throw new RuntimeException("idCliente debe ser mayor a 0");
        }

        if (esAdministrador(usuarioAutenticado) && idSucursalFiltro == null) {
            return clienteRepository.findByIdClienteAndDeletedAtIsNull(idClienteRequest)
                    .map(Cliente::getIdCliente)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        }

        Integer idSucursalCliente = idSucursalFiltro != null
                ? idSucursalFiltro
                : obtenerIdSucursalUsuario(usuarioAutenticado);
        return clienteRepository
                .findByIdClienteAndDeletedAtIsNullAndSucursal_IdSucursal(idClienteRequest, idSucursalCliente)
                .map(Cliente::getIdCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
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
        String normalized = normalizarAliasTipoComprobante(tipoComprobante);
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
                && usuario.getRol() != Rol.VENTAS) {
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

    private boolean esVentas(Usuario usuario) {
        return usuario.getRol() == Rol.VENTAS;
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
            String nombreSucursal,
            Integer idCliente) {
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
            String descripcion,
            int cantidad,
            String unidadMedida,
            String codigoTipoAfectacionIgv,
            BigDecimal precioUnitario,
            BigDecimal descuento,
            BigDecimal subtotal,
            BigDecimal igvDetalle,
            BigDecimal totalDetalle) {
    }

    private record PagoCalculado(
            MetodoPagoConfig metodoPago,
            BigDecimal monto,
            String codigoOperacion) {
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

    public record ArchivoDescargable(
            String nombreArchivo,
            String contentType,
            byte[] bytes) {
    }
}
