package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.util.usuario.Rol;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiOperationsService {
    public static final String ACTIVE = "ACTIVE";
    public static final String EMERGENCY_STOP = "EMERGENCY_STOP";
    private final CrmWhatsappAiConfigRepository configRepository;
    private final CrmWhatsappAiRunRepository runRepository;
    private final CrmWhatsappAiJobRepository jobRepository;
    private final CrmWhatsappAiDeliveryRepository deliveryRepository;
    private final CrmWhatsappConnectionService connectionService;
    private final CrmWhatsappAiAuditService auditService;

    @Transactional(readOnly = true)
    public OperationalSnapshot snapshot(CrmWhatsappAiConfig config) {
        ZoneId zone = safeZone(config.getZonaHoraria());
        ZonedDateTime now = ZonedDateTime.now(zone);
        Usage daily = usage(config.getConnection().getIdConnection(), now.toLocalDate().atStartOfDay());
        Usage monthly = usage(config.getConnection().getIdConnection(), now.withDayOfMonth(1).toLocalDate().atStartOfDay());
        boolean limited = exceeds(daily.tokens(), config.getDailyTokenLimit())
                || exceeds(monthly.tokens(), config.getMonthlyTokenLimit())
                || exceeds(monthly.cost(), config.getMonthlyBudgetUsd());
        String status = EMERGENCY_STOP.equals(config.getOperationalStatus()) ? EMERGENCY_STOP
                : limited ? "LIMITED" : ACTIVE;
        return new OperationalSnapshot(status, limited, daily.tokens(), monthly.tokens(), monthly.cost(),
                config.getDailyTokenLimit(), config.getMonthlyTokenLimit(), config.getMonthlyBudgetUsd(),
                config.getAutomaticRolloutPercent() == null ? 0 : config.getAutomaticRolloutPercent(),
                config.getOperationalReason());
    }

    @Transactional(readOnly = true)
    public boolean automaticAllowed(CrmWhatsappAiConfig config, Long conversationId) {
        OperationalSnapshot value = snapshot(config);
        if (!ACTIVE.equals(value.status())) return false;
        return includedInRollout(value, conversationId);
    }

    @Transactional(readOnly = true)
    public boolean automaticAllowedForConversation(CrmWhatsappAiConfig config, CrmWhatsappConversation conversation) {
        OperationalSnapshot value = snapshot(config);
        if (!ACTIVE.equals(value.status())) return false;
        return conversation != null
                && conversation.getAiAttentionMode() == CrmWhatsappAiAttentionMode.AUTOMATICA;
    }

    private boolean includedInRollout(OperationalSnapshot value, Long conversationId) {
        if (conversationId == null) return false;
        int rollout = Math.max(0, Math.min(100, value.rolloutPercent()));
        return rollout >= 100 || (rollout > 0 && Math.floorMod(Long.hashCode(conversationId), 100) < rollout);
    }

    @Transactional(readOnly = true)
    public boolean manualAllowed(CrmWhatsappAiConfig config, Long conversationId) {
        if (EMERGENCY_STOP.equals(config.getOperationalStatus())) return false;
        return runRepository.countByConversation_IdConversationAndCreatedAtAfter(
                conversationId, LocalDateTime.now().minusMinutes(10)) < 10;
    }

    @Transactional
    public OperationalSnapshot control(ControlRequest request, Usuario actor) {
        requireAdmin(actor);
        CrmWhatsappConnection connection = connectionService.requireConexionConfigurada(actor);
        CrmWhatsappAiConfig config = configRepository.findByConnection_IdConnection(connection.getIdConnection())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configure primero la IA"));
        String status = request == null ? "" : clean(request.status()).toUpperCase();
        String reason = request == null ? "" : clean(request.reason());
        if (!List.of(ACTIVE, EMERGENCY_STOP).contains(status)) throw badRequest("Estado operativo invalido");
        if (reason.length() < 5) throw badRequest("Ingrese un motivo de al menos 5 caracteres");
        config.setOperationalStatus(status);
        config.setOperationalReason(reason);
        config.setOperationalChangedAt(LocalDateTime.now());
        config.setOperationalChangedBy(actor);
        configRepository.save(config);
        if (EMERGENCY_STOP.equals(status)) {
            jobRepository.cancelForConnection(connection.getIdConnection(),
                    List.of(CrmWhatsappAiJobStatus.PENDING, CrmWhatsappAiJobStatus.PROCESSING),
                    CrmWhatsappAiJobStatus.SKIPPED, "Apagado de emergencia", LocalDateTime.now());
            deliveryRepository.cancelForConnection(connection.getIdConnection(),
                    List.of(CrmWhatsappAiDeliveryStatus.PENDING, CrmWhatsappAiDeliveryStatus.SENDING),
                    List.of(CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE,
                            CrmWhatsappAiDeliveryType.PRODUCT_IMAGE,
                            CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE,
                            CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT,
                            CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD,
                            CrmWhatsappAiDeliveryType.PRODUCT_PROMOTION_SUGGESTION,
                            CrmWhatsappAiDeliveryType.CART_PROMOTION_SUGGESTION,
                            CrmWhatsappAiDeliveryType.HANDOFF_NOTICE),
                    CrmWhatsappAiDeliveryStatus.CANCELLED, "Apagado de emergencia");
        }
        auditService.record(connection, null, null, actor, "OPERATIONAL_CONTROL", "WARN", reason,
                Map.of("status", status));
        return snapshot(config);
    }

    private Usage usage(Long connectionId, LocalDateTime from) {
        Object[] row = runRepository.sumUsageSince(connectionId, from);
        if (row != null && row.length == 1 && row[0] instanceof Object[] nested) row = nested;
        long tokens = row == null || row.length == 0 || row[0] == null ? 0 : ((Number) row[0]).longValue();
        BigDecimal cost = row == null || row.length < 2 || row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString());
        return new Usage(tokens, cost);
    }

    private boolean exceeds(long current, Long limit) { return limit != null && limit > 0 && current >= limit; }
    private boolean exceeds(BigDecimal current, BigDecimal limit) {
        return limit != null && limit.signum() > 0 && current.compareTo(limit) >= 0;
    }
    private ZoneId safeZone(String value) { try { return ZoneId.of(value); } catch (Exception e) { return ZoneId.of("America/Lima"); } }
    private void requireAdmin(Usuario actor) {
        if (actor == null || actor.getRol() != Rol.ADMINISTRADOR) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede controlar la IA");
    }
    private ResponseStatusException badRequest(String value) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, value); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private record Usage(long tokens, BigDecimal cost) {}
    public record ControlRequest(String status, String reason) {}
    public record OperationalSnapshot(String status, boolean limited, long dailyTokens, long monthlyTokens,
            BigDecimal monthlyCostUsd, Long dailyTokenLimit, Long monthlyTokenLimit,
            BigDecimal monthlyBudgetUsd, int rolloutPercent, String reason) {}
}
