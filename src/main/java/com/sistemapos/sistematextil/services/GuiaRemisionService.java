package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoConductor;
import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoTransportista;
import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoVehiculo;
import com.sistemapos.sistematextil.model.GuiaRemisionConductor;
import com.sistemapos.sistematextil.model.GuiaRemisionDetalle;
import com.sistemapos.sistematextil.model.GuiaRemisionDocumentoRelacionado;
import com.sistemapos.sistematextil.model.GuiaRemisionTransportista;
import com.sistemapos.sistematextil.model.GuiaRemisionVehiculo;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionConductorRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionDetalleRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionDocumentoRelacionadoRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionTransportistaRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionVehiculoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionConductorRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionCreateRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionDetalleCreateItem;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionDetalleResponse;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionDocumentoRelacionadoRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionDocumentoRelacionadoResponse;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionResponse;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionTransportistaRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionUpdateRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionVehiculoRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionVentaAutocompleteResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

import lombok.RequiredArgsConstructor;

/**
 * Servicio de Guias de Remision Remitente (09) para motivos operativos SUNAT.
 */
@Service
@RequiredArgsConstructor
public class GuiaRemisionService {

    private static final Logger log = LoggerFactory.getLogger(GuiaRemisionService.class);
    private static final int PAGE_SIZE = 20;
    private static final String TIPO_COMPROBANTE_GUIA_REMISION = "GUIA_REMISION";
    private static final String MOTIVO_TRASLADO_INTERNO = "04";
    private static final String MOTIVO_TRASLADO_OTROS = "13";
    private static final Set<String> MOTIVOS_REQUIEREN_DOCUMENTO_RELACIONADO = Set.of("01", "02", "03");
    private static final Set<String> TIPOS_DOCUMENTO_RELACIONADO_PERMITIDOS = Set.of("01", "03", "04");
    private static final Map<String, String> MOTIVOS_TRASLADO_PERMITIDOS = Map.of(
            "01", "Venta",
            "02", "Compra",
            "03", "Venta con entrega a terceros",
            "04", "Traslado entre establecimientos de la misma empresa",
            "05", "Consignacion",
            "06", "Devolucion",
            "07", "Recojo de bienes transformados",
            "13", "Otros no comprendidos en ningun codigo del presente catalogo",
            "14", "Venta sujeta a confirmacion del comprador",
            "17", "Traslado de bienes para transformacion");
    private static final Set<String> MOTIVOS_CON_DESTINATARIO_EMPRESA_POR_DEFECTO = Set.of("02", "04", "07");
    private static final String TIPO_DOC_RUC = "6";
    private static final String ESTADO_BORRADOR = "BORRADOR";
    private static final String ESTADO_EMITIDA = "EMITIDA";
    private static final String ESTADO_ACEPTADA = "ACEPTADA";
    private static final String ESTADO_ANULADA = "ANULADA";
    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final GuiaRemisionRepository guiaRemisionRepository;
    private final GuiaRemisionDetalleRepository detalleRepository;
    private final GuiaRemisionDocumentoRelacionadoRepository documentoRelacionadoRepository;
    private final GuiaRemisionConductorRepository conductorRepository;
    private final GuiaRemisionTransportistaRepository transportistaRepository;
    private final GuiaRemisionVehiculoRepository vehiculoRepository;
    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final EmpresaRepository empresaRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final SunatGuiaRemisionEmissionService emissionService;
    private final SunatJobService sunatJobService;
    private final SunatDocumentStorageService documentStorageService;
    private final GuiaRemisionPdfService guiaRemisionPdfService;
    private final GuiaRemisionCatalogoService guiaRemisionCatalogoService;

    // ────────────── Listar ──────────────

