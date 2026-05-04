package com.sistemapos.sistematextil.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.repositories.MetodoPagoConfigRepository;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoConfigCreateRequest;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoConfigResponse;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoConfigUpdateRequest;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoCuentaRequest;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoCuentaResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MetodoPagoConfigService {

    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";

    private final MetodoPagoConfigRepository metodoPagoConfigRepository;

    public List<MetodoPagoConfigResponse> listar(String estado) {
        String estadoNormalizado = normalizarEstadoParaFiltro(estado);
        if (estadoNormalizado == null) {
            return metodoPagoConfigRepository.findByDeletedAtIsNull(Sort.by("nombre").ascending()).stream()
                    .map(this::toResponse)
                    .toList();
        }
        return metodoPagoConfigRepository.findByEstadoAndDeletedAtIsNullOrderByNombreAsc(estadoNormalizado).stream()
                .map(this::toResponse)
                .toList();
    }

    public MetodoPagoConfigResponse obtener(Integer idMetodoPago) {
        MetodoPagoConfig metodoPago = obtenerEntidad(idMetodoPago);
        return toResponse(metodoPago);
    }

    @Transactional
    public MetodoPagoConfigResponse crear(MetodoPagoConfigCreateRequest request) {
        String nombre = normalizarNombre(request.nombre());

        MetodoPagoConfig metodoPago = buscarPorNombreNormalizado(nombre);
        if (metodoPago != null && metodoPago.getDeletedAt() == null) {
            throw new RuntimeException("Ya existe un metodo de pago con ese nombre");
        }

        if (metodoPago == null) {
            metodoPago = new MetodoPagoConfig();
        }

        metodoPago.setNombre(nombre);
        metodoPago.setDescripcion(normalizarDescripcion(request.descripcion()));
        metodoPago.setEstado(normalizarEstadoOpcional(request.estado()));
        metodoPago.setDeletedAt(null);
        metodoPago.setCuentas(mapearCuentas(request.cuentas()));

        try {
            return toResponse(metodoPagoConfigRepository.save(metodoPago));
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Ya existe un metodo de pago con ese nombre");
        }
    }

    @Transactional
    public MetodoPagoConfigResponse actualizar(Integer idMetodoPago, MetodoPagoConfigUpdateRequest request) {
        MetodoPagoConfig metodoPago = obtenerEntidad(idMetodoPago);

        String nombre = normalizarNombreActualizar(request.nombre(), idMetodoPago);
        metodoPago.setNombre(nombre);
        metodoPago.setDescripcion(normalizarDescripcion(request.descripcion()));
        metodoPago.setEstado(normalizarEstado(request.estado()));
        metodoPago.setCuentas(mapearCuentas(request.cuentas()));

        try {
            return toResponse(metodoPagoConfigRepository.save(metodoPago));
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Ya existe un metodo de pago con ese nombre");
        }
    }

    @Transactional
    public MetodoPagoConfigResponse actualizarEstado(Integer idMetodoPago, String estado) {
        MetodoPagoConfig metodoPago = obtenerEntidad(idMetodoPago);

        metodoPago.setEstado(normalizarEstado(estado));
        return toResponse(metodoPagoConfigRepository.save(metodoPago));
    }

    @Transactional
    public void eliminar(Integer idMetodoPago) {
        MetodoPagoConfig metodoPago = obtenerEntidad(idMetodoPago);
        metodoPago.setEstado(INACTIVO);
        metodoPago.setDeletedAt(LocalDateTime.now());
        metodoPagoConfigRepository.save(metodoPago);
    }

    private String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new RuntimeException("Ingrese estado");
        }

        String estadoNormalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVO.equals(estadoNormalizado) && !INACTIVO.equals(estadoNormalizado)) {
            throw new RuntimeException("Estado permitido: ACTIVO o INACTIVO");
        }

        return estadoNormalizado;
    }

    private String normalizarEstadoOpcional(String estado) {
        if (estado == null || estado.isBlank()) {
            return ACTIVO;
        }
        return normalizarEstado(estado);
    }

    private String normalizarEstadoParaFiltro(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }

        String estadoNormalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVO.equals(estadoNormalizado) && !INACTIVO.equals(estadoNormalizado)) {
            throw new RuntimeException("Estado permitido: ACTIVO o INACTIVO");
        }

        return estadoNormalizado;
    }

    private MetodoPagoConfig obtenerEntidad(Integer idMetodoPago) {
        return metodoPagoConfigRepository.findByIdMetodoPagoAndDeletedAtIsNull(idMetodoPago)
                .orElseThrow(() -> new RuntimeException("Metodo de pago con ID " + idMetodoPago + " no encontrado"));
    }

    private String normalizarNombreActualizar(String nombre, Integer idMetodoPago) {
        String normalizado = normalizarNombre(nombre);
        MetodoPagoConfig existente = buscarPorNombreNormalizado(normalizado);
        if (existente != null
                && existente.getDeletedAt() == null
                && !existente.getIdMetodoPago().equals(idMetodoPago)) {
            throw new RuntimeException("Ya existe un metodo de pago con ese nombre");
        }
        return normalizado;
    }

    private MetodoPagoConfig buscarPorNombreNormalizado(String nombre) {
        return metodoPagoConfigRepository.findAllByNombreNormalizado(nombre).stream()
                .findFirst()
                .orElse(null);
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new RuntimeException("Ingrese nombre");
        }
        String normalizado = nombre.trim().toUpperCase(Locale.ROOT);
        if (normalizado.length() > 50) {
            throw new RuntimeException("nombre no debe superar 50 caracteres");
        }
        return normalizado;
    }

    private String normalizarDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String normalizada = descripcion.trim();
        if (normalizada.isEmpty()) {
            return null;
        }
        if (normalizada.length() > 255) {
            throw new RuntimeException("descripcion no debe superar 255 caracteres");
        }
        return normalizada;
    }

    private List<MetodoPagoCuenta> mapearCuentas(List<MetodoPagoCuentaRequest> cuentas) {
        if (cuentas == null || cuentas.isEmpty()) {
            return List.of();
        }

        Set<String> numerosUnicos = new LinkedHashSet<>();
        for (MetodoPagoCuentaRequest cuenta : cuentas) {
            if (cuenta == null) {
                continue;
            }
            String numeroCuenta = normalizarNumeroCuenta(cuenta.numeroCuenta());
            numerosUnicos.add(numeroCuenta);
        }

        List<MetodoPagoCuenta> cuentasMapeadas = new ArrayList<>();
        for (String numeroCuenta : numerosUnicos) {
            MetodoPagoCuenta cuenta = new MetodoPagoCuenta();
            cuenta.setNumeroCuenta(numeroCuenta);
            cuentasMapeadas.add(cuenta);
        }
        return cuentasMapeadas;
    }

    private String normalizarNumeroCuenta(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            throw new RuntimeException("Ingrese numeroCuenta");
        }
        String normalizado = numeroCuenta.trim();
        if (normalizado.length() > 50) {
            throw new RuntimeException("numeroCuenta no debe superar 50 caracteres");
        }
        return normalizado;
    }

    private MetodoPagoConfigResponse toResponse(MetodoPagoConfig metodoPago) {
        List<MetodoPagoCuentaResponse> cuentas = metodoPago.getCuentas() == null
                ? List.of()
                : metodoPago.getCuentas().stream()
                        .map(cuenta -> new MetodoPagoCuentaResponse(
                                cuenta.getIdMetodoPagoCuenta(),
                                cuenta.getNumeroCuenta()))
                        .toList();

        return new MetodoPagoConfigResponse(
                metodoPago.getIdMetodoPago(),
                metodoPago.getNombre(),
                metodoPago.getEstado(),
                metodoPago.getDescripcion(),
                cuentas,
                metodoPago.getCreatedAt(),
                metodoPago.getUpdatedAt(),
                metodoPago.getDeletedAt());
    }
}
