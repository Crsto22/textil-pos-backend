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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.NotaCreditoDetalle;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoDetalleRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoCreateRequest;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoItemRequest;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoListItemResponse;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaAnulacionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotaCreditoService {

    private static final String ESTADO_EMITIDA = "EMITIDA";
    private static final String ESTADO_ANULADA = "ANULADA";
    private static final String ESTADO_NC_EMITIDA = "NC_EMITIDA";
    private static final String TIPO_NC = "NOTA_CREDITO";
    private static final String TIPO_BOLETA = "BOLETA";
    private static final String TIPO_FACTURA = "FACTURA";
    private static final String TIPO_NC_BOLETA = "NOTA_CREDITO_BOLETA";
    private static final String TIPO_NC_FACTURA = "NOTA_CREDITO_FACTURA";

    private static final String CODIGO_ANULACION_OPERACION = "01";
    private static final String CODIGO_ERROR_RUC = "02";
    private static final String CODIGO_ERROR_DESCRIPCION = "03";
    private static final String CODIGO_DESCUENTO_GLOBAL = "04";
    private static final String CODIGO_DESCUENTO_ITEM = "05";
    private static final String CODIGO_DEVOLUCION_TOTAL = "06";
    private static final String CODIGO_DEVOLUCION_PARCIAL = "07";
    private static final String CODIGO_BONIFICACION = "08";
    private static final String CODIGO_DISMINUCION_VALOR = "09";

    private static final Set<String> CODIGOS_PERMITIDOS = Set.of(
            CODIGO_ANULACION_OPERACION,
            CODIGO_ERROR_RUC,
            CODIGO_ERROR_DESCRIPCION,
            CODIGO_DESCUENTO_GLOBAL,
            CODIGO_DESCUENTO_ITEM,
            CODIGO_DEVOLUCION_TOTAL,
            CODIGO_DEVOLUCION_PARCIAL,
            CODIGO_BONIFICACION,
            CODIGO_DISMINUCION_VALOR);

    private static final Set<String> CODIGOS_SOPORTADOS_ENDPOINT_GENERAL = Set.of(
            CODIGO_ERROR_RUC,
            CODIGO_ERROR_DESCRIPCION,
            CODIGO_DEVOLUCION_TOTAL,
            CODIGO_DEVOLUCION_PARCIAL);

    private static final Set<String> CODIGOS_DEVUELVEN_STOCK = Set.of(
            CODIGO_ANULACION_OPERACION,
            CODIGO_DEVOLUCION_TOTAL,
            CODIGO_DEVOLUCION_PARCIAL);

    private static final Set<SunatEstado> ESTADOS_ACTIVOS_SUNAT = Set.of(
            SunatEstado.PENDIENTE,
            SunatEstado.ACEPTADO,
            SunatEstado.OBSERVADO);

    private final TransactionTemplate transactionTemplate;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final HistorialStockRepository historialStockRepository;
    private final UsuarioRepository usuarioRepository;
    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoDetalleRepository notaCreditoDetalleRepository;
    private final SunatNotaCreditoEmissionService sunatNotaCreditoEmissionService;
    private final SunatDocumentStorageService sunatDocumentStorageService;
    private final SunatCdrParserService sunatCdrParserService;
    private final SunatMontoTextoService sunatMontoTextoService;
    private final SunatProperties sunatProperties;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public VentaAnulacionResponse anularConNotaCreditoTotal(
            Integer idVenta,
            String descripcionMotivo,
            Usuario usuarioAutenticado) {
        NotaCreditoPreparada preparada = transactionTemplate.execute(
                status -> prepararNotaCreditoAnulacionTotal(idVenta, descripcionMotivo, usuarioAutenticado));

        if (preparada == null) {
            throw new RuntimeException("No se pudo preparar la nota de credito para la anulacion");
        }

        if (!esRespuestaSunatValida(preparada.sunatEstado())) {
            sunatNotaCreditoEmissionService.emitir(preparada.idNotaCredito());
        }

        return transactionTemplate.execute(
                status -> finalizarAnulacionConNotaCredito(
                        idVenta,
                        preparada.idNotaCredito(),
                        descripcionMotivo,
                        usuarioAutenticado));
    }

    public NotaCreditoResponse emitirDesdeVenta(
            Integer idVenta,
            NotaCreditoCreateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolNotaCredito(usuarioAutenticado);

        String codigoMotivo = normalizarCodigoMotivo(request.codigoMotivo());
        String descripcionMotivo = normalizarDescripcion(request.descripcionMotivo());
        validarCodigoMotivoEndpointGeneral(codigoMotivo);

        NotaCreditoPreparada preparada = transactionTemplate.execute(status -> prepararNotaCreditoGeneral(
                idVenta,
                codigoMotivo,
                descripcionMotivo,
                request.items(),
                usuarioAutenticado));

        if (preparada == null) {
            throw new RuntimeException("No se pudo preparar la nota de credito");
        }

        if (!esRespuestaSunatValida(preparada.sunatEstado())) {
            sunatNotaCreditoEmissionService.emitir(preparada.idNotaCredito());
        }

        return transactionTemplate.execute(
                status -> finalizarNotaCreditoGeneral(
                        idVenta,
                        preparada.idNotaCredito(),
                        codigoMotivo,
                        usuarioAutenticado));
    }

    public PagedResponse<NotaCreditoListItemResponse> listarPaginado(
            String term,
            Integer idVenta,
            Integer idUsuario,
            Integer idCliente,
            String codigoMotivo,
            String periodo,
            LocalDate fecha,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            int page,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLecturaNotaCredito(usuarioAutenticado);

        String termNormalizado = normalizarTerminoBusqueda(term);
        Integer idVentaFiltro = normalizarIdVentaFiltro(idVenta);
        String codigoMotivoFiltro = normalizarCodigoMotivoFiltro(codigoMotivo);
        boolean listarSinFiltros = esListadoSinFiltros(
                termNormalizado,
                idVentaFiltro,
                idUsuario,
                idCliente,
                codigoMotivoFiltro,
                periodo,
                fecha,
                desde,
                hasta,
                idSucursal);

        Integer idUsuarioFiltro = resolverIdUsuarioListado(usuarioAutenticado, idUsuario, listarSinFiltros);
        Integer idSucursalFiltro = resolverIdSucursalListado(usuarioAutenticado, idSucursal, listarSinFiltros);
        Integer idClienteFiltro = resolverIdClienteFiltro(usuarioAutenticado, idCliente, idSucursalFiltro);
        RangoFechas rangoFechasFiltro = resolverRangoFechasListado(periodo, fecha, desde, hasta);
        LocalDateTime fechaInicioFiltro = rangoFechasFiltro == null ? null : rangoFechasFiltro.desde().atStartOfDay();
        LocalDateTime fechaFinExclusiveFiltro = rangoFechasFiltro == null
                ? null
                : rangoFechasFiltro.hasta().plusDays(1).atStartOfDay();

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idNotaCredito").descending());
        Page<NotaCredito> notas = notaCreditoRepository.buscarConFiltros(
                termNormalizado,
                idSucursalFiltro,
                idUsuarioFiltro,
                idClienteFiltro,
                idVentaFiltro,
                codigoMotivoFiltro,
                fechaInicioFiltro,
                fechaFinExclusiveFiltro,
                pageable);

        return PagedResponse.fromPage(notas.map(this::toListItemResponse));
    }

    public byte[] generarComprobantePdfA4(Integer idNotaCredito, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLecturaNotaCredito(usuarioAutenticado);

        NotaCredito notaCredito = obtenerNotaCreditoConAlcance(idNotaCredito, usuarioAutenticado);
        List<NotaCreditoDetalle> detalles = notaCreditoDetalleRepository
                .findByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(notaCredito.getIdNotaCredito());
        if (detalles.isEmpty()) {
            throw new RuntimeException("La nota de credito no tiene detalles para generar el PDF");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40f, 40f, 28f, 28f);
            PdfWriter.getInstance(document, output);
            document.open();

            agregarCabeceraComprobantePdf(document, notaCredito);
            document.add(new Paragraph(" "));
            agregarDatosNotaCreditoPdf(document, notaCredito);
            document.add(new Paragraph(" "));
            agregarDetalleNotaCreditoPdf(document, detalles);
            agregarResumenNotaCreditoPdf(document, notaCredito);
            document.add(new Paragraph(" "));
            agregarPieNotaCreditoPdf(document, notaCredito, detalles);

            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("No se pudo generar el comprobante PDF de la nota de credito");
        }
    }

    public VentaService.ArchivoDescargable descargarSunatXml(Integer idNotaCredito, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLecturaNotaCredito(usuarioAutenticado);

        NotaCredito notaCredito = obtenerNotaCreditoConAlcance(idNotaCredito, usuarioAutenticado);
        if (notaCredito.getSunatXmlKey() == null || notaCredito.getSunatXmlKey().isBlank()) {
            throw new RuntimeException("La nota de credito aun no tiene XML SUNAT disponible");
        }

        byte[] contenido = sunatDocumentStorageService.download(notaCredito.getSunatXmlKey());
        return new VentaService.ArchivoDescargable(
                notaCredito.getSunatXmlNombre() != null && !notaCredito.getSunatXmlNombre().isBlank()
                        ? notaCredito.getSunatXmlNombre()
                        : SunatComprobanteHelper.construirNombreArchivoXml(notaCredito),
                MediaType.APPLICATION_XML_VALUE,
                contenido);
    }

    public VentaService.ArchivoDescargable descargarSunatCdr(Integer idNotaCredito, String correoUsuarioAutenticado) {
        return descargarSunatCdr(idNotaCredito, correoUsuarioAutenticado, "xml");
    }

    public VentaService.ArchivoDescargable descargarSunatCdr(
            Integer idNotaCredito,
            String correoUsuarioAutenticado,
            String formato) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLecturaNotaCredito(usuarioAutenticado);

        NotaCredito notaCredito = obtenerNotaCreditoConAlcance(idNotaCredito, usuarioAutenticado);
        if (notaCredito.getSunatCdrKey() == null || notaCredito.getSunatCdrKey().isBlank()) {
            throw new RuntimeException("La nota de credito aun no tiene CDR SUNAT disponible");
        }

        byte[] contenido = sunatDocumentStorageService.download(notaCredito.getSunatCdrKey());
        return construirArchivoDescargableCdr(
                contenido,
                notaCredito.getSunatCdrNombre(),
                SunatComprobanteHelper.construirNombreArchivoCdrXml(notaCredito),
                SunatComprobanteHelper.construirNombreArchivoCdrZip(notaCredito),
                formato);
    }

    private VentaService.ArchivoDescargable construirArchivoDescargableCdr(
            byte[] contenido,
            String nombreRegistrado,
            String nombreXmlFallback,
            String nombreZipFallback,
            String formatoSolicitado) {
        String formato = normalizarFormatoCdr(formatoSolicitado);
        String nombreXml = resolverNombreCdrXml(nombreRegistrado, nombreXmlFallback);
        String nombreZip = resolverNombreCdrZip(nombreRegistrado, nombreZipFallback);

        if ("zip".equals(formato)) {
            byte[] zipBytes = sunatCdrParserService.isZip(contenido)
                    ? contenido
                    : sunatCdrParserService.wrapXmlAsZip(nombreXml, contenido);
            return new VentaService.ArchivoDescargable(nombreZip, "application/zip", zipBytes);
        }

        if (sunatCdrParserService.isZip(contenido)) {
            SunatCdrParserService.ExtractedXml extractedXml = sunatCdrParserService.extractXml(contenido);
            String nombreXmlExtraido = extractedXml.fileName() == null || extractedXml.fileName().isBlank()
                    ? nombreXml
                    : extractedXml.fileName();
            return new VentaService.ArchivoDescargable(
                    nombreXmlExtraido,
                    MediaType.APPLICATION_XML_VALUE,
                    extractedXml.bytes());
        }

        return new VentaService.ArchivoDescargable(nombreXml, MediaType.APPLICATION_XML_VALUE, contenido);
    }

    private String normalizarFormatoCdr(String formato) {
        if (formato == null || formato.isBlank()) {
            return "xml";
        }
        String formatoNormalizado = formato.trim().toLowerCase(Locale.ROOT);
        if ("xml".equals(formatoNormalizado) || "zip".equals(formatoNormalizado)) {
            return formatoNormalizado;
        }
        throw new RuntimeException("Formato de CDR no valido. Use xml o zip");
    }

    private String resolverNombreCdrXml(String nombreRegistrado, String fallback) {
        if (nombreRegistrado == null || nombreRegistrado.isBlank()) {
            return fallback;
        }
        String nombre = nombreRegistrado.trim();
        if (nombre.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return nombre.substring(0, nombre.length() - 4) + ".xml";
        }
        return nombre;
    }

    private String resolverNombreCdrZip(String nombreRegistrado, String fallback) {
        if (nombreRegistrado == null || nombreRegistrado.isBlank()) {
            return fallback;
        }
        String nombre = nombreRegistrado.trim();
        if (nombre.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            return nombre.substring(0, nombre.length() - 4) + ".zip";
        }
        return nombre;
    }

    private NotaCreditoListItemResponse toListItemResponse(NotaCredito notaCredito) {
        Venta ventaReferencia = notaCredito.getVentaReferencia();
        Cliente cliente = notaCredito.getCliente();
        Usuario usuario = notaCredito.getUsuario();
        Sucursal sucursal = notaCredito.getSucursal();

        return new NotaCreditoListItemResponse(
                notaCredito.getIdNotaCredito(),
                notaCredito.getFecha(),
                "NOTA DE CREDITO",
                notaCredito.getSerie(),
                notaCredito.getCorrelativo(),
                notaCredito.getMoneda(),
                valorDecimal(notaCredito.getTotal()),
                valorTexto(notaCredito.getEstado()),
                notaCredito.getSunatEstado(),
                valorTexto(notaCredito.getCodigoMotivo()),
                valorTexto(notaCredito.getDescripcionMotivo()),
                Boolean.TRUE.equals(notaCredito.getStockDevuelto()),
                ventaReferencia != null ? ventaReferencia.getIdVenta() : null,
                ventaReferencia != null ? SunatComprobanteHelper.numeroComprobante(ventaReferencia) : construirNumeroReferenciaPdf(notaCredito),
                ventaReferencia != null ? valorTexto(ventaReferencia.getTipoComprobante()) : tipoComprobanteReferenciaPdf(notaCredito),
                cliente != null ? cliente.getIdCliente() : null,
                cliente != null ? valorTexto(cliente.getNombres()) : "",
                usuario != null ? usuario.getIdUsuario() : null,
                nombreUsuario(usuario),
                sucursal != null ? sucursal.getIdSucursal() : null,
                sucursal != null ? valorTexto(sucursal.getNombre()) : "",
                notaCreditoDetalleRepository.countByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(notaCredito.getIdNotaCredito()));
    }

    private NotaCredito obtenerNotaCreditoConAlcance(Integer idNotaCredito, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(idNotaCredito)
                    .orElseThrow(() -> new RuntimeException("Nota de credito con ID " + idNotaCredito + " no encontrada"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return notaCreditoRepository
                .findByIdNotaCreditoAndDeletedAtIsNullAndSucursal_IdSucursal(idNotaCredito, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Nota de credito con ID " + idNotaCredito + " no encontrada"));
    }

    private String normalizarTerminoBusqueda(String term) {
        if (term == null) {
            return null;
        }
        String normalizado = term.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private Integer normalizarIdVentaFiltro(Integer idVenta) {
        if (idVenta == null) {
            return null;
        }
        if (idVenta <= 0) {
            throw new RuntimeException("idVenta debe ser mayor a 0");
        }
        return idVenta;
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

    private String normalizarCodigoMotivoFiltro(String codigoMotivo) {
        if (codigoMotivo == null || codigoMotivo.isBlank()) {
            return null;
        }
        return normalizarCodigoMotivo(codigoMotivo);
    }

    private Integer resolverIdUsuarioListado(Usuario usuarioAutenticado, Integer idUsuarioRequest, boolean listarSinFiltros) {
        Integer idUsuarioFiltro = normalizarIdUsuarioFiltro(idUsuarioRequest);
        if (usuarioAutenticado.getRol() != Rol.VENTAS) {
            return idUsuarioFiltro;
        }
        if (listarSinFiltros) {
            return null;
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

    private Integer resolverIdClienteFiltro(Usuario usuarioAutenticado, Integer idClienteRequest, Integer idSucursalFiltro) {
        if (idClienteRequest == null) {
            return null;
        }
        if (idClienteRequest <= 0) {
            throw new RuntimeException("idCliente debe ser mayor a 0");
        }
        return idClienteRequest;
    }

    private Integer resolverIdSucursalListado(Usuario usuarioAutenticado, Integer idSucursalRequest, boolean listarSinFiltros) {
        if (esAdministrador(usuarioAutenticado)) {
            if (idSucursalRequest == null) {
                return null;
            }
            if (idSucursalRequest <= 0) {
                throw new RuntimeException("idSucursal debe ser mayor a 0");
            }
            return idSucursalRequest;
        }

        if (listarSinFiltros) {
            return null;
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para filtrar por otra sucursal");
        }
        return idSucursalUsuario;
    }

    private boolean esListadoSinFiltros(
            String term,
            Integer idVenta,
            Integer idUsuario,
            Integer idCliente,
            String codigoMotivo,
            String periodo,
            LocalDate fecha,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal) {
        return term == null
                && idVenta == null
                && idUsuario == null
                && idCliente == null
                && codigoMotivo == null
                && (periodo == null || periodo.isBlank())
                && fecha == null
                && desde == null
                && hasta == null
                && idSucursal == null;
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro 'page' no puede ser negativo");
        }
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
                yield new RangoFechas(inicio, inicio.plusDays(6));
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
            default -> throw new RuntimeException("periodo permitido: HOY, AYER, SEMANA, MES, FECHA o RANGO");
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

    private void agregarCabeceraComprobantePdf(Document document, NotaCredito notaCredito) throws DocumentException {
        Sucursal sucursal = notaCredito.getSucursal();
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
        Image logo = cargarLogoEmpresaParaPdf(notaCredito);
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

        Paragraph pTipo = new Paragraph("NOTA DE CREDITO ELECTRONICA", fuentePdf(true, 15f, colorPrimario));
        pTipo.setAlignment(Element.ALIGN_CENTER);
        tipoCell.addElement(pTipo);

        Paragraph pNumero = new Paragraph(SunatComprobanteHelper.numeroComprobante(notaCredito), fuentePdf(true, 13f));
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

    private void agregarDatosNotaCreditoPdf(Document document, NotaCredito notaCredito) throws DocumentException {
        Cliente cliente = notaCredito.getCliente();
        Venta ventaReferencia = notaCredito.getVentaReferencia();
        String nombreCliente = cliente != null && !valorTexto(cliente.getNombres()).isBlank()
                ? valorTexto(cliente.getNombres())
                : "CLIENTE";
        String tipoDoc = etiquetaTipoDocumentoPdf(cliente);
        String nroDocumento = cliente != null && !valorTexto(cliente.getNroDocumento()).isBlank()
                ? valorTexto(cliente.getNroDocumento())
                : "-";
        String direccionCliente = cliente != null && !valorTexto(cliente.getDireccion()).isBlank()
                ? valorTexto(cliente.getDireccion())
                : "";
        String fechaEmision = notaCredito.getFecha() == null
                ? ""
                : notaCredito.getFecha().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String horaEmision = notaCredito.getFecha() == null
                ? ""
                : notaCredito.getFecha().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String moneda = normalizarMonedaPdf(notaCredito.getMoneda());
        String numeroReferencia = ventaReferencia != null
                ? SunatComprobanteHelper.numeroComprobante(ventaReferencia)
                : construirNumeroReferenciaPdf(notaCredito);
        String tipoReferencia = ventaReferencia != null
                ? valorTexto(ventaReferencia.getTipoComprobante())
                : tipoComprobanteReferenciaPdf(notaCredito);
        String estadoSunat = notaCredito.getSunatEstado() == null ? "-" : notaCredito.getSunatEstado().name();
        String stockDevuelto = Boolean.TRUE.equals(notaCredito.getStockDevuelto()) ? "SI" : "NO";

        Color colorFondoInfo = new Color(245, 247, 250);
        PdfPTable tabla = new PdfPTable(new float[] { 2.2f, 4.3f, 2f, 2.3f });
        tabla.setWidthPercentage(100);

        agregarFilaDatosComprobantePdf(tabla, "Cliente", nombreCliente, "Fecha", fechaEmision, colorFondoInfo);
        agregarFilaDatosComprobantePdf(tabla, tipoDoc, nroDocumento, "Hora", horaEmision, colorFondoInfo);
        agregarFilaDatosComprobantePdf(
                tabla,
                "Direccion",
                direccionCliente.isBlank() ? "-" : direccionCliente,
                "Moneda",
                moneda,
                colorFondoInfo);
        agregarFilaDatosComprobantePdf(tabla, "Comprobante ref.", numeroReferencia, "Tipo ref.", tipoReferencia, colorFondoInfo);
        agregarFilaDatosComprobantePdf(tabla, "Codigo motivo", valorTexto(notaCredito.getCodigoMotivo()), "Estado SUNAT", estadoSunat, colorFondoInfo);
        agregarFilaDatosComprobantePdf(
                tabla,
                "Motivo",
                valorTexto(notaCredito.getDescripcionMotivo()),
                "Stock devuelto",
                stockDevuelto,
                colorFondoInfo);

        document.add(tabla);
    }

    private void agregarDetalleNotaCreditoPdf(Document document, List<NotaCreditoDetalle> detalles) throws DocumentException {
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
        for (NotaCreditoDetalle detalle : detalles) {
            Color bgFila = fila % 2 == 0 ? new Color(247, 249, 252) : null;
            tabla.addCell(crearCeldaDetallePdf(String.valueOf(fila), Element.ALIGN_CENTER, bgFila));
            tabla.addCell(crearCeldaDetallePdf(descripcionDetalleParaPdf(detalle), Element.ALIGN_LEFT, bgFila));
            tabla.addCell(crearCeldaDetallePdf(String.valueOf(valorEntero(detalle.getCantidad())), Element.ALIGN_CENTER, bgFila));
            tabla.addCell(crearCeldaDetallePdf(formatearMonedaPdf(detalle.getPrecioUnitario()), Element.ALIGN_RIGHT, bgFila));
            tabla.addCell(crearCeldaDetallePdf(formatearMonedaPdf(detalle.getDescuento()), Element.ALIGN_RIGHT, bgFila));
            tabla.addCell(crearCeldaDetallePdf(formatearMonedaPdf(totalDetalle(detalle)), Element.ALIGN_RIGHT, bgFila));
            fila++;
        }

        document.add(tabla);
    }

    private void agregarResumenNotaCreditoPdf(Document document, NotaCredito notaCredito) throws DocumentException {
        PdfPTable totales = new PdfPTable(new float[] { 6.5f, 2.5f });
        totales.setWidthPercentage(42);
        totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totales.setSpacingBefore(6f);

        agregarFilaTotalComprobantePdf(totales, "Subtotal", formatearMonedaPdf(notaCredito.getSubtotal()));
        if (notaCredito.getDescuentoTotal() != null && notaCredito.getDescuentoTotal().compareTo(BigDecimal.ZERO) > 0) {
            agregarFilaTotalComprobantePdf(totales, "Descuento", "-" + formatearMonedaPdf(notaCredito.getDescuentoTotal()));
        }
        agregarFilaTotalComprobantePdf(
                totales,
                "IGV (" + formatearDecimalPdf(notaCredito.getIgvPorcentaje()) + "%)",
                formatearMonedaPdf(notaCredito.getIgv()));
        agregarFilaTotalComprobantePdf(totales, "TOTAL", formatearMonedaPdf(notaCredito.getTotal()), true);

        document.add(totales);

        Paragraph son = new Paragraph(
                "Son: " + sunatMontoTextoService.montoEnLetras(notaCredito.getTotal(), notaCredito.getMoneda()),
                fuentePdf(true, 8.5f, new Color(60, 60, 60)));
        son.setSpacingBefore(8f);
        document.add(son);
    }

    private void agregarPieNotaCreditoPdf(
            Document document,
            NotaCredito notaCredito,
            List<NotaCreditoDetalle> detalles) throws DocumentException {
        Color colorGris = new Color(100, 100, 100);

        PdfPTable pie = new PdfPTable(new float[] { 3.5f, 6.5f });
        pie.setWidthPercentage(100);
        pie.setSpacingBefore(14f);

        PdfPCell qrCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
        qrCell.setVerticalAlignment(Element.ALIGN_TOP);
        Image qr = generarQrComprobantePdf(notaCredito);
        if (qr != null) {
            qr.scaleToFit(120f, 120f);
            qr.setAlignment(Element.ALIGN_LEFT);
            qrCell.addElement(qr);
        }
        pie.addCell(qrCell);

        PdfPCell infoCell = crearCeldaBase(Rectangle.NO_BORDER, 0f);
        infoCell.setVerticalAlignment(Element.ALIGN_TOP);
        infoCell.setPaddingLeft(10f);

        Paragraph hashP = new Paragraph(
                "Hash: " + generarHashComprobantePdf(notaCredito, detalles),
                fuentePdf(false, 7.5f, colorGris));
        hashP.setSpacingBefore(2f);
        infoCell.addElement(hashP);

        Paragraph rep = new Paragraph(
                "Representacion impresa del comprobante electronico: NOTA DE CREDITO ELECTRONICA",
                fuentePdf(true, 8f, colorGris));
        rep.setSpacingBefore(6f);
        infoCell.addElement(rep);

        Paragraph ref = new Paragraph(
                "Documento modificado: " + construirNumeroReferenciaPdf(notaCredito),
                fuentePdf(false, 7f, colorGris));
        ref.setSpacingBefore(3f);
        infoCell.addElement(ref);

        Paragraph consulta = new Paragraph(
                "Consulte la validez de este comprobante en SUNAT: https://e-factura.sunat.gob.pe/",
                fuentePdf(false, 7f, colorGris));
        consulta.setSpacingBefore(3f);
        infoCell.addElement(consulta);

        Paragraph autorizado = new Paragraph(
                obtenerLeyendaEstadoSunatNotaCredito(notaCredito),
                fuentePdf(false, 7f, colorGris));
        autorizado.setSpacingBefore(4f);
        infoCell.addElement(autorizado);

        pie.addCell(infoCell);
        document.add(pie);
    }

    private void agregarFilaDatosComprobantePdf(
            PdfPTable tabla,
            String label,
            String valor,
            String labelDer,
            String valorDer,
            Color bgColor) {
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
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        Paragraph paragraph = new Paragraph(valorTexto(texto), fuentePdf(false, 9f));
        paragraph.setAlignment(alineacion);
        cell.addElement(paragraph);
        return cell;
    }

    private void agregarFilaTotalComprobantePdf(PdfPTable tabla, String label, String value) {
        agregarFilaTotalComprobantePdf(tabla, label, value, false);
    }

    private void agregarFilaTotalComprobantePdf(PdfPTable tabla, String label, String value, boolean resaltar) {
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
        if (!valorTexto(distrito).isBlank()) {
            sb.append(valorTexto(distrito));
        }
        if (!valorTexto(provincia).isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" - ");
            }
            sb.append(valorTexto(provincia));
        }
        if (!valorTexto(departamento).isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" - ");
            }
            sb.append(valorTexto(departamento));
        }
        return sb.toString();
    }

    private String formatearDecimalPdf(BigDecimal valor) {
        if (valor == null) {
            return "0";
        }
        return valor.stripTrailingZeros().toPlainString();
    }

    private String descripcionDetalleParaPdf(NotaCreditoDetalle detalle) {
        if (detalle.getDescripcion() != null && !detalle.getDescripcion().isBlank()) {
            return detalle.getDescripcion().trim();
        }
        ProductoVariante variante = detalle.getProductoVariante();
        if (variante != null && variante.getProducto() != null && variante.getProducto().getNombre() != null) {
            return variante.getProducto().getNombre().trim();
        }
        return "ITEM";
    }

    private String formatearMonedaPdf(BigDecimal monto) {
        BigDecimal normalizado = valorDecimal(monto);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat("0.00", symbols);
        return format.format(normalizado);
    }

    private BigDecimal totalDetalle(NotaCreditoDetalle detalle) {
        if (detalle.getTotalDetalle() != null) {
            return detalle.getTotalDetalle().setScale(2, RoundingMode.HALF_UP);
        }
        return valorDecimal(detalle.getSubtotal())
                .add(valorDecimal(detalle.getIgvDetalle()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String valorTexto(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nombreUsuario(Usuario usuario) {
        if (usuario == null) {
            return "";
        }
        String nombre = valorTexto(usuario.getNombre());
        String apellido = valorTexto(usuario.getApellido());
        if (nombre.isBlank()) {
            return apellido;
        }
        if (apellido.isBlank()) {
            return nombre;
        }
        return nombre + " " + apellido;
    }

    private String construirNumeroReferenciaPdf(NotaCredito notaCredito) {
        String serieRef = valorTexto(notaCredito.getSerieRef());
        Integer correlativoRef = notaCredito.getCorrelativoRef();
        if (serieRef.isBlank() && correlativoRef == null) {
            return "-";
        }
        String correlativo = correlativoRef == null ? "" : String.format(Locale.ROOT, "%08d", correlativoRef);
        if (serieRef.isBlank()) {
            return correlativo;
        }
        if (correlativo.isBlank()) {
            return serieRef;
        }
        return serieRef + "-" + correlativo;
    }

    private String tipoComprobanteReferenciaPdf(NotaCredito notaCredito) {
        if (notaCredito.getVentaReferencia() != null && notaCredito.getVentaReferencia().getTipoComprobante() != null) {
            return notaCredito.getVentaReferencia().getTipoComprobante();
        }
        return switch (valorTexto(notaCredito.getTipoDocumentoRef())) {
            case "01" -> "FACTURA";
            case "03" -> "BOLETA";
            default -> "COMPROBANTE";
        };
    }

    private Image cargarLogoEmpresaParaPdf(NotaCredito notaCredito) {
        String logoUrl = notaCredito != null
                && notaCredito.getSucursal() != null
                && notaCredito.getSucursal().getEmpresa() != null
                        ? notaCredito.getSucursal().getEmpresa().getLogoUrl()
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

    private Image generarQrComprobantePdf(NotaCredito notaCredito) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter()
                    .encode(SunatComprobanteHelper.construirContenidoQr(notaCredito), BarcodeFormat.QR_CODE, 180, 180, hints);
            BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(buffered, "png", out);
            return Image.getInstance(out.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    private String generarHashComprobantePdf(NotaCredito notaCredito, List<NotaCreditoDetalle> detalles) {
        if (notaCredito.getSunatHash() != null && !notaCredito.getSunatHash().isBlank()) {
            return notaCredito.getSunatHash();
        }
        return SunatComprobanteHelper.generarHashBase64(
                SunatComprobanteHelper.construirCadenaResumen(notaCredito, detalles));
    }

    private NotaCreditoPreparada prepararNotaCreditoAnulacionTotal(
            Integer idVenta,
            String descripcionMotivo,
            Usuario usuarioAutenticado) {
        Venta venta = obtenerVentaConAlcanceForUpdate(idVenta, usuarioAutenticado);
        validarVentaDisponibleParaNotaCreditoTotal(venta);
        validarVentaElectronicaParaNotaCredito(venta);

        List<NotaCredito> notasExistentes = notaCreditoRepository
                .findByVentaReferencia_IdVentaAndDeletedAtIsNullOrderByIdNotaCreditoDesc(venta.getIdVenta());

        for (NotaCredito notaExistente : notasExistentes) {
            if (!CODIGO_ANULACION_OPERACION.equals(notaExistente.getCodigoMotivo())
                    && ESTADOS_ACTIVOS_SUNAT.contains(notaExistente.getSunatEstado())) {
                throw new RuntimeException(
                        "La venta ya tiene notas de credito asociadas; no se puede anular totalmente.");
            }
        }

        for (NotaCredito notaExistente : notasExistentes) {
            if (!CODIGO_ANULACION_OPERACION.equals(notaExistente.getCodigoMotivo())) {
                continue;
            }
            if (esRespuestaSunatValida(notaExistente.getSunatEstado())) {
                return new NotaCreditoPreparada(notaExistente.getIdNotaCredito(), notaExistente.getSunatEstado());
            }
            notaExistente.setDescripcionMotivo(descripcionMotivo);
            notaExistente.setUsuario(usuarioAutenticado);
            notaCreditoRepository.save(notaExistente);
            return new NotaCreditoPreparada(notaExistente.getIdNotaCredito(), notaExistente.getSunatEstado());
        }

        List<VentaDetalle> detallesVenta = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        if (detallesVenta.isEmpty()) {
            throw new RuntimeException("La venta no tiene detalles para generar nota de credito");
        }

        return crearNotaCredito(
                venta,
                usuarioAutenticado,
                CODIGO_ANULACION_OPERACION,
                descripcionMotivo,
                construirLineasCompletas(detallesVenta));
    }

    private NotaCreditoPreparada prepararNotaCreditoGeneral(
            Integer idVenta,
            String codigoMotivo,
            String descripcionMotivo,
            List<NotaCreditoItemRequest> items,
            Usuario usuarioAutenticado) {
        Venta venta = obtenerVentaConAlcanceForUpdate(idVenta, usuarioAutenticado);
        validarVentaDisponibleParaNuevaNotaCredito(venta);
        validarVentaElectronicaParaNotaCredito(venta);
        validarCodigoMotivoSegunComprobante(venta, codigoMotivo);

        List<NotaCredito> notasExistentes = notaCreditoRepository
                .findByVentaReferencia_IdVentaAndDeletedAtIsNullOrderByIdNotaCreditoDesc(venta.getIdVenta());
        validarNotasExistentesParaEndpointGeneral(codigoMotivo, notasExistentes);

        List<VentaDetalle> detallesVenta = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        if (detallesVenta.isEmpty()) {
            throw new RuntimeException("La venta no tiene detalles para generar nota de credito");
        }

        List<DetalleNotaCreditoPlan> lineas = construirLineasParaMotivo(venta, codigoMotivo, items, detallesVenta);
        return crearNotaCredito(venta, usuarioAutenticado, codigoMotivo, descripcionMotivo, lineas);
    }

    private NotaCreditoPreparada crearNotaCredito(
            Venta venta,
            Usuario usuarioAutenticado,
            String codigoMotivo,
            String descripcionMotivo,
            List<DetalleNotaCreditoPlan> lineas) {
        if (lineas.isEmpty()) {
            throw new RuntimeException("La nota de credito no tiene detalles para emitir");
        }

        NumeroComprobanteNotaCredito numero = asignarNumeroNotaCredito(venta);
        NotaCredito notaCredito = new NotaCredito();
        notaCredito.setVentaReferencia(venta);
        notaCredito.setSucursal(venta.getSucursal());
        notaCredito.setUsuario(usuarioAutenticado);
        notaCredito.setCliente(venta.getCliente());
        notaCredito.setTipoComprobante(numero.tipoComprobante());
        notaCredito.setSerie(numero.serie());
        notaCredito.setCorrelativo(numero.correlativo());
        notaCredito.setMoneda(venta.getMoneda());
        notaCredito.setCodigoMotivo(codigoMotivo);
        notaCredito.setDescripcionMotivo(descripcionMotivo);
        notaCredito.setTipoDocumentoRef(SunatComprobanteHelper.codigoTipoComprobante(venta.getTipoComprobante()));
        notaCredito.setSerieRef(venta.getSerie());
        notaCredito.setCorrelativoRef(venta.getCorrelativo());
        notaCredito.setIgvPorcentaje(venta.getIgvPorcentaje());
        notaCredito.setSubtotal(sumar(lineas, DetalleNotaCreditoPlan::subtotal));
        notaCredito.setDescuentoTotal(sumar(lineas, DetalleNotaCreditoPlan::descuento));
        notaCredito.setIgv(sumar(lineas, DetalleNotaCreditoPlan::igvDetalle));
        notaCredito.setTotal(sumar(lineas, DetalleNotaCreditoPlan::totalDetalle));
        notaCredito.setEstado(ESTADO_EMITIDA);
        notaCredito.setSunatEstado(SunatEstado.PENDIENTE);
        notaCredito.setStockDevuelto(false);
        notaCredito.setActivo("ACTIVO");

        NotaCredito notaCreditoGuardada = notaCreditoRepository.save(notaCredito);

        List<NotaCreditoDetalle> detallesNotaCredito = new ArrayList<>();
        for (DetalleNotaCreditoPlan linea : lineas) {
            NotaCreditoDetalle detalle = new NotaCreditoDetalle();
            detalle.setNotaCredito(notaCreditoGuardada);
            detalle.setVentaDetalleReferencia(linea.ventaDetalle());
            detalle.setProductoVariante(linea.ventaDetalle().getProductoVariante());
            detalle.setDescripcion(linea.descripcion());
            detalle.setCantidad(linea.cantidad());
            detalle.setUnidadMedida(linea.unidadMedida());
            detalle.setCodigoTipoAfectacionIgv(linea.codigoTipoAfectacionIgv());
            detalle.setPrecioUnitario(linea.precioUnitario());
            detalle.setDescuento(linea.descuento());
            detalle.setIgvDetalle(linea.igvDetalle());
            detalle.setSubtotal(linea.subtotal());
            detalle.setTotalDetalle(linea.totalDetalle());
            detalle.setActivo("ACTIVO");
            detallesNotaCredito.add(detalle);
        }
        notaCreditoDetalleRepository.saveAll(detallesNotaCredito);

        return new NotaCreditoPreparada(notaCreditoGuardada.getIdNotaCredito(), notaCreditoGuardada.getSunatEstado());
    }

    private VentaAnulacionResponse finalizarAnulacionConNotaCredito(
            Integer idVenta,
            Integer idNotaCredito,
            String descripcionMotivo,
            Usuario usuarioAutenticado) {
        Venta venta = obtenerVentaConAlcanceForUpdate(idVenta, usuarioAutenticado);
        NotaCredito notaCredito = notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(idNotaCredito)
                .orElseThrow(() -> new RuntimeException("Nota de credito con ID " + idNotaCredito + " no encontrada"));

        if (esRespuestaSunatValida(notaCredito.getSunatEstado())) {
            if (!Boolean.TRUE.equals(notaCredito.getStockDevuelto())) {
                revertirStockPorNotaCredito(notaCredito, usuarioAutenticado);
                notaCredito.setStockDevuelto(true);
            }

            venta.setEstado(ESTADO_ANULADA);
            venta.setTipoAnulacion(TIPO_NC);
            venta.setMotivoAnulacion(descripcionMotivo);
            venta.setAnuladoAt(LocalDateTime.now());
            venta.setUsuarioAnulacion(usuarioAutenticado);

            notaCreditoRepository.save(notaCredito);
            ventaRepository.save(venta);

            return new VentaAnulacionResponse(
                    venta.getIdVenta(),
                    SunatComprobanteHelper.numeroComprobante(venta),
                    venta.getTipoComprobante(),
                    venta.getEstado(),
                    venta.getTipoAnulacion(),
                    venta.getMotivoAnulacion(),
                    venta.getAnuladoAt(),
                    true,
                    notaCredito.getIdNotaCredito(),
                    SunatComprobanteHelper.numeroComprobante(notaCredito),
                    notaCredito.getTipoComprobante(),
                    notaCredito.getSunatEstado(),
                    notaCredito.getSunatCodigo(),
                    notaCredito.getSunatMensaje(),
                    "Nota de credito emitida correctamente. Venta anulada y stock revertido.");
        }

        return new VentaAnulacionResponse(
                venta.getIdVenta(),
                SunatComprobanteHelper.numeroComprobante(venta),
                venta.getTipoComprobante(),
                venta.getEstado(),
                venta.getTipoAnulacion(),
                venta.getMotivoAnulacion(),
                venta.getAnuladoAt(),
                false,
                notaCredito.getIdNotaCredito(),
                SunatComprobanteHelper.numeroComprobante(notaCredito),
                notaCredito.getTipoComprobante(),
                notaCredito.getSunatEstado(),
                notaCredito.getSunatCodigo(),
                notaCredito.getSunatMensaje(),
                mensajeSegunEstadoSunat(notaCredito.getSunatEstado(), false));
    }

    private NotaCreditoResponse finalizarNotaCreditoGeneral(
            Integer idVenta,
            Integer idNotaCredito,
            String codigoMotivo,
            Usuario usuarioAutenticado) {
        Venta venta = obtenerVentaConAlcanceForUpdate(idVenta, usuarioAutenticado);
        NotaCredito notaCredito = notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(idNotaCredito)
                .orElseThrow(() -> new RuntimeException("Nota de credito con ID " + idNotaCredito + " no encontrada"));

        boolean stockDevuelto = Boolean.TRUE.equals(notaCredito.getStockDevuelto());
        if (esRespuestaSunatValida(notaCredito.getSunatEstado())
                && CODIGOS_DEVUELVEN_STOCK.contains(codigoMotivo)
                && !stockDevuelto) {
            revertirStockPorNotaCredito(notaCredito, usuarioAutenticado);
            notaCredito.setStockDevuelto(true);
            notaCreditoRepository.save(notaCredito);
            stockDevuelto = true;
        }

        if (esRespuestaSunatValida(notaCredito.getSunatEstado())) {
            aplicarEstadoVentaSegunNotaCredito(venta, notaCredito, codigoMotivo, usuarioAutenticado);
            ventaRepository.save(venta);
        }

        return new NotaCreditoResponse(
                venta.getIdVenta(),
                SunatComprobanteHelper.numeroComprobante(venta),
                venta.getTipoComprobante(),
                venta.getEstado(),
                notaCredito.getIdNotaCredito(),
                SunatComprobanteHelper.numeroComprobante(notaCredito),
                notaCredito.getTipoComprobante(),
                notaCredito.getCodigoMotivo(),
                notaCredito.getDescripcionMotivo(),
                notaCredito.getFecha(),
                stockDevuelto,
                notaCredito.getSunatEstado(),
                notaCredito.getSunatCodigo(),
                notaCredito.getSunatMensaje(),
                mensajeSegunEstadoSunat(notaCredito.getSunatEstado(), CODIGOS_DEVUELVEN_STOCK.contains(codigoMotivo)));
    }

    private void aplicarEstadoVentaSegunNotaCredito(
            Venta venta,
            NotaCredito notaCredito,
            String codigoMotivo,
            Usuario usuarioAutenticado) {
        if (CODIGO_DEVOLUCION_TOTAL.equals(codigoMotivo)) {
            venta.setEstado(ESTADO_ANULADA);
            venta.setTipoAnulacion(TIPO_NC);
            venta.setMotivoAnulacion(notaCredito.getDescripcionMotivo());
            venta.setAnuladoAt(LocalDateTime.now());
            venta.setUsuarioAnulacion(usuarioAutenticado);
            return;
        }

        if (CODIGO_ERROR_RUC.equals(codigoMotivo)
                || CODIGO_ERROR_DESCRIPCION.equals(codigoMotivo)
                || CODIGO_DEVOLUCION_PARCIAL.equals(codigoMotivo)) {
            venta.setEstado(ESTADO_NC_EMITIDA);
            venta.setTipoAnulacion(TIPO_NC);
            venta.setMotivoAnulacion(notaCredito.getDescripcionMotivo());
        }
    }

    private List<DetalleNotaCreditoPlan> construirLineasParaMotivo(
            Venta venta,
            String codigoMotivo,
            List<NotaCreditoItemRequest> items,
            List<VentaDetalle> detallesVenta) {
        return switch (codigoMotivo) {
            case CODIGO_ERROR_RUC, CODIGO_ERROR_DESCRIPCION -> construirLineasDocumentales(items, detallesVenta);
            case CODIGO_DEVOLUCION_TOTAL -> construirLineasDevolucionTotal(venta, items, detallesVenta);
            case CODIGO_DEVOLUCION_PARCIAL -> construirLineasDevolucionParcial(venta, items, detallesVenta);
            default -> throw new RuntimeException("El codigoMotivo enviado aun no esta soportado por este endpoint");
        };
    }

    private List<DetalleNotaCreditoPlan> construirLineasDocumentales(
            List<NotaCreditoItemRequest> items,
            List<VentaDetalle> detallesVenta) {
        if (items != null && !items.isEmpty()) {
            throw new RuntimeException("Este codigoMotivo no acepta items; la nota de credito se emite sobre todo el documento");
        }
        return construirLineasCompletas(detallesVenta);
    }

    private List<DetalleNotaCreditoPlan> construirLineasDevolucionTotal(
            Venta venta,
            List<NotaCreditoItemRequest> items,
            List<VentaDetalle> detallesVenta) {
        if (items != null && !items.isEmpty()) {
            throw new RuntimeException("La devolucion total no requiere items; se calcula con el saldo pendiente de la venta");
        }

        List<DetalleNotaCreditoPlan> lineas = new ArrayList<>();
        for (VentaDetalle detalleVenta : detallesVenta) {
            int cantidadDisponible = obtenerCantidadDisponibleParaDevolver(venta, detalleVenta);
            if (cantidadDisponible > 0) {
                lineas.add(construirLinea(detalleVenta, cantidadDisponible));
            }
        }

        if (lineas.isEmpty()) {
            throw new RuntimeException("La venta ya no tiene cantidades disponibles para devolucion total");
        }
        return lineas;
    }

    private List<DetalleNotaCreditoPlan> construirLineasDevolucionParcial(
            Venta venta,
            List<NotaCreditoItemRequest> items,
            List<VentaDetalle> detallesVenta) {
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("La devolucion parcial requiere al menos un item");
        }

        Map<Integer, Integer> cantidadesSolicitadas = new LinkedHashMap<>();
        for (NotaCreditoItemRequest item : items) {
            if (cantidadesSolicitadas.containsKey(item.idVentaDetalle())) {
                throw new RuntimeException("No se puede repetir el mismo idVentaDetalle en la nota de credito");
            }
            cantidadesSolicitadas.put(item.idVentaDetalle(), item.cantidad());
        }

        Map<Integer, VentaDetalle> detallesPorId = new LinkedHashMap<>();
        for (VentaDetalle detalleVenta : detallesVenta) {
            detallesPorId.put(detalleVenta.getIdVentaDetalle(), detalleVenta);
        }

        List<DetalleNotaCreditoPlan> lineas = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : cantidadesSolicitadas.entrySet()) {
            VentaDetalle detalleVenta = detallesPorId.get(entry.getKey());
            if (detalleVenta == null) {
                throw new RuntimeException("El idVentaDetalle " + entry.getKey() + " no pertenece a la venta");
            }

            int cantidadSolicitada = valorEntero(entry.getValue());
            int cantidadDisponible = obtenerCantidadDisponibleParaDevolver(venta, detalleVenta);
            if (cantidadSolicitada > cantidadDisponible) {
                throw new RuntimeException(
                        "La cantidad solicitada para el detalle " + entry.getKey()
                                + " supera el saldo disponible para devolver");
            }
            lineas.add(construirLinea(detalleVenta, cantidadSolicitada));
        }

        return lineas;
    }

    private List<DetalleNotaCreditoPlan> construirLineasCompletas(List<VentaDetalle> detallesVenta) {
        List<DetalleNotaCreditoPlan> lineas = new ArrayList<>();
        for (VentaDetalle detalleVenta : detallesVenta) {
            lineas.add(construirLinea(detalleVenta, valorEntero(detalleVenta.getCantidad())));
        }
        return lineas;
    }

    private DetalleNotaCreditoPlan construirLinea(VentaDetalle detalleVenta, int cantidad) {
        int cantidadOriginal = valorEntero(detalleVenta.getCantidad());
        if (cantidadOriginal <= 0) {
            throw new RuntimeException("Uno de los detalles de la venta tiene una cantidad invalida");
        }
        if (cantidad <= 0 || cantidad > cantidadOriginal) {
            throw new RuntimeException("La cantidad solicitada para la nota de credito es invalida");
        }

        if (cantidad == cantidadOriginal) {
            return new DetalleNotaCreditoPlan(
                    detalleVenta,
                    cantidad,
                    normalizarTexto(detalleVenta.getDescripcion(), 255),
                    normalizarTexto(detalleVenta.getUnidadMedida(), 3),
                    normalizarTexto(detalleVenta.getCodigoTipoAfectacionIgv(), 2),
                    valorDecimal(detalleVenta.getPrecioUnitario()),
                    valorDecimal(detalleVenta.getDescuento()),
                    valorDecimal(detalleVenta.getIgvDetalle()),
                    valorDecimal(detalleVenta.getSubtotal()),
                    totalDetalle(detalleVenta));
        }

        BigDecimal factor = BigDecimal.valueOf(cantidad)
                .divide(BigDecimal.valueOf(cantidadOriginal), 10, RoundingMode.HALF_UP);

        return new DetalleNotaCreditoPlan(
                detalleVenta,
                cantidad,
                normalizarTexto(detalleVenta.getDescripcion(), 255),
                normalizarTexto(detalleVenta.getUnidadMedida(), 3),
                normalizarTexto(detalleVenta.getCodigoTipoAfectacionIgv(), 2),
                valorDecimal(detalleVenta.getPrecioUnitario()),
                prorratear(detalleVenta.getDescuento(), factor),
                prorratear(detalleVenta.getIgvDetalle(), factor),
                prorratear(detalleVenta.getSubtotal(), factor),
                prorratear(totalDetalle(detalleVenta), factor));
    }

    private void revertirStockPorNotaCredito(NotaCredito notaCredito, Usuario usuarioAutenticado) {
        List<NotaCreditoDetalle> detalles = notaCreditoDetalleRepository
                .findByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(notaCredito.getIdNotaCredito());
        if (detalles.isEmpty()) {
            throw new RuntimeException("La nota de credito no tiene detalles para revertir stock");
        }

        List<ProductoVariante> variantesActualizar = new ArrayList<>();
        List<HistorialStock> historial = new ArrayList<>();

        for (NotaCreditoDetalle detalle : detalles) {
            Integer idProductoVariante = detalle.getProductoVariante() != null
                    ? detalle.getProductoVariante().getIdProductoVariante()
                    : null;
            if (idProductoVariante == null) {
                throw new RuntimeException("Uno de los detalles de la nota de credito no tiene variante de producto");
            }

            ProductoVariante variante = productoVarianteRepository.findByIdProductoVarianteForUpdate(idProductoVariante)
                    .orElseThrow(() -> new RuntimeException(
                            "La variante con ID " + idProductoVariante + " no existe"));

            int stockAnterior = valorEntero(variante.getStock());
            int stockNuevo = stockAnterior + valorEntero(detalle.getCantidad());
            variante.setStock(stockNuevo);
            variante.setEstado(stockNuevo <= 0 ? "AGOTADO" : "ACTIVO");
            variantesActualizar.add(variante);

            HistorialStock movimiento = new HistorialStock();
            movimiento.setTipoMovimiento(HistorialStock.TipoMovimiento.DEVOLUCION);
            movimiento.setMotivo("NOTA CREDITO " + SunatComprobanteHelper.numeroComprobante(notaCredito));
            movimiento.setProductoVariante(variante);
            movimiento.setSucursal(notaCredito.getSucursal());
            movimiento.setUsuario(usuarioAutenticado);
            movimiento.setCantidad(valorEntero(detalle.getCantidad()));
            movimiento.setStockAnterior(stockAnterior);
            movimiento.setStockNuevo(stockNuevo);
            historial.add(movimiento);
        }

        productoVarianteRepository.saveAll(variantesActualizar);
        historialStockRepository.saveAll(historial);
    }

    private NumeroComprobanteNotaCredito asignarNumeroNotaCredito(Venta venta) {
        Integer idSucursal = venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null;
        if (idSucursal == null) {
            throw new RuntimeException("La venta no tiene sucursal asociada");
        }

        String tipoComprobanteNc = TIPO_BOLETA.equals(normalizarTipoComprobante(venta.getTipoComprobante()))
                ? TIPO_NC_BOLETA
                : TIPO_NC_FACTURA;

        ComprobanteConfig config = comprobanteConfigRepository.findActivoForUpdate(idSucursal, tipoComprobanteNc)
                .orElseThrow(() -> new RuntimeException(
                        "No existe configuracion activa de nota de credito para la sucursal y tipo"));

        String serie = normalizarTexto(config.getSerie(), 10);
        if (serie == null) {
            throw new RuntimeException("La configuracion de nota de credito no tiene serie valida");
        }

        int ultimoConfig = valorEntero(config.getUltimoCorrelativo());
        int maxNotaCredito = valorEntero(notaCreditoRepository
                .obtenerMaxCorrelativoPorDocumento(idSucursal, tipoComprobanteNc, serie));
        int nuevoCorrelativo = Math.max(ultimoConfig, maxNotaCredito) + 1;

        config.setUltimoCorrelativo(nuevoCorrelativo);
        comprobanteConfigRepository.save(config);

        return new NumeroComprobanteNotaCredito(tipoComprobanteNc, serie, nuevoCorrelativo);
    }

    private int obtenerCantidadDisponibleParaDevolver(Venta venta, VentaDetalle detalleVenta) {
        int cantidadOriginal = valorEntero(detalleVenta.getCantidad());
        Integer cantidadAplicada = notaCreditoDetalleRepository.obtenerCantidadAplicadaPorVentaDetalle(
                detalleVenta.getIdVentaDetalle(),
                CODIGOS_DEVUELVEN_STOCK,
                ESTADOS_ACTIVOS_SUNAT);
        int cantidadDisponible = cantidadOriginal - valorEntero(cantidadAplicada);
        if (cantidadDisponible < 0) {
            throw new RuntimeException(
                    "La venta #" + venta.getIdVenta() + " tiene un exceso de devolucion registrado en el detalle "
                            + detalleVenta.getIdVentaDetalle());
        }
        return cantidadDisponible;
    }

    private void validarVentaDisponibleParaNotaCreditoTotal(Venta venta) {
        String estado = normalizarTexto(venta.getEstado(), 20);
        if (ESTADO_ANULADA.equals(estado)) {
            throw new RuntimeException("La venta ya se encuentra anulada");
        }
        if (ESTADO_NC_EMITIDA.equals(estado)) {
            throw new RuntimeException("La venta ya tiene una nota de credito emitida");
        }
        if (!ESTADO_EMITIDA.equals(estado)) {
            throw new RuntimeException("Solo se pueden anular ventas en estado EMITIDA");
        }
    }

    private void validarVentaDisponibleParaNuevaNotaCredito(Venta venta) {
        String estado = normalizarTexto(venta.getEstado(), 20);
        if (ESTADO_ANULADA.equals(estado)) {
            throw new RuntimeException("La venta ya se encuentra anulada");
        }
        if (ESTADO_NC_EMITIDA.equals(estado)) {
            throw new RuntimeException("La venta ya fue anulada con una nota de credito");
        }
        if (!ESTADO_EMITIDA.equals(estado)) {
            throw new RuntimeException("Solo se puede emitir nota de credito para ventas en estado EMITIDA");
        }
    }

    private void validarVentaElectronicaParaNotaCredito(Venta venta) {
        if (venta.getSerie() == null || venta.getSerie().isBlank() || venta.getCorrelativo() == null) {
            throw new RuntimeException("La venta no tiene numeracion valida para emitir nota de credito");
        }
        if (venta.getCliente() == null) {
            throw new RuntimeException("La venta no tiene cliente asociado para emitir nota de credito");
        }

        String tipoComprobante = normalizarTipoComprobante(venta.getTipoComprobante());
        if (!TIPO_BOLETA.equals(tipoComprobante) && !TIPO_FACTURA.equals(tipoComprobante)) {
            throw new RuntimeException("Solo se puede emitir nota de credito para ventas con boleta o factura");
        }

        String mode = sunatProperties.normalizedMode();
        if (isRealMode(mode)
                && venta.getSunatEstado() != SunatEstado.ACEPTADO
                && venta.getSunatEstado() != SunatEstado.OBSERVADO) {
            throw new RuntimeException(
                    "Solo se puede emitir nota de credito cuando el comprobante original fue aceptado por SUNAT");
        }
    }

    private void validarCodigoMotivoEndpointGeneral(String codigoMotivo) {
        if (CODIGO_ANULACION_OPERACION.equals(codigoMotivo)) {
            throw new RuntimeException("Para el codigoMotivo 01 use el endpoint /api/venta/{id}/anular");
        }
        if (!CODIGOS_SOPORTADOS_ENDPOINT_GENERAL.contains(codigoMotivo)) {
            throw new RuntimeException(
                    "Por ahora este endpoint soporta solo los codigosMotivo 02, 03, 06 y 07");
        }
    }

    private void validarCodigoMotivoSegunComprobante(Venta venta, String codigoMotivo) {
        String tipoComprobante = normalizarTipoComprobante(venta.getTipoComprobante());
        if (TIPO_BOLETA.equals(tipoComprobante)
                && (CODIGO_DESCUENTO_GLOBAL.equals(codigoMotivo)
                        || CODIGO_DESCUENTO_ITEM.equals(codigoMotivo)
                        || CODIGO_BONIFICACION.equals(codigoMotivo))) {
            throw new RuntimeException(
                    "SUNAT no permite nota de credito para boleta con los codigosMotivo 04, 05 y 08");
        }
    }

    private void validarNotasExistentesParaEndpointGeneral(String codigoMotivo, List<NotaCredito> notasExistentes) {
        for (NotaCredito notaExistente : notasExistentes) {
            if (CODIGO_ANULACION_OPERACION.equals(notaExistente.getCodigoMotivo())
                    && ESTADOS_ACTIVOS_SUNAT.contains(notaExistente.getSunatEstado())) {
                throw new RuntimeException("La venta ya fue enviada a un flujo de anulacion total con nota de credito");
            }
        }

        if (CODIGO_ERROR_RUC.equals(codigoMotivo) || CODIGO_ERROR_DESCRIPCION.equals(codigoMotivo)) {
            for (NotaCredito notaExistente : notasExistentes) {
                if (ESTADOS_ACTIVOS_SUNAT.contains(notaExistente.getSunatEstado())) {
                    throw new RuntimeException(
                            "No se puede emitir una nota de credito documental cuando la venta ya tiene otra nota de credito activa");
                }
            }
        }
    }

    private Venta obtenerVentaConAlcanceForUpdate(Integer idVenta, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return ventaRepository.findByIdVentaForUpdate(idVenta)
                    .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return ventaRepository.findByIdVentaAndSucursalForUpdate(idVenta, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolNotaCredito(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para emitir notas de credito");
        }
    }

    private void validarRolLecturaNotaCredito(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar notas de credito");
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

    private String normalizarCodigoMotivo(String codigoMotivo) {
        String normalizado = normalizarTexto(codigoMotivo, 2);
        if (normalizado == null || !CODIGOS_PERMITIDOS.contains(normalizado)) {
            throw new RuntimeException("codigoMotivo invalido segun catalogo SUNAT 09");
        }
        return normalizado;
    }

    private String normalizarDescripcion(String descripcionMotivo) {
        String normalizado = normalizarTexto(descripcionMotivo, 255);
        if (normalizado == null || normalizado.length() < 5) {
            throw new RuntimeException("La descripcionMotivo debe tener entre 5 y 255 caracteres");
        }
        return normalizado;
    }

    private String normalizarTipoComprobante(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return "";
        }
        return tipoComprobante.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarTexto(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String normalizado = value.trim();
        if (normalizado.isEmpty()) {
            return null;
        }
        return normalizado.length() <= maxLen ? normalizado : normalizado.substring(0, maxLen);
    }

    private BigDecimal valorDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalDetalle(VentaDetalle detalleVenta) {
        if (detalleVenta.getTotalDetalle() != null) {
            return detalleVenta.getTotalDetalle().setScale(2, RoundingMode.HALF_UP);
        }
        return valorDecimal(detalleVenta.getSubtotal())
                .add(valorDecimal(detalleVenta.getIgvDetalle()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal prorratear(BigDecimal value, BigDecimal factor) {
        BigDecimal base = value == null ? BigDecimal.ZERO : value;
        return base.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumar(List<DetalleNotaCreditoPlan> lineas, DecimalExtractor extractor) {
        return lineas.stream()
                .map(extractor::extract)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean esRespuestaSunatValida(SunatEstado estado) {
        return estado == SunatEstado.ACEPTADO || estado == SunatEstado.OBSERVADO;
    }

    private String obtenerLeyendaEstadoSunatNotaCredito(NotaCredito notaCredito) {
        SunatEstado estado = notaCredito.getSunatEstado();
        if (estado == SunatEstado.ACEPTADO) {
            return "Comprobante electronico aceptado por SUNAT.";
        }
        if (estado == SunatEstado.OBSERVADO) {
            return "Comprobante electronico aceptado con observaciones por SUNAT.";
        }
        if (estado == SunatEstado.RECHAZADO) {
            return "Comprobante electronico rechazado por SUNAT.";
        }
        if (estado == SunatEstado.ERROR) {
            return "Comprobante electronico generado con incidencia en el envio a SUNAT.";
        }
        return "Comprobante electronico generado y pendiente de validacion por SUNAT.";
    }

    private String mensajeSegunEstadoSunat(SunatEstado estado, boolean devuelveStock) {
        if (estado == SunatEstado.PENDIENTE) {
            return "La nota de credito fue registrada y queda pendiente de envio o respuesta SUNAT. La venta sigue activa.";
        }
        if (estado == SunatEstado.RECHAZADO) {
            return "SUNAT rechazo la nota de credito. La venta sigue activa.";
        }
        if (estado == SunatEstado.ERROR) {
            return "No se pudo completar el envio de la nota de credito a SUNAT. La venta sigue activa.";
        }
        if (devuelveStock) {
            return "Nota de credito emitida correctamente. Stock revertido.";
        }
        return "Nota de credito emitida correctamente.";
    }

    private boolean isRealMode(String mode) {
        return "REAL".equals(mode)
                || "LIVE".equals(mode)
                || "ENABLED".equals(mode)
                || "PRODUCCION".equals(mode)
                || "PRODUCTION".equals(mode);
    }

    @FunctionalInterface
    private interface DecimalExtractor {
        BigDecimal extract(DetalleNotaCreditoPlan detalle);
    }

    private record RangoFechas(
            LocalDate desde,
            LocalDate hasta) {
    }

    private record DetalleNotaCreditoPlan(
            VentaDetalle ventaDetalle,
            Integer cantidad,
            String descripcion,
            String unidadMedida,
            String codigoTipoAfectacionIgv,
            BigDecimal precioUnitario,
            BigDecimal descuento,
            BigDecimal igvDetalle,
            BigDecimal subtotal,
            BigDecimal totalDetalle) {
    }

    private record NotaCreditoPreparada(
            Integer idNotaCredito,
            SunatEstado sunatEstado) {
    }

    private record NumeroComprobanteNotaCredito(
            String tipoComprobante,
            String serie,
            Integer correlativo) {
    }
}
