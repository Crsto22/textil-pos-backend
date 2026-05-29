package com.sistemapos.sistematextil.services;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import org.springframework.stereotype.Service;

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
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Pago;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.pago.PagoActualizarCodigoRequest;
import com.sistemapos.sistematextil.util.pago.PagoListItemResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PagoService {

    private static final DateTimeFormatter FECHA_HORA_PDF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA_PDF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA_EXCEL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Set<String> ESTADOS_VENTA_PERMITIDOS = Set.of("EMITIDA", "ANULADA", "NC_EMITIDA");

    private final PagoRepository pagoRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final S3StorageService s3StorageService;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<PagoListItemResponse> listarPaginado(
            String term,
            Integer idVenta,
            Integer idUsuario,
            Integer idMetodoPago,
            Integer idSucursal,
            String estadoVenta,
            LocalDate desde,
            LocalDate hasta,
            int page,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        PagoFiltros filtros = prepararFiltros(
                usuarioAutenticado,
                term,
                idVenta,
                idUsuario,
                idMetodoPago,
                idSucursal,
                estadoVenta,
                desde,
                hasta);

        int pageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        PageRequest pageable = PageRequest.of(
                page,
                pageSize,
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("idPago")));

        Page<Pago> pagos = pagoRepository.buscarConFiltros(
                filtros.term(),
                filtros.idVenta(),
                filtros.idUsuario(),
                filtros.idMetodoPago(),
                filtros.idSucursal(),
                filtros.estadoVenta(),
                filtros.fechaInicio(),
                filtros.fechaFinExclusive(),
                pageable);

        return PagedResponse.fromPage(pagos.map(this::toListItemResponse));
    }

    public byte[] generarReportePagosPdf(
            String term,
            Integer idVenta,
            Integer idUsuario,
            Integer idMetodoPago,
            Integer idSucursal,
            String estadoVenta,
            LocalDate desde,
            LocalDate hasta,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        PagoFiltros filtros = prepararFiltros(
                usuarioAutenticado,
                term,
                idVenta,
                idUsuario,
                idMetodoPago,
                idSucursal,
                estadoVenta,
                desde,
                hasta);

        List<Pago> pagos = pagoRepository.buscarReporteConFiltros(
                filtros.term(),
                filtros.idVenta(),
                filtros.idUsuario(),
                filtros.idMetodoPago(),
                filtros.idSucursal(),
                filtros.estadoVenta(),
                filtros.fechaInicio(),
                filtros.fechaFinExclusive());

        Sucursal sucursalCabecera = resolverSucursalCabeceraReporte(usuarioAutenticado, filtros.idSucursal(), pagos);
        Usuario usuarioFiltro = resolverUsuarioFiltroReporte(filtros.idUsuario(), pagos);
        Usuario usuarioReporte = esVentas(usuarioAutenticado) ? usuarioAutenticado : usuarioFiltro;

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 30f, 30f, 28f, 28f);
            PdfWriter.getInstance(document, output);
            document.open();

            agregarCabeceraReportePdf(document, sucursalCabecera, pagos.size(), filtros, usuarioReporte);
            document.add(new Paragraph(" "));
            agregarTablaReportePdf(document, pagos);

            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("No se pudo generar el reporte PDF de pagos");
        }
    }

    public byte[] generarReportePagosExcel(
            String term,
            Integer idVenta,
            Integer idUsuario,
            Integer idMetodoPago,
            Integer idSucursal,
            String estadoVenta,
            LocalDate desde,
            LocalDate hasta,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        PagoFiltros filtros = prepararFiltros(
                usuarioAutenticado,
                term,
                idVenta,
                idUsuario,
                idMetodoPago,
                idSucursal,
                estadoVenta,
                desde,
                hasta);

        List<Pago> pagos = pagoRepository.buscarReporteConFiltros(
                filtros.term(),
                filtros.idVenta(),
                filtros.idUsuario(),
                filtros.idMetodoPago(),
                filtros.idSucursal(),
                filtros.estadoVenta(),
                filtros.fechaInicio(),
                filtros.fechaFinExclusive());

        Sucursal sucursalCabecera = resolverSucursalCabeceraReporte(usuarioAutenticado, filtros.idSucursal(), pagos);
        Usuario usuarioFiltro = resolverUsuarioFiltroReporte(filtros.idUsuario(), pagos);
        Usuario usuarioReporte = esVentas(usuarioAutenticado) ? usuarioAutenticado : usuarioFiltro;

        return construirExcelReportePagos(sucursalCabecera, pagos, filtros, usuarioReporte);
    }

    @Transactional
    public PagoListItemResponse actualizarCodigoOperacion(
            Integer idPago,
            PagoActualizarCodigoRequest request,
            String correoUsuarioAutenticado) {
        if (idPago == null || idPago <= 0) {
            throw new RuntimeException("El ID del pago es inválido");
        }
        if (request == null || request.codigoOperacion() == null || request.codigoOperacion().isBlank()) {
            throw new RuntimeException("El código de operación no puede estar vacío");
        }

        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolActualizacion(usuarioAutenticado);

        Pago pago = pagoRepository.findByIdPagoAndDeletedAtIsNull(idPago)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));

        if (!esAdministrador(usuarioAutenticado)) {
            Integer sucursalPago = pago.getVenta() != null && pago.getVenta().getSucursal() != null
                    ? pago.getVenta().getSucursal().getIdSucursal()
                    : null;
            usuarioSucursalAccessService.validarSucursalPermitida(
                    usuarioAutenticado,
                    sucursalPago,
                    "El usuario autenticado no tiene permisos para actualizar pagos de otra sucursal");
        }

        pago.setCodigoOperacion(request.codigoOperacion().trim());
        pagoRepository.save(pago);

        return toListItemResponse(pago);
    }

    @Transactional
    public PagoListItemResponse actualizarPago(
            Integer idPago,
            PagoActualizarCodigoRequest request,
            String correoUsuarioAutenticado) {
        if (idPago == null || idPago <= 0) {
            throw new RuntimeException("El ID del pago es invalido");
        }
        if (request == null) {
            throw new RuntimeException("Debe enviar codigoOperacion o fecha");
        }

        boolean actualizaCodigoOperacion = request.codigoOperacion() != null;
        boolean actualizaFecha = request.fecha() != null;
        if (!actualizaCodigoOperacion && !actualizaFecha) {
            throw new RuntimeException("Debe enviar codigoOperacion o fecha");
        }

        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolActualizacion(usuarioAutenticado);

        Pago pago = pagoRepository.findByIdPagoAndDeletedAtIsNull(idPago)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));

        if (!esAdministrador(usuarioAutenticado)) {
            Integer sucursalPago = pago.getVenta() != null && pago.getVenta().getSucursal() != null
                    ? pago.getVenta().getSucursal().getIdSucursal()
                    : null;
            usuarioSucursalAccessService.validarSucursalPermitida(
                    usuarioAutenticado,
                    sucursalPago,
                    "El usuario autenticado no tiene permisos para actualizar pagos de otra sucursal");
        }

        if (actualizaCodigoOperacion) {
            String codigoOperacion = request.codigoOperacion().trim();
            if (codigoOperacion.isBlank()) {
                throw new RuntimeException("El codigo de operacion no puede estar vacio");
            }
            if (codigoOperacion.length() > 100) {
                throw new RuntimeException("El codigo de operacion no debe superar 100 caracteres");
            }
            pago.setCodigoOperacion(codigoOperacion);
        }
        if (actualizaFecha) {
            pago.setFecha(request.fecha());
        }
        pagoRepository.save(pago);

        return toListItemResponse(pago);
    }

    private PagoListItemResponse toListItemResponse(Pago pago) {
        Integer idMetodoPago = pago.getMetodoPago() != null ? pago.getMetodoPago().getIdMetodoPago() : null;
        String metodoPago = pago.getMetodoPago() != null ? pago.getMetodoPago().getNombre() : null;
        Integer idVenta = pago.getVenta() != null ? pago.getVenta().getIdVenta() : null;
        String tipoComprobante = pago.getVenta() != null ? pago.getVenta().getTipoComprobante() : null;
        String serie = pago.getVenta() != null ? pago.getVenta().getSerie() : null;
        Integer correlativo = pago.getVenta() != null ? pago.getVenta().getCorrelativo() : null;
        Integer idCliente = pago.getVenta() != null && pago.getVenta().getCliente() != null
                ? pago.getVenta().getCliente().getIdCliente()
                : null;
        String nombreCliente = pago.getVenta() != null && pago.getVenta().getCliente() != null
                ? pago.getVenta().getCliente().getNombres()
                : null;
        Integer idUsuario = pago.getVenta() != null && pago.getVenta().getUsuario() != null
                ? pago.getVenta().getUsuario().getIdUsuario()
                : null;
        String nombreUsuario = nombreUsuario(pago.getVenta() != null ? pago.getVenta().getUsuario() : null);
        Integer idSucursal = pago.getVenta() != null && pago.getVenta().getSucursal() != null
                ? pago.getVenta().getSucursal().getIdSucursal()
                : null;
        String nombreSucursal = pago.getVenta() != null && pago.getVenta().getSucursal() != null
                ? pago.getVenta().getSucursal().getNombre()
                : null;
        String estadoVenta = pago.getVenta() != null ? pago.getVenta().getEstado() : null;

        return new PagoListItemResponse(
                pago.getIdPago(),
                pago.getFecha(),
                pago.getMonto(),
                pago.getCodigoOperacion(),
                idMetodoPago,
                metodoPago,
                idVenta,
                tipoComprobante,
                serie,
                correlativo,
                idCliente,
                nombreCliente,
                idUsuario,
                nombreUsuario,
                idSucursal,
                nombreSucursal,
                estadoVenta);
    }

    private PagoFiltros prepararFiltros(
            Usuario usuarioAutenticado,
            String term,
            Integer idVenta,
            Integer idUsuario,
            Integer idMetodoPago,
            Integer idSucursal,
            String estadoVenta,
            LocalDate desde,
            LocalDate hasta) {
        validarRolLectura(usuarioAutenticado);

        String termNormalizado = normalizarTerminoBusqueda(term);
        Integer idVentaFiltro = normalizarIdPositivo(idVenta, "idVenta");
        Integer idUsuarioFiltro = resolverIdUsuarioListado(usuarioAutenticado, idUsuario);
        Integer idMetodoPagoFiltro = normalizarIdPositivo(idMetodoPago, "idMetodoPago");
        Integer idSucursalFiltro = resolverIdSucursalListado(usuarioAutenticado, idSucursal);
        String estadoVentaFiltro = normalizarEstadoVenta(estadoVenta);
        RangoFechas rango = resolverRangoFechas(desde, hasta);
        LocalDateTime fechaInicio = rango == null ? null : rango.desde().atStartOfDay();
        LocalDateTime fechaFinExclusive = rango == null ? null : rango.hasta().plusDays(1).atStartOfDay();

        return new PagoFiltros(
                termNormalizado,
                idVentaFiltro,
                idUsuarioFiltro,
                idMetodoPagoFiltro,
                idSucursalFiltro,
                estadoVentaFiltro,
                fechaInicio,
                fechaFinExclusive,
                rango);
    }

    private String normalizarEstadoVenta(String estadoVenta) {
        if (estadoVenta == null) {
            return null;
        }

        String normalizado = estadoVenta.trim().toUpperCase(Locale.ROOT);
        if (normalizado.isBlank() || "TODOS".equals(normalizado)) {
            return null;
        }

        if (!ESTADOS_VENTA_PERMITIDOS.contains(normalizado)) {
            throw new RuntimeException(
                    "estadoVenta invalido. Valores permitidos: "
                            + String.join(", ", Arrays.asList("TODOS", "EMITIDA", "ANULADA", "NC_EMITIDA")));
        }
        return normalizado;
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolLectura(Usuario usuario) {
        if (!usuario.getRol().permiteVentas()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar pagos");
        }
    }

    private void validarRolActualizacion(Usuario usuario) {
        if (!usuario.getRol().permiteVentas()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para actualizar pagos");
        }
    }

    private Integer resolverIdSucursalListado(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        return usuarioSucursalAccessService.resolverIdSucursalFiltro(
                usuarioAutenticado,
                idSucursalRequest,
                "El usuario autenticado no tiene permisos para filtrar por otra sucursal");
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol().esAdministrador();
    }

    private boolean esVentas(Usuario usuario) {
        return usuario.getRol().operaVentas();
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

    private String normalizarTerminoBusqueda(String term) {
        if (term == null) {
            return null;
        }
        String normalizado = term.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private Integer normalizarIdPositivo(Integer value, String field) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new RuntimeException(field + " debe ser mayor a 0");
        }
        return value;
    }

    private Integer resolverIdUsuarioListado(Usuario usuarioAutenticado, Integer idUsuarioRequest) {
        Integer idUsuarioFiltro = normalizarIdPositivo(idUsuarioRequest, "idUsuario");
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

    private RangoFechas resolverRangoFechas(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return null;
        }

        LocalDate desdeNormalizado = desde == null ? hasta : desde;
        LocalDate hastaNormalizado = hasta == null ? desde : hasta;
        if (desdeNormalizado.isAfter(hastaNormalizado)) {
            throw new RuntimeException("La fecha 'desde' no puede ser mayor a 'hasta'");
        }
        return new RangoFechas(desdeNormalizado, hastaNormalizado);
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro 'page' no puede ser negativo");
        }
    }

    private Sucursal resolverSucursalCabeceraReporte(
            Usuario usuarioAutenticado,
            Integer idSucursalFiltro,
            List<Pago> pagos) {
        if (idSucursalFiltro != null) {
            return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalFiltro).orElse(null);
        }
        if (!esAdministrador(usuarioAutenticado)) {
            return usuarioAutenticado.getSucursal();
        }
        if (pagos == null || pagos.isEmpty()) {
            return null;
        }
        Pago primerPago = pagos.getFirst();
        return primerPago.getVenta() != null ? primerPago.getVenta().getSucursal() : null;
    }

    private Usuario resolverUsuarioFiltroReporte(Integer idUsuarioFiltro, List<Pago> pagos) {
        if (idUsuarioFiltro == null) {
            return null;
        }

        return usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuarioFiltro)
                .orElseGet(() -> obtenerUsuarioDesdePagos(idUsuarioFiltro, pagos));
    }

    private Usuario obtenerUsuarioDesdePagos(Integer idUsuarioFiltro, List<Pago> pagos) {
        if (pagos == null || pagos.isEmpty()) {
            return null;
        }

        for (Pago pago : pagos) {
            Usuario usuario = pago.getVenta() != null ? pago.getVenta().getUsuario() : null;
            if (usuario != null && usuario.getIdUsuario() != null && usuario.getIdUsuario().equals(idUsuarioFiltro)) {
                return usuario;
            }
        }
        return null;
    }

    private void agregarCabeceraReportePdf(
            Document document,
            Sucursal sucursal,
            int totalRegistros,
            PagoFiltros filtros,
            Usuario usuarioFiltro) throws DocumentException {
        PdfPTable header = new PdfPTable(new float[] { 6.8f, 5.2f });
        header.setWidthPercentage(100);

        PdfPCell empresaCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
        Image logo = cargarLogoEmpresaParaPdf(sucursal);
        if (logo != null) {
            logo.scaleToFit(120f, 60f);
            logo.setAlignment(Element.ALIGN_LEFT);
            empresaCell.addElement(logo);
        }

        String nombreEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getNombreComercial() != null
                        && !sucursal.getEmpresa().getNombreComercial().isBlank()
                                ? sucursal.getEmpresa().getNombreComercial()
                                : sucursal.getEmpresa().getNombre())
                : "Sistema Textil";
        String rucEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getRuc())
                : "";
        String nombreSucursal = sucursal != null ? valorTexto(sucursal.getNombre()) : "Todas las sucursales";

        Paragraph pEmpresa = new Paragraph(nombreEmpresa, fuentePdf(true, 14f));
        pEmpresa.setSpacingBefore(logo == null ? 8f : 4f);
        empresaCell.addElement(pEmpresa);
        if (!rucEmpresa.isBlank()) {
            empresaCell.addElement(new Paragraph("RUC: " + rucEmpresa, fuentePdf(false, 9f)));
        }
        empresaCell.addElement(new Paragraph("Sucursal: " + nombreSucursal, fuentePdf(false, 9f)));
        header.addCell(empresaCell);

        PdfPCell infoCell = crearCeldaBase(Rectangle.BOX, 10f);
        infoCell.setBorderColor(new Color(45, 58, 82));
        infoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph titulo = new Paragraph("REPORTE DE PAGOS", fuentePdf(true, 15f, new Color(27, 43, 65)));
        titulo.setAlignment(Element.ALIGN_CENTER);
        infoCell.addElement(titulo);
        infoCell.addElement(new Paragraph(
                "Generado: " + LocalDateTime.now().format(FECHA_HORA_PDF),
                fuentePdf(false, 9f)));
        infoCell.addElement(new Paragraph("Registros: " + totalRegistros, fuentePdf(false, 9f)));
        infoCell.addElement(new Paragraph(
                "Periodo: " + textoPeriodoReporte(filtros.rango()),
                fuentePdf(false, 9f)));
        if (filtros.idUsuario() != null) {
            infoCell.addElement(new Paragraph(
                    "Usuario filtrado: " + textoUsuarioFiltrado(filtros.idUsuario(), usuarioFiltro),
                    fuentePdf(false, 9f)));
        }
        infoCell.addElement(new Paragraph(
            "Estado venta: " + (filtros.estadoVenta() == null ? "TODOS" : filtros.estadoVenta()),
            fuentePdf(false, 9f)));
        if (usuarioFiltro != null) {
            infoCell.addElement(new Paragraph(
                "Usuario reporte: " + nombreUsuario(usuarioFiltro),
                fuentePdf(false, 9f)));
        }
        header.addCell(infoCell);

        document.add(header);
    }

    private void agregarTablaReportePdf(Document document, List<Pago> pagos) throws DocumentException {
        if (pagos == null || pagos.isEmpty()) {
            Paragraph empty = new Paragraph("No se encontraron pagos con los filtros enviados.", fuentePdf(false, 11f));
            empty.setAlignment(Element.ALIGN_CENTER);
            empty.setSpacingBefore(24f);
            document.add(empty);
            return;
        }

        PdfPTable tabla = new PdfPTable(new float[] { 1.6f, 2.8f, 2.0f, 1.8f, 1.6f, 1.9f, 2.1f });
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(8f);

        Color bgHeader = new Color(235, 240, 248);
        agregarHeaderTabla(tabla, "Celular Cliente", bgHeader);
        agregarHeaderTabla(tabla, "Cliente", bgHeader);
        agregarHeaderTabla(tabla, "Codigo Operacion", bgHeader);
        agregarHeaderTabla(tabla, "Metodo Pago", bgHeader);
        agregarHeaderTabla(tabla, "Estado Venta", bgHeader);
        agregarHeaderTabla(tabla, "Monto", bgHeader);
        agregarHeaderTabla(tabla, "Fecha y Hora de Operacion", bgHeader);

        for (Pago pago : pagos) {
            Cliente cliente = pago.getVenta() != null ? pago.getVenta().getCliente() : null;
            agregarCeldaTexto(tabla, cliente != null ? valorTexto(cliente.getTelefono()) : "-", Element.ALIGN_LEFT);
            agregarCeldaTexto(tabla, cliente != null ? valorTexto(cliente.getNombres()) : "-", Element.ALIGN_LEFT);
            agregarCeldaTexto(tabla, valorTexto(pago.getCodigoOperacion()).isBlank() ? "-" : valorTexto(pago.getCodigoOperacion()), Element.ALIGN_LEFT);
            agregarCeldaTexto(tabla,
                    pago.getMetodoPago() != null ? valorTexto(pago.getMetodoPago().getNombre()) : "-",
                    Element.ALIGN_LEFT);
                agregarCeldaTexto(tabla,
                    pago.getVenta() != null && valorTexto(pago.getVenta().getEstado()).isBlank()
                        ? "-"
                        : (pago.getVenta() != null ? valorTexto(pago.getVenta().getEstado()) : "-"),
                    Element.ALIGN_CENTER);
            
            String montoStr = pago.getMonto() != null ? String.format(Locale.US, "S/ %.2f", pago.getMonto()) : "S/ 0.00";
            agregarCeldaTexto(tabla, montoStr, Element.ALIGN_RIGHT);
            
            agregarCeldaTexto(tabla,
                    pago.getFecha() != null ? pago.getFecha().format(FECHA_HORA_PDF) : "",
                    Element.ALIGN_CENTER);
        }

        document.add(tabla);
    }

    private byte[] construirExcelReportePagos(
            Sucursal sucursal,
            List<Pago> pagos,
            PagoFiltros filtros,
            Usuario usuarioReporte) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte Pagos");
            CellStyle headerStyle = crearEstiloHeaderExcel(workbook);
            CellStyle moneyStyle = crearEstiloMonedaExcel(workbook);

            int rowIdx = 0;
            rowIdx = agregarCabeceraReporteExcel(sheet, rowIdx, sucursal, pagos.size(), filtros, usuarioReporte);
            rowIdx++;

            Row header = sheet.createRow(rowIdx++);
            String[] headers = {
                    "Celular Cliente",
                    "Cliente",
                    "Codigo Operacion",
                    "Metodo Pago",
                    "Estado Venta",
                    "Monto",
                    "Fecha y Hora de Operacion"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }

            BigDecimal total = BigDecimal.ZERO;
            for (Pago pago : pagos) {
                Cliente cliente = pago.getVenta() != null ? pago.getVenta().getCliente() : null;
                BigDecimal monto = pago.getMonto() == null ? BigDecimal.ZERO : pago.getMonto();
                total = total.add(monto);

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(cliente != null ? valorTexto(cliente.getTelefono()) : "-");
                row.createCell(1).setCellValue(cliente != null ? valorTexto(cliente.getNombres()) : "-");
                row.createCell(2).setCellValue(valorTexto(pago.getCodigoOperacion()).isBlank()
                        ? "-"
                        : valorTexto(pago.getCodigoOperacion()));
                row.createCell(3).setCellValue(pago.getMetodoPago() != null
                        ? valorTexto(pago.getMetodoPago().getNombre())
                        : "-");
                row.createCell(4).setCellValue(pago.getVenta() != null && !valorTexto(pago.getVenta().getEstado()).isBlank()
                        ? valorTexto(pago.getVenta().getEstado())
                        : "-");
                row.createCell(5).setCellValue(monto.doubleValue());
                row.getCell(5).setCellStyle(moneyStyle);
                row.createCell(6).setCellValue(pago.getFecha() != null ? pago.getFecha().format(FECHA_HORA_EXCEL) : "");
            }

            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(4).setCellValue("TOTAL");
            totalRow.createCell(5).setCellValue(total.doubleValue());
            totalRow.getCell(5).setCellStyle(moneyStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el reporte Excel de pagos");
        }
    }

    private int agregarCabeceraReporteExcel(
            Sheet sheet,
            int rowIdx,
            Sucursal sucursal,
            int totalRegistros,
            PagoFiltros filtros,
            Usuario usuarioReporte) {
        String nombreEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getNombreComercial() != null
                        && !sucursal.getEmpresa().getNombreComercial().isBlank()
                                ? sucursal.getEmpresa().getNombreComercial()
                                : sucursal.getEmpresa().getNombre())
                : "Sistema Textil";
        String rucEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? valorTexto(sucursal.getEmpresa().getRuc())
                : "";
        String nombreSucursal = sucursal != null ? valorTexto(sucursal.getNombre()) : "Todas las sucursales";

        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "REPORTE DE PAGOS", "");
        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Empresa", nombreEmpresa);
        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "RUC", rucEmpresa);
        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Sucursal", nombreSucursal);
        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Generado", LocalDateTime.now().format(FECHA_HORA_EXCEL));
        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Registros", String.valueOf(totalRegistros));
        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Periodo", textoPeriodoReporte(filtros.rango()));
        rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Estado venta",
                filtros.estadoVenta() == null ? "TODOS" : filtros.estadoVenta());
        if (filtros.idUsuario() != null) {
            rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Usuario filtrado",
                    textoUsuarioFiltrado(filtros.idUsuario(), usuarioReporte));
        }
        if (usuarioReporte != null) {
            rowIdx = agregarFilaInfoExcel(sheet, rowIdx, "Usuario reporte", nombreUsuario(usuarioReporte));
        }
        return rowIdx;
    }

    private int agregarFilaInfoExcel(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value == null ? "" : value);
        return rowIdx;
    }

    private CellStyle crearEstiloHeaderExcel(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle crearEstiloMonedaExcel(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("\"S/\" #,##0.00"));
        return style;
    }

    private void agregarHeaderTabla(PdfPTable tabla, String texto, Color fondo) {
        PdfPCell cell = crearCeldaBase(Rectangle.BOX, 7f);
        cell.setBackgroundColor(fondo);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph p = new Paragraph(texto, fuentePdf(true, 10f, new Color(27, 43, 65)));
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
        tabla.addCell(cell);
    }

    private void agregarCeldaTexto(PdfPTable tabla, String texto, int align) {
        PdfPCell cell = crearCeldaBase(Rectangle.BOX, 6f);
        cell.setHorizontalAlignment(align);
        Paragraph p = new Paragraph(texto, fuentePdf(false, 9f));
        p.setAlignment(align);
        cell.addElement(p);
        tabla.addCell(cell);
    }

    private PdfPCell crearCeldaBase(int border, float padding) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(border);
        cell.setPadding(padding);
        cell.setBorderWidth(0.8f);
        cell.setBorderColor(new Color(196, 202, 212));
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

    private Image cargarLogoEmpresaParaPdf(Sucursal sucursal) {
        String logoUrl = sucursal != null && sucursal.getEmpresa() != null
                ? sucursal.getEmpresa().getLogoUrl()
                : null;
        if (logoUrl == null || logoUrl.isBlank()) {
            return null;
        }

        ImageIO.scanForPlugins();
        try (InputStream stream = s3StorageService.openStream(logoUrl)) {
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

    private String textoPeriodoReporte(RangoFechas rango) {
        if (rango == null) {
            return "Todos";
        }
        if (rango.desde().equals(rango.hasta())) {
            return rango.desde().format(FECHA_PDF);
        }
        return rango.desde().format(FECHA_PDF) + " al " + rango.hasta().format(FECHA_PDF);
    }

    private String textoUsuarioFiltrado(Integer idUsuarioFiltro, Usuario usuarioFiltro) {
        if (idUsuarioFiltro == null) {
            return "";
        }
        if (usuarioFiltro == null) {
            return "ID " + idUsuarioFiltro;
        }
        return nombreUsuario(usuarioFiltro) + " (ID: " + idUsuarioFiltro + ")";
    }

    private String valorTexto(String value) {
        return value == null ? "" : value.trim();
    }

    private record RangoFechas(
            LocalDate desde,
            LocalDate hasta) {
    }

    private record PagoFiltros(
            String term,
            Integer idVenta,
            Integer idUsuario,
            Integer idMetodoPago,
            Integer idSucursal,
            String estadoVenta,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFinExclusive,
            RangoFechas rango) {
    }
}