    @Transactional(readOnly = true)
    public PagedResponse<GuiaRemisionResponse> listarPaginado(
            String q, Integer idSucursal, String estado,
            String sunatEstado, int page, String correoAutenticado) {

        Usuario usuario = obtenerUsuarioAutenticado(correoAutenticado);
        Integer sucursalFiltro = resolverSucursalFiltro(usuario, idSucursal);
        SunatEstado sunatEstadoEnum = parseSunatEstado(sunatEstado);

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "idGuiaRemision"));

        Page<GuiaRemision> pagina = guiaRemisionRepository.buscarConFiltros(
                normalizeSearch(q), sucursalFiltro, estado, sunatEstadoEnum, pageRequest);

        List<GuiaRemisionResponse> items = pagina.getContent().stream()
                .map(this::toResponseResumen)
                .toList();

        return new PagedResponse<>(
                items,
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalPages(),
                pagina.getTotalElements(),
                pagina.getNumberOfElements(),
                pagina.isFirst(),
                pagina.isLast(),
                pagina.isEmpty());
    }

    // ────────────── Detalle ──────────────

    @Transactional(readOnly = true)
    public GuiaRemisionResponse obtenerDetalle(Integer id, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);
        return toResponseCompleto(guia);
    }

    @Transactional
    public ArchivoDescargable descargarPdf(Integer id, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);
        String nombreArchivo = numeroGuiaRemision(guia) + ".pdf";

        if (!documentStorageService.isStoredDocumentUpToDate(guia.getSunatPdfKey(), guia.getUpdatedAt())) {
            byte[] pdfGenerado = guiaRemisionPdfService.generarPdfA4(guia);
            SunatDocumentStorageService.StoredDocument stored = documentStorageService
                    .storePdf(guia, nombreArchivo, pdfGenerado);
            guia.setSunatPdfNombre(stored.fileName());
            guia.setSunatPdfKey(stored.key());
            guiaRemisionRepository.save(guia);
        }

        byte[] contenido = documentStorageService.download(guia.getSunatPdfKey());
        return new ArchivoDescargable(nombreArchivo, MediaType.APPLICATION_PDF_VALUE, contenido);
    }

    @Transactional(readOnly = true)
    public GuiaRemisionVentaAutocompleteResponse autocompletarDesdeVenta(
            String tipoDocumento,
            String serie,
            String numero,
            String correoAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoAutenticado);
        String tipoComprobante = tipoComprobanteVentaDesdeSunat(tipoDocumento);
        String serieNormalizada = normalizeSerieVenta(serie);
        Integer correlativo = parseCorrelativoVenta(numero);

        List<Venta> ventas = ventaRepository.buscarComprobanteParaGuia(
                tipoComprobante, serieNormalizada, correlativo);
        if (ventas.isEmpty()) {
            throw new RuntimeException("No se encontro venta con serie " + serieNormalizada
                    + " y numero " + numero);
        }
        if (ventas.size() > 1) {
            throw new RuntimeException("Existe mas de una venta para esa serie y numero. Envie tipoDocumento 01 o 03");
        }

        Venta venta = ventas.get(0);
        validarAccesoVentaParaGuia(usuario, venta);
        if (!"EMITIDA".equalsIgnoreCase(venta.getEstado())) {
            throw new RuntimeException("Solo se puede autocompletar desde ventas emitidas");
        }

        List<VentaDetalle> ventaDetalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        if (ventaDetalles.isEmpty()) {
            throw new RuntimeException("La venta no tiene detalles para generar guia");
        }

        List<GuiaRemisionDetalleCreateItem> detalles = ventaDetalles.stream()
                .map(this::toGuiaDetalleDesdeVenta)
                .toList();
        List<GuiaRemisionVentaAutocompleteResponse.ItemVenta> itemsVenta = ventaDetalles.stream()
                .map(this::toItemVentaAutocomplete)
                .toList();
        GuiaRemisionDocumentoRelacionadoRequest documentoRelacionado =
                new GuiaRemisionDocumentoRelacionadoRequest(
                        tipoDocumentoSunatDesdeVenta(venta),
                        venta.getSerie().trim().toUpperCase(Locale.ROOT),
                        String.format(Locale.ROOT, "%08d", venta.getCorrelativo()));

        GuiaRemisionVentaAutocompleteResponse.ClienteResumen cliente = toClienteResumen(venta);
        GuiaRemisionVentaAutocompleteResponse.SucursalResumen sucursal = toSucursalResumen(venta.getSucursal());
        String destinatarioTipoDoc = cliente != null ? cliente.tipoDocumentoSunat() : null;
        String destinatarioNroDoc = cliente != null ? cliente.nroDocumento() : null;
        String destinatarioRazonSocial = cliente != null ? cliente.nombres() : null;

        GuiaRemisionVentaAutocompleteResponse.GuiaSugerida guiaSugerida =
                new GuiaRemisionVentaAutocompleteResponse.GuiaSugerida(
                        "01",
                        venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null,
                        venta.getSucursal() != null ? venta.getSucursal().getUbigeo() : null,
                        venta.getSucursal() != null ? venta.getSucursal().getDireccion() : null,
                        null,
                        cliente != null ? cliente.direccion() : null,
                        destinatarioTipoDoc,
                        destinatarioNroDoc,
                        destinatarioRazonSocial,
                        List.of(documentoRelacionado),
                        detalles);

        return new GuiaRemisionVentaAutocompleteResponse(
                venta.getIdVenta(),
                venta.getTipoComprobante(),
                documentoRelacionado.tipoDocumento(),
                venta.getSerie(),
                venta.getCorrelativo(),
                documentoRelacionado.serie() + "-" + documentoRelacionado.numero(),
                venta.getFecha(),
                venta.getEstado(),
                venta.getSunatEstado(),
                venta.getSubtotal(),
                venta.getIgv(),
                venta.getTotal(),
                cliente,
                sucursal,
                documentoRelacionado,
                itemsVenta,
                detalles,
                guiaSugerida);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionResponse.ConductorResponse> listarConductores(
            Integer idGuiaRemision, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        return conductorRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision())
                .stream()
                .map(this::toConductorResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuiaRemisionResponse.ConductorResponse obtenerConductor(
            Integer idGuiaRemision, Integer idGuiaConductor, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        obtenerGuia(idGuiaRemision);
        return toConductorResponse(obtenerConductorEntidad(idGuiaRemision, idGuiaConductor));
    }

    @Transactional
    public GuiaRemisionResponse.ConductorResponse crearConductor(
            Integer idGuiaRemision,
            GuiaRemisionConductorRequest request,
            String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "agregar conductores");
        GuiaRemisionConductor conductor = construirConductor(guia, request);
        return toConductorResponse(conductorRepository.save(conductor));
    }

    @Transactional
    public GuiaRemisionResponse.ConductorResponse actualizarConductor(
            Integer idGuiaRemision,
            Integer idGuiaConductor,
            GuiaRemisionConductorRequest request,
            String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "editar conductores");
        GuiaRemisionConductor conductor = obtenerConductorEntidad(idGuiaRemision, idGuiaConductor);
        aplicarConductor(conductor, request);
        return toConductorResponse(conductorRepository.save(conductor));
    }

    @Transactional
    public void eliminarConductor(Integer idGuiaRemision, Integer idGuiaConductor, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "eliminar conductores");
        GuiaRemisionConductor conductor = obtenerConductorEntidad(idGuiaRemision, idGuiaConductor);
        softDelete(List.of(conductor));
        conductorRepository.save(conductor);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionResponse.TransportistaResponse> listarTransportistas(
            Integer idGuiaRemision, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        return transportistaRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(guia.getIdGuiaRemision())
                .stream()
                .map(this::toTransportistaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuiaRemisionResponse.TransportistaResponse obtenerTransportista(
            Integer idGuiaRemision, Integer idGuiaTransportista, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        obtenerGuia(idGuiaRemision);
        return toTransportistaResponse(obtenerTransportistaEntidad(idGuiaRemision, idGuiaTransportista));
    }

    @Transactional
    public GuiaRemisionResponse.TransportistaResponse crearTransportista(
            Integer idGuiaRemision,
            GuiaRemisionTransportistaRequest request,
            String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "agregar transportistas");
        GuiaRemisionTransportista transportista = construirTransportista(guia, request);
        return toTransportistaResponse(transportistaRepository.save(transportista));
    }

    @Transactional
    public GuiaRemisionResponse.TransportistaResponse actualizarTransportista(
            Integer idGuiaRemision,
            Integer idGuiaTransportista,
            GuiaRemisionTransportistaRequest request,
            String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "editar transportistas");
        GuiaRemisionTransportista transportista = obtenerTransportistaEntidad(idGuiaRemision, idGuiaTransportista);
        aplicarTransportista(transportista, request);
        return toTransportistaResponse(transportistaRepository.save(transportista));
    }

    @Transactional
    public void eliminarTransportista(
            Integer idGuiaRemision, Integer idGuiaTransportista, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "eliminar transportistas");
        GuiaRemisionTransportista transportista = obtenerTransportistaEntidad(idGuiaRemision, idGuiaTransportista);
        softDelete(List.of(transportista));
        transportistaRepository.save(transportista);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionResponse.VehiculoResponse> listarVehiculos(
            Integer idGuiaRemision, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        return vehiculoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision())
                .stream()
                .map(this::toVehiculoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuiaRemisionResponse.VehiculoResponse obtenerVehiculo(
            Integer idGuiaRemision, Integer idGuiaVehiculo, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        obtenerGuia(idGuiaRemision);
        return toVehiculoResponse(obtenerVehiculoEntidad(idGuiaRemision, idGuiaVehiculo));
    }

    @Transactional
    public GuiaRemisionResponse.VehiculoResponse crearVehiculo(
            Integer idGuiaRemision,
            GuiaRemisionVehiculoRequest request,
            String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "agregar vehiculos");
        GuiaRemisionVehiculo vehiculo = construirVehiculo(guia, request);
        return toVehiculoResponse(vehiculoRepository.save(vehiculo));
    }

    @Transactional
    public GuiaRemisionResponse.VehiculoResponse actualizarVehiculo(
            Integer idGuiaRemision,
            Integer idGuiaVehiculo,
            GuiaRemisionVehiculoRequest request,
            String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "editar vehiculos");
        GuiaRemisionVehiculo vehiculo = obtenerVehiculoEntidad(idGuiaRemision, idGuiaVehiculo);
        aplicarVehiculo(vehiculo, request);
        return toVehiculoResponse(vehiculoRepository.save(vehiculo));
    }

    @Transactional
    public void eliminarVehiculo(Integer idGuiaRemision, Integer idGuiaVehiculo, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        validarGuiaEditable(guia, "eliminar vehiculos");
        GuiaRemisionVehiculo vehiculo = obtenerVehiculoEntidad(idGuiaRemision, idGuiaVehiculo);
        softDelete(List.of(vehiculo));
        vehiculoRepository.save(vehiculo);
    }

    // ────────────── Crear GRE remitente ──────────────

    @Transactional
    public GuiaRemisionResponse crear(GuiaRemisionCreateRequest request, String correoAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoAutenticado);
        Sucursal sucursalUsuario = resolverSucursal(usuario);
        Empresa empresa = resolverEmpresa(sucursalUsuario);

        validarEmpresaEmisora(empresa);

        String motivoTraslado = normalizarMotivoTraslado(request.motivoTraslado());
        Sucursal sucursalPartida = resolverSucursalOpcional(request.idSucursalPartida(), "partida");
        Sucursal sucursalLlegada = resolverSucursalOpcional(request.idSucursalLlegada(), "llegada");

        String direccionPartida = resolverDireccionTraslado(
                request.direccionPartida(), sucursalPartida, "partida");
        String direccionLlegada = resolverDireccionTraslado(
                request.direccionLlegada(), sucursalLlegada, "llegada");
        String ubigeoPartida = resolverUbigeoTraslado(request.ubigeoPartida(), sucursalPartida, "partida");
        String ubigeoLlegada = resolverUbigeoTraslado(request.ubigeoLlegada(), sucursalLlegada, "llegada");
        validarPuntosTraslado(motivoTraslado, empresa, sucursalPartida, sucursalLlegada);

        String serie = request.serie() != null && !request.serie().isBlank()
                ? normalizeSerie(request.serie())
                : obtenerSerieGuiaRemisionPorDefecto();
        int nuevoCorrelativo = obtenerSiguienteCorrelativo(serie);

        GuiaRemision guia = new GuiaRemision();
        guia.setSucursal(sucursalUsuario);
        guia.setUsuario(usuario);
        guia.setSerie(serie);
        guia.setCorrelativo(nuevoCorrelativo);
        guia.setFechaEmision(nowLima());
        guia.setFechaInicioTraslado(request.fechaInicioTraslado());
        guia.setFechaEntregaTransportista(request.fechaEntregaTransportista());
        guia.setMotivoTraslado(motivoTraslado);
        guia.setDescripcionMotivo(resolverDescripcionMotivo(motivoTraslado, request.descripcionMotivo()));
        guia.setModalidadTransporte(request.modalidadTransporte().trim());
        guia.setPesoBrutoTotal(request.pesoBrutoTotal());
        guia.setUnidadPeso(normalizeUnitCode(request.unidadPeso(), "KGM"));
        guia.setNumeroBultos(request.numeroBultos());
        guia.setObservaciones(normalizeText(request.observaciones(), 500));
        guia.setUbigeoPartida(ubigeoPartida);
        guia.setDireccionPartida(direccionPartida);
        guia.setSucursalPartida(sucursalPartida);
        guia.setUbigeoLlegada(ubigeoLlegada);
        guia.setDireccionLlegada(direccionLlegada);
        guia.setSucursalLlegada(sucursalLlegada);
        aplicarDestinatario(guia, motivoTraslado, empresa,
                request.destinatarioTipoDoc(),
                request.destinatarioNroDoc(),
                request.destinatarioRazonSocial());
        guia.setEstado(ESTADO_BORRADOR);
        guia.setSunatEstado(SunatEstado.NO_APLICA);
        guia.setActivo(ACTIVO);

        GuiaRemision guiaGuardada = guiaRemisionRepository.save(guia);

        guardarDetalles(guiaGuardada, request.detalles());
        guardarDocumentosRelacionados(guiaGuardada, request.documentosRelacionados());
        guardarConductoresSegunFuente(
                guiaGuardada,
                request.conductores(),
                request.idsCatalogoConductores(),
                empresa.getIdEmpresa());
        guardarTransportistasSegunFuente(
                guiaGuardada,
                request.transportistas(),
                request.idsCatalogoTransportistas(),
                empresa.getIdEmpresa());
        guardarVehiculosSegunFuente(
                guiaGuardada,
                request.vehiculos(),
                request.idsCatalogoVehiculos(),
                empresa.getIdEmpresa());

        if (Boolean.TRUE.equals(request.emitirDirectamente())) {
            prepararGuiaParaEmision(guiaGuardada);
            guiaRemisionRepository.save(guiaGuardada);
            sunatJobService.enqueueGuiaRemision(guiaGuardada.getIdGuiaRemision());
        }

        return toResponseCompleto(guiaGuardada);
    }

    // ────────────── Editar borrador ──────────────

    @Transactional
    public GuiaRemisionResponse editarBorrador(
            Integer id, GuiaRemisionUpdateRequest request, String correoAutenticado) {

        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);
        validarGuiaEditable(guia, "editar la guia");

        Empresa empresa = resolverEmpresa(guia.getSucursal());

        if (request.motivoTraslado() != null) {
            guia.setMotivoTraslado(normalizarMotivoTraslado(request.motivoTraslado()));
            guia.setDescripcionMotivo(resolverDescripcionMotivo(
                    guia.getMotivoTraslado(), request.descripcionMotivo()));
        }
        if (request.motivoTraslado() == null && request.descripcionMotivo() != null) {
            guia.setDescripcionMotivo(resolverDescripcionMotivo(
                    guia.getMotivoTraslado(), request.descripcionMotivo()));
        }

        if (request.idSucursalPartida() != null) {
            Sucursal sp = resolverSucursalOpcional(request.idSucursalPartida(), "partida");
            guia.setSucursalPartida(sp);
            guia.setDireccionPartida(resolverDireccionTraslado(request.direccionPartida(), sp, "partida"));
            guia.setUbigeoPartida(resolverUbigeoTraslado(request.ubigeoPartida(), sp, "partida"));
        } else if (request.direccionPartida() != null) {
            guia.setSucursalPartida(null);
            guia.setDireccionPartida(normalizeRequiredText(request.direccionPartida(), "direccionPartida", 255));
            if (request.ubigeoPartida() != null) {
                validarUbigeo(request.ubigeoPartida(), "ubigeoPartida");
                guia.setUbigeoPartida(request.ubigeoPartida().trim());
            }
        } else if (request.ubigeoPartida() != null) {
            guia.setUbigeoPartida(request.ubigeoPartida().trim());
        }

        if (request.idSucursalLlegada() != null) {
            Sucursal sl = resolverSucursalOpcional(request.idSucursalLlegada(), "llegada");
            guia.setSucursalLlegada(sl);
            guia.setDireccionLlegada(resolverDireccionTraslado(request.direccionLlegada(), sl, "llegada"));
            guia.setUbigeoLlegada(resolverUbigeoTraslado(request.ubigeoLlegada(), sl, "llegada"));
        } else if (request.direccionLlegada() != null) {
            guia.setSucursalLlegada(null);
            guia.setDireccionLlegada(normalizeRequiredText(request.direccionLlegada(), "direccionLlegada", 255));
            if (request.ubigeoLlegada() != null) {
                validarUbigeo(request.ubigeoLlegada(), "ubigeoLlegada");
                guia.setUbigeoLlegada(request.ubigeoLlegada().trim());
            }
        } else if (request.ubigeoLlegada() != null) {
            guia.setUbigeoLlegada(request.ubigeoLlegada().trim());
        }

        // Validar que partida != llegada si ambas están seteadas
        validarPuntosTraslado(
                guia.getMotivoTraslado(), empresa, guia.getSucursalPartida(), guia.getSucursalLlegada());

        if (request.fechaInicioTraslado() != null) {
            guia.setFechaInicioTraslado(request.fechaInicioTraslado());
        }
        if (request.fechaEntregaTransportista() != null) {
            guia.setFechaEntregaTransportista(request.fechaEntregaTransportista());
        }
        if (request.modalidadTransporte() != null) {
            guia.setModalidadTransporte(request.modalidadTransporte().trim());
        }
        if (request.pesoBrutoTotal() != null) {
            guia.setPesoBrutoTotal(request.pesoBrutoTotal());
        }
        if (request.unidadPeso() != null) {
            guia.setUnidadPeso(normalizeUnitCode(request.unidadPeso(), "KGM"));
        }
        if (request.numeroBultos() != null) {
            guia.setNumeroBultos(request.numeroBultos());
        }
        if (request.observaciones() != null) {
            guia.setObservaciones(normalizeText(request.observaciones(), 500));
        }

        if (request.destinatarioTipoDoc() != null
                || request.destinatarioNroDoc() != null
                || request.destinatarioRazonSocial() != null
                || MOTIVO_TRASLADO_INTERNO.equals(guia.getMotivoTraslado())) {
            aplicarDestinatario(guia, guia.getMotivoTraslado(), empresa,
                    request.destinatarioTipoDoc() != null
                            ? request.destinatarioTipoDoc() : guia.getDestinatarioTipoDoc(),
                    request.destinatarioNroDoc() != null
                            ? request.destinatarioNroDoc() : guia.getDestinatarioNroDoc(),
                    request.destinatarioRazonSocial() != null
                            ? request.destinatarioRazonSocial() : guia.getDestinatarioRazonSocial());
        }

        guiaRemisionRepository.save(guia);

        if (request.detalles() != null) {
            reemplazarDetalles(guia, request.detalles());
        }
        if (request.documentosRelacionados() != null) {
            reemplazarDocumentosRelacionados(guia, request.documentosRelacionados());
        }
        if (request.conductores() != null || request.idsCatalogoConductores() != null) {
            reemplazarConductoresSegunFuente(
                    guia,
                    request.conductores(),
                    request.idsCatalogoConductores(),
                    empresa.getIdEmpresa());
        }
        if (request.transportistas() != null || request.idsCatalogoTransportistas() != null) {
            reemplazarTransportistasSegunFuente(
                    guia,
                    request.transportistas(),
                    request.idsCatalogoTransportistas(),
                    empresa.getIdEmpresa());
        }
        if (request.vehiculos() != null || request.idsCatalogoVehiculos() != null) {
            reemplazarVehiculosSegunFuente(
                    guia,
                    request.vehiculos(),
                    request.idsCatalogoVehiculos(),
                    empresa.getIdEmpresa());
        }

        return toResponseCompleto(guia);
    }

    // ────────────── Emitir ──────────────

    @Transactional
    public GuiaRemisionResponse emitir(Integer id, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);
        prepararGuiaParaEmision(guia);
        guiaRemisionRepository.save(guia);
        sunatJobService.enqueueGuiaRemision(guia.getIdGuiaRemision());
        return toResponseCompleto(guia);
    }

    @Transactional
    public void procesarEmisionElectronica(Integer idGuiaRemision) {
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        emitirInternamente(guia);
    }

    @Transactional
    public void consultarCdrPendiente(Integer idGuiaRemision) {
        GuiaRemision guia = obtenerGuia(idGuiaRemision);
        if (guia.getSunatTicket() == null || guia.getSunatTicket().isBlank()) {
            throw new RuntimeException("La guia no tiene ticket SUNAT para consultar");
        }
        emissionService.consultarTicketYActualizar(guia);
        guiaRemisionRepository.save(guia);
    }

    // ────────────── Consultar CDR ──────────────

    @Transactional
    public GuiaRemisionResponse consultarCdr(Integer id, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);

        if (guia.getSunatTicket() == null || guia.getSunatTicket().isBlank()) {
            throw new RuntimeException("La guia no tiene ticket SUNAT para consultar");
        }
        guia.setSunatEstado(SunatEstado.PENDIENTE_CDR);
        guia.setSunatMensaje("Consulta de CDR programada.");
        guiaRemisionRepository.save(guia);
        sunatJobService.enqueueConsultaTicketGuia(guia.getIdGuiaRemision(), guia.getSunatTicket());
        return toResponseCompleto(guia);
    }

    // ────────────── Anular ──────────────

    @Transactional
    public GuiaRemisionResponse anular(Integer id, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);

        if (ESTADO_ACEPTADA.equalsIgnoreCase(guia.getEstado())) {
            throw new RuntimeException(
                    "La anulacion electronica de guias aceptadas por SUNAT aun no esta implementada");
        }
        if (ESTADO_ANULADA.equalsIgnoreCase(guia.getEstado())) {
            return toResponseCompleto(guia);
        }

        guia.setEstado(ESTADO_ANULADA);
        guia.setSunatMensaje("Guia de remision anulada localmente");
        guiaRemisionRepository.save(guia);
        return toResponseCompleto(guia);
    }

    // ────────────── Descargas ──────────────

    public ArchivoDescargable descargarSunatXml(Integer id, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);
        if (guia.getSunatXmlKey() == null || guia.getSunatXmlKey().isBlank()) {
            throw new RuntimeException("La guia de remision no tiene XML SUNAT registrado");
        }
        byte[] bytes = documentStorageService.download(guia.getSunatXmlKey());
        String nombre = guia.getSunatXmlNombre() != null ? guia.getSunatXmlNombre() : "guia.xml";
        return new ArchivoDescargable(nombre, "application/xml", bytes);
    }

    public ArchivoDescargable descargarSunatCdr(Integer id, String correoAutenticado) {
        obtenerUsuarioAutenticado(correoAutenticado);
        GuiaRemision guia = obtenerGuia(id);
        if (guia.getSunatCdrKey() == null || guia.getSunatCdrKey().isBlank()) {
            throw new RuntimeException("La guia de remision no tiene CDR SUNAT registrado");
        }
        byte[] bytes = documentStorageService.download(guia.getSunatCdrKey());
        String nombre = guia.getSunatCdrNombre() != null ? guia.getSunatCdrNombre() : "cdr.zip";
        return new ArchivoDescargable(nombre, "application/zip", bytes);
    }

    // ────────────── Emision interna ──────────────

    private void emitirInternamente(GuiaRemision guia) {
        prepararGuiaParaEmision(guia);
        guia.setSunatEstado(SunatEstado.ENVIANDO);
        guia.setSunatMensaje("Procesando envio de la guia a SUNAT.");
        guiaRemisionRepository.save(guia);

        try {
            emissionService.emitir(guia);
        } catch (RuntimeException e) {
            log.error("Error al emitir guia {}: {}", guia.getIdGuiaRemision(), e.getMessage(), e);
            guia.setSunatEstado(SunatEstado.ERROR_DEFINITIVO);
            guia.setSunatCodigo("EXCEPTION");
            guia.setSunatMensaje(e.getMessage() == null
                    ? "Ocurrio un error al emitir la guia en SUNAT"
                    : e.getMessage());
            guia.setSunatRespondidoAt(nowLima());
        }
        guiaRemisionRepository.save(guia);
    }

    private void prepararGuiaParaEmision(GuiaRemision guia) {
        if (ESTADO_ANULADA.equalsIgnoreCase(guia.getEstado())) {
            throw new RuntimeException("No se puede emitir una guia anulada");
        }
        if (ESTADO_ACEPTADA.equalsIgnoreCase(guia.getEstado())) {
            throw new RuntimeException("La guia ya fue aceptada por SUNAT");
        }

        List<GuiaRemisionDetalle> detalles = detalleRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaRemisionDetalleAsc(
                        guia.getIdGuiaRemision());
        List<GuiaRemisionDocumentoRelacionado> documentosRelacionados = documentoRelacionadoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaDocumentoRelacionadoAsc(
                        guia.getIdGuiaRemision());
        List<GuiaRemisionConductor> conductores = conductorRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision());
        List<GuiaRemisionTransportista> transportistas = transportistaRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(guia.getIdGuiaRemision());
        List<GuiaRemisionVehiculo> vehiculos = vehiculoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision());

        validarAntesDeEmitir(guia, detalles, documentosRelacionados, conductores, transportistas, vehiculos);

        guia.setEstado(ESTADO_EMITIDA);
        guia.setSunatEstado(SunatEstado.PENDIENTE_ENVIO);
        guia.setSunatCodigo(null);
        guia.setSunatMensaje("Guia programada para envio a SUNAT.");
        guia.setSunatHash(null);
        guia.setSunatTicket(null);
        guia.setSunatXmlNombre(null);
        guia.setSunatXmlKey(null);
        guia.setSunatZipNombre(null);
        guia.setSunatCdrNombre(null);
        guia.setSunatCdrKey(null);
        guia.setSunatEnviadoAt(null);
        guia.setSunatRespondidoAt(null);
    }

    private void validarAntesDeEmitir(
            GuiaRemision guia,
            List<GuiaRemisionDetalle> detalles,
            List<GuiaRemisionDocumentoRelacionado> documentosRelacionados,
            List<GuiaRemisionConductor> conductores,
            List<GuiaRemisionTransportista> transportistas,
            List<GuiaRemisionVehiculo> vehiculos) {

        guia.setMotivoTraslado(normalizarMotivoTraslado(guia.getMotivoTraslado()));
        guia.setDescripcionMotivo(resolverDescripcionMotivo(
                guia.getMotivoTraslado(), guia.getDescripcionMotivo()));
        if (guia.getFechaEmision() == null) {
            throw new RuntimeException("La guia debe tener fechaEmision");
        }
        if (guia.getFechaInicioTraslado() == null) {
            throw new RuntimeException("La guia debe tener fechaInicioTraslado");
        }
        if (guia.getFechaInicioTraslado().isBefore(guia.getFechaEmision().toLocalDate())) {
            throw new RuntimeException("fechaInicioTraslado no puede ser menor a fechaEmision");
        }

        validarSerie(guia.getSerie());
        validarUbigeo(guia.getUbigeoPartida(), "ubigeoPartida");
        validarUbigeo(guia.getUbigeoLlegada(), "ubigeoLlegada");
        validarDireccionTraslado(guia.getDireccionPartida(), "direccionPartida");
        validarDireccionTraslado(guia.getDireccionLlegada(), "direccionLlegada");
        validarPuntosTraslado(
                guia.getMotivoTraslado(),
                resolverEmpresa(guia.getSucursal()),
                guia.getSucursalPartida(),
                guia.getSucursalLlegada());
        validarSucursalesDeclaradasSunatSiAplican(guia);

        if (guia.getPesoBrutoTotal() == null || guia.getPesoBrutoTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("pesoBrutoTotal debe ser mayor a 0");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("La guia debe incluir al menos un detalle");
        }
        for (GuiaRemisionDetalle detalle : detalles) {
            if (detalle.getCantidad() == null || detalle.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Cada detalle debe tener cantidad mayor a 0");
            }
        }

        validarDestinatario(guia);
        validarDocumentosRelacionados(guia.getMotivoTraslado(), documentosRelacionados);

        // Validar transporte segun modalidad
        String modalidad = guia.getModalidadTransporte() == null ? "" : guia.getModalidadTransporte().trim();
        if ("02".equals(modalidad)) {
            if (vehiculos == null || vehiculos.isEmpty()) {
                throw new RuntimeException("La modalidad privada requiere al menos un vehiculo");
            }
            for (GuiaRemisionVehiculo v : vehiculos) {
                validarPlacaSunat(v.getPlaca());
            }
            if (conductores == null || conductores.isEmpty()) {
                throw new RuntimeException("La modalidad privada requiere al menos un conductor");
            }
            long principales = conductores.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getEsPrincipal()))
                    .count();
            if (principales != 1) {
                throw new RuntimeException("La modalidad privada requiere exactamente un conductor principal");
            }
            for (GuiaRemisionConductor c : conductores) {
                validarLicenciaConductor(c.getLicencia());
            }
        } else if ("01".equals(modalidad)) {
            if (transportistas == null || transportistas.isEmpty()) {
                throw new RuntimeException("La modalidad publica requiere al menos un transportista");
            }
            if (guia.getFechaEntregaTransportista() == null) {
                throw new RuntimeException("La modalidad publica requiere fechaEntregaTransportista");
            }
            if (guia.getFechaEntregaTransportista().isBefore(guia.getFechaEmision().toLocalDate())) {
                throw new RuntimeException("fechaEntregaTransportista no puede ser menor a fechaEmision");
            }
            if (guia.getFechaInicioTraslado().isBefore(guia.getFechaEntregaTransportista())) {
                throw new RuntimeException("fechaInicioTraslado debe ser mayor o igual a fechaEntregaTransportista");
            }
            for (GuiaRemisionTransportista t : transportistas) {
                if (t.getTransportistaNroDoc() == null
                        || !t.getTransportistaNroDoc().trim().matches("\\d{11}")) {
                    throw new RuntimeException("El transportista debe tener RUC valido de 11 digitos");
                }
            }
        } else {
            throw new RuntimeException("modalidadTransporte permitida: 01 (publica) o 02 (privada)");
        }
    }

    // ────────────── Guardar sub-entidades ──────────────

    private void guardarDetalles(GuiaRemision guia, List<GuiaRemisionDetalleCreateItem> detalles) {
        if (detalles == null || detalles.isEmpty()) return;
        List<GuiaRemisionDetalle> entities = new ArrayList<>();
        for (GuiaRemisionDetalleCreateItem item : detalles) {
            ProductoVariante variante = productoVarianteRepository.findById(item.idProductoVariante())
                    .orElseThrow(() -> new RuntimeException(
                            "Producto variante no encontrado: " + item.idProductoVariante()));

            GuiaRemisionDetalle detalle = new GuiaRemisionDetalle();
            detalle.setGuiaRemision(guia);
            detalle.setProductoVariante(variante);
            detalle.setDescripcion(resolverDescripcion(item, variante));
            detalle.setCantidad(item.cantidad());
            detalle.setUnidadMedida(normalizeUnitCode(item.unidadMedida(), "NIU"));
            detalle.setCodigoProducto(normalizeText(item.codigoProducto(), 30));
            detalle.setPesoUnitario(item.pesoUnitario());
            detalle.setActivo(ACTIVO);
            entities.add(detalle);
        }
        detalleRepository.saveAll(entities);
    }

    private void guardarDocumentosRelacionados(
            GuiaRemision guia,
            List<GuiaRemisionDocumentoRelacionadoRequest> documentosRelacionados) {
        if (documentosRelacionados == null || documentosRelacionados.isEmpty()) return;
        List<GuiaRemisionDocumentoRelacionado> entities = new ArrayList<>();
        for (GuiaRemisionDocumentoRelacionadoRequest request : documentosRelacionados) {
            GuiaRemisionDocumentoRelacionado documento = new GuiaRemisionDocumentoRelacionado();
            documento.setGuiaRemision(guia);
            aplicarDocumentoRelacionado(documento, request);
            entities.add(documento);
        }
        documentoRelacionadoRepository.saveAll(entities);
    }

    private void aplicarDocumentoRelacionado(
            GuiaRemisionDocumentoRelacionado documento,
            GuiaRemisionDocumentoRelacionadoRequest request) {
        if (request == null) {
            throw new RuntimeException("Los datos del documento relacionado son obligatorios");
        }
        String tipoDocumento = normalizeRequiredText(request.tipoDocumento(), "tipoDocumento", 2);
        if (!TIPOS_DOCUMENTO_RELACIONADO_PERMITIDOS.contains(tipoDocumento)) {
            throw new RuntimeException("tipoDocumento relacionado permitido: 01 factura, 03 boleta o 04 liquidacion de compra");
        }
        String serie = normalizeRequiredText(request.serie(), "serie documento relacionado", 4)
                .toUpperCase(Locale.ROOT);
        if (!serie.matches("[A-Z0-9]{4}")) {
            throw new RuntimeException("La serie del documento relacionado debe tener 4 caracteres alfanumericos");
        }
        String numero = normalizeRequiredText(request.numero(), "numero documento relacionado", 20)
                .replace("-", "");
        if (!numero.matches("\\d{1,20}")) {
            throw new RuntimeException("El numero del documento relacionado debe ser numerico");
        }
        documento.setTipoDocumento(tipoDocumento);
        documento.setSerie(serie);
        documento.setNumero(numero);
        documento.setActivo(ACTIVO);
        documento.setDeletedAt(null);
    }

    private void guardarConductores(GuiaRemision guia, List<GuiaRemisionConductorRequest> conductores) {
        if (conductores == null || conductores.isEmpty()) return;
        List<GuiaRemisionConductor> entities = new ArrayList<>();
        for (GuiaRemisionConductorRequest req : conductores) {
            entities.add(construirConductor(guia, req));
        }
        conductorRepository.saveAll(entities);
    }

    private String normalizeUnitCode(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void guardarTransportistas(GuiaRemision guia, List<GuiaRemisionTransportistaRequest> transportistas) {
        if (transportistas == null || transportistas.isEmpty()) return;
        List<GuiaRemisionTransportista> entities = new ArrayList<>();
        for (GuiaRemisionTransportistaRequest req : transportistas) {
            entities.add(construirTransportista(guia, req));
        }
        transportistaRepository.saveAll(entities);
    }

    private void guardarVehiculos(GuiaRemision guia, List<GuiaRemisionVehiculoRequest> vehiculos) {
        if (vehiculos == null || vehiculos.isEmpty()) return;
        List<GuiaRemisionVehiculo> entities = new ArrayList<>();
        for (GuiaRemisionVehiculoRequest req : vehiculos) {
            entities.add(construirVehiculo(guia, req));
        }
        vehiculoRepository.saveAll(entities);
    }

    private void guardarConductoresSegunFuente(
            GuiaRemision guia,
            List<GuiaRemisionConductorRequest> conductores,
            List<Integer> idsCatalogoConductores,
            Integer idEmpresa) {
        validarFuentesExclusivas(conductores, idsCatalogoConductores, "conductores");
        if (idsCatalogoConductores != null && !idsCatalogoConductores.isEmpty()) {
            guardarConductoresDesdeCatalogo(guia, idsCatalogoConductores, idEmpresa);
            return;
        }
        guardarConductores(guia, conductores);
    }

    private void guardarConductoresDesdeCatalogo(
            GuiaRemision guia, List<Integer> idsCatalogoConductores, Integer idEmpresa) {
        List<GuiaRemisionCatalogoConductor> catalogos = guiaRemisionCatalogoService
                .obtenerConductoresPorIds(idsCatalogoConductores, idEmpresa);
        if (catalogos.isEmpty()) return;
        List<GuiaRemisionConductor> entities = catalogos.stream()
                .map(catalogo -> construirConductor(guia, catalogo))
                .toList();
        conductorRepository.saveAll(entities);
    }

    private void guardarTransportistasSegunFuente(
            GuiaRemision guia,
            List<GuiaRemisionTransportistaRequest> transportistas,
            List<Integer> idsCatalogoTransportistas,
            Integer idEmpresa) {
        validarFuentesExclusivas(transportistas, idsCatalogoTransportistas, "transportistas");
        if (idsCatalogoTransportistas != null && !idsCatalogoTransportistas.isEmpty()) {
            guardarTransportistasDesdeCatalogo(guia, idsCatalogoTransportistas, idEmpresa);
            return;
        }
        guardarTransportistas(guia, transportistas);
    }

    private void guardarTransportistasDesdeCatalogo(
            GuiaRemision guia, List<Integer> idsCatalogoTransportistas, Integer idEmpresa) {
        List<GuiaRemisionCatalogoTransportista> catalogos = guiaRemisionCatalogoService
                .obtenerTransportistasPorIds(idsCatalogoTransportistas, idEmpresa);
        if (catalogos.isEmpty()) return;
        List<GuiaRemisionTransportista> entities = catalogos.stream()
                .map(catalogo -> construirTransportista(guia, catalogo))
                .toList();
        transportistaRepository.saveAll(entities);
    }

    private void guardarVehiculosSegunFuente(
            GuiaRemision guia,
            List<GuiaRemisionVehiculoRequest> vehiculos,
            List<Integer> idsCatalogoVehiculos,
            Integer idEmpresa) {
        validarFuentesExclusivas(vehiculos, idsCatalogoVehiculos, "vehiculos");
        if (idsCatalogoVehiculos != null && !idsCatalogoVehiculos.isEmpty()) {
            guardarVehiculosDesdeCatalogo(guia, idsCatalogoVehiculos, idEmpresa);
            return;
        }
        guardarVehiculos(guia, vehiculos);
    }

    private void guardarVehiculosDesdeCatalogo(
            GuiaRemision guia, List<Integer> idsCatalogoVehiculos, Integer idEmpresa) {
        List<GuiaRemisionCatalogoVehiculo> catalogos = guiaRemisionCatalogoService
                .obtenerVehiculosPorIds(idsCatalogoVehiculos, idEmpresa);
        if (catalogos.isEmpty()) return;
        List<GuiaRemisionVehiculo> entities = catalogos.stream()
                .map(catalogo -> construirVehiculo(guia, catalogo))
                .toList();
        vehiculoRepository.saveAll(entities);
    }

    private GuiaRemisionConductor construirConductor(
            GuiaRemision guia, GuiaRemisionConductorRequest request) {
        GuiaRemisionConductor conductor = new GuiaRemisionConductor();
        conductor.setGuiaRemision(guia);
        conductor.setActivo(ACTIVO);
        conductor.setDeletedAt(null);
        aplicarConductor(conductor, request);
        return conductor;
    }

    private GuiaRemisionConductor construirConductor(
            GuiaRemision guia, GuiaRemisionCatalogoConductor catalogo) {
        return construirConductor(guia, new GuiaRemisionConductorRequest(
                catalogo.getTipoDocumento(),
                catalogo.getNroDocumento(),
                catalogo.getNombres(),
                catalogo.getApellidos(),
                catalogo.getLicencia(),
                catalogo.getEsPrincipal()));
    }

    private void aplicarConductor(GuiaRemisionConductor conductor, GuiaRemisionConductorRequest request) {
        if (request == null) {
            throw new RuntimeException("Los datos del conductor son obligatorios");
        }
        conductor.setTipoDocumento(request.tipoDocumento() != null ? request.tipoDocumento().trim() : "1");
        conductor.setNroDocumento(requireText(request.nroDocumento(),
                "El nroDocumento del conductor es obligatorio"));
        conductor.setNombres(requireText(request.nombres(),
                "Los nombres del conductor son obligatorios"));
        conductor.setApellidos(requireText(request.apellidos(),
                "Los apellidos del conductor son obligatorios"));
        conductor.setLicencia(normalizarLicenciaConductor(request.licencia()));
        conductor.setEsPrincipal(request.esPrincipal() != null ? request.esPrincipal() : true);
    }

    private GuiaRemisionTransportista construirTransportista(
            GuiaRemision guia, GuiaRemisionTransportistaRequest request) {
        GuiaRemisionTransportista transportista = new GuiaRemisionTransportista();
        transportista.setGuiaRemision(guia);
        transportista.setActivo(ACTIVO);
        transportista.setDeletedAt(null);
        aplicarTransportista(transportista, request);
        return transportista;
    }

    private GuiaRemisionTransportista construirTransportista(
            GuiaRemision guia, GuiaRemisionCatalogoTransportista catalogo) {
        return construirTransportista(guia, new GuiaRemisionTransportistaRequest(
                catalogo.getTransportistaTipoDoc(),
                catalogo.getTransportistaNroDoc(),
                catalogo.getTransportistaRazonSocial(),
                catalogo.getTransportistaRegistroMtc()));
    }

    private void aplicarTransportista(
            GuiaRemisionTransportista transportista, GuiaRemisionTransportistaRequest request) {
        if (request == null) {
            throw new RuntimeException("Los datos del transportista son obligatorios");
        }
        transportista.setTransportistaTipoDoc(
                request.transportistaTipoDoc() != null ? request.transportistaTipoDoc().trim() : "6");
        transportista.setTransportistaNroDoc(requireText(request.transportistaNroDoc(),
                "El nroDoc del transportista es obligatorio"));
        transportista.setTransportistaRazonSocial(requireText(request.transportistaRazonSocial(),
                "La razon social del transportista es obligatoria"));
        transportista.setTransportistaRegistroMtc(normalizeText(request.transportistaRegistroMtc(), 20));
    }

    private GuiaRemisionVehiculo construirVehiculo(
            GuiaRemision guia, GuiaRemisionVehiculoRequest request) {
        GuiaRemisionVehiculo vehiculo = new GuiaRemisionVehiculo();
        vehiculo.setGuiaRemision(guia);
        vehiculo.setActivo(ACTIVO);
        vehiculo.setDeletedAt(null);
        aplicarVehiculo(vehiculo, request);
        return vehiculo;
    }

    private GuiaRemisionVehiculo construirVehiculo(
            GuiaRemision guia, GuiaRemisionCatalogoVehiculo catalogo) {
        return construirVehiculo(guia, new GuiaRemisionVehiculoRequest(
                catalogo.getPlaca(),
                catalogo.getEsPrincipal()));
    }

    private void aplicarVehiculo(GuiaRemisionVehiculo vehiculo, GuiaRemisionVehiculoRequest request) {
        if (request == null) {
            throw new RuntimeException("Los datos del vehiculo son obligatorios");
        }
        vehiculo.setPlaca(normalizarPlacaSunat(request.placa()));
        vehiculo.setEsPrincipal(request.esPrincipal() != null ? request.esPrincipal() : true);
    }

    private void reemplazarDetalles(GuiaRemision guia, List<GuiaRemisionDetalleCreateItem> detalles) {
        if (detalles.isEmpty()) {
            throw new RuntimeException("La guia debe incluir al menos un detalle");
        }
        softDelete(detalleRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaRemisionDetalleAsc(
                        guia.getIdGuiaRemision()));
        guardarDetalles(guia, detalles);
    }

    private void reemplazarDocumentosRelacionados(
            GuiaRemision guia,
            List<GuiaRemisionDocumentoRelacionadoRequest> documentosRelacionados) {
        softDelete(documentoRelacionadoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaDocumentoRelacionadoAsc(
                        guia.getIdGuiaRemision()));
        guardarDocumentosRelacionados(guia, documentosRelacionados);
    }

    private void reemplazarConductores(GuiaRemision guia, List<GuiaRemisionConductorRequest> conductores) {
        softDelete(conductorRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision()));
        guardarConductores(guia, conductores);
    }

    private void reemplazarTransportistas(GuiaRemision guia, List<GuiaRemisionTransportistaRequest> transportistas) {
        softDelete(transportistaRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(guia.getIdGuiaRemision()));
        guardarTransportistas(guia, transportistas);
    }

    private void reemplazarVehiculos(GuiaRemision guia, List<GuiaRemisionVehiculoRequest> vehiculos) {
        softDelete(vehiculoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision()));
        guardarVehiculos(guia, vehiculos);
    }

    private void reemplazarConductoresSegunFuente(
            GuiaRemision guia,
            List<GuiaRemisionConductorRequest> conductores,
            List<Integer> idsCatalogoConductores,
            Integer idEmpresa) {
        softDelete(conductorRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision()));
        guardarConductoresSegunFuente(guia, conductores, idsCatalogoConductores, idEmpresa);
    }

    private void reemplazarTransportistasSegunFuente(
            GuiaRemision guia,
            List<GuiaRemisionTransportistaRequest> transportistas,
            List<Integer> idsCatalogoTransportistas,
            Integer idEmpresa) {
        softDelete(transportistaRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(guia.getIdGuiaRemision()));
        guardarTransportistasSegunFuente(guia, transportistas, idsCatalogoTransportistas, idEmpresa);
    }

    private void reemplazarVehiculosSegunFuente(
            GuiaRemision guia,
            List<GuiaRemisionVehiculoRequest> vehiculos,
            List<Integer> idsCatalogoVehiculos,
            Integer idEmpresa) {
        softDelete(vehiculoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision()));
        guardarVehiculosSegunFuente(guia, vehiculos, idsCatalogoVehiculos, idEmpresa);
    }

    private void validarFuentesExclusivas(List<?> directos, List<Integer> idsCatalogo, String label) {
        boolean tieneDirectos = directos != null && !directos.isEmpty();
        boolean tieneIds = idsCatalogo != null && !idsCatalogo.isEmpty();
        if (tieneDirectos && tieneIds) {
            throw new RuntimeException(
                    "Para " + label + " envie solo una fuente: datos directos o IDs de catalogo");
        }
    }

    private <T> void softDelete(List<T> entities) {
        LocalDateTime now = nowLima();
        for (T entity : entities) {
            if (entity instanceof GuiaRemisionDetalle d) { d.setActivo(INACTIVO); d.setDeletedAt(now); }
            else if (entity instanceof GuiaRemisionDocumentoRelacionado d) { d.setActivo(INACTIVO); d.setDeletedAt(now); }
            else if (entity instanceof GuiaRemisionConductor c) { c.setActivo(INACTIVO); c.setDeletedAt(now); }
            else if (entity instanceof GuiaRemisionTransportista t) { t.setActivo(INACTIVO); t.setDeletedAt(now); }
            else if (entity instanceof GuiaRemisionVehiculo v) { v.setActivo(INACTIVO); v.setDeletedAt(now); }
        }
    }

    // ────────────── Response builders ──────────────

    private GuiaRemisionResponse toResponseResumen(GuiaRemision guia) {
        return buildResponse(guia, null, null, null, null, null);
    }

    private GuiaRemisionResponse toResponseCompleto(GuiaRemision guia) {
        List<GuiaRemisionDetalleResponse> detalleResponses = detalleRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaRemisionDetalleAsc(
                        guia.getIdGuiaRemision())
                .stream()
                .map(d -> new GuiaRemisionDetalleResponse(
                        d.getIdGuiaRemisionDetalle(),
                        d.getProductoVariante() != null ? d.getProductoVariante().getIdProductoVariante() : null,
                        d.getProductoVariante() != null ? d.getProductoVariante().getSku() : null,
                        d.getProductoVariante() != null && d.getProductoVariante().getProducto() != null
                                ? d.getProductoVariante().getProducto().getNombre() : null,
                        d.getDescripcion(),
                        d.getCantidad(),
                        d.getUnidadMedida(),
                        d.getCodigoProducto(),
                        d.getPesoUnitario()))
                .toList();

        List<GuiaRemisionResponse.ConductorResponse> conductorResponses = conductorRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision())
                .stream()
                .map(this::toConductorResponse)
                .toList();

        List<GuiaRemisionResponse.TransportistaResponse> transportistaResponses = transportistaRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(guia.getIdGuiaRemision())
                .stream()
                .map(this::toTransportistaResponse)
                .toList();

        List<GuiaRemisionResponse.VehiculoResponse> vehiculoResponses = vehiculoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                        guia.getIdGuiaRemision())
                .stream()
                .map(this::toVehiculoResponse)
                .toList();

        List<GuiaRemisionDocumentoRelacionadoResponse> documentoResponses = documentoRelacionadoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaDocumentoRelacionadoAsc(
                        guia.getIdGuiaRemision())
                .stream()
                .map(this::toDocumentoRelacionadoResponse)
                .toList();

        return buildResponse(
                guia,
                detalleResponses,
                documentoResponses,
                conductorResponses,
                transportistaResponses,
                vehiculoResponses);
    }

    private GuiaRemisionResponse buildResponse(
            GuiaRemision guia,
            List<GuiaRemisionDetalleResponse> detalles,
            List<GuiaRemisionDocumentoRelacionadoResponse> documentosRelacionados,
            List<GuiaRemisionResponse.ConductorResponse> conductores,
            List<GuiaRemisionResponse.TransportistaResponse> transportistas,
            List<GuiaRemisionResponse.VehiculoResponse> vehiculos) {

        return new GuiaRemisionResponse(
                guia.getIdGuiaRemision(),
                SunatGuiaRemisionXmlBuilderService.numeroGuia(guia),
                guia.getFechaEmision(),
                guia.getFechaInicioTraslado(),
                guia.getFechaEntregaTransportista(),
                guia.getMotivoTraslado(),
                guia.getDescripcionMotivo(),
                guia.getModalidadTransporte(),
                guia.getPesoBrutoTotal(),
                guia.getUnidadPeso(),
                guia.getNumeroBultos(),
                guia.getObservaciones(),
                guia.getUbigeoPartida(),
                guia.getDireccionPartida(),
                guia.getSucursalPartida() != null ? guia.getSucursalPartida().getIdSucursal() : null,
                guia.getSucursalPartida() != null ? guia.getSucursalPartida().getNombre() : null,
                guia.getUbigeoLlegada(),
                guia.getDireccionLlegada(),
                guia.getSucursalLlegada() != null ? guia.getSucursalLlegada().getIdSucursal() : null,
                guia.getSucursalLlegada() != null ? guia.getSucursalLlegada().getNombre() : null,
                guia.getDestinatarioTipoDoc(),
                guia.getDestinatarioNroDoc(),
                guia.getDestinatarioRazonSocial(),
                guia.getEstado(),
                guia.getUsuario() != null ? guia.getUsuario().getIdUsuario() : null,
                guia.getUsuario() != null ? guia.getUsuario().getNombre() : null,
                guia.getSucursal() != null ? guia.getSucursal().getIdSucursal() : null,
                guia.getSucursal() != null ? guia.getSucursal().getNombre() : null,
                detalles, documentosRelacionados, conductores, transportistas, vehiculos,
                guia.getSunatEstado(),
                guia.getSunatCodigo(),
                guia.getSunatMensaje(),
                guia.getSunatHash(),
                guia.getSunatTicket(),
                guia.getSunatXmlNombre(),
                guia.getSunatZipNombre(),
                guia.getSunatCdrNombre(),
                guia.getSunatEnviadoAt(),
                guia.getSunatRespondidoAt(),
                null);
    }

    private String numeroGuiaRemision(GuiaRemision guia) {
        return SunatGuiaRemisionXmlBuilderService.numeroGuia(guia);
    }

    private GuiaRemisionDocumentoRelacionadoResponse toDocumentoRelacionadoResponse(
            GuiaRemisionDocumentoRelacionado documento) {
        String numeroDocumento = documento.getSerie() + "-" + documento.getNumero();
        return new GuiaRemisionDocumentoRelacionadoResponse(
                documento.getIdGuiaDocumentoRelacionado(),
                documento.getTipoDocumento(),
                documento.getSerie(),
                documento.getNumero(),
                numeroDocumento);
    }

    private GuiaRemisionResponse.ConductorResponse toConductorResponse(GuiaRemisionConductor conductor) {
        return new GuiaRemisionResponse.ConductorResponse(
                conductor.getIdGuiaConductor(),
                conductor.getTipoDocumento(),
                conductor.getNroDocumento(),
                conductor.getNombres(),
                conductor.getApellidos(),
                conductor.getLicencia(),
                conductor.getEsPrincipal());
    }

    private GuiaRemisionResponse.TransportistaResponse toTransportistaResponse(
            GuiaRemisionTransportista transportista) {
        return new GuiaRemisionResponse.TransportistaResponse(
                transportista.getIdGuiaTransportista(),
                transportista.getTransportistaTipoDoc(),
                transportista.getTransportistaNroDoc(),
                transportista.getTransportistaRazonSocial(),
                transportista.getTransportistaRegistroMtc());
    }

    private GuiaRemisionResponse.VehiculoResponse toVehiculoResponse(GuiaRemisionVehiculo vehiculo) {
        return new GuiaRemisionResponse.VehiculoResponse(
                vehiculo.getIdGuiaVehiculo(),
                vehiculo.getPlaca(),
                vehiculo.getEsPrincipal());
    }

    // ────────────── Validaciones GRE remitente ──────────────

    private String normalizarMotivoTraslado(String motivo) {
        String normalized = motivo == null || motivo.isBlank()
                ? MOTIVO_TRASLADO_INTERNO
                : motivo.trim();
        if (!MOTIVOS_TRASLADO_PERMITIDOS.containsKey(normalized)) {
            throw new RuntimeException("motivoTraslado permitido: 01, 02, 03, 04, 05, 06, 07, 13, 14 o 17");
        }
        return normalized;
    }

    private String resolverDescripcionMotivo(String motivoTraslado, String descripcionMotivo) {
        String motivo = normalizarMotivoTraslado(motivoTraslado);
        if (MOTIVO_TRASLADO_OTROS.equals(motivo)) {
            return normalizeRequiredText(descripcionMotivo, "descripcionMotivo", 255);
        }
        String descripcion = normalizeText(descripcionMotivo, 255);
        return descripcion != null ? descripcion : MOTIVOS_TRASLADO_PERMITIDOS.get(motivo);
    }

    private Sucursal resolverSucursalOpcional(Integer idSucursal, String label) {
        if (idSucursal == null) return null;
        return sucursalRepository.findById(idSucursal)
                .orElseThrow(() -> new RuntimeException(
                        "Sucursal de " + label + " no encontrada: " + idSucursal));
    }

    private String resolverDireccionTraslado(String direccionManual, Sucursal sucursal, String label) {
        if (sucursal != null) return resolverDireccionSucursal(sucursal, label);
        return normalizeRequiredText(direccionManual, "direccion" + capitalizar(label), 255);
    }

    private String resolverUbigeoTraslado(String ubigeoManual, Sucursal sucursal, String label) {
        if (sucursal != null) return resolverUbigeo(ubigeoManual, sucursal, label);
        String fieldName = "ubigeo" + capitalizar(label);
        validarUbigeo(ubigeoManual, fieldName);
        return ubigeoManual.trim();
    }

    private void validarPuntosTraslado(
            String motivoTraslado, Empresa empresa, Sucursal partida, Sucursal llegada) {
        String motivo = normalizarMotivoTraslado(motivoTraslado);
        if (MOTIVO_TRASLADO_INTERNO.equals(motivo)) {
            validarSucursalesTrasladoInterno(partida, llegada);
            validarSucursalesDeEmpresaEmisora(empresa, partida, llegada);
            return;
        }
        validarSucursalDeEmpresaSiFueSeleccionada(empresa, partida, "partida");
        validarSucursalDeEmpresaSiFueSeleccionada(empresa, llegada, "llegada");
    }

    private void validarSucursalDeEmpresaSiFueSeleccionada(Empresa empresa, Sucursal sucursal, String label) {
        if (sucursal == null) return;
        Integer idEmpresaEmisora = empresa != null ? empresa.getIdEmpresa() : null;
        Integer idEmpresaSucursal = sucursal.getEmpresa() != null ? sucursal.getEmpresa().getIdEmpresa() : null;
        if (idEmpresaEmisora == null || !idEmpresaEmisora.equals(idEmpresaSucursal)) {
            throw new RuntimeException("La sucursal de " + label
                    + " debe pertenecer a la empresa emisora de la guia");
        }
    }

    private void aplicarDestinatario(
            GuiaRemision guia,
            String motivoTraslado,
            Empresa empresa,
            String tipoDoc,
            String nroDoc,
            String razonSocial) {
        String motivo = normalizarMotivoTraslado(motivoTraslado);
        boolean usarEmpresa = MOTIVOS_CON_DESTINATARIO_EMPRESA_POR_DEFECTO.contains(motivo)
                && (tipoDoc == null || tipoDoc.isBlank()
                || nroDoc == null || nroDoc.isBlank()
                || razonSocial == null || razonSocial.isBlank());

        if (usarEmpresa) {
            guia.setDestinatarioTipoDoc(TIPO_DOC_RUC);
            guia.setDestinatarioNroDoc(empresa.getRuc().trim());
            guia.setDestinatarioRazonSocial(empresa.getRazonSocial().trim());
            return;
        }

        guia.setDestinatarioTipoDoc(normalizeRequiredText(tipoDoc, "destinatarioTipoDoc", 1));
        guia.setDestinatarioNroDoc(normalizeRequiredText(nroDoc, "destinatarioNroDoc", 20));
        guia.setDestinatarioRazonSocial(normalizeRequiredText(razonSocial, "destinatarioRazonSocial", 255));
    }

    private void validarDestinatario(GuiaRemision guia) {
        String tipoDoc = normalizeRequiredText(guia.getDestinatarioTipoDoc(), "destinatarioTipoDoc", 1);
        String nroDoc = normalizeRequiredText(guia.getDestinatarioNroDoc(), "destinatarioNroDoc", 20);
        normalizeRequiredText(guia.getDestinatarioRazonSocial(), "destinatarioRazonSocial", 255);
        if (TIPO_DOC_RUC.equals(tipoDoc) && !nroDoc.matches("\\d{11}")) {
            throw new RuntimeException("El destinatario con tipoDoc 6 debe tener RUC valido de 11 digitos");
        }
        if ("1".equals(tipoDoc) && !nroDoc.matches("\\d{8}")) {
            throw new RuntimeException("El destinatario con tipoDoc 1 debe tener DNI valido de 8 digitos");
        }
    }

    private void validarDocumentosRelacionados(
            String motivoTraslado,
            List<GuiaRemisionDocumentoRelacionado> documentosRelacionados) {
        boolean requiereDocumento = MOTIVOS_REQUIEREN_DOCUMENTO_RELACIONADO.contains(motivoTraslado);
        if (requiereDocumento && (documentosRelacionados == null || documentosRelacionados.isEmpty())) {
            throw new RuntimeException("El motivo " + motivoTraslado
                    + " requiere al menos un documento relacionado SUNAT");
        }
        if (documentosRelacionados == null) return;

        for (GuiaRemisionDocumentoRelacionado documento : documentosRelacionados) {
            String tipoDocumento = normalizeRequiredText(documento.getTipoDocumento(), "tipoDocumento", 2);
            if (!TIPOS_DOCUMENTO_RELACIONADO_PERMITIDOS.contains(tipoDocumento)) {
                throw new RuntimeException("tipoDocumento relacionado permitido: 01 factura, 03 boleta o 04 liquidacion de compra");
            }
            normalizeRequiredText(documento.getSerie(), "serie documento relacionado", 4);
            normalizeRequiredText(documento.getNumero(), "numero documento relacionado", 20);
        }

        if ("01".equals(motivoTraslado) || "03".equals(motivoTraslado)) {
            boolean tieneComprobanteVenta = documentosRelacionados.stream()
                    .anyMatch(d -> "01".equals(d.getTipoDocumento()) || "03".equals(d.getTipoDocumento()));
            if (!tieneComprobanteVenta) {
                throw new RuntimeException("El motivo " + motivoTraslado
                        + " requiere factura (01) o boleta (03) relacionada");
            }
        }

        if ("02".equals(motivoTraslado)) {
            boolean tieneCompra = documentosRelacionados.stream()
                    .anyMatch(d -> "01".equals(d.getTipoDocumento()) || "03".equals(d.getTipoDocumento())
                            || "04".equals(d.getTipoDocumento()));
            if (!tieneCompra) {
                throw new RuntimeException("El motivo 02 requiere factura, boleta o liquidacion de compra relacionada");
            }
        }
    }

    private void validarDireccionTraslado(String direccion, String fieldName) {
        normalizeRequiredText(direccion, fieldName, 255);
    }

    private void validarEmpresaEmisora(Empresa empresa) {
        if (empresa == null || empresa.getRuc() == null || empresa.getRuc().trim().length() != 11) {
            throw new RuntimeException("La empresa emisora debe tener RUC de 11 digitos");
        }
        if (empresa.getRazonSocial() == null || empresa.getRazonSocial().isBlank()) {
            throw new RuntimeException("La empresa emisora debe tener razon social");
        }
    }

    private void validarSucursalesTrasladoInterno(Sucursal partida, Sucursal llegada) {
        if (partida == null || llegada == null) {
            throw new RuntimeException("La sucursal de partida y llegada son obligatorias");
        }
        if (partida.getIdSucursal().equals(llegada.getIdSucursal())) {
            throw new RuntimeException("La sucursal de partida y llegada no pueden ser la misma");
        }
        Integer idEmpPartida = partida.getEmpresa() != null ? partida.getEmpresa().getIdEmpresa() : null;
        Integer idEmpLlegada = llegada.getEmpresa() != null ? llegada.getEmpresa().getIdEmpresa() : null;
        if (idEmpPartida == null || !idEmpPartida.equals(idEmpLlegada)) {
            throw new RuntimeException(
                    "Ambas sucursales deben pertenecer a la misma empresa para traslado interno");
        }
    }

    private void validarSucursalesDeEmpresaEmisora(Empresa empresa, Sucursal partida, Sucursal llegada) {
        Integer idEmpresaEmisora = empresa != null ? empresa.getIdEmpresa() : null;
        Integer idEmpPartida = partida != null && partida.getEmpresa() != null
                ? partida.getEmpresa().getIdEmpresa()
                : null;
        Integer idEmpLlegada = llegada != null && llegada.getEmpresa() != null
                ? llegada.getEmpresa().getIdEmpresa()
                : null;
        if (idEmpresaEmisora == null
                || !idEmpresaEmisora.equals(idEmpPartida)
                || !idEmpresaEmisora.equals(idEmpLlegada)) {
            throw new RuntimeException(
                    "Las sucursales de partida y llegada deben pertenecer a la empresa emisora de la guia");
        }
    }

    private void validarSucursalesDeclaradasSunatSiAplican(GuiaRemision guia) {
        if (MOTIVO_TRASLADO_INTERNO.equals(guia.getMotivoTraslado())) {
            validarSucursalDeclaradaSunat(guia.getSucursalPartida(), "partida");
            validarSucursalDeclaradaSunat(guia.getSucursalLlegada(), "llegada");
            return;
        }
        if (guia.getSucursalPartida() != null) {
            validarSucursalDeclaradaSunat(guia.getSucursalPartida(), "partida");
        }
        if (guia.getSucursalLlegada() != null) {
            validarSucursalDeclaradaSunat(guia.getSucursalLlegada(), "llegada");
        }
    }

    private void validarSucursalDeclaradaSunat(Sucursal sucursal, String label) {
        if (sucursal == null) {
            throw new RuntimeException("La sucursal de " + label + " es obligatoria");
        }
        String codigo = sucursal.getCodigoEstablecimientoSunat();
        if (codigo == null || !codigo.trim().matches("\\d{4}")) {
            throw new RuntimeException("La sucursal de " + label + " (" + sucursal.getNombre()
                    + ") debe tener codigoEstablecimientoSunat de 4 digitos declarado en SUNAT/RUC");
        }
    }

    private void validarGuiaEditable(GuiaRemision guia, String accion) {
        if (!ESTADO_BORRADOR.equalsIgnoreCase(guia.getEstado())) {
            throw new RuntimeException("Solo se puede " + accion + " en guias con estado BORRADOR");
        }
    }

    private String resolverDireccionSucursal(Sucursal sucursal, String label) {
        String dir = sucursal.getDireccion();
        if (dir != null && !dir.isBlank()) return dir.trim();
        throw new RuntimeException("La sucursal de " + label + " (" + sucursal.getNombre()
                + ") no tiene direccion configurada");
    }

    private String resolverUbigeo(String override, Sucursal sucursal, String label) {
        String ubigeo = sucursal.getUbigeo();
        if (ubigeo != null && !ubigeo.isBlank() && ubigeo.trim().matches("\\d{6}")) {
            String ubigeoSucursal = ubigeo.trim();
            if (override != null && !override.isBlank() && !override.trim().equals(ubigeoSucursal)) {
                throw new RuntimeException("ubigeo" + label.substring(0, 1).toUpperCase()
                        + label.substring(1) + " debe coincidir con el ubigeo registrado en la sucursal");
            }
            return ubigeoSucursal;
        }
        throw new RuntimeException("No se pudo determinar el ubigeo de " + label
                + " para la sucursal " + sucursal.getNombre()
                + ". Configure el ubigeo en la sucursal");
    }

    // ────────────── Helpers ──────────────

    private GuiaRemision obtenerGuia(Integer id) {
        return guiaRemisionRepository.findByIdGuiaRemisionAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Guia de remision no encontrada: " + id));
    }

    private GuiaRemisionConductor obtenerConductorEntidad(Integer idGuiaRemision, Integer idGuiaConductor) {
        return conductorRepository
                .findByIdGuiaConductorAndGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(
                        idGuiaConductor, idGuiaRemision)
                .orElseThrow(() -> new RuntimeException("Conductor no encontrado en la guia: " + idGuiaConductor));
    }

    private GuiaRemisionTransportista obtenerTransportistaEntidad(
            Integer idGuiaRemision, Integer idGuiaTransportista) {
        return transportistaRepository
                .findByIdGuiaTransportistaAndGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(
                        idGuiaTransportista, idGuiaRemision)
                .orElseThrow(() -> new RuntimeException(
                        "Transportista no encontrado en la guia: " + idGuiaTransportista));
    }

    private GuiaRemisionVehiculo obtenerVehiculoEntidad(Integer idGuiaRemision, Integer idGuiaVehiculo) {
        return vehiculoRepository
                .findByIdGuiaVehiculoAndGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(
                        idGuiaVehiculo, idGuiaRemision)
                .orElseThrow(() -> new RuntimeException("Vehiculo no encontrado en la guia: " + idGuiaVehiculo));
    }

    private Usuario obtenerUsuarioAutenticado(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private Sucursal resolverSucursal(Usuario usuario) {
        if (usuario.getSucursal() != null) return usuario.getSucursal();
        Empresa empresa = empresaRepository.findTopByOrderByIdEmpresaAsc().orElse(null);
        if (empresa != null) {
            return sucursalRepository
                    .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSucursalAsc(empresa.getIdEmpresa())
                    .stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("No hay sucursales registradas en la empresa"));
        }
        throw new RuntimeException("No se pudo determinar la sucursal del usuario");
    }

    private Empresa resolverEmpresa(Sucursal sucursal) {
        if (sucursal != null && sucursal.getEmpresa() != null) return sucursal.getEmpresa();
        return empresaRepository.findTopByOrderByIdEmpresaAsc().orElse(null);
    }

    private Integer resolverSucursalFiltro(Usuario usuario, Integer idSucursalParam) {
        String rol = usuario.getRol() != null ? usuario.getRol().name() : "";
        if ("ADMINISTRADOR".equals(rol) || "SISTEMA".equals(rol)) return idSucursalParam;
        return usuario.getSucursal() != null ? usuario.getSucursal().getIdSucursal() : null;
    }

    private SunatEstado parseSunatEstado(String value) {
        if (value == null || value.isBlank()) return null;
        try { return SunatEstado.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String resolverDescripcion(GuiaRemisionDetalleCreateItem item, ProductoVariante variante) {
        if (item.descripcion() != null && !item.descripcion().isBlank()) return item.descripcion().trim();
        if (variante.getProducto() != null && variante.getProducto().getNombre() != null) {
            return variante.getProducto().getNombre().trim();
        }
        return "ITEM";
    }

    private GuiaRemisionDetalleCreateItem toGuiaDetalleDesdeVenta(VentaDetalle detalle) {
        ProductoVariante variante = detalle.getProductoVariante();
        String descripcion = !isBlank(detalle.getDescripcion())
                ? detalle.getDescripcion().trim()
                : descripcionProductoVariante(variante);
        String codigoProducto = variante != null && !isBlank(variante.getSku())
                ? variante.getSku().trim()
                : variante != null && !isBlank(variante.getCodigoBarras())
                ? variante.getCodigoBarras().trim()
                : null;
        return new GuiaRemisionDetalleCreateItem(
                variante != null ? variante.getIdProductoVariante() : null,
                descripcion,
                detalle.getCantidad() == null ? BigDecimal.ZERO : BigDecimal.valueOf(detalle.getCantidad()),
                normalizeUnitCode(detalle.getUnidadMedida(), "NIU"),
                codigoProducto,
                null);
    }

    private GuiaRemisionVentaAutocompleteResponse.ItemVenta toItemVentaAutocomplete(VentaDetalle detalle) {
        ProductoVariante variante = detalle.getProductoVariante();
        return new GuiaRemisionVentaAutocompleteResponse.ItemVenta(
                detalle.getIdVentaDetalle(),
                variante != null ? variante.getIdProductoVariante() : null,
                variante != null ? variante.getSku() : null,
                variante != null ? variante.getCodigoBarras() : null,
                variante != null && variante.getProducto() != null
                        ? variante.getProducto().getNombre()
                        : null,
                variante != null && variante.getColor() != null
                        ? variante.getColor().getNombre()
                        : null,
                variante != null && variante.getTalla() != null
                        ? variante.getTalla().getNombre()
                        : null,
                !isBlank(detalle.getDescripcion()) ? detalle.getDescripcion().trim() : descripcionProductoVariante(variante),
                detalle.getCantidad(),
                normalizeUnitCode(detalle.getUnidadMedida(), "NIU"),
                detalle.getPrecioUnitario(),
                detalle.getDescuento(),
                detalle.getIgvDetalle(),
                detalle.getSubtotal(),
                detalle.getTotalDetalle());
    }

    private String descripcionProductoVariante(ProductoVariante variante) {
        if (variante == null) return "ITEM";
        StringBuilder builder = new StringBuilder();
        if (variante.getProducto() != null && !isBlank(variante.getProducto().getNombre())) {
            builder.append(variante.getProducto().getNombre().trim());
        }
        if (variante.getColor() != null && !isBlank(variante.getColor().getNombre())) {
            if (builder.length() > 0) builder.append(" - ");
            builder.append(variante.getColor().getNombre().trim());
        }
        if (variante.getTalla() != null && !isBlank(variante.getTalla().getNombre())) {
            if (builder.length() > 0) builder.append(" - ");
            builder.append(variante.getTalla().getNombre().trim());
        }
        return builder.length() == 0 ? "ITEM" : builder.toString();
    }

    private GuiaRemisionVentaAutocompleteResponse.ClienteResumen toClienteResumen(Venta venta) {
        if (venta.getCliente() == null) return null;
        String tipoDocumentoSunat = tipoDocumentoSunatCliente(venta.getCliente().getTipoDocumento());
        return new GuiaRemisionVentaAutocompleteResponse.ClienteResumen(
                venta.getCliente().getIdCliente(),
                venta.getCliente().getTipoDocumento() != null
                        ? venta.getCliente().getTipoDocumento().name()
                        : null,
                tipoDocumentoSunat,
                venta.getCliente().getNroDocumento(),
                venta.getCliente().getNombres(),
                venta.getCliente().getDireccion());
    }

    private GuiaRemisionVentaAutocompleteResponse.SucursalResumen toSucursalResumen(Sucursal sucursal) {
        if (sucursal == null) return null;
        return new GuiaRemisionVentaAutocompleteResponse.SucursalResumen(
                sucursal.getIdSucursal(),
                sucursal.getNombre(),
                sucursal.getUbigeo(),
                sucursal.getDireccion(),
                sucursal.getCodigoEstablecimientoSunat());
    }

    private String tipoComprobanteVentaDesdeSunat(String tipoDocumento) {
        if (tipoDocumento == null || tipoDocumento.isBlank()) return null;
        return switch (tipoDocumento.trim().toUpperCase(Locale.ROOT)) {
            case "01", "FACTURA" -> "FACTURA";
            case "03", "BOLETA" -> "BOLETA";
            default -> throw new RuntimeException("tipoDocumento permitido para buscar venta: 01 factura o 03 boleta");
        };
    }

    private String tipoDocumentoSunatDesdeVenta(Venta venta) {
        if ("FACTURA".equalsIgnoreCase(venta.getTipoComprobante())) return "01";
        if ("BOLETA".equalsIgnoreCase(venta.getTipoComprobante())) return "03";
        throw new RuntimeException("La venta no es factura ni boleta");
    }

    private String tipoDocumentoSunatCliente(com.sistemapos.sistematextil.util.cliente.TipoDocumento tipoDocumento) {
        if (tipoDocumento == null) return null;
        return switch (tipoDocumento) {
            case DNI -> "1";
            case RUC -> "6";
            case CE -> "4";
            case SIN_DOC -> "0";
        };
    }

    private String normalizeSerieVenta(String serie) {
        if (serie == null || serie.isBlank()) {
            throw new RuntimeException("serie es obligatoria");
        }
        String normalized = serie.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{4}")) {
            throw new RuntimeException("serie debe tener 4 caracteres alfanumericos");
        }
        return normalized;
    }

    private Integer parseCorrelativoVenta(String numero) {
        if (numero == null || numero.isBlank()) {
            throw new RuntimeException("numero es obligatorio");
        }
        String normalized = numero.trim().replace("-", "");
        if (!normalized.matches("\\d{1,20}")) {
            throw new RuntimeException("numero debe ser numerico");
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new RuntimeException("numero de comprobante fuera de rango");
        }
    }

    private void validarAccesoVentaParaGuia(Usuario usuario, Venta venta) {
        String rol = usuario.getRol() != null ? usuario.getRol().name() : "";
        if ("ADMINISTRADOR".equals(rol) || "SISTEMA".equals(rol)) return;
        Integer idSucursalUsuario = usuario.getSucursal() != null ? usuario.getSucursal().getIdSucursal() : null;
        Integer idSucursalVenta = venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null;
        if (idSucursalUsuario == null || !idSucursalUsuario.equals(idSucursalVenta)) {
            throw new RuntimeException("No tiene permisos para consultar esta venta");
        }
    }

    private int obtenerSiguienteCorrelativo(String serie) {
        ComprobanteConfig config = comprobanteConfigRepository
                .findActivoForUpdate(TIPO_COMPROBANTE_GUIA_REMISION, serie)
                .orElseThrow(() -> new RuntimeException(
                        "No existe comprobante_config activo para GUIA_REMISION con serie " + serie));
        int ultimoConfigurado = config.getUltimoCorrelativo() == null ? 0 : config.getUltimoCorrelativo();
        int ultimoHistorico = guiaRemisionRepository.obtenerMaxCorrelativoHistoricoPorSerie(serie);
        int baseCorrelativo = Math.max(ultimoConfigurado, ultimoHistorico);
        if (baseCorrelativo != ultimoConfigurado) {
            log.warn(
                    "Correlativo GUIA_REMISION desfasado para serie {}. comprobante_config={}, historico={}. Se sincronizara automaticamente.",
                    serie, ultimoConfigurado, ultimoHistorico);
        }
        int siguiente = baseCorrelativo + 1;
        config.setUltimoCorrelativo(siguiente);
        comprobanteConfigRepository.save(config);
        return siguiente;
    }

    private String obtenerSerieGuiaRemisionPorDefecto() {
        return comprobanteConfigRepository
                .findTopByTipoComprobanteAndDeletedAtIsNullAndActivoOrderByIdComprobanteAsc(
                        TIPO_COMPROBANTE_GUIA_REMISION, ACTIVO)
                .map(ComprobanteConfig::getSerie)
                .orElse("T001");
    }

    private void validarSerie(String serie) {
        if (serie == null || !serie.matches("T\\d{3}")) {
            throw new RuntimeException("La serie de la guia debe tener formato T seguido de 3 digitos (ej: T001)");
        }
    }

    private void validarUbigeo(String ubigeo, String fieldName) {
        if (ubigeo == null || !ubigeo.trim().matches("\\d{6}")) {
            throw new RuntimeException(fieldName + " debe tener exactamente 6 digitos");
        }
    }

    private String normalizeSerie(String serie) {
        if (serie == null || serie.isBlank()) throw new RuntimeException("La serie es obligatoria");
        String normalizada = serie.trim().toUpperCase(Locale.ROOT);
        validarSerie(normalizada);
        return normalizada;
    }

    private String normalizeSearch(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String normalizeText(String value, int maxLen) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLen) {
        String normalized = normalizeText(value, maxLen);
        if (normalized == null) {
            throw new RuntimeException(fieldName + " es obligatorio");
        }
        return normalized;
    }

    private String capitalizar(String value) {
        if (value == null || value.isBlank()) return "";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String normalizarPlacaSunat(String placa) {
        String normalizada = requireText(placa, "La placa del vehiculo es obligatoria")
                .toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "");
        validarPlacaSunat(normalizada);
        return normalizada;
    }

    private void validarPlacaSunat(String placa) {
        if (placa == null || !placa.trim().matches("[A-Z0-9]{6,10}")) {
            throw new RuntimeException(
                    "La placa del vehiculo debe tener entre 6 y 10 caracteres alfanumericos, sin simbolos");
        }
    }

    private String normalizarLicenciaConductor(String licencia) {
        String normalizada = requireText(licencia, "La licencia del conductor es obligatoria")
                .toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "");
        validarLicenciaConductor(normalizada);
        return normalizada;
    }

    private void validarLicenciaConductor(String licencia) {
        if (licencia == null || !licencia.trim().matches("[A-Z0-9]{1,10}")) {
            throw new RuntimeException(
                    "La licencia del conductor debe tener hasta 10 caracteres alfanumericos, sin simbolos");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new RuntimeException(message);
        return value.trim();
    }

    private LocalDateTime nowLima() {
        return LocalDateTime.now(LIMA_ZONE);
    }

    public record ArchivoDescargable(String nombreArchivo, String contentType, byte[] bytes) {
    }
}
