package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CotizacionRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
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
    private final ProductoVarianteRepository productoVarianteRepository;

    public Object obtenerDashboard(String filtro, String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);

        return switch (usuario.getRol()) {
            case ADMINISTRADOR -> construirDashboardAdmin(usuario, filtro);
            case VENTAS -> construirDashboardVentas(usuario, filtro);
            case ALMACEN -> construirDashboardAlmacen(usuario);
        };
    }

    private DashboardAdminResponse construirDashboardAdmin(Usuario usuario, String filtro) {
        RangoFechas rango = resolverRangoFechas(filtro);
        Integer idSucursal = resolverIdSucursalSegunRol(usuario);

        BigDecimal ventasTotales = asegurarMoneda(
                ventaRepository.sumarTotalEmitido(idSucursal, null, rango.desde().atStartOfDay(), rango.hastaExclusiva()));
        long productosVendidos = ventaDetalleRepository.sumarCantidadVendida(
                idSucursal,
                null,
                rango.desde().atStartOfDay(),
                rango.hastaExclusiva());
        long ticketsEmitidos = ventaRepository.contarTicketsEmitidos(
                idSucursal,
                null,
                rango.desde().atStartOfDay(),
                rango.hastaExclusiva());

        List<DashboardIngresoMetodoPagoItem> ingresosPorMetodoPago = mapIngresosPorMetodo(
                pagoRepository.obtenerIngresosPorMetodoPago(
                        idSucursal,
                        null,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()));

        List<DashboardSerieItem> ventasPorFecha = construirSerieContinua(
                rango.desde(),
                rango.hasta(),
                ventaRepository.obtenerVentasPorFecha(
                        idSucursal,
                        null,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()));

        List<DashboardTopProductoItem> topProductosMasVendidos = mapTopProductos(
                ventaDetalleRepository.obtenerTopProductosVendidos(
                        idSucursal,
                        null,
                        rango.desde().atStartOfDay(),
                        rango.hastaExclusiva()));

        return new DashboardAdminResponse(
                "ADMIN",
                rango.filtroAplicado(),
                rango.desde(),
                rango.hasta(),
                ventasTotales,
                productosVendidos,
                ticketsEmitidos,
                ingresosPorMetodoPago,
                ventasPorFecha,
                topProductosMasVendidos);
    }

    private DashboardVentasResponse construirDashboardVentas(Usuario usuario, String filtro) {
        RangoFechas rango = resolverRangoFechas(filtro);
        Integer idSucursal = resolverIdSucursalSegunRol(usuario);
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

        List<DashboardSerieItem> misVentasPorFecha = construirSerieContinua(
                rango.desde(),
                rango.hasta(),
                ventaRepository.obtenerVentasPorFecha(
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

    private DashboardAlmacenResponse construirDashboardAlmacen(Usuario usuario) {
        Integer idSucursal = resolverIdSucursalSegunRol(usuario);

        long variantesAgotadas = productoVarianteRepository.contarVariantesAgotadas(idSucursal);
        long stockBajo = productoVarianteRepository.contarStockBajo(idSucursal, UMBRAL_STOCK_BAJO);
        long totalFisicoEnTienda = productoVarianteRepository.sumarStockTotal(idSucursal);
        long variantesDisponibles = productoVarianteRepository.contarVariantesDisponibles(idSucursal);

        List<DashboardStockCeroItem> reposicionUrgente = mapStockCero(
                productoVarianteRepository.listarReposicionUrgente(idSucursal));

        List<DashboardTopProductoItem> topMayorSalida = mapTopProductos(
                ventaDetalleRepository.obtenerTopProductosVendidos(idSucursal, null, null, null));

        return new DashboardAlmacenResponse(
                "ALMACEN",
                "TIEMPO_REAL",
                variantesAgotadas,
                stockBajo,
                totalFisicoEnTienda,
                variantesDisponibles,
                reposicionUrgente,
                topMayorSalida);
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer resolverIdSucursalSegunRol(Usuario usuario) {
        if (usuario.getRol() == Rol.ADMINISTRADOR) {
            return null;
        }
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private RangoFechas resolverRangoFechas(String filtro) {
        String filtroNormalizado = normalizarFiltro(filtro);
        LocalDate hoy = LocalDate.now();

        return switch (filtroNormalizado) {
            case "HOY" -> new RangoFechas("HOY", hoy, hoy);
            case "ULT_7_DIAS" -> new RangoFechas("ULT_7_DIAS", hoy.minusDays(6), hoy);
            case "ULT_14_DIAS" -> new RangoFechas("ULT_14_DIAS", hoy.minusDays(13), hoy);
            case "ULT_30_DIAS" -> new RangoFechas("ULT_30_DIAS", hoy.minusDays(29), hoy);
            case "ULT_12_MESES" -> new RangoFechas("ULT_12_MESES", hoy.minusMonths(12).plusDays(1), hoy);
            default -> throw new RuntimeException(
                    "Filtro invalido. Use: HOY, ULT_7_DIAS, ULT_14_DIAS, ULT_30_DIAS, ULT_12_MESES");
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
        if ("ULT12MESES".equals(f) || "12_MESES".equals(f) || "12MESES".equals(f)) {
            return "ULT_12_MESES";
        }

        return f;
    }

    private List<DashboardIngresoMetodoPagoItem> mapIngresosPorMetodo(List<Object[]> rows) {
        List<DashboardIngresoMetodoPagoItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            String metodo = row[0] == null ? "SIN_METODO" : row[0].toString();
            BigDecimal monto = asegurarMoneda((BigDecimal) row[1]);
            items.add(new DashboardIngresoMetodoPagoItem(metodo, monto));
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
        List<DashboardStockCeroItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new DashboardStockCeroItem(
                    toInteger(row[0]),
                    row[1] == null ? "-" : row[1].toString(),
                    row[2] == null ? "-" : row[2].toString(),
                    row[3] == null ? "-" : row[3].toString(),
                    toInteger(row[4]),
                    row[5] == null ? "-" : row[5].toString()));
        }
        return items;
    }

    private List<DashboardSerieItem> construirSerieContinua(LocalDate desde, LocalDate hasta, List<Object[]> rows) {
        Map<LocalDate, BigDecimal> montosPorFecha = new LinkedHashMap<>();
        LocalDate cursor = desde;
        while (!cursor.isAfter(hasta)) {
            montosPorFecha.put(cursor, CERO_MONETARIO);
            cursor = cursor.plusDays(1);
        }

        for (Object[] row : rows) {
            LocalDate fecha = toLocalDate(row[0]);
            BigDecimal monto = asegurarMoneda((BigDecimal) row[1]);
            if (fecha != null && montosPorFecha.containsKey(fecha)) {
                montosPorFecha.put(fecha, monto);
            }
        }

        List<DashboardSerieItem> serie = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : montosPorFecha.entrySet()) {
            serie.add(new DashboardSerieItem(entry.getKey(), entry.getValue()));
        }
        return serie;
    }

    private BigDecimal asegurarMoneda(BigDecimal valor) {
        if (valor == null) {
            return CERO_MONETARIO;
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
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
    }
}
