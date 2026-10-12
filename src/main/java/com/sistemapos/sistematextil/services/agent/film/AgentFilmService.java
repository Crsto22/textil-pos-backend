package com.sistemapos.sistematextil.services.agent.film;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentFilmService {
    public static final BigDecimal PRICE = new BigDecimal("2.00");
    private final CrmWhatsappFilmAddonRepository addons;
    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappFulfillmentRepository fulfillments;
    private final SucursalMetodoPagoConfigRepository branchMethods;
    private final MetodoPagoCuentaRepository accounts;
    private final CrmWhatsappAgentStateMachine stateMachine;

    @Transactional
    public FilmResult offer(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId);
        CrmWhatsappFulfillment fulfillment = requireShalom(sessionId);
        CrmWhatsappFilmAddon addon = addons.findByAgentSession_IdAgentSession(sessionId).orElseGet(() -> {
            CrmWhatsappFilmAddon created = new CrmWhatsappFilmAddon();
            created.setAgentSession(session); created.setFulfillment(fulfillment);
            created.setConversation(session.getConversation()); created.setAmount(PRICE);
            created.setStatus(CrmWhatsappFilmStatus.OFFERED);
            created.setIdempotencyKey(hash("film:" + sessionId));
            return addons.save(created);
        });
        return result(addon, false);
    }

    @Transactional
    public FilmResult accept(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId);
        requireState(session, CrmWhatsappAgentState.ESPERANDO_DECISION_FILM);
        CrmWhatsappFilmAddon addon = offer(sessionId).addonId() == null ? null
                : addons.findByAgentSession_IdAgentSession(sessionId).orElseThrow();
        MetodoPagoCuenta account = findYapeAccount(session);
        if (account == null) {
            addon.setStatus(CrmWhatsappFilmStatus.UNAVAILABLE_WITHOUT_FILM);
            addon.setResolvedAt(LocalDateTime.now());
            transition(session, CrmWhatsappAgentEvent.PAGO_FILM_NO_DISPONIBLE);
            return result(addons.save(addon), true);
        }
        LocalDateTime now = LocalDateTime.now();
        addon.setPaymentAccount(account); addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PAYMENT);
        addon.setPaymentStartedAt(now); addon.setExpiresAt(now.plusHours(1));
        transition(session, CrmWhatsappAgentEvent.FILM_ACEPTADO);
        return result(addons.save(addon), false);
    }

    @Transactional
    public FilmResult decline(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId);
        if (session.getState() != CrmWhatsappAgentState.ESPERANDO_DECISION_FILM
                && session.getState() != CrmWhatsappAgentState.ESPERANDO_PAGO_FILM) {
            throw new IllegalStateException("La sesion no esta esperando una decision sobre el film");
        }
        CrmWhatsappFilmAddon addon = addons.findByAgentSession_IdAgentSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("No existe una oferta de film"));
        addon.setStatus(CrmWhatsappFilmStatus.DECLINED); addon.setResolvedAt(LocalDateTime.now());
        transition(session, CrmWhatsappAgentEvent.FILM_RECHAZADO);
        return result(addons.save(addon), true);
    }

    private MetodoPagoCuenta findYapeAccount(CrmWhatsappAgentSession session) {
        if (session.getConnection() == null || session.getConnection().getSucursal() == null) return null;
        return branchMethods.findActivosBySucursal(session.getConnection().getSucursal().getIdSucursal()).stream()
                .filter(c -> active(c.getEstado()) && c.getMetodoPago() != null
                        && active(c.getMetodoPago().getEstado())
                        && normalize(c.getMetodoPago().getNombre()).equals("YAPE"))
                .flatMap(c -> accounts.findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(
                        c.getMetodoPago().getIdMetodoPago()).stream()).findFirst().orElse(null);
    }
    private boolean active(String value) { return "ACTIVO".equalsIgnoreCase(value); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private CrmWhatsappAgentSession requireSession(Long id) { return sessions.findForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("La sesion no existe")); }
    private CrmWhatsappFulfillment requireShalom(Long id) { return fulfillments.findForUpdateBySessionId(id)
            .filter(f -> f.getMethod() == CrmWhatsappFulfillmentMethod.SHALOM
                    && f.getStatus() == CrmWhatsappFulfillmentStatus.READY)
            .orElseThrow(() -> new IllegalStateException("El envio Shalom aun no esta confirmado")); }
    private void requireState(CrmWhatsappAgentSession s, CrmWhatsappAgentState state) {
        if (s.getState() != state) throw new IllegalStateException("La sesion no esta en " + state);
    }
    private void transition(CrmWhatsappAgentSession s, CrmWhatsappAgentEvent e) {
        LocalDateTime now = LocalDateTime.now(); s.setState(stateMachine.transition(s.getState(), e));
        s.setLastEvent(e); s.setStateChangedAt(now); s.setLastActivityAt(now); sessions.save(s);
    }
    private FilmResult result(CrmWhatsappFilmAddon a, boolean continueWithoutFilm) {
        MetodoPagoCuenta c = a.getPaymentAccount();
        return new FilmResult(a.getIdFilmAddon(), a.getStatus().name(), a.getAmount(),
                c == null ? null : c.getNumeroCuenta(), c == null ? null : c.getTitular(),
                a.getExpiresAt(), continueWithoutFilm);
    }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public record FilmResult(Long addonId, String status, BigDecimal amount, String accountNumber,
            String accountHolder, LocalDateTime expiresAt, boolean continueWithoutFilm) {}
}
