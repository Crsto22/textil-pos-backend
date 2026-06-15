package com.sistemapos.sistematextil.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalMetodoPagoConfig;
import com.sistemapos.sistematextil.repositories.MetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.util.metodopago.SucursalMetodoPagoConfigItemRequest;
import com.sistemapos.sistematextil.util.metodopago.SucursalMetodoPagoConfigResponse;
import com.sistemapos.sistematextil.util.metodopago.SucursalMetodoPagoConfigUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SucursalMetodoPagoConfigService {

    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";

    private final SucursalRepository sucursalRepository;
    private final MetodoPagoConfigRepository metodoPagoConfigRepository;
    private final SucursalMetodoPagoConfigRepository sucursalMetodoPagoConfigRepository;

    public List<SucursalMetodoPagoConfigResponse> listarPorSucursal(Integer idSucursal) {
        validarSucursal(idSucursal);

        Map<Integer, SucursalMetodoPagoConfig> configuraciones = new LinkedHashMap<>();
        sucursalMetodoPagoConfigRepository.findActivosBySucursal(idSucursal)
                .forEach(config -> {
                    if (config.getMetodoPago() != null && config.getMetodoPago().getIdMetodoPago() != null) {
                        configuraciones.put(config.getMetodoPago().getIdMetodoPago(), config);
                    }
                });

        return metodoPagoConfigRepository.findByDeletedAtIsNull(Sort.by("nombre").ascending()).stream()
                .map(metodo -> toResponse(metodo, configuraciones.get(metodo.getIdMetodoPago())))
                .toList();
    }

    @Transactional
    public List<SucursalMetodoPagoConfigResponse> actualizarPorSucursal(
            Integer idSucursal,
            SucursalMetodoPagoConfigUpdateRequest request) {
        Sucursal sucursal = validarSucursal(idSucursal);

        Map<Integer, SucursalMetodoPagoConfigItemRequest> items = normalizarItems(request);
        List<SucursalMetodoPagoConfig> guardar = new ArrayList<>();

        for (SucursalMetodoPagoConfigItemRequest item : items.values()) {
            MetodoPagoConfig metodoPago = metodoPagoConfigRepository
                    .findByIdMetodoPagoAndDeletedAtIsNull(item.idMetodoPago())
                    .orElseThrow(() -> new RuntimeException(
                            "Metodo de pago con ID " + item.idMetodoPago() + " no encontrado"));
            if (!Boolean.FALSE.equals(item.activo()) && !ACTIVO.equalsIgnoreCase(metodoPago.getEstado())) {
                throw new RuntimeException("El metodo de pago '" + metodoPago.getNombre() + "' esta INACTIVO");
            }

            SucursalMetodoPagoConfig config = sucursalMetodoPagoConfigRepository
                    .findBySucursal_IdSucursalAndMetodoPago_IdMetodoPagoAndDeletedAtIsNull(
                            idSucursal,
                            metodoPago.getIdMetodoPago())
                    .orElseGet(SucursalMetodoPagoConfig::new);

            config.setSucursal(sucursal);
            config.setMetodoPago(metodoPago);
            config.setEstado(Boolean.FALSE.equals(item.activo()) ? INACTIVO : ACTIVO);
            config.setRequiereCodigoOperacion(valorBooleano(item.requiereCodigoOperacion()));
            config.setRequiereFechaPago(valorBooleano(item.requiereFechaPago()));
            config.setRequiereHoraPago(valorBooleano(item.requiereHoraPago()));
            config.setDeletedAt(null);
            guardar.add(config);
        }

        sucursalMetodoPagoConfigRepository.saveAll(guardar);
        return listarPorSucursal(idSucursal);
    }

    private Sucursal validarSucursal(Integer idSucursal) {
        if (idSucursal == null) {
            throw new RuntimeException("Ingrese idSucursal");
        }
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal con ID " + idSucursal + " no encontrada"));
    }

    private Map<Integer, SucursalMetodoPagoConfigItemRequest> normalizarItems(
            SucursalMetodoPagoConfigUpdateRequest request) {
        if (request == null || request.metodosPago() == null || request.metodosPago().isEmpty()) {
            throw new RuntimeException("Ingrese al menos un metodo de pago");
        }

        Map<Integer, SucursalMetodoPagoConfigItemRequest> items = new LinkedHashMap<>();
        for (SucursalMetodoPagoConfigItemRequest item : request.metodosPago()) {
            if (item == null || item.idMetodoPago() == null) {
                throw new RuntimeException("Cada metodo de pago debe incluir idMetodoPago");
            }
            items.put(item.idMetodoPago(), item);
        }
        return items;
    }

    private SucursalMetodoPagoConfigResponse toResponse(
            MetodoPagoConfig metodoPago,
            SucursalMetodoPagoConfig config) {
        boolean activo = config != null
                && ACTIVO.equalsIgnoreCase(config.getEstado())
                && ACTIVO.equalsIgnoreCase(metodoPago.getEstado());

        return new SucursalMetodoPagoConfigResponse(
                metodoPago.getIdMetodoPago(),
                metodoPago.getNombre(),
                activo,
                config != null && Boolean.TRUE.equals(config.getRequiereCodigoOperacion()),
                config != null && Boolean.TRUE.equals(config.getRequiereFechaPago()),
                config != null && Boolean.TRUE.equals(config.getRequiereHoraPago()));
    }

    private boolean valorBooleano(Boolean valor) {
        return Boolean.TRUE.equals(valor);
    }
}
