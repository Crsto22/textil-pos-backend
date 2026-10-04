package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryType;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiDeliveryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappConnectionStateService {

    private static final String CONNECTED = "CONNECTED";
    private static final List<String> VALID_STATUSES = List.of(
            "INITIALIZING", "QR_REQUIRED", CONNECTED, "DISCONNECTED", "AUTH_FAILURE", "UNKNOWN");

    private final CrmWhatsappConnectionRepository connectionRepository;
    private final CrmWhatsappAiJobRepository jobRepository;
    private final CrmWhatsappAiDeliveryRepository deliveryRepository;
    private final CrmWhatsappEventService eventService;

    @Value("${whatsapp.bridge.client-id:kiments-main}")
    private String configuredClientId;

    @Transactional
    public ConnectionStateResponse update(BridgeConnectionState request) {
        String clientId = clean(request == null ? null : request.clientId());
        if (clientId.isBlank()) clientId = configuredClientId();
        CrmWhatsappConnection connection = connectionRepository.findByClientId(clientId).orElse(null);
        if (connection == null) {
            return transientState(request, clientId);
        }

        String previousStatus = normalizedStatus(connection.getConnectionStatus());
        String previousNumber = clean(connection.getConnectedNumber());
        String previousApproved = clean(connection.getApprovedNumber());
        LocalDateTime previousChangedAt = connection.getPhoneChangedAt();
        String status = normalizedStatus(request == null ? null : request.status());
        LocalDateTime now = LocalDateTime.now();

        String normalizedStoredNumber = normalizeWhatsappNumber(connection.getConnectedNumber());
        String normalizedApprovedNumber = normalizeWhatsappNumber(connection.getApprovedNumber());
        if (!normalizedStoredNumber.isBlank()) connection.setConnectedNumber(normalizedStoredNumber);
        if (!normalizedApprovedNumber.isBlank()) connection.setApprovedNumber(normalizedApprovedNumber);

        connection.setConnectionStatus(status);
        if (CONNECTED.equals(status)) {
            String actualNumber = normalizeWhatsappNumber(request == null ? null : request.connectedNumber());
            if (!actualNumber.isBlank()) {
                connection.setConnectedNumber(actualNumber);
                if (normalizeWhatsappNumber(connection.getApprovedNumber()).isBlank()) {
                    connection.setApprovedNumber(actualNumber);
                }
            }
            connection.setDisconnectedAt(null);
            if (phoneChanged(connection)) {
                if (connection.getPhoneChangedAt() == null) connection.setPhoneChangedAt(now);
            } else {
                connection.setPhoneChangedAt(null);
            }
        } else {
            if (connection.getDisconnectedAt() == null || CONNECTED.equals(previousStatus)) {
                connection.setDisconnectedAt(now);
            }
        }

        connection = connectionRepository.save(connection);
        ConnectionStateResponse response = toResponse(connection);
        if (!response.operational()) {
            cancelAutomaticWork(connection, response.blockedReason());
        }

        boolean changed = !Objects.equals(previousStatus, response.status())
                || !Objects.equals(previousNumber, clean(connection.getConnectedNumber()))
                || !Objects.equals(previousApproved, clean(connection.getApprovedNumber()))
                || !Objects.equals(previousChangedAt, connection.getPhoneChangedAt());
        if (changed) publish(response);
        return response;
    }

    @Transactional
    public ConnectionStateResponse acknowledgePhoneChange(Usuario actor) {
        if (actor == null || actor.getRol() != Rol.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede confirmar el cambio de numero");
        }
        CrmWhatsappConnection connection = connectionRepository.findByClientId(configuredClientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conexion de WhatsApp no configurada"));
        if (!CONNECTED.equals(normalizedStatus(connection.getConnectionStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WhatsApp debe estar conectado para confirmar el numero");
        }
        String connected = normalizeWhatsappNumber(connection.getConnectedNumber());
        if (connected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo identificar el numero conectado");
        }
        connection.setApprovedNumber(connected);
        connection.setPhoneChangedAt(null);
        connection = connectionRepository.save(connection);
        ConnectionStateResponse response = toResponse(connection);
        publish(response);
        return response;
    }

    @Transactional(readOnly = true)
    public ConnectionStateResponse current() {
        return connectionRepository.findByClientId(configuredClientId())
                .map(this::toResponse)
                .orElse(new ConnectionStateResponse(
                        configuredClientId(), "UNKNOWN", null, null, false, true,
                        "Configure la conexion de WhatsApp", null, null));
    }

    @Transactional(readOnly = true)
    public boolean isOperational(CrmWhatsappConnection connection) {
        return connection != null && toResponse(connection).operational();
    }

    @Transactional(readOnly = true)
    public void requireOperational() {
        CrmWhatsappConnection connection = connectionRepository.findByClientId(configuredClientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Configure la conexion de WhatsApp"));
        ConnectionStateResponse state = toResponse(connection);
        if (!state.operational()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, state.blockedReason());
        }
    }

    public ConnectionStateResponse toResponse(CrmWhatsappConnection connection) {
        String status = normalizedStatus(connection == null ? null : connection.getConnectionStatus());
        String connected = normalizeWhatsappNumber(connection == null ? null : connection.getConnectedNumber());
        String approved = normalizeWhatsappNumber(connection == null ? null : connection.getApprovedNumber());
        boolean changed = CONNECTED.equals(status) && !connected.isBlank() && !approved.isBlank()
                && !connected.equals(approved);
        boolean operational = CONNECTED.equals(status) && !changed;
        String reason = operational ? "" : changed
                ? "WhatsApp esta conectado con otro numero. Un administrador debe confirmar el cambio."
                : "WhatsApp esta desconectado. Ve a Conexiones para volver a vincularlo.";
        return new ConnectionStateResponse(
                connection == null ? configuredClientId() : connection.getClientId(),
                status,
                connected.isBlank() ? null : connected,
                changed ? approved : null,
                changed,
                !changed,
                reason,
                connection == null ? null : connection.getDisconnectedAt(),
                connection == null ? null : connection.getPhoneChangedAt());
    }

    private ConnectionStateResponse transientState(BridgeConnectionState request, String clientId) {
        String status = normalizedStatus(request == null ? null : request.status());
        String connected = normalizeWhatsappNumber(request == null ? null : request.connectedNumber());
        boolean operational = CONNECTED.equals(status);
        return new ConnectionStateResponse(clientId, status, connected.isBlank() ? null : connected,
                null, false, true, operational ? "" : "WhatsApp esta desconectado. Ve a Conexiones para volver a vincularlo.",
                operational ? null : LocalDateTime.now(), null);
    }

    private void cancelAutomaticWork(CrmWhatsappConnection connection, String reason) {
        if (connection == null || connection.getIdConnection() == null) return;
        String cancellationReason = clean(reason).isBlank() ? "Conexion de WhatsApp no disponible" : reason;
        jobRepository.cancelForConnection(
                connection.getIdConnection(),
                List.of(CrmWhatsappAiJobStatus.PENDING, CrmWhatsappAiJobStatus.PROCESSING),
                CrmWhatsappAiJobStatus.SKIPPED,
                cancellationReason,
                LocalDateTime.now());
        deliveryRepository.cancelForConnection(
                connection.getIdConnection(),
                List.of(CrmWhatsappAiDeliveryStatus.PENDING, CrmWhatsappAiDeliveryStatus.SENDING),
                List.of(CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE,
                        CrmWhatsappAiDeliveryType.PRODUCT_IMAGE,
                        CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE,
                        CrmWhatsappAiDeliveryType.HANDOFF_NOTICE),
                CrmWhatsappAiDeliveryStatus.CANCELLED,
                cancellationReason);
    }

    private void publish(ConnectionStateResponse response) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "whatsapp.connection.updated");
        event.put("conversationId", 0);
        event.put("connectionId", response.clientId());
        event.put("connection", response);
        eventService.publishAfterCommit(event, event, null, true);
    }

    private boolean phoneChanged(CrmWhatsappConnection connection) {
        String connected = normalizeWhatsappNumber(connection.getConnectedNumber());
        String approved = normalizeWhatsappNumber(connection.getApprovedNumber());
        return !connected.isBlank() && !approved.isBlank() && !connected.equals(approved);
    }

    private String normalizeWhatsappNumber(Object value) {
        String raw = clean(value);
        if (raw.isBlank()) return "";
        int at = raw.indexOf('@');
        if (at >= 0) raw = raw.substring(0, at);
        int device = raw.indexOf(':');
        if (device >= 0) raw = raw.substring(0, device);
        return raw.replaceAll("\\D", "");
    }

    private String normalizedStatus(Object value) {
        String status = clean(value).toUpperCase(Locale.ROOT);
        return VALID_STATUSES.contains(status) ? status : "UNKNOWN";
    }

    private String configuredClientId() {
        String value = clean(configuredClientId);
        return value.isBlank() ? "kiments-main" : value;
    }

    private String clean(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record BridgeConnectionState(String clientId, String status, String connectedNumber) {}

    public record ConnectionStateResponse(
            String clientId,
            String status,
            String connectedNumber,
            String previousConnectedNumber,
            boolean phoneChanged,
            boolean changeAcknowledged,
            String blockedReason,
            LocalDateTime disconnectedAt,
            LocalDateTime phoneChangedAt) {
        public boolean operational() {
            return CONNECTED.equals(status) && !phoneChanged;
        }
    }
}
