package com.sistemapos.sistematextil.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionStateService.BridgeConnectionState;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionStateService.ConnectionStateResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappConnectionService {

    private final CrmWhatsappConnectionRepository connectionRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final SucursalRepository sucursalRepository;
    private final CrmWhatsappBridgeService bridgeService;
    private final CrmWhatsappConnectionStateService connectionStateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${whatsapp.bridge.client-id:kiments-main}")
    private String configuredClientId;

    @Transactional
    public ResponseEntity<Map<String, Object>> obtenerEstado(Usuario actor) {
        ResponseEntity<String> bridgeResponse = bridgeService.obtenerEstado();
        Map<String, Object> payload = parsePayload(bridgeResponse.getBody());
        String clientId = clean(payload.get("clientId"));
        if (clientId.isBlank()) {
            clientId = clientIdConfigurado();
            payload.put("clientId", clientId);
        }

        if (!bridgeResponse.getStatusCode().is2xxSuccessful()) {
            payload.put("status", "DISCONNECTED");
            payload.put("hasQr", false);
            payload.putIfAbsent("lastError", payload.getOrDefault("message", "Microservicio de WhatsApp no disponible"));
        }

        Integer idEmpresa = resolverIdEmpresa(actor);
        recuperarConexionLegacy(clientId, idEmpresa);
        ConnectionStateResponse state = connectionStateService.update(new BridgeConnectionState(
                clientId,
                clean(payload.get("status")),
                clean(payload.get("connectedNumber"))));
        CrmWhatsappConnection connection = connectionRepository
                .findByClientIdAndEmpresa_IdEmpresa(clientId, idEmpresa)
                .orElse(null);
        payload.put("status", state.status());
        payload.put("connectedNumber", state.connectedNumber());
        payload.put("previousConnectedNumber", state.previousConnectedNumber());
        payload.put("phoneChanged", state.phoneChanged());
        payload.put("changeAcknowledged", state.changeAcknowledged());
        payload.put("operationsBlocked", !state.operational());
        payload.put("blockedReason", state.blockedReason());
        payload.put("disconnectedAt", state.disconnectedAt());
        payload.put("phoneChangedAt", state.phoneChangedAt());
        payload.put("connectionConfigured", connection != null && sucursalValida(connection.getSucursal(), idEmpresa));
        payload.put("branch", connection == null ? null : toBranch(connection.getSucursal()));
        return ResponseEntity.ok(payload);
    }

    @Transactional
    public ConnectionStateResponse actualizarEstadoBridge(BridgeConnectionState request) {
        return connectionStateService.update(request);
    }

    @Transactional
    public ConnectionStateResponse confirmarCambioNumero(Usuario actor) {
        return connectionStateService.acknowledgePhoneChange(actor);
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> listarSucursales(Usuario actor) {
        Integer idEmpresa = resolverIdEmpresa(actor);
        return sucursalRepository.findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSucursalAsc(idEmpresa).stream()
                .filter(sucursal -> "ACTIVO".equalsIgnoreCase(sucursal.getEstado()))
                .filter(sucursal -> sucursal.getTipo() == SucursalTipo.VENTA)
                .map(this::toBranch)
                .toList();
    }

    @Transactional
    public ConnectionConfigResponse asignarSucursal(Integer idSucursal, Usuario actor) {
        if (idSucursal == null || idSucursal <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione una sucursal de venta");
        }
        Integer idEmpresa = resolverIdEmpresa(actor);
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .filter(item -> item.getEmpresa() != null && idEmpresa.equals(item.getEmpresa().getIdEmpresa()))
                .filter(item -> "ACTIVO".equalsIgnoreCase(item.getEstado()))
                .filter(item -> item.getTipo() == SucursalTipo.VENTA)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La sucursal debe estar activa, pertenecer a su empresa y ser de tipo VENTA"));

        String clientId = clientIdConfigurado();
        CrmWhatsappConnection connection = connectionRepository.findByClientId(clientId)
                .orElseGet(CrmWhatsappConnection::new);
        if (connection.getIdConnection() != null
                && connection.getEmpresa() != null
                && !idEmpresa.equals(connection.getEmpresa().getIdEmpresa())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La conexion pertenece a otra empresa");
        }
        connection.setClientId(clientId);
        connection.setEmpresa(sucursal.getEmpresa());
        connection.setSucursal(sucursal);
        if (connection.getConnectionStatus() == null || connection.getConnectionStatus().isBlank()) {
            connection.setConnectionStatus("UNKNOWN");
        }
        connection = connectionRepository.saveAndFlush(connection);
        conversationRepository.assignUnlinkedConversations(connection.getIdConnection());
        return new ConnectionConfigResponse(clientId, true, toBranch(sucursal));
    }

    @Transactional(readOnly = true)
    public CrmWhatsappConnection buscarPorClientId(String clientId) {
        String normalized = clean(clientId);
        if (normalized.isBlank()) {
            normalized = clientIdConfigurado();
        }
        return connectionRepository.findByClientId(normalized).orElse(null);
    }

    @Transactional(readOnly = true)
    public CrmWhatsappConnection buscarConexionConfigurada(Usuario actor) {
        Integer idEmpresa = resolverIdEmpresa(actor);
        return connectionRepository
                .findByClientIdAndEmpresa_IdEmpresa(clientIdConfigurado(), idEmpresa)
                .filter(connection -> sucursalValida(connection.getSucursal(), idEmpresa))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public CrmWhatsappConnection requireConexionConfigurada(Usuario actor) {
        CrmWhatsappConnection connection = buscarConexionConfigurada(actor);
        if (connection == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configure una sucursal de venta para WhatsApp antes de guardar la IA");
        }
        return connection;
    }

    @Transactional
    public Sucursal requireSucursal(CrmWhatsappConversation conversation, Integer idEmpresa) {
        CrmWhatsappConnection connection = conversation == null ? null : conversation.getConnection();
        if (connection == null || !sucursalValida(connection.getSucursal(), idEmpresa)) {
            connection = connectionRepository.findByClientIdAndEmpresa_IdEmpresa(clientIdConfigurado(), idEmpresa)
                    .orElse(null);
        }
        if (connection == null) {
            connection = recuperarConexionLegacy(clientIdConfigurado(), idEmpresa);
        }
        if (connection == null || !sucursalValida(connection.getSucursal(), idEmpresa)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configure una sucursal de venta para WhatsApp en Conexiones");
        }
        return connection.getSucursal();
    }

    private synchronized CrmWhatsappConnection recuperarConexionLegacy(String clientId, Integer idEmpresa) {
        String normalizedClientId = clean(clientId);
        if (normalizedClientId.isBlank()) {
            normalizedClientId = clientIdConfigurado();
        }

        CrmWhatsappConnection existing = connectionRepository.findByClientId(normalizedClientId).orElse(null);
        if (existing != null) {
            return existing.getEmpresa() != null
                    && idEmpresa.equals(existing.getEmpresa().getIdEmpresa())
                    ? existing
                    : null;
        }

        List<Sucursal> candidates = sucursalRepository
                .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSucursalAsc(idEmpresa).stream()
                .filter(sucursal -> "ACTIVO".equalsIgnoreCase(sucursal.getEstado()))
                .filter(sucursal -> sucursal.getTipo() == SucursalTipo.VENTA)
                .filter(sucursal -> Boolean.TRUE.equals(sucursal.getPublicarEcommerce()))
                .toList();
        if (candidates.size() != 1) {
            return null;
        }

        Sucursal sucursal = candidates.get(0);
        CrmWhatsappConnection recovered = new CrmWhatsappConnection();
        recovered.setClientId(normalizedClientId);
        recovered.setEmpresa(sucursal.getEmpresa());
        recovered.setSucursal(sucursal);
        recovered.setConnectionStatus("UNKNOWN");
        recovered = connectionRepository.saveAndFlush(recovered);
        conversationRepository.assignUnlinkedConversations(recovered.getIdConnection());
        return recovered;
    }

    public String clientIdConfigurado() {
        String value = clean(configuredClientId);
        return value.isBlank() ? "kiments-main" : value;
    }

    private boolean sucursalValida(Sucursal sucursal, Integer idEmpresa) {
        return sucursal != null
                && sucursal.getDeletedAt() == null
                && "ACTIVO".equalsIgnoreCase(sucursal.getEstado())
                && sucursal.getTipo() == SucursalTipo.VENTA
                && sucursal.getEmpresa() != null
                && idEmpresa.equals(sucursal.getEmpresa().getIdEmpresa());
    }

    private Integer resolverIdEmpresa(Usuario actor) {
        if (actor != null && actor.getSucursal() != null && actor.getSucursal().getEmpresa() != null) {
            return actor.getSucursal().getEmpresa().getIdEmpresa();
        }
        return sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO").stream()
                .filter(sucursal -> sucursal.getEmpresa() != null)
                .findFirst()
                .map(sucursal -> sucursal.getEmpresa().getIdEmpresa())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay empresa registrada"));
    }

    private Map<String, Object> parsePayload(String body) {
        if (body == null || body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("message", "Respuesta invalida del microservicio de WhatsApp");
            return fallback;
        }
    }

    private BranchResponse toBranch(Sucursal sucursal) {
        return new BranchResponse(sucursal.getIdSucursal(), sucursal.getNombre(), sucursal.getTipo().name());
    }

    private String clean(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record BranchResponse(Integer idSucursal, String nombreSucursal, String tipoSucursal) {}
    public record ConnectionConfigResponse(String clientId, boolean connectionConfigured, BranchResponse branch) {}
    public record BranchRequest(Integer idSucursal) {}
}
