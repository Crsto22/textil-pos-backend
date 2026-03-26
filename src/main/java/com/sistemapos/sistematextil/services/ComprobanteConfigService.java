package com.sistemapos.sistematextil.services;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CotizacionRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigCreateRequest;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigResponse;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigUpdateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComprobanteConfigService {

    private static final String TIPO_NOTA_VENTA = "NOTA DE VENTA";
    private static final String TIPO_BOLETA = "BOLETA";
    private static final String TIPO_FACTURA = "FACTURA";
    private static final String TIPO_NC_BOLETA = "NOTA_CREDITO_BOLETA";
    private static final String TIPO_NC_FACTURA = "NOTA_CREDITO_FACTURA";
    private static final String TIPO_COTIZACION = "COTIZACION";
    private static final String PREFIJO_SERIE_COTIZACION = "COT";
    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";

    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final SucursalRepository sucursalRepository;
    private final VentaRepository ventaRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final CotizacionRepository cotizacionRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public List<ComprobanteConfigResponse> listar(
            String activo,
            Integer idSucursal,
            Boolean habilitadoVenta) {
        String activoFiltro = normalizarActivoParaFiltro(activo);
        Integer idSucursalFiltro = normalizarIdSucursalParaFiltro(idSucursal);
        Boolean habilitadoVentaFiltro = normalizarHabilitadoVentaParaFiltro(habilitadoVenta);

        return comprobanteConfigRepository.buscar(activoFiltro, idSucursalFiltro, habilitadoVentaFiltro)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PagedResponse<ComprobanteConfigResponse> listarPaginado(
            int page,
            String activo,
            Integer idSucursal,
            Boolean habilitadoVenta) {
        validarPagina(page);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idComprobante").ascending());
        Page<ComprobanteConfigResponse> comprobantes = comprobanteConfigRepository.buscarPaginado(
                null,
                normalizarActivoParaFiltro(activo),
                normalizarIdSucursalParaFiltro(idSucursal),
                normalizarHabilitadoVentaParaFiltro(habilitadoVenta),
                pageable).map(this::toResponse);

        return PagedResponse.fromPage(comprobantes);
    }

    public PagedResponse<ComprobanteConfigResponse> buscarPaginado(
            String term,
            int page,
            String activo,
            Integer idSucursal,
            Boolean habilitadoVenta) {
        validarPagina(page);

        String termino = normalizarTerminoBusqueda(term);
        if (termino == null) {
            return listarPaginado(page, activo, idSucursal, habilitadoVenta);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idComprobante").ascending());
        Page<ComprobanteConfigResponse> comprobantes = comprobanteConfigRepository.buscarPaginado(
                termino,
                normalizarActivoParaFiltro(activo),
                normalizarIdSucursalParaFiltro(idSucursal),
                normalizarHabilitadoVentaParaFiltro(habilitadoVenta),
                pageable).map(this::toResponse);

        return PagedResponse.fromPage(comprobantes);
    }

    public ComprobanteConfigResponse obtener(Integer idComprobante) {
        ComprobanteConfig comprobante = comprobanteConfigRepository.findByIdComprobanteAndDeletedAtIsNull(idComprobante)
                .orElseThrow(() -> new RuntimeException("Comprobante con ID " + idComprobante + " no encontrado"));
        return toResponse(comprobante);
    }

    @Transactional
    public ComprobanteConfigResponse crear(ComprobanteConfigCreateRequest request) {
        return insertar(request);
    }

    @Transactional
    public ComprobanteConfigResponse insertar(ComprobanteConfigCreateRequest request) {
        Integer idSucursal = normalizarIdSucursalObligatorio(request.idSucursal());
        String tipoComprobante = normalizarTipoComprobante(request.tipoComprobante());
        String serie = normalizarSerie(request.serie());
        String activo = normalizarActivo(request.activo());
        int ultimoCorrelativo = normalizarUltimoCorrelativoOpcional(request.ultimoCorrelativo());

        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        int maxCorrelativoDocumento = maxCorrelativoDocumento(idSucursal, tipoComprobante, serie);
        if (ultimoCorrelativo < maxCorrelativoDocumento) {
            throw new RuntimeException("ultimoCorrelativo no puede ser menor al maximo correlativo registrado ("
                    + maxCorrelativoDocumento + ") para " + tipoComprobante + " " + serie);
        }

        ComprobanteConfig comprobante = comprobanteConfigRepository
                .findBySucursal_IdSucursalAndTipoComprobanteAndSerie(idSucursal, tipoComprobante, serie)
                .orElse(null);

        if (comprobante != null && comprobante.getDeletedAt() == null) {
            throw new RuntimeException("Ya existe configuracion para ese tipo de comprobante con esa serie en la sucursal");
        }

        validarConfiguracionActivaUnicaCotizacion(idSucursal, tipoComprobante, activo, null);

        if (comprobante == null) {
            comprobante = new ComprobanteConfig();
        }

        comprobante.setSucursal(sucursal);
        comprobante.setTipoComprobante(tipoComprobante);
        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(ultimoCorrelativo);
        comprobante.setActivo(activo);
        comprobante.setHabilitadoVenta(esTipoHabilitadoVenta(tipoComprobante));
        comprobante.setDeletedAt(null);

        try {
            return toResponse(comprobanteConfigRepository.save(comprobante));
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Ya existe configuracion para ese tipo de comprobante con esa serie en la sucursal");
        }
    }

    @Transactional
    public ComprobanteConfigResponse actualizar(Integer idComprobante, ComprobanteConfigUpdateRequest request) {
        ComprobanteConfig comprobante = comprobanteConfigRepository.findByIdComprobanteAndDeletedAtIsNull(idComprobante)
                .orElseThrow(() -> new RuntimeException("Comprobante con ID " + idComprobante + " no encontrado"));

        String serie = normalizarSerie(request.serie());
        String activo = normalizarActivo(request.activo());
        int ultimoCorrelativo = normalizarUltimoCorrelativoObligatorio(request.ultimoCorrelativo());
        Integer idSucursal = comprobante.getSucursal() != null ? comprobante.getSucursal().getIdSucursal() : null;

        int maxCorrelativoDocumento = maxCorrelativoDocumento(idSucursal, comprobante.getTipoComprobante(), serie);
        if (ultimoCorrelativo < maxCorrelativoDocumento) {
            throw new RuntimeException("ultimoCorrelativo no puede ser menor al maximo correlativo registrado ("
                    + maxCorrelativoDocumento + ") para " + comprobante.getTipoComprobante() + " " + serie);
        }

        ComprobanteConfig duplicado = comprobanteConfigRepository
                .findBySucursal_IdSucursalAndTipoComprobanteAndSerie(idSucursal, comprobante.getTipoComprobante(), serie)
                .orElse(null);
        if (duplicado != null && !duplicado.getIdComprobante().equals(comprobante.getIdComprobante())) {
            throw new RuntimeException("Ya existe configuracion para ese tipo de comprobante con esa serie en la sucursal");
        }

        validarConfiguracionActivaUnicaCotizacion(
                idSucursal,
                comprobante.getTipoComprobante(),
                activo,
                comprobante.getIdComprobante());

        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(ultimoCorrelativo);
        comprobante.setActivo(activo);
        comprobante.setHabilitadoVenta(esTipoHabilitadoVenta(comprobante.getTipoComprobante()));

        try {
            return toResponse(comprobanteConfigRepository.save(comprobante));
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Ya existe configuracion para ese tipo de comprobante con esa serie en la sucursal");
        }
    }

    private ComprobanteConfigResponse toResponse(ComprobanteConfig comprobante) {
        Integer ultimo = comprobante.getUltimoCorrelativo() == null ? 0 : comprobante.getUltimoCorrelativo();
        return new ComprobanteConfigResponse(
                comprobante.getIdComprobante(),
                comprobante.getSucursal() != null ? comprobante.getSucursal().getIdSucursal() : null,
                comprobante.getSucursal() != null ? comprobante.getSucursal().getNombre() : null,
                comprobante.getTipoComprobante(),
                comprobante.getSerie(),
                ultimo,
                ultimo + 1,
                comprobante.getActivo(),
                comprobante.getHabilitadoVenta(),
                comprobante.getCreatedAt(),
                comprobante.getUpdatedAt(),
                comprobante.getDeletedAt());
    }

    private boolean esTipoHabilitadoVenta(String tipoComprobante) {
        return TIPO_NOTA_VENTA.equals(tipoComprobante)
                || TIPO_BOLETA.equals(tipoComprobante)
                || TIPO_FACTURA.equals(tipoComprobante);
    }

    private boolean esTipoNotaCredito(String tipoComprobante) {
        return TIPO_NC_BOLETA.equals(tipoComprobante) || TIPO_NC_FACTURA.equals(tipoComprobante);
    }

    private boolean esTipoCotizacion(String tipoComprobante) {
        return TIPO_COTIZACION.equals(tipoComprobante);
    }

    private int maxCorrelativoDocumento(Integer idSucursal, String tipoComprobante, String serie) {
        if (idSucursal == null || tipoComprobante == null || serie == null) {
            return 0;
        }
        Integer max;
        if (esTipoHabilitadoVenta(tipoComprobante)) {
            max = ventaRepository.obtenerMaxCorrelativoPorDocumento(idSucursal, tipoComprobante, serie);
        } else if (esTipoNotaCredito(tipoComprobante)) {
            max = notaCreditoRepository.obtenerMaxCorrelativoPorDocumento(idSucursal, tipoComprobante, serie);
        } else if (esTipoCotizacion(tipoComprobante)) {
            max = cotizacionRepository.obtenerMaxCorrelativoPorSerie(idSucursal, serie);
        } else {
            return 0;
        }
        return max == null ? 0 : max;
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private Integer normalizarIdSucursalObligatorio(Integer idSucursal) {
        if (idSucursal == null || idSucursal <= 0) {
            throw new RuntimeException("idSucursal debe ser mayor a 0");
        }
        return idSucursal;
    }

    private Integer normalizarIdSucursalParaFiltro(Integer idSucursal) {
        if (idSucursal == null) {
            return null;
        }
        if (idSucursal <= 0) {
            throw new RuntimeException("idSucursal debe ser mayor a 0");
        }
        return idSucursal;
    }

    private String normalizarTipoComprobante(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            throw new RuntimeException("Ingrese tipoComprobante");
        }
        String normalizado = tipoComprobante.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (normalizado) {
            case "NOTA_DE_VENTA" -> TIPO_NOTA_VENTA;
            case "BOLETA" -> TIPO_BOLETA;
            case "FACTURA" -> TIPO_FACTURA;
            case "NOTA_CREDITO_BOLETA", "NOTA_DE_CREDITO_BOLETA" -> TIPO_NC_BOLETA;
            case "NOTA_CREDITO_FACTURA", "NOTA_DE_CREDITO_FACTURA" -> TIPO_NC_FACTURA;
            case "COTIZACION" -> TIPO_COTIZACION;
            default -> throw new RuntimeException(
                    "tipoComprobante permitido: NOTA DE VENTA, BOLETA, FACTURA, NOTA_CREDITO_BOLETA, NOTA_CREDITO_FACTURA o COTIZACION");
        };
    }

    private String normalizarSerie(String serie) {
        if (serie == null || serie.isBlank()) {
            throw new RuntimeException("Ingrese serie");
        }
        String normalizada = serie.trim().toUpperCase(Locale.ROOT);
        if (normalizada.length() > 10) {
            throw new RuntimeException("La serie no debe superar 10 caracteres");
        }
        return normalizada;
    }

    private int normalizarUltimoCorrelativoOpcional(Integer ultimoCorrelativo) {
        if (ultimoCorrelativo == null) {
            return 0;
        }
        if (ultimoCorrelativo < 0) {
            throw new RuntimeException("ultimoCorrelativo no puede ser negativo");
        }
        return ultimoCorrelativo;
    }

    private int normalizarUltimoCorrelativoObligatorio(Integer ultimoCorrelativo) {
        if (ultimoCorrelativo == null) {
            throw new RuntimeException("Ingrese ultimoCorrelativo");
        }
        if (ultimoCorrelativo < 0) {
            throw new RuntimeException("ultimoCorrelativo no puede ser negativo");
        }
        return ultimoCorrelativo;
    }

    private String normalizarActivo(String activo) {
        if (activo == null || activo.isBlank()) {
            return ACTIVO;
        }
        String activoNormalizado = activo.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVO.equals(activoNormalizado) && !INACTIVO.equals(activoNormalizado)) {
            throw new RuntimeException("activo permitido: ACTIVO o INACTIVO");
        }
        return activoNormalizado;
    }

    private String normalizarActivoParaFiltro(String activo) {
        if (activo == null || activo.isBlank()) {
            return null;
        }
        String activoNormalizado = activo.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVO.equals(activoNormalizado) && !INACTIVO.equals(activoNormalizado)) {
            throw new RuntimeException("activo permitido: ACTIVO o INACTIVO");
        }
        return activoNormalizado;
    }

    private Boolean normalizarHabilitadoVentaParaFiltro(Boolean habilitadoVenta) {
        return habilitadoVenta;
    }

    private String normalizarTerminoBusqueda(String term) {
        if (term == null) {
            return null;
        }
        String normalizado = term.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    @Transactional
    public void asegurarConfiguracionesInicialesSucursal(Integer idSucursal) {
        Integer idSucursalNormalizado = normalizarIdSucursalObligatorio(idSucursal);
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalNormalizado)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        asegurarConfiguracionInicial(sucursal, TIPO_NOTA_VENTA, "NV01");
        asegurarConfiguracionInicial(sucursal, TIPO_BOLETA, "B001");
        asegurarConfiguracionInicial(sucursal, TIPO_FACTURA, "F001");
        asegurarConfiguracionInicial(sucursal, TIPO_NC_BOLETA, "BC01");
        asegurarConfiguracionInicial(sucursal, TIPO_NC_FACTURA, "FC01");
        asegurarConfiguracionCotizacionInicial(sucursal);
    }

    private void asegurarConfiguracionInicial(Sucursal sucursal, String tipoComprobante, String serie) {
        Integer idSucursal = sucursal.getIdSucursal();
        ComprobanteConfig existente = comprobanteConfigRepository
                .findBySucursal_IdSucursalAndTipoComprobanteAndSerie(idSucursal, tipoComprobante, serie)
                .orElse(null);

        if (existente != null && existente.getDeletedAt() == null) {
            return;
        }

        ComprobanteConfig comprobante = existente == null ? new ComprobanteConfig() : existente;
        comprobante.setSucursal(sucursal);
        comprobante.setTipoComprobante(tipoComprobante);
        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(maxCorrelativoDocumento(idSucursal, tipoComprobante, serie));
        comprobante.setActivo(ACTIVO);
        comprobante.setHabilitadoVenta(esTipoHabilitadoVenta(tipoComprobante));
        comprobante.setDeletedAt(null);
        comprobanteConfigRepository.save(comprobante);
    }

    private void asegurarConfiguracionCotizacionInicial(Sucursal sucursal) {
        Integer idSucursal = sucursal.getIdSucursal();
        String serie = serieCotizacionPorSucursal(idSucursal);

        ComprobanteConfig comprobante = comprobanteConfigRepository
                .findBySucursal_IdSucursalAndTipoComprobanteAndSerie(idSucursal, TIPO_COTIZACION, serie)
                .orElseGet(() -> comprobanteConfigRepository
                        .findFirstBySucursal_IdSucursalAndTipoComprobanteAndDeletedAtIsNullOrderByIdComprobanteDesc(
                                idSucursal,
                                TIPO_COTIZACION)
                        .orElse(null));

        if (comprobante == null) {
            comprobante = new ComprobanteConfig();
        }

        int ultimoCorrelativoActual = comprobante.getUltimoCorrelativo() == null ? 0 : comprobante.getUltimoCorrelativo();
        int ultimoCorrelativoHistorico = maxCorrelativoDocumento(idSucursal, TIPO_COTIZACION, serie);

        comprobante.setSucursal(sucursal);
        comprobante.setTipoComprobante(TIPO_COTIZACION);
        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(Math.max(ultimoCorrelativoActual, ultimoCorrelativoHistorico));
        comprobante.setActivo(ACTIVO);
        comprobante.setHabilitadoVenta(false);
        comprobante.setDeletedAt(null);
        comprobanteConfigRepository.save(comprobante);
    }

    private String serieCotizacionPorSucursal(Integer idSucursal) {
        if (idSucursal == null || idSucursal <= 0) {
            throw new RuntimeException("idSucursal debe ser mayor a 0");
        }

        String serieExistente = comprobanteConfigRepository
                .findFirstBySucursal_IdSucursalAndTipoComprobanteAndDeletedAtIsNullOrderByIdComprobanteDesc(
                        idSucursal,
                        TIPO_COTIZACION)
                .map(ComprobanteConfig::getSerie)
                .map(this::normalizarSerieCotizacionExistente)
                .orElse(null);
        if (serieExistente != null) {
            return serieExistente;
        }

        int siguienteSufijo = valorEntero(comprobanteConfigRepository.obtenerMaxSufijoSerieCotizacion(TIPO_COTIZACION)) + 1;
        String serie = String.format(Locale.ROOT, "%s%02d", PREFIJO_SERIE_COTIZACION, siguienteSufijo);
        if (serie.length() > 10) {
            throw new RuntimeException("No se pudo generar la serie de cotizacion para la sucursal");
        }
        return serie;
    }

    private String normalizarSerieCotizacionExistente(String serie) {
        if (serie == null || serie.isBlank()) {
            return null;
        }

        String serieNormalizada = serie.trim().toUpperCase(Locale.ROOT);
        return serieNormalizada.matches("^COT\\d+$") ? serieNormalizada : null;
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
    }

    private void validarConfiguracionActivaUnicaCotizacion(
            Integer idSucursal,
            String tipoComprobante,
            String activo,
            Integer idComprobanteActual) {
        if (!esTipoCotizacion(tipoComprobante) || !ACTIVO.equals(activo)) {
            return;
        }

        long activos = idComprobanteActual == null
                ? comprobanteConfigRepository.countBySucursal_IdSucursalAndTipoComprobanteAndActivoAndDeletedAtIsNull(
                        idSucursal,
                        tipoComprobante,
                        ACTIVO)
                : comprobanteConfigRepository
                        .countBySucursal_IdSucursalAndTipoComprobanteAndActivoAndDeletedAtIsNullAndIdComprobanteNot(
                                idSucursal,
                                tipoComprobante,
                                ACTIVO,
                                idComprobanteActual);
        if (activos > 0) {
            throw new RuntimeException("Solo puede existir una configuracion ACTIVA de COTIZACION por sucursal");
        }
    }
}
