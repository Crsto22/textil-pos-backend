package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.cliente.ClienteReporteResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteReporteService {

    private static final BigDecimal CERO_MONETARIO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CERO_PORCENTUAL = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;

    public ClienteReporteResponse obtenerReporte(
            String filtro,
            Integer idSucursalRequest,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursal = resolverIdSucursalFiltro(usuarioAutenticado, idSucursalRequest);
        Integer idEmpresa = resolverEmpresaContexto(usuarioAutenticado).getIdEmpresa();
        String nombreSucursal = resolverNombreSucursal(idSucursal);
        RangoFechas rango = resolverRangoFechas(filtro);
        LocalDateTime fechaInicio = rango.desde().atStartOfDay();
        LocalDateTime fechaFinExclusive = rango.hastaExclusiva();

        List<ResumenCliente> resumenes = mapResumenes(
                clienteRepository.obtenerResumenReporteClientes(
                        idEmpresa,
                        idSucursal,
                        fechaInicio,
                        fechaFinExclusive,
                        rango.hasta().atStartOfDay()));

        YearMonth mesCorte = YearMonth.from(rango.hasta());
        long clientesNuevosMes = clienteRepository.contarNuevosMesParaReporte(
                idEmpresa,
                idSucursal,
                mesCorte.atDay(1).atStartOfDay(),
                mesCorte.plusMonths(1).atDay(1).atStartOfDay());

        long clientesActivos = resumenes.size();
        BigDecimal recurrenciaPct = calcularRecurrencia(resumenes);

        List<ClienteReporteResponse.ClienteRankingItem> topClientesPorMonto = resumenes.stream()
                .filter(item -> item.totalGastado().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(ResumenCliente::totalGastado).reversed()
                        .thenComparing(ResumenCliente::compras, Comparator.reverseOrder())
                        .thenComparing(ResumenCliente::cliente))
                .limit(10)
                .map(this::toRankingItem)
                .toList();

        List<ClienteReporteResponse.ClienteRankingItem> topClientesPorCompras = resumenes.stream()
                .filter(item -> item.compras() > 0)
                .sorted(Comparator.comparing(ResumenCliente::compras).reversed()
                        .thenComparing(ResumenCliente::totalGastado, Comparator.reverseOrder())
                        .thenComparing(ResumenCliente::cliente))
                .limit(10)
                .map(this::toRankingItem)
                .toList();

        List<ClienteReporteResponse.CohorteSemanalItem> cohorteSemanal = mapCohortes(
                clienteRepository.obtenerCohorteSemanalReporte(idEmpresa, idSucursal, fechaInicio, fechaFinExclusive));

        List<ClienteReporteResponse.RfmClienteItem> segmentacionRfm = resumenes.stream()
                .sorted(Comparator.comparing(ResumenCliente::recenciaDias, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ResumenCliente::compras, Comparator.reverseOrder())
                        .thenComparing(ResumenCliente::totalGastado, Comparator.reverseOrder())
                        .thenComparing(ResumenCliente::cliente))
                .map(this::toRfmItem)
                .toList();

        return new ClienteReporteResponse(
                rango.filtroAplicado(),
                rango.desde(),
                rango.hasta(),
                idSucursal,
                nombreSucursal,
                new ClienteReporteResponse.KpiResumen(
                        clientesActivos,
                        clientesNuevosMes,
                        recurrenciaPct),
                topClientesPorMonto,
                topClientesPorCompras,
                cohorteSemanal,
                segmentacionRfm);
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolPermitido(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getRol() != Rol.ADMINISTRADOR && usuarioAutenticado.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar reportes de clientes");
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
            throw new RuntimeException("No tiene permisos para consultar clientes de otra sucursal");
        }
        return idSucursalUsuario;
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getSucursal() == null || usuarioAutenticado.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuarioAutenticado.getSucursal().getIdSucursal();
    }

    private Empresa resolverEmpresaContexto(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getSucursal() != null
                && usuarioAutenticado.getSucursal().getEmpresa() != null
                && usuarioAutenticado.getSucursal().getEmpresa().getIdEmpresa() != null) {
            return usuarioAutenticado.getSucursal().getEmpresa();
        }
        return empresaRepository.findTopByOrderByIdEmpresaAsc()
                .orElseThrow(() -> new RuntimeException("No hay empresa registrada"));
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
    }

    private String resolverNombreSucursal(Integer idSucursal) {
        if (idSucursal == null) {
            return "TODAS";
        }
        return obtenerSucursalActiva(idSucursal).getNombre();
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

    private List<ResumenCliente> mapResumenes(List<Object[]> rows) {
        List<ResumenCliente> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new ResumenCliente(
                    toInteger(row[0]),
                    toText(row[1], "-"),
                    toText(row[2], null),
                    toText(row[3], null),
                    toLocalDateTime(row[4]),
                    toLong(row[5]),
                    toBigDecimal(row[6]),
                    toBigDecimal(row[7]),
                    toInteger(row[8])));
        }
        return items;
    }

    private List<ClienteReporteResponse.CohorteSemanalItem> mapCohortes(List<Object[]> rows) {
        List<ClienteReporteResponse.CohorteSemanalItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            long clientesNuevos = toLong(row[2]);
            long clientesQueRecompran = toLong(row[3]);
            BigDecimal tasa = clientesNuevos <= 0
                    ? CERO_PORCENTUAL
                    : BigDecimal.valueOf(clientesQueRecompran)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(clientesNuevos), 2, RoundingMode.HALF_UP);
            items.add(new ClienteReporteResponse.CohorteSemanalItem(
                    toText(row[0], "-"),
                    toLocalDate(row[1]),
                    clientesNuevos,
                    clientesQueRecompran,
                    tasa));
        }
        return items;
    }

    private BigDecimal calcularRecurrencia(List<ResumenCliente> resumenes) {
        long clientesConCompra = resumenes.stream()
                .filter(item -> item.compras() > 0)
                .count();
        if (clientesConCompra <= 0) {
            return CERO_PORCENTUAL;
        }
        long clientesRecurrentes = resumenes.stream()
                .filter(item -> item.compras() > 1)
                .count();
        return BigDecimal.valueOf(clientesRecurrentes)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(clientesConCompra), 2, RoundingMode.HALF_UP);
    }

    private ClienteReporteResponse.ClienteRankingItem toRankingItem(ResumenCliente item) {
        return new ClienteReporteResponse.ClienteRankingItem(
                item.idCliente(),
                item.cliente(),
                item.tipoDocumento(),
                item.nroDocumento(),
                item.compras(),
                item.totalGastado(),
                item.ticketPromedio(),
                item.ultimaCompra());
    }

    private ClienteReporteResponse.RfmClienteItem toRfmItem(ResumenCliente item) {
        return new ClienteReporteResponse.RfmClienteItem(
                item.idCliente(),
                item.cliente(),
                item.tipoDocumento(),
                item.nroDocumento(),
                item.ultimaCompra(),
                item.recenciaDias(),
                item.compras(),
                item.totalGastado());
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
        return LocalDate.parse(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().replace(" ", "T"));
    }

    private record RangoFechas(
            String filtroAplicado,
            LocalDate desde,
            LocalDate hasta) {

        private LocalDateTime hastaExclusiva() {
            return hasta.plusDays(1).atStartOfDay();
        }
    }

    private record ResumenCliente(
            Integer idCliente,
            String cliente,
            String tipoDocumento,
            String nroDocumento,
            LocalDateTime ultimaCompra,
            long compras,
            BigDecimal totalGastado,
            BigDecimal ticketPromedio,
            Integer recenciaDias) {
    }
}
