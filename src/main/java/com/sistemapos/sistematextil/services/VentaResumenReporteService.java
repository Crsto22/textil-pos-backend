package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaResumenReporteResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VentaResumenReporteService {

    private static final BigDecimal CERO_MONETARIO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String ESTADO_EMITIDA = "EMITIDA";
    private static final String ESTADO_ANULADA = "ANULADA";

    private final VentaRepository ventaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;

    public VentaResumenReporteResponse obtenerReporte(
            String filtro,
            Integer idSucursalRequest,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursal = resolverIdSucursalFiltro(usuarioAutenticado, idSucursalRequest);
        String nombreSucursal = resolverNombreSucursal(idSucursal);
        RangoFechas rango = resolverRangoFechas(filtro);
        LocalDateTime fechaInicio = rango.desde().atStartOfDay();
        LocalDateTime fechaFinExclusive = rango.hastaExclusiva();

        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioDia = hoy.atStartOfDay();
        LocalDateTime finDiaExclusive = hoy.plusDays(1).atStartOfDay();
        YearMonth mesActual = YearMonth.from(hoy);
        LocalDateTime inicioMes = mesActual.atDay(1).atStartOfDay();
        LocalDateTime finMesExclusive = mesActual.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal ventasDelDia = toBigDecimal(ventaRepository.sumarTotalEmitido(
                idSucursal,
                null,
                inicioDia,
                finDiaExclusive));
        BigDecimal ventasDelMes = toBigDecimal(ventaRepository.sumarTotalEmitido(
                idSucursal,
                null,
                inicioMes,
                finMesExclusive));
        BigDecimal ticketPromedio = toBigDecimal(ventaRepository.promedioVentaEmitida(
                idSucursal,
                null,
                fechaInicio,
                fechaFinExclusive));
        long cantidadComprobantes = ventaRepository.contarTicketsEmitidos(
                idSucursal,
                null,
                fechaInicio,
                fechaFinExclusive);

        List<VentaResumenReporteResponse.TendenciaDiaItem> tendenciaMontoPorDia = construirTendenciaMontoPorDia(
                ventaRepository.obtenerVentasPorFecha(idSucursal, null, fechaInicio, fechaFinExclusive),
                rango.desde(),
                rango.hasta());
        List<VentaResumenReporteResponse.TipoComprobanteItem> ventasPorTipoComprobante = mapVentasPorTipoComprobante(
                ventaRepository.obtenerVentasPorTipoComprobanteResumen(idSucursal, fechaInicio, fechaFinExclusive));
        List<VentaResumenReporteResponse.EstadoDistribucionItem> distribucionPorEstado = construirDistribucionPorEstado(
                ventaRepository.obtenerDistribucionPorEstadoResumen(idSucursal, fechaInicio, fechaFinExclusive));
        List<VentaResumenReporteResponse.SucursalVentaItem> ventasPorSucursal = mapVentasPorSucursal(
                ventaRepository.obtenerVentasPorSucursalResumen(idSucursal, fechaInicio, fechaFinExclusive));

        return new VentaResumenReporteResponse(
                rango.filtroAplicado(),
                rango.desde(),
                rango.hasta(),
                idSucursal,
                nombreSucursal,
                new VentaResumenReporteResponse.KpiResumen(
                        ventasDelDia,
                        ventasDelMes,
                        ticketPromedio,
                        cantidadComprobantes),
                tendenciaMontoPorDia,
                ventasPorTipoComprobante,
                distribucionPorEstado,
                ventasPorSucursal);
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolPermitido(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getRol() != Rol.ADMINISTRADOR
                && usuarioAutenticado.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar reportes de ventas");
        }
    }

    private Integer resolverIdSucursalFiltro(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        if (usuarioAutenticado.getRol() == Rol.ADMINISTRADOR) {
            if (idSucursalRequest == null) {
                return null;
            }
            return obtenerSucursalActiva(idSucursalRequest).getIdSucursal();
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalRequest.equals(idSucursalUsuario)) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getSucursal() == null || usuarioAutenticado.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuarioAutenticado.getSucursal().getIdSucursal();
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

        String filtroNormalizado = filtro.trim().toUpperCase(Locale.ROOT)
                .replace(" ", "_")
                .replace(".", "");

        if ("ULT7DIAS".equals(filtroNormalizado) || "ULT_7".equals(filtroNormalizado) || "7_DIAS".equals(filtroNormalizado)) {
            return "ULT_7_DIAS";
        }
        if ("ULT14DIAS".equals(filtroNormalizado)
                || "ULT_14".equals(filtroNormalizado)
                || "14_DIAS".equals(filtroNormalizado)) {
            return "ULT_14_DIAS";
        }
        if ("ULT30DIAS".equals(filtroNormalizado)
                || "ULT_30".equals(filtroNormalizado)
                || "30_DIAS".equals(filtroNormalizado)) {
            return "ULT_30_DIAS";
        }
        if ("ULT12MESES".equals(filtroNormalizado)
                || "12_MESES".equals(filtroNormalizado)
                || "12MESES".equals(filtroNormalizado)) {
            return "ULT_12_MESES";
        }

        return filtroNormalizado;
    }

    private List<VentaResumenReporteResponse.TendenciaDiaItem> construirTendenciaMontoPorDia(
            List<Object[]> rows,
            LocalDate desde,
            LocalDate hasta) {
        LinkedHashMap<LocalDate, BigDecimal> montosPorDia = new LinkedHashMap<>();
        LocalDate cursor = desde;
        while (!cursor.isAfter(hasta)) {
            montosPorDia.put(cursor, CERO_MONETARIO);
            cursor = cursor.plusDays(1);
        }

        for (Object[] row : rows) {
            LocalDate fecha = toLocalDate(row[0]);
            if (fecha != null && montosPorDia.containsKey(fecha)) {
                montosPorDia.put(fecha, toBigDecimal(row[1]));
            }
        }

        List<VentaResumenReporteResponse.TendenciaDiaItem> items = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : montosPorDia.entrySet()) {
            items.add(new VentaResumenReporteResponse.TendenciaDiaItem(entry.getKey(), entry.getValue()));
        }
        return items;
    }

    private List<VentaResumenReporteResponse.TipoComprobanteItem> mapVentasPorTipoComprobante(List<Object[]> rows) {
        List<VentaResumenReporteResponse.TipoComprobanteItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new VentaResumenReporteResponse.TipoComprobanteItem(
                    toText(row[0], "-"),
                    toLong(row[1]),
                    toBigDecimal(row[2])));
        }
        return items;
    }

    private List<VentaResumenReporteResponse.EstadoDistribucionItem> construirDistribucionPorEstado(List<Object[]> rows) {
        LinkedHashMap<String, VentaResumenReporteResponse.EstadoDistribucionItem> items = new LinkedHashMap<>();
        items.put(ESTADO_EMITIDA, new VentaResumenReporteResponse.EstadoDistribucionItem(
                ESTADO_EMITIDA,
                0L,
                CERO_MONETARIO));
        items.put(ESTADO_ANULADA, new VentaResumenReporteResponse.EstadoDistribucionItem(
                ESTADO_ANULADA,
                0L,
                CERO_MONETARIO));

        for (Object[] row : rows) {
            String estado = toText(row[0], null);
            if (estado == null || !items.containsKey(estado)) {
                continue;
            }
            items.put(estado, new VentaResumenReporteResponse.EstadoDistribucionItem(
                    estado,
                    toLong(row[1]),
                    toBigDecimal(row[2])));
        }

        return new ArrayList<>(items.values());
    }

    private List<VentaResumenReporteResponse.SucursalVentaItem> mapVentasPorSucursal(List<Object[]> rows) {
        List<VentaResumenReporteResponse.SucursalVentaItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new VentaResumenReporteResponse.SucursalVentaItem(
                    toInteger(row[0]),
                    toText(row[1], "-"),
                    toLong(row[2]),
                    toBigDecimal(row[3])));
        }
        return items;
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

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return CERO_MONETARIO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private String toText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isBlank() ? fallback : text;
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
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(value.toString().replace("T", " ").substring(0, 10));
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
