package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.time.temporal.WeekFields;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Cotizacion;
import com.sistemapos.sistematextil.model.CotizacionDetalle;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.CotizacionDetalleRepository;
import com.sistemapos.sistematextil.repositories.CotizacionRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionCreateRequest;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionConvertirVentaRequest;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionConvertirVentaResponse;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionDetalleCreateItem;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionDetalleResponse;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionEstadoUpdateRequest;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionListItemResponse;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionReporteResponse;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionResponse;
import com.sistemapos.sistematextil.util.cotizacion.CotizacionUpdateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CotizacionService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal CERO_MONETARIO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String SERIE_DEFAULT = "COT";

    private final CotizacionRepository cotizacionRepository;
    private final CotizacionDetalleRepository cotizacionDetalleRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final ClienteRepository clienteRepository;
    private final VentaService ventaService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<CotizacionListItemResponse> listarPaginado(
            String term,
            Integer idUsuario,
            String estado,
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
        String estadoFiltro = normalizarEstadoCotizacionFiltro(estado);
        RangoFechas rangoFechasFiltro = resolverRangoFechasListado(periodo, fecha, desde, hasta);
        LocalDateTime fechaInicioFiltro = rangoFechasFiltro == null ? null : rangoFechasFiltro.desde().atStartOfDay();
        LocalDateTime fechaFinExclusiveFiltro = rangoFechasFiltro == null
                ? null
                : rangoFechasFiltro.hasta().plusDays(1).atStartOfDay();

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCotizacion").descending());
        Integer idSucursalFiltro = resolverIdSucursalListado(usuarioAutenticado, idSucursal);

        Page<Cotizacion> cotizaciones = cotizacionRepository.buscarConFiltros(
                termNormalizado,
                idSucursalFiltro,
                idUsuarioFiltro,
                estadoFiltro,
                fechaInicioFiltro,
                fechaFinExclusiveFiltro,
                pageable);

        return PagedResponse.fromPage(cotizaciones.map(this::toListItemResponse));
    }

    public CotizacionReporteResponse obtenerReporteCotizaciones(
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            String estado,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        FiltroReporteCotizacion filtro = resolverFiltroReporteCotizacion(
                usuarioAutenticado,
                agrupar,
                periodo,
                desde,
                hasta,
                idSucursal,
                estado);
        List<Cotizacion> cotizaciones = buscarCotizacionesParaReporte(filtro);

        return construirReporteCotizaciones(cotizaciones, filtro);
    }

    public CotizacionResponse obtenerDetalle(Integer idCotizacion, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        Cotizacion cotizacion = obtenerCotizacionConAlcance(idCotizacion, usuarioAutenticado);
        List<CotizacionDetalle> detalles = cotizacionDetalleRepository
                .findByCotizacion_IdCotizacionAndDeletedAtIsNullOrderByIdCotizacionDetalleAsc(cotizacion.getIdCotizacion());

        return toResponse(cotizacion, detalles);
    }

    @Transactional
    public CotizacionResponse insertar(CotizacionCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);

        Sucursal sucursal = resolverSucursalParaEscritura(request.idSucursal(), usuarioAutenticado);
        Cliente cliente = resolverCliente(request.idCliente(), sucursal.getIdSucursal());
        List<DetalleCalculado> detallesCalculados = calcularDetalles(request.detalles(), sucursal.getIdSucursal());
        TotalesCotizacion totales = calcularTotales(
                detallesCalculados,
                request.descuentoTotal(),
                request.tipoDescuento(),
                request.igvPorcentaje());
        NumeroCotizacion numeroCotizacion = resolverNumeroCotizacionInsert(
                request.serie(),
                request.correlativo(),
                sucursal.getIdSucursal());

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setSucursal(sucursal);
        cotizacion.setUsuario(usuarioAutenticado);
        cotizacion.setCliente(cliente);
        cotizacion.setSerie(numeroCotizacion.serie());
        cotizacion.setCorrelativo(numeroCotizacion.correlativo());
        cotizacion.setFechaVencimiento(request.fechaVencimiento());
        cotizacion.setIgvPorcentaje(totales.igvPorcentaje());
        cotizacion.setSubtotal(totales.subtotal());
        cotizacion.setDescuentoTotal(totales.descuentoAplicado());
        cotizacion.setTipoDescuento(totales.tipoDescuento());
        cotizacion.setIgv(totales.igv());
        cotizacion.setTotal(totales.total());
        cotizacion.setEstado(normalizarEstadoCotizacion(request.estado(), "BORRADOR"));
        cotizacion.setObservacion(normalizarTexto(request.observacion(), 500));
        cotizacion.setActivo("ACTIVO");

        Cotizacion cotizacionGuardada = cotizacionRepository.save(cotizacion);
        List<CotizacionDetalle> detallesGuardados = guardarDetalles(cotizacionGuardada, detallesCalculados);
        return toResponse(cotizacionGuardada, detallesGuardados);
    }

    @Transactional
    public CotizacionResponse actualizar(
            Integer idCotizacion,
            CotizacionUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);

        Cotizacion cotizacion = obtenerCotizacionConAlcance(idCotizacion, usuarioAutenticado);
        validarCotizacionEditable(cotizacion);
        Sucursal sucursalDestino = resolverSucursalParaActualizacion(request.idSucursal(), usuarioAutenticado, cotizacion);
        Cliente cliente = resolverCliente(request.idCliente(), sucursalDestino.getIdSucursal());
        List<DetalleCalculado> detallesCalculados = calcularDetalles(request.detalles(), sucursalDestino.getIdSucursal());
        TotalesCotizacion totales = calcularTotales(
                detallesCalculados,
                request.descuentoTotal(),
                request.tipoDescuento(),
                request.igvPorcentaje());
        NumeroCotizacion numeroCotizacion = resolverNumeroCotizacionUpdate(
                request.serie(),
                request.correlativo(),
                sucursalDestino.getIdSucursal(),
                cotizacion.getIdCotizacion(),
                cotizacion.getSerie(),
                cotizacion.getCorrelativo());

        cotizacion.setSucursal(sucursalDestino);
        cotizacion.setCliente(cliente);
        cotizacion.setSerie(numeroCotizacion.serie());
        cotizacion.setCorrelativo(numeroCotizacion.correlativo());
        cotizacion.setFechaVencimiento(request.fechaVencimiento());
        cotizacion.setIgvPorcentaje(totales.igvPorcentaje());
        cotizacion.setSubtotal(totales.subtotal());
        cotizacion.setDescuentoTotal(totales.descuentoAplicado());
        cotizacion.setTipoDescuento(totales.tipoDescuento());
        cotizacion.setIgv(totales.igv());
        cotizacion.setTotal(totales.total());
        cotizacion.setEstado(normalizarEstadoCotizacion(request.estado(), cotizacion.getEstado()));
        cotizacion.setObservacion(normalizarTexto(request.observacion(), 500));
        cotizacion.setActivo("ACTIVO");

        Cotizacion cotizacionActualizada = cotizacionRepository.save(cotizacion);
        desactivarDetallesExistentes(cotizacionActualizada.getIdCotizacion());
        List<CotizacionDetalle> detallesGuardados = guardarDetalles(cotizacionActualizada, detallesCalculados);
        return toResponse(cotizacionActualizada, detallesGuardados);
    }

    @Transactional
    public void eliminarLogico(Integer idCotizacion, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);

        Cotizacion cotizacion = obtenerCotizacionConAlcance(idCotizacion, usuarioAutenticado);
        validarCotizacionEditable(cotizacion);
        cotizacion.setActivo("INACTIVO");
        cotizacion.setDeletedAt(LocalDateTime.now());
        cotizacionRepository.save(cotizacion);
        desactivarDetallesExistentes(idCotizacion);
    }

    @Transactional
    public CotizacionResponse actualizarEstado(
            Integer idCotizacion,
            CotizacionEstadoUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);

        Cotizacion cotizacion = obtenerCotizacionConAlcance(idCotizacion, usuarioAutenticado);
        validarCotizacionEditable(cotizacion);
        cotizacion.setEstado(normalizarEstadoCotizacionGestion(request.estado()));

        Cotizacion cotizacionActualizada = cotizacionRepository.save(cotizacion);
        List<CotizacionDetalle> detalles = cotizacionDetalleRepository
                .findByCotizacion_IdCotizacionAndDeletedAtIsNullOrderByIdCotizacionDetalleAsc(cotizacionActualizada.getIdCotizacion());
        return toResponse(cotizacionActualizada, detalles);
    }

    @Transactional
    public CotizacionConvertirVentaResponse convertirAVenta(
            Integer idCotizacion,
            CotizacionConvertirVentaRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);

        Cotizacion cotizacion = obtenerCotizacionConAlcance(idCotizacion, usuarioAutenticado);
        validarCotizacionConvertible(cotizacion);

        List<CotizacionDetalle> detalles = cotizacionDetalleRepository
                .findByCotizacion_IdCotizacionAndDeletedAtIsNullOrderByIdCotizacionDetalleAsc(cotizacion.getIdCotizacion());
        if (detalles.isEmpty()) {
            throw new RuntimeException("La cotizacion no tiene detalles para convertir");
        }

        List<VentaDetalleCreateItem> detallesVenta = detalles.stream()
                .map(detalle -> new VentaDetalleCreateItem(
                        detalle.getProductoVariante().getIdProductoVariante(),
                        detalle.getCantidad(),
                        detalle.getPrecioUnitario() == null ? null : detalle.getPrecioUnitario().doubleValue(),
                        detalle.getDescuento() == null ? null : detalle.getDescuento().doubleValue()))
                .toList();

        DescuentoConversionVenta descuentoConversion = resolverDescuentoParaVenta(cotizacion);

        VentaCreateRequest ventaRequest = new VentaCreateRequest(
                cotizacion.getSucursal() != null ? cotizacion.getSucursal().getIdSucursal() : null,
                cotizacion.getCliente() != null ? cotizacion.getCliente().getIdCliente() : null,
                request.tipoComprobante(),
                null,
                null,
                cotizacion.getIgvPorcentaje() == null ? null : cotizacion.getIgvPorcentaje().doubleValue(),
                descuentoConversion.descuentoTotal(),
                descuentoConversion.tipoDescuento(),
                detallesVenta,
                request.pagos());

        VentaResponse venta = ventaService.registrarVenta(ventaRequest, correoUsuarioAutenticado);

        cotizacion.setEstado("CONVERTIDA");
        cotizacionRepository.save(cotizacion);

        return new CotizacionConvertirVentaResponse(
                "Cotizacion convertida a venta",
                cotizacion.getIdCotizacion(),
                cotizacion.getEstado(),
                venta.idVenta(),
                venta);
    }

    private List<DetalleCalculado> calcularDetalles(
            List<CotizacionDetalleCreateItem> detalles,
            Integer idSucursalCotizacion) {
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un detalle de cotizacion");
        }

        Set<Integer> variantesUnicas = new HashSet<>();
        List<DetalleCalculado> calculados = new ArrayList<>();

        for (CotizacionDetalleCreateItem item : detalles) {
            Integer idProductoVariante = item.idProductoVariante();
            if (idProductoVariante == null) {
                throw new RuntimeException("Cada detalle debe incluir idProductoVariante");
            }
            if (!variantesUnicas.add(idProductoVariante)) {
                throw new RuntimeException("No puede repetir la misma variante en el detalle de cotizacion");
            }

            ProductoVariante variante = productoVarianteRepository.findByIdProductoVarianteAndDeletedAtIsNull(idProductoVariante)
                    .orElseThrow(() -> new RuntimeException(
                            "La variante con ID " + idProductoVariante + " no existe"));

            if (variante.getSucursal() == null
                    || variante.getSucursal().getIdSucursal() == null
                    || !idSucursalCotizacion.equals(variante.getSucursal().getIdSucursal())) {
                throw new RuntimeException(
                        "La variante con ID " + idProductoVariante + " no pertenece a la sucursal de la cotizacion");
            }

            if (!"ACTIVO".equalsIgnoreCase(variante.getActivo())) {
                throw new RuntimeException("La variante con SKU '" + variante.getSku() + "' esta INACTIVA");
            }

            int cantidad = item.cantidad();
            BigDecimal precioUnitario = item.precioUnitario() == null
                    ? decimalPositivo(precioVigenteVariante(variante), "precioUnitario")
                    : decimalPositivo(item.precioUnitario(), "precioUnitario");
            BigDecimal descuento = item.descuento() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
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

    private TotalesCotizacion calcularTotales(
            List<DetalleCalculado> detalles,
            Double descuentoTotalInput,
            String tipoDescuentoInput,
            Double igvPorcentajeInput) {
        BigDecimal subtotalBase = detalles.stream()
                .map(DetalleCalculado::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal descuentoInput = descuentoTotalInput == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : decimalNoNegativo(descuentoTotalInput, "descuentoTotal");
        String tipoDescuento = normalizarTipoDescuento(tipoDescuentoInput, descuentoInput);

        BigDecimal descuentoAplicado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (descuentoInput.compareTo(BigDecimal.ZERO) > 0) {
            if ("MONTO".equals(tipoDescuento)) {
                descuentoAplicado = descuentoInput.setScale(2, RoundingMode.HALF_UP);
            } else {
                descuentoAplicado = subtotalBase
                        .multiply(descuentoInput)
                        .divide(CIEN, 2, RoundingMode.HALF_UP);
            }
        }

        if (descuentoAplicado.compareTo(subtotalBase) > 0) {
            throw new RuntimeException("El descuento total no puede superar el subtotal");
        }

        BigDecimal subtotal = subtotalBase.subtract(descuentoAplicado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igvPorcentaje = normalizarIgv(igvPorcentajeInput);
        BigDecimal igv = subtotal
                .multiply(igvPorcentaje)
                .divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(igv).setScale(2, RoundingMode.HALF_UP);

        return new TotalesCotizacion(
                igvPorcentaje,
                subtotal,
                descuentoAplicado.setScale(2, RoundingMode.HALF_UP),
                tipoDescuento,
                igv,
                total);
    }

    private List<CotizacionDetalle> guardarDetalles(
            Cotizacion cotizacion,
            List<DetalleCalculado> detallesCalculados) {
        List<CotizacionDetalle> detallesGuardar = new ArrayList<>();
        for (DetalleCalculado detalleCalculado : detallesCalculados) {
            CotizacionDetalle detalle = new CotizacionDetalle();
            detalle.setCotizacion(cotizacion);
            detalle.setProductoVariante(detalleCalculado.variante());
            detalle.setCantidad(detalleCalculado.cantidad());
            detalle.setPrecioUnitario(detalleCalculado.precioUnitario());
            detalle.setDescuento(detalleCalculado.descuento());
            detalle.setSubtotal(detalleCalculado.subtotal());
            detalle.setActivo("ACTIVO");
            detallesGuardar.add(detalle);
        }
        return cotizacionDetalleRepository.saveAll(detallesGuardar);
    }

    private void desactivarDetallesExistentes(Integer idCotizacion) {
        List<CotizacionDetalle> detallesActuales = cotizacionDetalleRepository
                .findByCotizacion_IdCotizacionAndDeletedAtIsNullOrderByIdCotizacionDetalleAsc(idCotizacion);
        if (detallesActuales.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (CotizacionDetalle detalle : detallesActuales) {
            detalle.setActivo("INACTIVO");
            detalle.setDeletedAt(now);
        }
        cotizacionDetalleRepository.saveAll(detallesActuales);
    }

    private Cotizacion obtenerCotizacionConAlcance(Integer idCotizacion, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return cotizacionRepository.findByIdCotizacionAndDeletedAtIsNull(idCotizacion)
                    .orElseThrow(() -> new RuntimeException("Cotizacion con ID " + idCotizacion + " no encontrada"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return cotizacionRepository.findByIdCotizacionAndDeletedAtIsNullAndSucursal_IdSucursal(idCotizacion, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Cotizacion con ID " + idCotizacion + " no encontrada"));
    }

    private Sucursal resolverSucursalParaEscritura(Integer idSucursalRequest, Usuario usuarioAutenticado) {
        Integer idSucursalDestino = esAdministrador(usuarioAutenticado)
                ? idSucursalRequeridaParaAdmin(idSucursalRequest)
                : obtenerIdSucursalUsuario(usuarioAutenticado);
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalDestino)
                .filter(s -> "ACTIVO".equalsIgnoreCase(s.getEstado()))
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada o inactiva"));
    }

    private Sucursal resolverSucursalParaActualizacion(
            Integer idSucursalRequest,
            Usuario usuarioAutenticado,
            Cotizacion cotizacion) {
        Integer idSucursalDestino;
        if (esAdministrador(usuarioAutenticado)) {
            idSucursalDestino = idSucursalRequest == null
                    ? cotizacion.getSucursal().getIdSucursal()
                    : idSucursalRequest;
        } else {
            Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
            if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
                throw new RuntimeException("No tiene permisos para mover la cotizacion a otra sucursal");
            }
            idSucursalDestino = idSucursalUsuario;
        }

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

    private NumeroCotizacion resolverNumeroCotizacionInsert(
            String serieInput,
            Integer correlativoInput,
            Integer idSucursal) {
        String serie = normalizarTexto(serieInput, 10);
        Integer correlativo = normalizarCorrelativo(correlativoInput);

        if (serie == null && correlativo != null) {
            serie = SERIE_DEFAULT;
        }
        if (serie == null) {
            serie = SERIE_DEFAULT;
        }
        if (correlativo == null) {
            correlativo = siguienteCorrelativo(idSucursal, serie);
        }

        validarNumeroCotizacionDisponible(idSucursal, serie, correlativo, null);
        return new NumeroCotizacion(serie, correlativo);
    }

    private NumeroCotizacion resolverNumeroCotizacionUpdate(
            String serieInput,
            Integer correlativoInput,
            Integer idSucursal,
            Integer idCotizacion,
            String serieActual,
            Integer correlativoActual) {
        String serie = normalizarTexto(serieInput, 10);
        Integer correlativo = normalizarCorrelativo(correlativoInput);

        if (serie == null) {
            serie = normalizarTexto(serieActual, 10);
        }
        if (correlativo == null) {
            correlativo = correlativoActual;
        }
        if (serie == null && correlativo != null) {
            serie = SERIE_DEFAULT;
        }
        if (serie == null) {
            serie = SERIE_DEFAULT;
        }
        if (correlativo == null) {
            correlativo = siguienteCorrelativo(idSucursal, serie);
        }

        validarNumeroCotizacionDisponible(idSucursal, serie, correlativo, idCotizacion);
        return new NumeroCotizacion(serie, correlativo);
    }

    private int siguienteCorrelativo(Integer idSucursal, String serie) {
        int max = valorEntero(cotizacionRepository.obtenerMaxCorrelativoPorSerie(idSucursal, serie));
        return max + 1;
    }

    private void validarNumeroCotizacionDisponible(
            Integer idSucursal,
            String serie,
            Integer correlativo,
            Integer idCotizacionExcluir) {
        boolean existe = idCotizacionExcluir == null
                ? cotizacionRepository.existsBySucursal_IdSucursalAndSerieAndCorrelativoAndDeletedAtIsNull(
                        idSucursal,
                        serie,
                        correlativo)
                : cotizacionRepository.existsBySucursal_IdSucursalAndSerieAndCorrelativoAndDeletedAtIsNullAndIdCotizacionNot(
                        idSucursal,
                        serie,
                        correlativo,
                        idCotizacionExcluir);
        if (existe) {
            throw new RuntimeException("Ya existe una cotizacion con la serie y correlativo indicados");
        }
    }

    private FiltroReporteCotizacion resolverFiltroReporteCotizacion(
            Usuario usuarioAutenticado,
            String agrupar,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursalRequest,
            String estadoRequest) {
        AgrupacionReporteCotizacion agrupacion = normalizarAgrupacionReporte(agrupar);
        PeriodoFiltroReporteCotizacion periodoFiltro = normalizarPeriodoReporte(periodo);
        RangoFechas rango = resolverRangoFechasReporte(periodoFiltro, desde, hasta);

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

        return new FiltroReporteCotizacion(
                agrupacion,
                periodoFiltro,
                rango.desde(),
                rango.hasta(),
                idSucursalFiltro,
                nombreSucursalFiltro,
                normalizarEstadoCotizacionFiltro(estadoRequest));
    }

    private List<Cotizacion> buscarCotizacionesParaReporte(FiltroReporteCotizacion filtro) {
        LocalDateTime fechaInicio = filtro.desde().atStartOfDay();
        LocalDateTime fechaFinExclusive = filtro.hasta().plusDays(1).atStartOfDay();
        return cotizacionRepository.buscarParaReporte(
                filtro.idSucursal(),
                filtro.estadoFiltro(),
                fechaInicio,
                fechaFinExclusive);
    }

    private CotizacionReporteResponse construirReporteCotizaciones(
            List<Cotizacion> cotizaciones,
            FiltroReporteCotizacion filtro) {
        List<Cotizacion> cotizacionesOrdenadas = cotizaciones.stream()
                .sorted(Comparator.comparing(Cotizacion::getFecha))
                .toList();

        BigDecimal montoTotal = cotizacionesOrdenadas.stream()
                .map(Cotizacion::getTotal)
                .map(this::moneda)
                .reduce(CERO_MONETARIO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long cantidadCotizaciones = cotizacionesOrdenadas.size();
        BigDecimal ticketPromedio = promedio(montoTotal, cantidadCotizaciones);

        Map<LocalDate, AcumuladoPeriodoCotizacion> acumuladoPorPeriodo = new TreeMap<>();
        Map<String, AcumuladoClienteCotizacion> acumuladoPorCliente = new LinkedHashMap<>();
        List<CotizacionReporteResponse.DetalleItem> detalleCotizaciones = new ArrayList<>();

        for (Cotizacion cotizacion : cotizacionesOrdenadas) {
            LocalDate fecha = cotizacion.getFecha().toLocalDate();
            LocalDate inicioPeriodo = inicioPeriodoReporte(fecha, filtro.agrupacion());
            String etiquetaPeriodo = etiquetaPeriodoReporte(fecha, filtro.agrupacion());
            BigDecimal totalCotizacion = moneda(cotizacion.getTotal());

            AcumuladoPeriodoCotizacion periodoActual = acumuladoPorPeriodo.get(inicioPeriodo);
            if (periodoActual == null) {
                acumuladoPorPeriodo.put(
                        inicioPeriodo,
                        new AcumuladoPeriodoCotizacion(etiquetaPeriodo, 1L, totalCotizacion));
            } else {
                acumuladoPorPeriodo.put(
                        inicioPeriodo,
                        new AcumuladoPeriodoCotizacion(
                                etiquetaPeriodo,
                                periodoActual.cantidadCotizaciones() + 1,
                                periodoActual.montoTotal().add(totalCotizacion).setScale(2, RoundingMode.HALF_UP)));
            }

            Integer idCliente = cotizacion.getCliente() != null ? cotizacion.getCliente().getIdCliente() : null;
            String nombreCliente = cotizacion.getCliente() != null ? cotizacion.getCliente().getNombres() : "SIN CLIENTE";
            String claveCliente = idCliente == null ? "SIN_CLIENTE" : "ID_" + idCliente;
            AcumuladoClienteCotizacion clienteActual = acumuladoPorCliente.get(claveCliente);
            if (clienteActual == null) {
                acumuladoPorCliente.put(
                        claveCliente,
                        new AcumuladoClienteCotizacion(idCliente, nombreCliente, 1L, totalCotizacion));
            } else {
                acumuladoPorCliente.put(
                        claveCliente,
                        new AcumuladoClienteCotizacion(
                                clienteActual.idCliente(),
                                clienteActual.nombreCliente(),
                                clienteActual.cantidadCotizaciones() + 1,
                                clienteActual.montoTotal().add(totalCotizacion).setScale(2, RoundingMode.HALF_UP)));
            }

            detalleCotizaciones.add(new CotizacionReporteResponse.DetalleItem(
                    cotizacion.getIdCotizacion(),
                    cotizacion.getFecha(),
                    cotizacion.getFechaVencimiento(),
                    cotizacion.getSerie(),
                    cotizacion.getCorrelativo(),
                    cotizacion.getEstado(),
                    idCliente,
                    nombreCliente,
                    cotizacion.getUsuario() != null ? cotizacion.getUsuario().getIdUsuario() : null,
                    nombreUsuario(cotizacion.getUsuario()),
                    cotizacion.getSucursal() != null ? cotizacion.getSucursal().getIdSucursal() : null,
                    cotizacion.getSucursal() != null ? cotizacion.getSucursal().getNombre() : null,
                    moneda(cotizacion.getSubtotal()),
                    moneda(cotizacion.getDescuentoTotal()),
                    moneda(cotizacion.getIgv()),
                    totalCotizacion));
        }

        List<CotizacionReporteResponse.PeriodoItem> periodos = acumuladoPorPeriodo.values().stream()
                .map(item -> new CotizacionReporteResponse.PeriodoItem(
                        item.etiqueta(),
                        item.cantidadCotizaciones(),
                        item.montoTotal(),
                        promedio(item.montoTotal(), item.cantidadCotizaciones())))
                .toList();

        List<CotizacionReporteResponse.ClienteItem> clientes = acumuladoPorCliente.values().stream()
                .sorted(Comparator.comparing(AcumuladoClienteCotizacion::montoTotal).reversed())
                .map(item -> new CotizacionReporteResponse.ClienteItem(
                        item.idCliente(),
                        item.nombreCliente(),
                        item.cantidadCotizaciones(),
                        item.montoTotal(),
                        promedio(item.montoTotal(), item.cantidadCotizaciones())))
                .toList();

        List<CotizacionReporteResponse.DetalleItem> detalleDesc = detalleCotizaciones.stream()
                .sorted(Comparator.comparing(CotizacionReporteResponse.DetalleItem::fecha).reversed())
                .toList();

        return new CotizacionReporteResponse(
                filtro.agrupacion().name(),
                filtro.periodoFiltro().name(),
                filtro.desde(),
                filtro.hasta(),
                filtro.idSucursal(),
                filtro.nombreSucursal(),
                filtro.estadoFiltro(),
                montoTotal,
                cantidadCotizaciones,
                ticketPromedio,
                periodos,
                detalleDesc,
                clientes);
    }

    private CotizacionListItemResponse toListItemResponse(Cotizacion cotizacion) {
        String nombreCliente = cotizacion.getCliente() != null ? cotizacion.getCliente().getNombres() : null;
        String nombreUsuario = nombreUsuario(cotizacion.getUsuario());
        Integer idSucursal = cotizacion.getSucursal() != null ? cotizacion.getSucursal().getIdSucursal() : null;
        String nombreSucursal = cotizacion.getSucursal() != null ? cotizacion.getSucursal().getNombre() : null;
        long items = cotizacionDetalleRepository.countByCotizacion_IdCotizacionAndDeletedAtIsNull(cotizacion.getIdCotizacion());

        return new CotizacionListItemResponse(
                cotizacion.getIdCotizacion(),
                cotizacion.getFecha(),
                cotizacion.getFechaVencimiento(),
                cotizacion.getSerie(),
                cotizacion.getCorrelativo(),
                cotizacion.getTotal(),
                cotizacion.getEstado(),
                cotizacion.getCliente() != null ? cotizacion.getCliente().getIdCliente() : null,
                nombreCliente,
                cotizacion.getUsuario() != null ? cotizacion.getUsuario().getIdUsuario() : null,
                nombreUsuario,
                idSucursal,
                nombreSucursal,
                items);
    }

    private CotizacionResponse toResponse(Cotizacion cotizacion, List<CotizacionDetalle> detalles) {
        List<CotizacionDetalleResponse> detalleResponses = detalles.stream()
                .map(this::toDetalleResponse)
                .toList();

        return new CotizacionResponse(
                cotizacion.getIdCotizacion(),
                cotizacion.getFecha(),
                cotizacion.getFechaVencimiento(),
                cotizacion.getSerie(),
                cotizacion.getCorrelativo(),
                cotizacion.getIgvPorcentaje(),
                cotizacion.getSubtotal(),
                cotizacion.getDescuentoTotal(),
                cotizacion.getTipoDescuento(),
                cotizacion.getIgv(),
                cotizacion.getTotal(),
                cotizacion.getEstado(),
                cotizacion.getObservacion(),
                cotizacion.getCliente() != null ? cotizacion.getCliente().getIdCliente() : null,
                cotizacion.getCliente() != null ? cotizacion.getCliente().getNombres() : null,
                cotizacion.getUsuario() != null ? cotizacion.getUsuario().getIdUsuario() : null,
                nombreUsuario(cotizacion.getUsuario()),
                cotizacion.getSucursal() != null ? cotizacion.getSucursal().getIdSucursal() : null,
                cotizacion.getSucursal() != null ? cotizacion.getSucursal().getNombre() : null,
                detalleResponses);
    }

    private CotizacionDetalleResponse toDetalleResponse(CotizacionDetalle detalle) {
        ProductoVariante variante = detalle.getProductoVariante();
        Producto producto = variante != null ? variante.getProducto() : null;

        return new CotizacionDetalleResponse(
                detalle.getIdCotizacionDetalle(),
                variante != null ? variante.getIdProductoVariante() : null,
                producto != null ? producto.getIdProducto() : null,
                producto != null ? producto.getNombre() : null,
                variante != null ? variante.getSku() : null,
                variante != null ? variante.getPrecioOferta() : null,
                variante != null ? variante.getOfertaInicio() : null,
                variante != null ? variante.getOfertaFin() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getIdColor() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getIdTalla() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getDescuento(),
                detalle.getSubtotal());
    }

    private void validarCotizacionEditable(Cotizacion cotizacion) {
        if (cotizacion == null) {
            return;
        }
        if ("CONVERTIDA".equalsIgnoreCase(cotizacion.getEstado())) {
            throw new RuntimeException("La cotizacion ya fue convertida a venta y no puede modificarse");
        }
    }

    private void validarCotizacionConvertible(Cotizacion cotizacion) {
        if (cotizacion == null) {
            return;
        }
        String estado = cotizacion.getEstado() == null ? "" : cotizacion.getEstado().trim().toUpperCase(Locale.ROOT);
        if ("CONVERTIDA".equals(estado)) {
            throw new RuntimeException("La cotizacion ya fue convertida a venta");
        }
        if ("RECHAZADA".equals(estado) || "VENCIDA".equals(estado)) {
            throw new RuntimeException("La cotizacion no puede convertirse a venta en su estado actual");
        }
    }

    private String normalizarTipoDescuento(String tipoDescuento, BigDecimal descuentoInput) {
        if (descuentoInput == null || descuentoInput.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (tipoDescuento == null || tipoDescuento.isBlank()) {
            throw new RuntimeException("tipoDescuento es obligatorio cuando descuentoTotal es mayor a 0");
        }
        String normalized = tipoDescuento.trim().toUpperCase(Locale.ROOT);
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

    private String normalizarEstadoCotizacion(String estado, String defaultValue) {
        if (estado == null || estado.isBlank()) {
            return defaultValue;
        }
        String normalized = estado.trim().toUpperCase(Locale.ROOT);
        if (!"BORRADOR".equals(normalized)
                && !"ENVIADA".equals(normalized)
                && !"APROBADA".equals(normalized)
                && !"RECHAZADA".equals(normalized)
                && !"VENCIDA".equals(normalized)
                && !"CONVERTIDA".equals(normalized)) {
            throw new RuntimeException("estado permitido: BORRADOR, ENVIADA, APROBADA, RECHAZADA, VENCIDA o CONVERTIDA");
        }
        return normalized;
    }

    private String normalizarEstadoCotizacionFiltro(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        return normalizarEstadoCotizacion(estado, null);
    }

    private String normalizarEstadoCotizacionGestion(String estado) {
        String normalized = normalizarEstadoCotizacion(estado, null);
        if ("CONVERTIDA".equals(normalized)) {
            throw new RuntimeException("El estado CONVERTIDA solo puede asignarse al convertir la cotizacion");
        }
        return normalized;
    }

    private AgrupacionReporteCotizacion normalizarAgrupacionReporte(String agrupar) {
        if (agrupar == null || agrupar.isBlank()) {
            return AgrupacionReporteCotizacion.DIA;
        }
        String valor = agrupar.trim().toUpperCase(Locale.ROOT);
        return switch (valor) {
            case "DIA" -> AgrupacionReporteCotizacion.DIA;
            case "SEMANA" -> AgrupacionReporteCotizacion.SEMANA;
            case "MES" -> AgrupacionReporteCotizacion.MES;
            default -> throw new RuntimeException("agrupar permitido: DIA, SEMANA o MES");
        };
    }

    private PeriodoFiltroReporteCotizacion normalizarPeriodoReporte(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            return PeriodoFiltroReporteCotizacion.MES;
        }
        String valor = periodo.trim().toUpperCase(Locale.ROOT);
        return switch (valor) {
            case "HOY" -> PeriodoFiltroReporteCotizacion.HOY;
            case "AYER" -> PeriodoFiltroReporteCotizacion.AYER;
            case "SEMANA" -> PeriodoFiltroReporteCotizacion.SEMANA;
            case "MES" -> PeriodoFiltroReporteCotizacion.MES;
            case "RANGO" -> PeriodoFiltroReporteCotizacion.RANGO;
            default -> throw new RuntimeException("periodo permitido: HOY, AYER, SEMANA, MES o RANGO");
        };
    }

    private RangoFechas resolverRangoFechasReporte(
            PeriodoFiltroReporteCotizacion periodoFiltro,
            LocalDate desde,
            LocalDate hasta) {
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
            case RANGO -> resolverRangoPersonalizadoReporte(desde, hasta);
        };
    }

    private RangoFechas resolverRangoPersonalizadoReporte(LocalDate desde, LocalDate hasta) {
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

    private LocalDate inicioPeriodoReporte(LocalDate fecha, AgrupacionReporteCotizacion agrupacion) {
        return switch (agrupacion) {
            case DIA -> fecha;
            case SEMANA -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> fecha.withDayOfMonth(1);
        };
    }

    private String etiquetaPeriodoReporte(LocalDate fecha, AgrupacionReporteCotizacion agrupacion) {
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
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar cotizaciones");
        }
    }

    private void validarRolEscritura(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para registrar cotizaciones");
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

    private Integer idSucursalRequeridaParaAdmin(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            throw new RuntimeException("idSucursal es obligatorio para ADMINISTRADOR");
        }
        if (idSucursalRequest <= 0) {
            throw new RuntimeException("idSucursal debe ser mayor a 0");
        }
        return idSucursalRequest;
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

    private Integer normalizarCorrelativo(Integer correlativo) {
        if (correlativo == null) {
            return null;
        }
        if (correlativo <= 0) {
            throw new RuntimeException("correlativo debe ser mayor a 0");
        }
        return correlativo;
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
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

    private DescuentoConversionVenta resolverDescuentoParaVenta(Cotizacion cotizacion) {
        if (cotizacion == null || cotizacion.getDescuentoTotal() == null) {
            return new DescuentoConversionVenta(null, null);
        }

        BigDecimal descuento = moneda(cotizacion.getDescuentoTotal());
        if (descuento.compareTo(BigDecimal.ZERO) <= 0) {
            return new DescuentoConversionVenta(null, null);
        }

        String tipoDescuento = cotizacion.getTipoDescuento();
        if (tipoDescuento == null || tipoDescuento.isBlank()) {
            return new DescuentoConversionVenta(descuento.doubleValue(), "MONTO");
        }

        String tipoNormalizado = tipoDescuento.trim().toUpperCase(Locale.ROOT);
        if ("PORCENTAJE".equals(tipoNormalizado)) {
            return new DescuentoConversionVenta(descuento.doubleValue(), "MONTO");
        }
        return new DescuentoConversionVenta(descuento.doubleValue(), tipoNormalizado);
    }

    private BigDecimal promedio(BigDecimal montoTotal, long cantidadCotizaciones) {
        if (cantidadCotizaciones <= 0) {
            return CERO_MONETARIO;
        }
        return moneda(montoTotal)
                .divide(BigDecimal.valueOf(cantidadCotizaciones), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal moneda(BigDecimal valor) {
        if (valor == null) {
            return CERO_MONETARIO;
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private record RangoFechas(
            LocalDate desde,
            LocalDate hasta) {
    }

    private record FiltroReporteCotizacion(
            AgrupacionReporteCotizacion agrupacion,
            PeriodoFiltroReporteCotizacion periodoFiltro,
            LocalDate desde,
            LocalDate hasta,
            Integer idSucursal,
            String nombreSucursal,
            String estadoFiltro) {
    }

    private enum AgrupacionReporteCotizacion {
        DIA,
        SEMANA,
        MES
    }

    private enum PeriodoFiltroReporteCotizacion {
        HOY,
        AYER,
        SEMANA,
        MES,
        RANGO
    }

    private record AcumuladoPeriodoCotizacion(
            String etiqueta,
            long cantidadCotizaciones,
            BigDecimal montoTotal) {
    }

    private record AcumuladoClienteCotizacion(
            Integer idCliente,
            String nombreCliente,
            long cantidadCotizaciones,
            BigDecimal montoTotal) {
    }

    private record DescuentoConversionVenta(
            Double descuentoTotal,
            String tipoDescuento) {
    }

    private record DetalleCalculado(
            ProductoVariante variante,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal descuento,
            BigDecimal subtotal) {
    }

    private record TotalesCotizacion(
            BigDecimal igvPorcentaje,
            BigDecimal subtotal,
            BigDecimal descuentoAplicado,
            String tipoDescuento,
            BigDecimal igv,
            BigDecimal total) {
    }

    private record NumeroCotizacion(
            String serie,
            Integer correlativo) {
    }
}
