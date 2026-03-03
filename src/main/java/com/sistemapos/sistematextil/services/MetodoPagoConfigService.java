package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.repositories.MetodoPagoConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MetodoPagoConfigService {

    private final MetodoPagoConfigRepository metodoPagoConfigRepository;

    public List<MetodoPagoConfig> listar(String estado) {
        String estadoNormalizado = normalizarEstadoParaFiltro(estado);
        if (estadoNormalizado == null) {
            return metodoPagoConfigRepository.findAll(Sort.by("nombre").ascending());
        }
        return metodoPagoConfigRepository.findByEstadoOrderByNombreAsc(estadoNormalizado);
    }

    @Transactional
    public MetodoPagoConfig actualizarEstado(Integer idMetodoPago, String estado) {
        MetodoPagoConfig metodoPago = metodoPagoConfigRepository.findById(idMetodoPago)
                .orElseThrow(() -> new RuntimeException("Metodo de pago con ID " + idMetodoPago + " no encontrado"));

        metodoPago.setEstado(normalizarEstado(estado));
        return metodoPagoConfigRepository.save(metodoPago);
    }

    private String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new RuntimeException("Ingrese estado");
        }

        String estadoNormalizado = estado.trim().toUpperCase();
        if (!"ACTIVO".equals(estadoNormalizado) && !"INACTIVO".equals(estadoNormalizado)) {
            throw new RuntimeException("Estado permitido: ACTIVO o INACTIVO");
        }

        return estadoNormalizado;
    }

    private String normalizarEstadoParaFiltro(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }

        String estadoNormalizado = estado.trim().toUpperCase();
        if (!"ACTIVO".equals(estadoNormalizado) && !"INACTIVO".equals(estadoNormalizado)) {
            throw new RuntimeException("Estado permitido: ACTIVO o INACTIVO");
        }

        return estadoNormalizado;
    }
}
