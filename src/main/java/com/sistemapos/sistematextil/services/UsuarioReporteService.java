package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.usuario.UsuarioReporteResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioReporteService {

    private static final BigDecimal CERO_MONETARIO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;

    public UsuarioReporteResponse obtenerReporte(
            String filtro,
            Integer idSucursalRequest,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarAcceso(usuarioAutenticado);

        Integer idSucursal = resolverIdSucursalFiltro(idSucursalRequest);
        String nombreSucursal = resolverNombreSucursal(idSucursal);
        RangoFechas rango = resolverRangoFechas(filtro);
        LocalDateTime fechaInicio = rango.desde().atStartOfDay();
        LocalDateTime fechaFinExclusive = rango.hastaExclusiva();

        List<ResumenUsuario> resumenes = mapResumenes(
                usuarioRepository.obtenerResumenReporteUsuarios(idSucursal, fechaInicio, fechaFinExclusive));

        List<UsuarioReporteResponse.UsuarioKpiItem> rankingPorMonto = resumenes.stream()
                .sorted(Comparator.comparing(ResumenUsuario::monto).reversed()
                        .thenComparing(ResumenUsuario::ventas, Comparator.reverseOrder())
                        .thenComparing(ResumenUsuario::usuario))
                .map(this::toUsuarioKpiItem)
                .toList();

        List<UsuarioReporteResponse.UsuarioKpiItem> rankingPorComprobantes = resumenes.stream()
                .sorted(Comparator.comparing(ResumenUsuario::ventas).reversed()
                        .thenComparing(ResumenUsuario::monto, Comparator.reverseOrder())
                        .thenComparing(ResumenUsuario::usuario))
                .map(this::toUsuarioKpiItem)
                .toList();

        List<UsuarioReporteResponse.ControlAnulacionItem> controlAnulacionesPorUsuario = resumenes.stream()
                .sorted(Comparator.comparing(ResumenUsuario::anulaciones).reversed()
                        .thenComparing(ResumenUsuario::montoAnulado, Comparator.reverseOrder())
                        .thenComparing(ResumenUsuario::usuario))
                .map(this::toControlAnulacionItem)
                .toList();

        List<UsuarioReporteResponse.EvolucionUsuarioSerie> evolucionDiariaPorUsuario = construirEvolucionDiaria(
                resumenes,
                usuarioRepository.obtenerEvolucionDiariaReporteUsuarios(idSucursal, fechaInicio, fechaFinExclusive),
                rango.desde(),
                rango.hasta());

        return new UsuarioReporteResponse(
                rango.filtroAplicado(),
                rango.desde(),
                rango.hasta(),
                idSucursal,
                nombreSucursal,
                rankingPorMonto,
                rankingPorMonto,
                rankingPorComprobantes,
                controlAnulacionesPorUsuario,
                evolucionDiariaPorUsuario);
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarAcceso(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getRol() != Rol.ADMINISTRADOR) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar reportes de usuarios");
        }
    }

    private Integer resolverIdSucursalFiltro(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            return null;
        }
        return obtenerSucursalActiva(idSucursalRequest).getIdSucursal();
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

    private List<ResumenUsuario> mapResumenes(List<Object[]> rows) {
        List<ResumenUsuario> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new ResumenUsuario(
                    toInteger(row[0]),
                    toText(row[1], "-"),
                    toText(row[2], "-"),
                    toInteger(row[3]),
                    toText(row[4], null),
                    toLong(row[5]),
                    toBigDecimal(row[6]),
                    toLong(row[7]),
                    toBigDecimal(row[8])));
        }
        return items;
    }

    private UsuarioReporteResponse.UsuarioKpiItem toUsuarioKpiItem(ResumenUsuario item) {
        return new UsuarioReporteResponse.UsuarioKpiItem(
                item.idUsuario(),
                item.usuario(),
                item.rol(),
                item.ventas(),
                item.monto(),
                calcularTicketPromedio(item.monto(), item.ventas()));
    }

    private UsuarioReporteResponse.ControlAnulacionItem toControlAnulacionItem(ResumenUsuario item) {
        return new UsuarioReporteResponse.ControlAnulacionItem(
                item.idUsuario(),
                item.usuario(),
                item.rol(),
                item.anulaciones(),
                item.montoAnulado());
    }

    private List<UsuarioReporteResponse.EvolucionUsuarioSerie> construirEvolucionDiaria(
            List<ResumenUsuario> resumenes,
            List<Object[]> rows,
            LocalDate desde,
            LocalDate hasta) {
        Map<Integer, LinkedHashMap<LocalDate, UsuarioReporteResponse.PuntoDiarioItem>> seriesPorUsuario = new HashMap<>();
        Map<Integer, ResumenUsuario> resumenPorUsuario = new HashMap<>();

        for (ResumenUsuario resumen : resumenes) {
            resumenPorUsuario.put(resumen.idUsuario(), resumen);
            LinkedHashMap<LocalDate, UsuarioReporteResponse.PuntoDiarioItem> puntos = new LinkedHashMap<>();
            LocalDate cursor = desde;
            while (!cursor.isAfter(hasta)) {
                puntos.put(cursor, new UsuarioReporteResponse.PuntoDiarioItem(
                        cursor,
                        0L,
                        CERO_MONETARIO,
                        0L,
                        CERO_MONETARIO));
                cursor = cursor.plusDays(1);
            }
            seriesPorUsuario.put(resumen.idUsuario(), puntos);
        }

        for (Object[] row : rows) {
            LocalDate fecha = toLocalDate(row[0]);
            Integer idUsuario = toInteger(row[1]);
            LinkedHashMap<LocalDate, UsuarioReporteResponse.PuntoDiarioItem> puntos = seriesPorUsuario.get(idUsuario);
            if (fecha == null || puntos == null || !puntos.containsKey(fecha)) {
                continue;
            }
            puntos.put(fecha, new UsuarioReporteResponse.PuntoDiarioItem(
                    fecha,
                    toLong(row[4]),
                    toBigDecimal(row[5]),
                    toLong(row[6]),
                    toBigDecimal(row[7])));
        }

        List<UsuarioReporteResponse.EvolucionUsuarioSerie> series = new ArrayList<>();
        for (ResumenUsuario resumen : resumenes.stream()
                .sorted(Comparator.comparing(ResumenUsuario::monto).reversed()
                        .thenComparing(ResumenUsuario::ventas, Comparator.reverseOrder())
                        .thenComparing(ResumenUsuario::usuario))
                .toList()) {
            LinkedHashMap<LocalDate, UsuarioReporteResponse.PuntoDiarioItem> puntos = seriesPorUsuario.get(resumen.idUsuario());
            series.add(new UsuarioReporteResponse.EvolucionUsuarioSerie(
                    resumen.idUsuario(),
                    resumen.usuario(),
                    resumen.rol(),
                    new ArrayList<>(puntos.values())));
        }
        return series;
    }

    private BigDecimal calcularTicketPromedio(BigDecimal monto, long ventas) {
        if (monto == null || ventas <= 0) {
            return CERO_MONETARIO;
        }
        return monto.divide(BigDecimal.valueOf(ventas), 2, RoundingMode.HALF_UP);
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

    private record ResumenUsuario(
            Integer idUsuario,
            String usuario,
            String rol,
            Integer idSucursal,
            String nombreSucursal,
            long ventas,
            BigDecimal monto,
            long anulaciones,
            BigDecimal montoAnulado) {
    }
}
