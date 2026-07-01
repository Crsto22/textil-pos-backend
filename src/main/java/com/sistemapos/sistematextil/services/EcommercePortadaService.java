package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.model.EcommercePortada;
import com.sistemapos.sistematextil.repositories.EcommercePortadaRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePortadaResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenGlobalUploadResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EcommercePortadaService {

    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";

    private final EcommercePortadaRepository portadaRepository;
    private final ProductoImagenService productoImagenService;
    private final S3StorageService storageService;
    private final EcommerceCacheInvalidationService ecommerceCacheInvalidationService;

    public List<EcommercePortadaResponse> listarAdmin() {
        return portadaRepository.findByDeletedAtIsNullOrderByOrdenAscIdEcommercePortadaAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EcommercePortadaResponse> listarPublicas() {
        return portadaRepository.findByEstadoAndDeletedAtIsNullOrderByOrdenAscIdEcommercePortadaAsc(ACTIVO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EcommercePortadaResponse crear(MultipartFile desktop, MultipartFile mobile) {
        ProductoImagenGlobalUploadResponse desktopUpload = productoImagenService.subirPortadaEcommerce(desktop, "DESKTOP");
        ProductoImagenGlobalUploadResponse mobileUpload = null;
        try {
            mobileUpload = productoImagenService.subirPortadaEcommerce(mobile, "MOBILE");
            EcommercePortada portada = new EcommercePortada();
            portada.setDesktopUrl(desktopUpload.url());
            portada.setDesktopThumbUrl(desktopUpload.urlThumb());
            portada.setMobileUrl(mobileUpload.url());
            portada.setMobileThumbUrl(mobileUpload.urlThumb());
            portada.setOrden(portadaRepository.findByDeletedAtIsNullOrderByOrdenAscIdEcommercePortadaAsc().size() + 1);
            EcommercePortada guardada = portadaRepository.save(portada);
            ecommerceCacheInvalidationService.invalidate();
            return toResponse(guardada);
        } catch (RuntimeException e) {
            eliminar(desktopUpload);
            eliminar(mobileUpload);
            throw e;
        }
    }

    @Transactional
    public EcommercePortadaResponse cambiarEstado(Integer id, String estado) {
        EcommercePortada portada = obtenerActivaOInactiva(id);
        portada.setEstado(normalizarEstado(estado));
        EcommercePortada guardada = portadaRepository.save(portada);
        ecommerceCacheInvalidationService.invalidate();
        return toResponse(guardada);
    }

    @Transactional
    public void eliminar(Integer id) {
        EcommercePortada portada = obtenerActivaOInactiva(id);
        portada.setEstado(INACTIVO);
        portada.setDeletedAt(LocalDateTime.now());
        portadaRepository.save(portada);
        ecommerceCacheInvalidationService.invalidate();
        eliminar(portada.getDesktopUrl());
        eliminar(portada.getDesktopThumbUrl());
        eliminar(portada.getMobileUrl());
        eliminar(portada.getMobileThumbUrl());
    }

    private EcommercePortada obtenerActivaOInactiva(Integer id) {
        if (id == null) {
            throw new RuntimeException("Ingrese id de portada");
        }
        return portadaRepository.findById(id)
                .filter(portada -> portada.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("Portada ecommerce no encontrada"));
    }

    private String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new RuntimeException("Estado obligatorio");
        }
        String normalizado = estado.trim().toUpperCase(java.util.Locale.ROOT);
        if (ACTIVO.equals(normalizado) || INACTIVO.equals(normalizado)) {
            return normalizado;
        }
        throw new RuntimeException("Estado no permitido");
    }

    private void eliminar(ProductoImagenGlobalUploadResponse upload) {
        if (upload == null) {
            return;
        }
        eliminar(upload.url());
        eliminar(upload.urlThumb());
    }

    private void eliminar(String url) {
        if (url != null && !url.isBlank()) {
            try {
                storageService.deleteByUrl(url);
            } catch (RuntimeException e) {
                log.warn("No se pudo eliminar portada ecommerce del storage: {}", url, e);
            }
        }
    }

    private EcommercePortadaResponse toResponse(EcommercePortada portada) {
        return new EcommercePortadaResponse(
                portada.getIdEcommercePortada(),
                portada.getDesktopUrl(),
                portada.getDesktopThumbUrl(),
                portada.getMobileUrl(),
                portada.getMobileThumbUrl(),
                portada.getOrden(),
                portada.getEstado());
    }
}
