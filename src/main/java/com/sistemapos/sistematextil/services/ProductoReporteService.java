package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.util.producto.ProductoReporteResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoReporteService {

    private static final BigDecimal CERO_MONETARIO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final VentaDetalleRepository ventaDetalleRepository;

    public ProductoReporteResponse obtenerReporte(
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

        long productosActivos = productoRepository.contarActivosParaReporte(idSucursal);
        long variantesActivas = productoVarianteRepository.contarVariantesActivasParaReporte(idSucursal);
        long variantesSinStock = productoVarianteRepository.contarVariantesSinStockParaReporte(idSucursal);

        List<ProductoReporteResponse.TopProductoItem> topProductosPorMonto = mapTopVariantes(
                ventaDetalleRepository.obtenerTopProductosPorMonto(idSucursal, fechaInicio, fechaFinExclusive));
        List<ProductoReporteResponse.TopProductoItem> topProductosPorUnidades = mapTopVariantes(
                ventaDetalleRepository.obtenerTopProductosPorUnidades(idSucursal, fechaInicio, fechaFinExclusive));
        List<ProductoReporteResponse.HeatmapTallaColorItem> heatmapVentasPorTallaColor = mapHeatmap(
                ventaDetalleRepository.obtenerHeatmapVentasPorTallaColor(idSucursal, fechaInicio, fechaFinExclusive));
        List<ProductoReporteResponse.CategoriaVentaItem> ventasPorCategoria = mapCategorias(
                ventaDetalleRepository.obtenerVentasPorCategoria(idSucursal, fechaInicio, fechaFinExclusive));
        long totalUnidadesVendidas = ventaDetalleRepository.sumarCantidadVendida(
                idSucursal,
                null,
                fechaInicio,
                fechaFinExclusive);
        BigDecimal rotacionPromedio = calcularRotacionPromedio(totalUnidadesVendidas, variantesActivas);

        return new ProductoReporteResponse(
                rango.filtroAplicado(),
                rango.desde(),
                rango.hasta(),
                idSucursal,
                nombreSucursal,
                new ProductoReporteResponse.KpiResumen(
                        productosActivos,
                        variantesActivas,
                        variantesSinStock,
                        rotacionPromedio),
                topProductosPorMonto,
                topProductosPorUnidades,
                heatmapVentasPorTallaColor,
                ventasPorCategoria);
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar reportes de productos");
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

    private List<ProductoReporteResponse.TopProductoItem> mapTopVariantes(List<Object[]> rows) {
        List<ProductoReporteResponse.TopProductoItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            String color = toText(row[3], null);
            String talla = toText(row[4], null);
            items.add(new ProductoReporteResponse.TopProductoItem(
                    toInteger(row[0]),
                    toText(row[1], "-"),
                    toInteger(row[2]),
                    construirEtiquetaVariante(color, talla),
                    color,
                    talla,
                    toInteger(row[5]),
                    toText(row[6], "-"),
                    toLong(row[7]),
                    toBigDecimal(row[8])));
        }
        return items;
    }

    private List<ProductoReporteResponse.HeatmapTallaColorItem> mapHeatmap(List<Object[]> rows) {
        List<ProductoReporteResponse.HeatmapTallaColorItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new ProductoReporteResponse.HeatmapTallaColorItem(
                    toInteger(row[0]),
                    toText(row[1], "-"),
                    toText(row[2], null),
                    toInteger(row[3]),
                    toText(row[4], "-"),
                    toLong(row[5]),
                    toBigDecimal(row[6])));
        }
        return items;
    }

    private List<ProductoReporteResponse.CategoriaVentaItem> mapCategorias(List<Object[]> rows) {
        List<ProductoReporteResponse.CategoriaVentaItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new ProductoReporteResponse.CategoriaVentaItem(
                    toInteger(row[0]),
                    toText(row[1], "SIN_CATEGORIA"),
                    toLong(row[2]),
                    toBigDecimal(row[3])));
        }
        return items;
    }

    private BigDecimal calcularRotacionPromedio(long totalUnidadesVendidas, long variantesActivas) {
        if (variantesActivas <= 0) {
            return CERO_MONETARIO;
        }
        return BigDecimal.valueOf(totalUnidadesVendidas)
                .divide(BigDecimal.valueOf(variantesActivas), 2, RoundingMode.HALF_UP);
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

    private String construirEtiquetaVariante(String color, String talla) {
        if (color != null && talla != null) {
            return color + " / " + talla;
        }
        if (color != null) {
            return color;
        }
        if (talla != null) {
            return talla;
        }
        return "VARIANTE";
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
