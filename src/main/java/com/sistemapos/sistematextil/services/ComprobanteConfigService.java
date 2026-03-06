package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigCreateRequest;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigResponse;
import com.sistemapos.sistematextil.util.comprobante.ComprobanteConfigUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComprobanteConfigService {

    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final SucursalRepository sucursalRepository;
    private final VentaRepository ventaRepository;

    public List<ComprobanteConfigResponse> listar(String activo, Integer idSucursal) {
        String activoFiltro = normalizarActivoParaFiltro(activo);
        Integer idSucursalFiltro = normalizarIdSucursalParaFiltro(idSucursal);

        return comprobanteConfigRepository.buscar(activoFiltro, idSucursalFiltro)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ComprobanteConfigResponse obtener(Integer idComprobante) {
        ComprobanteConfig comprobante = comprobanteConfigRepository.findByIdComprobanteAndDeletedAtIsNull(idComprobante)
                .orElseThrow(() -> new RuntimeException("Comprobante con ID " + idComprobante + " no encontrado"));
        return toResponse(comprobante);
    }

    @Transactional
    public ComprobanteConfigResponse crear(ComprobanteConfigCreateRequest request) {
        Integer idSucursal = normalizarIdSucursalObligatorio(request.idSucursal());
        String tipoComprobante = normalizarTipoComprobante(request.tipoComprobante());
        String serie = normalizarSerie(request.serie());
        String activo = normalizarActivo(request.activo());
        int ultimoCorrelativo = normalizarUltimoCorrelativoOpcional(request.ultimoCorrelativo());

        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        int maxCorrelativoVenta = maxCorrelativoVenta(idSucursal, tipoComprobante, serie);
        if (ultimoCorrelativo < maxCorrelativoVenta) {
            throw new RuntimeException("ultimoCorrelativo no puede ser menor al maximo correlativo de ventas ("
                    + maxCorrelativoVenta + ") para " + tipoComprobante + " " + serie);
        }

        ComprobanteConfig comprobante = comprobanteConfigRepository
                .findBySucursal_IdSucursalAndTipoComprobante(idSucursal, tipoComprobante)
                .orElse(null);

        if (comprobante != null && comprobante.getDeletedAt() == null) {
            throw new RuntimeException("Ya existe configuracion para ese tipo de comprobante en la sucursal");
        }

        if (comprobante == null) {
            comprobante = new ComprobanteConfig();
        }

        comprobante.setSucursal(sucursal);
        comprobante.setTipoComprobante(tipoComprobante);
        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(ultimoCorrelativo);
        comprobante.setActivo(activo);
        comprobante.setDeletedAt(null);

        return toResponse(comprobanteConfigRepository.save(comprobante));
    }

    @Transactional
    public ComprobanteConfigResponse actualizar(Integer idComprobante, ComprobanteConfigUpdateRequest request) {
        ComprobanteConfig comprobante = comprobanteConfigRepository.findByIdComprobanteAndDeletedAtIsNull(idComprobante)
                .orElseThrow(() -> new RuntimeException("Comprobante con ID " + idComprobante + " no encontrado"));

        String serie = normalizarSerie(request.serie());
        String activo = normalizarActivo(request.activo());
        int ultimoCorrelativo = normalizarUltimoCorrelativoObligatorio(request.ultimoCorrelativo());
        Integer idSucursal = comprobante.getSucursal() != null ? comprobante.getSucursal().getIdSucursal() : null;

        int maxCorrelativoVenta = maxCorrelativoVenta(idSucursal, comprobante.getTipoComprobante(), serie);
        if (ultimoCorrelativo < maxCorrelativoVenta) {
            throw new RuntimeException("ultimoCorrelativo no puede ser menor al maximo correlativo de ventas ("
                    + maxCorrelativoVenta + ") para " + comprobante.getTipoComprobante() + " " + serie);
        }

        comprobante.setSerie(serie);
        comprobante.setUltimoCorrelativo(ultimoCorrelativo);
        comprobante.setActivo(activo);

        return toResponse(comprobanteConfigRepository.save(comprobante));
    }

    @Transactional
    public void eliminarLogico(Integer idComprobante) {
        ComprobanteConfig comprobante = comprobanteConfigRepository.findByIdComprobanteAndDeletedAtIsNull(idComprobante)
                .orElseThrow(() -> new RuntimeException(
                        "Comprobante con ID " + idComprobante + " no encontrado o ya eliminado"));

        comprobante.setActivo("INACTIVO");
        comprobante.setDeletedAt(LocalDateTime.now());
        comprobanteConfigRepository.save(comprobante);
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
                comprobante.getCreatedAt(),
                comprobante.getUpdatedAt(),
                comprobante.getDeletedAt());
    }

    private int maxCorrelativoVenta(Integer idSucursal, String tipoComprobante, String serie) {
        if (idSucursal == null || tipoComprobante == null || serie == null) {
            return 0;
        }
        Integer max = ventaRepository.obtenerMaxCorrelativoPorDocumento(idSucursal, tipoComprobante, serie);
        return max == null ? 0 : max;
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
        String normalizado = tipoComprobante.trim().toUpperCase(Locale.ROOT);
        if (!"NOTA DE VENTA".equals(normalizado)
                && !"BOLETA".equals(normalizado)
                && !"FACTURA".equals(normalizado)) {
            throw new RuntimeException("tipoComprobante permitido: NOTA DE VENTA, BOLETA o FACTURA");
        }
        return normalizado;
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
            return "ACTIVO";
        }
        String activoNormalizado = activo.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVO".equals(activoNormalizado) && !"INACTIVO".equals(activoNormalizado)) {
            throw new RuntimeException("activo permitido: ACTIVO o INACTIVO");
        }
        return activoNormalizado;
    }

    private String normalizarActivoParaFiltro(String activo) {
        if (activo == null || activo.isBlank()) {
            return null;
        }
        String activoNormalizado = activo.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVO".equals(activoNormalizado) && !"INACTIVO".equals(activoNormalizado)) {
            throw new RuntimeException("activo permitido: ACTIVO o INACTIVO");
        }
        return activoNormalizado;
    }
}
