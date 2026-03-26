package com.sistemapos.sistematextil.services;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;

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
    private static final Set<String> ESTADOS_VENTA_PERMITIDOS = Set.of("EMITIDA", "ANULADA", "NC_EMITIDA");

    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;

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
            Integer sucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
            if (sucursalPago != null && !sucursalPago.equals(sucursalUsuario)) {
                throw new RuntimeException("El usuario autenticado no tiene permisos para actualizar pagos de otra sucursal");
            }
        }

        pago.setCodigoOperacion(request.codigoOperacion().trim());
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
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar pagos");
        }
    }

    private void validarRolActualizacion(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para actualizar pagos");
        }
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

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private boolean esVentas(Usuario usuario) {
        return usuario.getRol() == Rol.VENTAS;
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
        agregarHeaderTabla(tabla, "Fecha y Hora", bgHeader);

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
                    pago.getFecha() != null ? pago.getFecha().format(FECHA_HORA_PDF) : "-",
                    Element.ALIGN_CENTER);
        }

        document.add(tabla);
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
