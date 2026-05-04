package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CotizacionRepository;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;
import com.sistemapos.sistematextil.repositories.TrasladoRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.dashboard.DashboardAdminResponse;
import com.sistemapos.sistematextil.util.dashboard.DashboardAlmacenResponse;
import com.sistemapos.sistematextil.util.dashboard.DashboardIngresoMetodoPagoItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardSerieItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardStockCeroItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardTopProductoItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardVentasResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int UMBRAL_STOCK_BAJO = 5;
    private static final BigDecimal CERO_MONETARIO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final PagoRepository pagoRepository;
    private final CotizacionRepository cotizacionRepository;
    private final HistorialStockRepository historialStockRepository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final SucursalRepository sucursalRepository;
    private final SucursalStockRepository sucursalStockRepository;
    private final TrasladoRepository trasladoRepository;
    private final SistemaDashboardService sistemaDashboardService;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:00");
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("yyyy-MM");

    public Object obtenerDashboard(
            String filtro,
            LocalDate desdeRequest,
            LocalDate hastaRequest,
            Integer idSucursalRequest,
            String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        if (usuario.getRol() == Rol.SISTEMA) {
            return sistemaDashboardService.obtenerDashboard();
        }

        Integer idSucursal = resolverIdSucursalFiltro(usuario, idSucursalRequest);
        Sucursal sucursalConsultada = idSucursal != null ? obtenerSucursalActiva(idSucursal) : null;

        if (sucursalConsultada != null && sucursalConsultada.getTipo() == com.sistemapos.sistematextil.model.SucursalTipo.ALMACEN) {
            return construirDashboardAlmacen(usuario, idSucursal);
        }

        return switch (usuario.getRol()) {
            case ADMINISTRADOR -> construirDashboardAdmin(usuario, filtro, desdeRequest, hastaRequest, idSucursal);
            case VENTAS -> construirDashboardVentas(usuario, filtro, desdeRequest, hastaRequest, idSucursal);
            case ALMACEN -> construirDashboardAlmacen(usuario, idSucursal);
            case VENTAS_ALMACEN -> construirDashboardVentas(usuario, filtro, desdeRequest, hastaRequest, idSucursal);
            case SISTEMA -> sistemaDashboardService.obtenerDashboard();
        };
    }

    private DashboardAdminResponse construirDashboardAdmin(
            Usuario usuario,
            String filtro,
            LocalDate desdeRequest,
            LocalDate hastaRequest,
            Integer idSucursal) {
        RangoFechas rango = resolverRangoFechas(filtro, desdeRequest, hastaRequest);
        String nombreSucursal = resolverNombreSucursal(idSucursal);
        LocalDateTime desde = rango.desde().atStartOfDay();
        LocalDateTime hastaExclusiva = rango.hastaExclusiva();
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioDia = hoy.atStartOfDay();
        LocalDateTime finDiaExclusive = hoy.plusDays(1).atStartOfDay();
        YearMonth mesActual = YearMonth.from(hoy);
        LocalDateTime inicioMes = mesActual.atDay(1).atStartOfDay();
        LocalDateTime finMesExclusive = mesActual.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal ventasTotales = asegurarMoneda(
                ventaRepository.sumarTotalEmitido(idSucursal, null, desde, hastaExclusiva));
        long productosVendidos = ventaDetalleRepository.sumarCantidadVendida(idSucursal, null, desde, hastaExclusiva);
        long ticketsEmitidos = ventaRepository.contarTicketsEmitidos(
                idSucursal,
                null,
                desde,
                hastaExclusiva);
        long variantesVendidas = ventaDetalleRepository.contarVariantesVendidas(idSucursal, null, desde, hastaExclusiva);
        BigDecimal ventasDelDia = asegurarMoneda(
                ventaRepository.sumarTotalEmitido(idSucursal, null, inicioDia, finDiaExclusive));
        BigDecimal ventasDelMes = asegurarMoneda(
                ventaRepository.sumarTotalEmitido(idSucursal, null, inicioMes, finMesExclusive));
        BigDecimal ticketPromedio = asegurarMoneda(
                ventaRepository.promedioVentaEmitida(idSucursal, null, desde, hastaExclusiva));

        List<DashboardIngresoMetodoPagoItem> ingresosPorMetodoPago = mapIngresosPorMetodo(
                pagoRepository.obtenerIngresosPorMetodoPago(
                        idSucursal,
                        null,
                        desde,
                        hastaExclusiva));

        List<DashboardSerieItem> ventasPorFecha = construirSerieVentas(
                rango,
                ventaRepository.obtenerVentasPorHora(
                        idSucursal,
                        null,
                        desde,
                        hastaExclusiva),
                ventaRepository.obtenerVentasPorFecha(
                        idSucursal,
                        null,
                        desde,
                        hastaExclusiva),
                ventaRepository.obtenerVentasPorMes(
                        idSucursal,
                        null,
                        desde,
                        hastaExclusiva));

        List<DashboardTopProductoItem> topProductosMasVendidos = mapTopProductos(
                ventaDetalleRepository.obtenerTopProductosVendidos(
                        idSucursal,
                        null,
                        desde,
                        hastaExclusiva));

        List<DashboardAdminResponse.ComprobanteTipoItem> comprobantesPorTipo = construirComprobantesPorTipo(
                ventaRepository.obtenerVentasPorTipoComprobanteResumen(idSucursal, desde, hastaExclusiva));
        List<DashboardAdminResponse.EstadoVentaItem> distribucionPorEstado = construirDistribucionPorEstado(
                ventaRepository.obtenerDistribucionPorEstadoResumen(idSucursal, desde, hastaExclusiva));
        List<DashboardAdminResponse.SucursalVentaItem> ventasPorSucursal = mapVentasPorSucursal(
                ventaRepository.obtenerVentasPorSucursalResumen(idSucursal, desde, hastaExclusiva));

        long comprobantesAnulados = obtenerCantidadPorEstado(distribucionPorEstado, "ANULADA");
        BigDecimal montoAnulado = obtenerMontoPorEstado(distribucionPorEstado, "ANULADA");
        long variantesAgotadas = productoVarianteRepository.contarVariantesAgotadas(idSucursal);
        long stockBajo = productoVarianteRepository.contarStockBajo(idSucursal, UMBRAL_STOCK_BAJO);
        List<DashboardStockCeroItem> agotados = limitarStockCritico(
                mapStockCero(productoVarianteRepository.listarReposicionUrgente(idSucursal)));
        List<DashboardStockCeroItem> prontosAgotarse = limitarStockCritico(
                mapStockCero(productoVarianteRepository.listarStockBajoResumen(idSucursal, UMBRAL_STOCK_BAJO)));

        return new DashboardAdminResponse(
                "ADMIN",
                rango.filtroAplicado(),
                idSucursal,
                nombreSucursal,
                rango.desde(),
                rango.hasta(),
                ventasTotales,
                productosVendidos,
                ticketsEmitidos,
                new DashboardAdminResponse.KpiResumen(
                        ventasTotales,
                        ventasDelDia,
                        ventasDelMes,
                        ticketPromedio,
                        ticketsEmitidos,
                        comprobantesAnulados,
                        montoAnulado,
                        productosVendidos,
                        variantesVendidas),
                ingresosPorMetodoPago,
                ventasPorFecha,
                topProductosMasVendidos,
                comprobantesPorTipo,
                distribucionPorEstado,
                ventasPorSucursal,
                new DashboardAdminResponse.StockCriticoResumen(
                        variantesAgotadas,
                        stockBajo,
                        agotados,
                        prontosAgotarse));
    }

    private DashboardVentasResponse construirDashboardVentas(
            Usuario usuario,
            String filtro,
            LocalDate desdeRequest,
            LocalDate hastaRequest,
            Integer idSucursal) {
        RangoFechas rango = resolverRangoFechas(filtro, desdeRequest, hastaRequest);
        Integer idUsuario = usuario.getIdUsuario();

        BigDecimal misVentasTotales = asegurarMoneda(
                ventaRepository.sumarTotalEmitido(
                        idSucursal,
                        idUsuario,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()));

        long misProductosVendidos = ventaDetalleRepository.sumarCantidadVendida(
                idSucursal,
                idUsuario,
                rango.desde().atStartOfDay(),
                rango.hastaExclusiva());

        long misCotizacionesAbiertas = cotizacionRepository.contarCotizacionesAbiertas(
                idSucursal,
                idUsuario,
                rango.desde().atStartOfDay(),
                rango.hastaExclusiva());

        BigDecimal miPromedioVenta = asegurarMoneda(
                ventaRepository.promedioVentaEmitida(
                        idSucursal,
                        idUsuario,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()));

        List<DashboardSerieItem> misVentasPorFecha = construirSerieVentas(
                rango,
                ventaRepository.obtenerVentasPorHora(
                        idSucursal,
                        idUsuario,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()),
                ventaRepository.obtenerVentasPorFecha(
                        idSucursal,
                        idUsuario,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()),
                ventaRepository.obtenerVentasPorMes(
                        idSucursal,
                        idUsuario,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()));

        List<DashboardTopProductoItem> topProductosMasVendidosGenerales = mapTopProductos(
                ventaDetalleRepository.obtenerTopProductosVendidos(
                        idSucursal,
                        null,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()));

        return new DashboardVentasResponse(
                "VENTAS",
                rango.filtroAplicado(),
                rango.desde(),
                rango.hasta(),
                misVentasTotales,
                misProductosVendidos,
                misCotizacionesAbiertas,
                miPromedioVenta,
                misVentasPorFecha,
                topProductosMasVendidosGenerales);
    }

    private DashboardAlmacenResponse construirDashboardAlmacen(Usuario usuario, Integer idSucursal) {
        Sucursal sucursal = obtenerSucursalActiva(idSucursal);

        long variantesAgotadas = productoVarianteRepository.contarVariantesAgotadas(idSucursal);
        long stockBajo = productoVarianteRepository.contarStockBajo(idSucursal, UMBRAL_STOCK_BAJO);
        long totalFisicoEnTienda = productoVarianteRepository.sumarStockTotal(idSucursal);
        long variantesDisponibles = productoVarianteRepository.contarVariantesDisponibles(idSucursal);

        List<DashboardStockCeroItem> reposicionUrgente = unirStockCritico(
                mapStockCero(productoVarianteRepository.listarReposicionUrgente(idSucursal)),
                mapStockCero(productoVarianteRepository.listarStockBajoResumen(idSucursal, UMBRAL_STOCK_BAJO))).stream()
                .sorted(Comparator
                        .comparing(DashboardStockCeroItem::stock, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DashboardStockCeroItem::producto, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(10)
                .toList();

        List<DashboardTopProductoItem> topMayorSalida = mapTopProductos(
                historialStockRepository.obtenerTopProductosConSalida(idSucursal));
        DashboardAlmacenResponse.ResumenMovimientos resumenMovimientos = construirResumenMovimientosAlmacen(idSucursal);
        List<DashboardAlmacenResponse.MovimientoRecienteItem> ultimosMovimientos = historialStockRepository
                .findTop10BySucursalIdSucursalOrderByFechaDesc(idSucursal).stream()
                .map(this::mapMovimientoReciente)
                .toList();
        List<DashboardAlmacenResponse.StockActualItem> topStockActual = mapTopStockActual(
                sucursalStockRepository.obtenerTopStockActual(idSucursal));

        return new DashboardAlmacenResponse(
                "ALMACEN",
                "TIEMPO_REAL",
                idSucursal,
                sucursal.getNombre(),
                variantesAgotadas,
                stockBajo,
                totalFisicoEnTienda,
                variantesDisponibles,
                reposicionUrgente,
                topMayorSalida,
                resumenMovimientos,
                ultimosMovimientos,
                topStockActual);
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer resolverIdSucursalFiltro(Usuario usuario, Integer idSucursalRequest) {
        return usuarioSucursalAccessService.resolverIdSucursalFiltro(
                usuario,
                idSucursalRequest,
                "El usuario autenticado no tiene permisos para consultar otra sucursal");
    }

    private String resolverNombreSucursal(Integer idSucursal) {
        if (idSucursal == null) {
            return "TODAS";
        }
        return obtenerSucursalActiva(idSucursal).getNombre();
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
    }

    private RangoFechas resolverRangoFechas(String filtro, LocalDate desdeRequest, LocalDate hastaRequest) {
        if (desdeRequest != null || hastaRequest != null) {
            LocalDate desde = desdeRequest != null ? desdeRequest : hastaRequest;
            LocalDate hasta = hastaRequest != null ? hastaRequest : desdeRequest;
            if (desde == null || hasta == null) {
                throw new RuntimeException("Envie 'desde' y/o 'hasta' validos");
            }
            if (desde.isAfter(hasta)) {
                throw new RuntimeException("La fecha 'desde' no puede ser mayor a 'hasta'");
            }
            return new RangoFechas("RANGO_FECHAS", desde, hasta);
        }

        String filtroNormalizado = normalizarFiltro(filtro);
        LocalDate hoy = LocalDate.now();

        return switch (filtroNormalizado) {
            case "HOY" -> new RangoFechas("HOY", hoy, hoy);
            case "ULT_7_DIAS" -> new RangoFechas("ULT_7_DIAS", hoy.minusDays(6), hoy);
            case "ULT_14_DIAS" -> new RangoFechas("ULT_14_DIAS", hoy.minusDays(13), hoy);
            case "ULT_30_DIAS" -> new RangoFechas("ULT_30_DIAS", hoy.minusDays(29), hoy);
            default -> throw new RuntimeException(
                    "Filtro invalido. Use: HOY, ULT_7_DIAS, ULT_14_DIAS, ULT_30_DIAS o envie desde/hasta");
        };
    }

    private String normalizarFiltro(String filtro) {
        if (filtro == null || filtro.isBlank()) {
            return "ULT_30_DIAS";
        }

        String f = filtro.trim().toUpperCase(Locale.ROOT)
                .replace(" ", "_")
                .replace(".", "")
                .replace("Ú", "U")
                .replace("Á", "A");

        if ("ULT7DIAS".equals(f) || "ULT_7".equals(f) || "7_DIAS".equals(f)) {
            return "ULT_7_DIAS";
        }
        if ("ULT14DIAS".equals(f) || "ULT_14".equals(f) || "14_DIAS".equals(f)) {
            return "ULT_14_DIAS";
        }
        if ("ULT30DIAS".equals(f) || "ULT_30".equals(f) || "30_DIAS".equals(f)) {
            return "ULT_30_DIAS";
        }
        return f;
    }

    private List<DashboardIngresoMetodoPagoItem> mapIngresosPorMetodo(List<Object[]> rows) {
        List<DashboardIngresoMetodoPagoItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            String metodo = row[0] == null ? "SIN_METODO" : row[0].toString();
            BigDecimal monto = toBigDecimal(row[1]);
            items.add(new DashboardIngresoMetodoPagoItem(metodo, monto));
        }
        return items;
    }

    private List<DashboardAdminResponse.ComprobanteTipoItem> construirComprobantesPorTipo(List<Object[]> rows) {
        LinkedHashMap<String, DashboardAdminResponse.ComprobanteTipoItem> items = new LinkedHashMap<>();
        items.put("NOTA DE VENTA", new DashboardAdminResponse.ComprobanteTipoItem("NOTA DE VENTA", 0L, CERO_MONETARIO));
        items.put("BOLETA", new DashboardAdminResponse.ComprobanteTipoItem("BOLETA", 0L, CERO_MONETARIO));
        items.put("FACTURA", new DashboardAdminResponse.ComprobanteTipoItem("FACTURA", 0L, CERO_MONETARIO));

        for (Object[] row : rows) {
            String tipoComprobante = row[0] == null ? "SIN_TIPO" : row[0].toString();
            items.put(tipoComprobante, new DashboardAdminResponse.ComprobanteTipoItem(
                    tipoComprobante,
                    toLong(row[1]),
                    toBigDecimal(row[2])));
        }
        return new ArrayList<>(items.values());
    }

    private List<DashboardAdminResponse.EstadoVentaItem> construirDistribucionPorEstado(List<Object[]> rows) {
        LinkedHashMap<String, DashboardAdminResponse.EstadoVentaItem> items = new LinkedHashMap<>();
        items.put("EMITIDA", new DashboardAdminResponse.EstadoVentaItem("EMITIDA", 0L, CERO_MONETARIO));
        items.put("ANULADA", new DashboardAdminResponse.EstadoVentaItem("ANULADA", 0L, CERO_MONETARIO));

        for (Object[] row : rows) {
            String estado = row[0] == null ? "SIN_ESTADO" : row[0].toString();
            items.put(estado, new DashboardAdminResponse.EstadoVentaItem(
                    estado,
                    toLong(row[1]),
                    toBigDecimal(row[2])));
        }
        return new ArrayList<>(items.values());
    }

    private List<DashboardAdminResponse.SucursalVentaItem> mapVentasPorSucursal(List<Object[]> rows) {
        List<DashboardAdminResponse.SucursalVentaItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new DashboardAdminResponse.SucursalVentaItem(
                    toInteger(row[0]),
                    row[1] == null ? "-" : row[1].toString(),
                    toLong(row[2]),
                    toBigDecimal(row[3])));
        }
        return items;
    }

    private List<DashboardTopProductoItem> mapTopProductos(List<Object[]> rows) {
        List<DashboardTopProductoItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            Integer idProductoVariante = toInteger(row[0]);
            String producto = row[1] == null ? "-" : row[1].toString();
            String color = row[2] == null ? "-" : row[2].toString();
            String talla = row[3] == null ? "-" : row[3].toString();
            long cantidad = toLong(row[4]);
            items.add(new DashboardTopProductoItem(idProductoVariante, producto, color, talla, cantidad));
        }
        return items;
    }

    private List<DashboardStockCeroItem> mapStockCero(List<Object[]> rows) {
        Map<ProductoColorKey, ImagenPrincipalData> imagenesPorProductoColor = resolverImagenesPorProductoColor(rows);
        List<DashboardStockCeroItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            ProductoColorKey key = new ProductoColorKey(toInteger(row[6]), toInteger(row[7]));
            ImagenPrincipalData imagen = imagenesPorProductoColor.get(key);
            items.add(new DashboardStockCeroItem(
                    toInteger(row[0]),
                    row[1] == null ? "-" : row[1].toString(),
                    row[2] == null ? "-" : row[2].toString(),
                    row[3] == null ? "-" : row[3].toString(),
                    toInteger(row[4]),
                    row[5] == null ? "-" : row[5].toString(),
                    imagen != null ? imagen.url() : null,
                    imagen != null ? imagen.urlThumb() : null));
        }
        return items;
    }

    private Map<ProductoColorKey, ImagenPrincipalData> resolverImagenesPorProductoColor(List<Object[]> rows) {
        Set<Integer> productoIds = new HashSet<>();
        Set<ProductoColorKey> keys = new HashSet<>();

        for (Object[] row : rows) {
            Integer productoId = toInteger(row[6]);
            Integer colorId = toInteger(row[7]);
            if (productoId == null || colorId == null) {
                continue;
            }
            productoIds.add(productoId);
            keys.add(new ProductoColorKey(productoId, colorId));
        }

        if (productoIds.isEmpty()) {
            return Map.of();
        }

        List<ProductoImagenColorRow> rowsImagen = productoColorImagenRepository
                .obtenerResumenPorProductos(new ArrayList<>(productoIds));

        Map<ProductoColorKey, ImagenPick> picks = new HashMap<>();
        for (ProductoImagenColorRow row : rowsImagen) {
            ProductoColorKey key = new ProductoColorKey(row.productoId(), row.colorId());
            if (!keys.contains(key)) {
                continue;
            }

            String url = preferirNoVacio(row.url(), row.urlThumb());
            String urlThumb = preferirNoVacio(row.urlThumb(), row.url());
            if (url == null && urlThumb == null) {
                continue;
            }

            ImagenPick actual = picks.get(key);
            boolean esPrincipal = Boolean.TRUE.equals(row.esPrincipal());
            int orden = row.orden() == null ? Integer.MAX_VALUE : row.orden();

            if (actual == null || debeReemplazarImagen(actual, esPrincipal, orden)) {
                picks.put(key, new ImagenPick(url, urlThumb, orden, esPrincipal));
            }
        }

        Map<ProductoColorKey, ImagenPrincipalData> resultado = new HashMap<>();
        for (Map.Entry<ProductoColorKey, ImagenPick> entry : picks.entrySet()) {
            resultado.put(entry.getKey(), new ImagenPrincipalData(entry.getValue().url(), entry.getValue().urlThumb()));
        }
        return resultado;
    }

    private boolean debeReemplazarImagen(ImagenPick actual, boolean esPrincipalNueva, int ordenNuevo) {
        if (esPrincipalNueva && !actual.esPrincipal()) {
            return true;
        }
        if (!esPrincipalNueva && actual.esPrincipal()) {
            return false;
        }
        return ordenNuevo < actual.orden();
    }

    private String preferirNoVacio(String valorPrincipal, String valorAlterno) {
        if (valorPrincipal != null && !valorPrincipal.isBlank()) {
            return valorPrincipal;
        }
        if (valorAlterno != null && !valorAlterno.isBlank()) {
            return valorAlterno;
        }
        return null;
    }

    private List<DashboardStockCeroItem> limitarStockCritico(List<DashboardStockCeroItem> items) {
        return items.stream()
                .sorted(Comparator
                        .comparing((DashboardStockCeroItem item) -> item.stock() == null ? Integer.MAX_VALUE : item.stock())
                        .thenComparing(DashboardStockCeroItem::producto, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(10)
                .toList();
    }

    private List<DashboardStockCeroItem> unirStockCritico(
            List<DashboardStockCeroItem> agotados,
            List<DashboardStockCeroItem> stockBajo) {
        Map<Integer, DashboardStockCeroItem> items = new LinkedHashMap<>();
        for (DashboardStockCeroItem item : agotados) {
            items.put(item.idProductoVariante(), item);
        }
        for (DashboardStockCeroItem item : stockBajo) {
            items.putIfAbsent(item.idProductoVariante(), item);
        }
        return new ArrayList<>(items.values());
    }

    private DashboardAlmacenResponse.ResumenMovimientos construirResumenMovimientosAlmacen(Integer idSucursal) {
        Object[] resumenHistorial = normalizarFilaResumen(historialStockRepository.obtenerResumenMovimientos(idSucursal), 6);
        Object[] resumenEntradas = normalizarFilaResumen(trasladoRepository.obtenerResumenEntradas(idSucursal), 2);
        Object[] resumenSalidas = normalizarFilaResumen(trasladoRepository.obtenerResumenSalidas(idSucursal), 2);

        return new DashboardAlmacenResponse.ResumenMovimientos(
                toLong(resumenHistorial[0]),
                toLong(resumenHistorial[1]),
                toLong(resumenHistorial[2]),
                toLong(resumenHistorial[3]),
                toLong(resumenHistorial[4]),
                toLong(resumenHistorial[5]),
                toLong(resumenEntradas[0]),
                toLong(resumenEntradas[1]),
                toLong(resumenSalidas[0]),
                toLong(resumenSalidas[1]));
    }

    private Object[] normalizarFilaResumen(Object[] row, int sizeEsperado) {
        if (row == null) {
            return new Object[sizeEsperado];
        }
        if (row.length == 1 && row[0] instanceof Object[] nestedRow) {
            return nestedRow;
        }
        return row;
    }

    private DashboardAlmacenResponse.MovimientoRecienteItem mapMovimientoReciente(com.sistemapos.sistematextil.model.HistorialStock historial) {
        return new DashboardAlmacenResponse.MovimientoRecienteItem(
                historial.getIdHistorial(),
                historial.getFecha(),
                historial.getTipoMovimiento() == null ? null : historial.getTipoMovimiento().name(),
                historial.getMotivo(),
                historial.getProductoVariante() == null ? null : historial.getProductoVariante().getIdProductoVariante(),
                historial.getProductoVariante() == null || historial.getProductoVariante().getProducto() == null
                        ? "-"
                        : historial.getProductoVariante().getProducto().getNombre(),
                historial.getProductoVariante() == null || historial.getProductoVariante().getColor() == null
                        ? "-"
                        : historial.getProductoVariante().getColor().getNombre(),
                historial.getProductoVariante() == null || historial.getProductoVariante().getTalla() == null
                        ? "-"
                        : historial.getProductoVariante().getTalla().getNombre(),
                historial.getCantidad(),
                historial.getStockAnterior(),
                historial.getStockNuevo());
    }

    private List<DashboardAlmacenResponse.StockActualItem> mapTopStockActual(List<Object[]> rows) {
        List<DashboardAlmacenResponse.StockActualItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new DashboardAlmacenResponse.StockActualItem(
                    toInteger(row[0]),
                    row[1] == null ? "-" : row[1].toString(),
                    row[2] == null ? "-" : row[2].toString(),
                    row[3] == null ? "-" : row[3].toString(),
                    toLong(row[4])));
        }
        return items;
    }

    private List<DashboardSerieItem> construirSerieVentas(
            RangoFechas rango,
            List<Object[]> rowsPorHora,
            List<Object[]> rowsPorDia,
            List<Object[]> rowsPorMes) {
        GranularidadSerie granularidad = rango.granularidadSerie();
        return switch (granularidad) {
            case HORA -> construirSeriePorHora(rango.desde(), rowsPorHora);
            case MES -> construirSeriePorMes(rango.desde(), rango.hasta(), rowsPorMes);
            case DIA -> construirSeriePorDia(rango.desde(), rango.hasta(), rowsPorDia);
        };
    }

    private List<DashboardSerieItem> construirSeriePorDia(LocalDate desde, LocalDate hasta, List<Object[]> rows) {
        Map<LocalDate, BigDecimal> montosPorFecha = new LinkedHashMap<>();
        LocalDate cursor = desde;
        while (!cursor.isAfter(hasta)) {
            montosPorFecha.put(cursor, CERO_MONETARIO);
            cursor = cursor.plusDays(1);
        }

        for (Object[] row : rows) {
            LocalDate fecha = toLocalDate(row[0]);
            BigDecimal monto = toBigDecimal(row[1]);
            if (fecha != null && montosPorFecha.containsKey(fecha)) {
                montosPorFecha.put(fecha, monto);
            }
        }

        List<DashboardSerieItem> serie = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : montosPorFecha.entrySet()) {
            serie.add(new DashboardSerieItem(
                    entry.getKey(),
                    entry.getKey().toString(),
                    GranularidadSerie.DIA.name(),
                    entry.getValue()));
        }
        return serie;
    }

    private List<DashboardSerieItem> construirSeriePorHora(LocalDate fecha, List<Object[]> rows) {
        Map<Integer, BigDecimal> montosPorHora = new LinkedHashMap<>();
        for (int hora = 0; hora < 24; hora++) {
            montosPorHora.put(hora, CERO_MONETARIO);
        }

        for (Object[] row : rows) {
            Integer hora = toInteger(row[0]);
            BigDecimal monto = toBigDecimal(row[1]);
            if (hora != null && montosPorHora.containsKey(hora)) {
                montosPorHora.put(hora, monto);
            }
        }

        List<DashboardSerieItem> serie = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : montosPorHora.entrySet()) {
            LocalDateTime fechaHora = fecha.atTime(entry.getKey(), 0);
            serie.add(new DashboardSerieItem(
                    fecha,
                    fechaHora.format(FORMATO_HORA),
                    GranularidadSerie.HORA.name(),
                    entry.getValue()));
        }
        return serie;
    }

    private List<DashboardSerieItem> construirSeriePorMes(LocalDate desde, LocalDate hasta, List<Object[]> rows) {
        Map<YearMonth, BigDecimal> montosPorMes = new LinkedHashMap<>();
        YearMonth cursor = YearMonth.from(desde);
        YearMonth fin = YearMonth.from(hasta);
        while (!cursor.isAfter(fin)) {
            montosPorMes.put(cursor, CERO_MONETARIO);
            cursor = cursor.plusMonths(1);
        }

        for (Object[] row : rows) {
            LocalDate fecha = toLocalDate(row[0]);
            BigDecimal monto = toBigDecimal(row[1]);
            if (fecha != null) {
                YearMonth yearMonth = YearMonth.from(fecha);
                if (montosPorMes.containsKey(yearMonth)) {
                    montosPorMes.put(yearMonth, monto);
                }
            }
        }

        List<DashboardSerieItem> serie = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal> entry : montosPorMes.entrySet()) {
            LocalDate fecha = entry.getKey().atDay(1);
            serie.add(new DashboardSerieItem(
                    fecha,
                    fecha.format(FORMATO_MES),
                    GranularidadSerie.MES.name(),
                    entry.getValue()));
        }
        return serie;
    }

    private BigDecimal asegurarMoneda(BigDecimal valor) {
        if (valor == null) {
            return CERO_MONETARIO;
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return CERO_MONETARIO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return asegurarMoneda(bigDecimal);
        }
        if (value instanceof Number number) {
            return asegurarMoneda(BigDecimal.valueOf(number.doubleValue()));
        }
        return asegurarMoneda(new BigDecimal(value.toString()));
    }

    private long obtenerCantidadPorEstado(List<DashboardAdminResponse.EstadoVentaItem> items, String estado) {
        return items.stream()
                .filter(item -> estado.equalsIgnoreCase(item.estado()))
                .findFirst()
                .map(DashboardAdminResponse.EstadoVentaItem::cantidadComprobantes)
                .orElse(0L);
    }

    private BigDecimal obtenerMontoPorEstado(List<DashboardAdminResponse.EstadoVentaItem> items, String estado) {
        return items.stream()
                .filter(item -> estado.equalsIgnoreCase(item.estado()))
                .findFirst()
                .map(DashboardAdminResponse.EstadoVentaItem::montoTotal)
                .orElse(CERO_MONETARIO);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private record RangoFechas(
            String filtroAplicado,
            LocalDate desde,
            LocalDate hasta) {

        private LocalDateTime hastaExclusiva() {
            return hasta.plusDays(1).atStartOfDay();
        }

        private GranularidadSerie granularidadSerie() {
            if (desde.equals(hasta)) {
                return GranularidadSerie.HORA;
            }
            return desde.plusMonths(2).isBefore(hasta) ? GranularidadSerie.MES : GranularidadSerie.DIA;
        }
    }

    private record ProductoColorKey(Integer productoId, Integer colorId) {
    }

    private record ImagenPrincipalData(String url, String urlThumb) {
    }

    private record ImagenPick(String url, String urlThumb, int orden, boolean esPrincipal) {
    }

    private enum GranularidadSerie {
        HORA,
        DIA,
        MES
    }
}
