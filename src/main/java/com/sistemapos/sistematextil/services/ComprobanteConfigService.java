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
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CotizacionRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
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
    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";

    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final VentaRepository ventaRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final CotizacionRepository cotizacionRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public List<ComprobanteConfigResponse> listar(
            String activo,
            Boolean habilitadoVenta) {
        String activoFiltro = normalizarActivoParaFiltro(activo);
        Boolean habilitadoVentaFiltro = normalizarHabilitadoVentaParaFiltro(habilitadoVenta);

        return comprobanteConfigRepository.buscar(activoFiltro, habilitadoVentaFiltro)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PagedResponse<ComprobanteConfigResponse> listarPaginado(
            int page,
            String activo,
            Boolean habilitadoVenta) {
        validarPagina(page);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idComprobante").ascending());
        Page<ComprobanteConfigResponse> comprobantes = comprobanteConfigRepository.buscarPaginado(
                null,
                normalizarActivoParaFiltro(activo),
                normalizarHabilitadoVentaParaFiltro(habilitadoVenta),
                pageable).map(this::toResponse);

        return PagedResponse.fromPage(comprobantes);
    }

    public PagedResponse<ComprobanteConfigResponse> buscarPaginado(
            String term,
            int page,
            String activo,
            Boolean habilitadoVenta) {
        validarPagina(page);

        String termino = normalizarTerminoBusqueda(term);
        if (termino == null) {
            return listarPaginado(page, activo, habilitadoVenta);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idComprobante").ascending());
        Page<ComprobanteConfigResponse> comprobantes = comprobanteConfigRepository.buscarPaginado(
                termino,
                normalizarActivoParaFiltro(activo),
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
        String tipoComprobante = normalizarTipoComprobante(request.tipoComprobante());
        String serie = normalizarSerie(request.serie());
        String activo = normalizarActivo(request.activo());
        int ultimoCorrelativo = normalizarUltimoCorrelativoOpcional(request.ultimoCorrelativo());

        int maxCorrelativoDocumento = maxCorrelativoDocumento(tipoComprobante, serie);
        if (ultimoCorrelativo < maxCorrelativoDocumento) {
            throw new RuntimeException("ultimoCorrelativo no puede ser menor al maximo correlativo registrado ("
                    + maxCorrelativoDocumento + ") para " + tipoComprobante + " " + serie);
        }

        if (comprobanteConfigRepository.findByTipoComprobanteAndSerieAndDeletedAtIsNull(tipoComprobante, serie).isPresent()) {
            throw new RuntimeException("Ya existe configuracion para ese tipo de comprobante con esa serie");
        }

        ComprobanteConfig comprobante = new ComprobanteConfig();
        comprobante.setTipoComprobante(tipoComprobante);
        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(ultimoCorrelativo);
        comprobante.setActivo(activo);
        comprobante.setHabilitadoVenta(esTipoHabilitadoVenta(tipoComprobante));
        comprobante.setDeletedAt(null);

        try {
            return toResponse(comprobanteConfigRepository.save(comprobante));
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La serie ya esta registrada para ese tipo de comprobante en otra configuracion");
        }
    }

    @Transactional
    public ComprobanteConfigResponse actualizar(Integer idComprobante, ComprobanteConfigUpdateRequest request) {
        ComprobanteConfig comprobante = comprobanteConfigRepository.findByIdComprobanteAndDeletedAtIsNull(idComprobante)
                .orElseThrow(() -> new RuntimeException("Comprobante con ID " + idComprobante + " no encontrado"));

        String serie = normalizarSerie(request.serie());
        String activo = normalizarActivo(request.activo());
        int ultimoCorrelativo = normalizarUltimoCorrelativoObligatorio(request.ultimoCorrelativo());

        int maxCorrelativoDocumento = maxCorrelativoDocumento(comprobante.getTipoComprobante(), serie);
        if (ultimoCorrelativo < maxCorrelativoDocumento) {
            throw new RuntimeException("ultimoCorrelativo no puede ser menor al maximo correlativo registrado ("
                    + maxCorrelativoDocumento + ") para " + comprobante.getTipoComprobante() + " " + serie);
        }

        ComprobanteConfig duplicado = comprobanteConfigRepository
                .findByTipoComprobanteAndSerieAndDeletedAtIsNull(comprobante.getTipoComprobante(), serie)
                .orElse(null);
        if (duplicado != null && !duplicado.getIdComprobante().equals(comprobante.getIdComprobante())) {
            throw new RuntimeException("Ya existe configuracion para ese tipo de comprobante con esa serie");
        }

        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(ultimoCorrelativo);
        comprobante.setActivo(activo);
        comprobante.setHabilitadoVenta(esTipoHabilitadoVenta(comprobante.getTipoComprobante()));

        try {
            return toResponse(comprobanteConfigRepository.save(comprobante));
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La serie ya esta registrada para ese tipo de comprobante en otra configuracion");
        }
    }

    private ComprobanteConfigResponse toResponse(ComprobanteConfig comprobante) {
        Integer ultimo = comprobante.getUltimoCorrelativo() == null ? 0 : comprobante.getUltimoCorrelativo();
        return new ComprobanteConfigResponse(
                comprobante.getIdComprobante(),
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

    private int maxCorrelativoDocumento(String tipoComprobante, String serie) {
        if (tipoComprobante == null || serie == null) {
            return 0;
        }
        Integer max;
        if (esTipoHabilitadoVenta(tipoComprobante)) {
            max = ventaRepository.obtenerMaxCorrelativoPorDocumento(tipoComprobante, serie);
        } else if (esTipoNotaCredito(tipoComprobante)) {
            max = notaCreditoRepository.obtenerMaxCorrelativoPorDocumento(tipoComprobante, serie);
        } else if (esTipoCotizacion(tipoComprobante)) {
            max = cotizacionRepository.obtenerMaxCorrelativoPorSerie(serie);
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
}
