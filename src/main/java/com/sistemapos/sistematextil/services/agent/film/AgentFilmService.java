package com.sistemapos.sistematextil.services.agent.film;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

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
    private final VentaRepository sales;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentFilmEscalationService escalation;
    private final JdbcTemplate jdbc;

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

    /** Starts the mandatory, non-sale-blocking film charge after the sale exists. */
    @Transactional
    public FilmResult startMandatoryPayment(Long sessionId, Integer saleId) {
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
        if (addon.getVenta() == null) addon.setVenta(sales.findById(saleId)
                .orElseThrow(() -> new IllegalStateException("La venta no existe")));
        if (addon.getStatus() == CrmWhatsappFilmStatus.AWAITING_PAYMENT) {
            ensureWaitingForFilmPayment(session);
            return result(addon, false);
        }
        if (addon.getStatus() == CrmWhatsappFilmStatus.CONFIRMED) return result(addon, false);
        MetodoPagoCuenta account = findYapeAccount(session);
        LocalDateTime now = LocalDateTime.now();
        fulfillment.setStatus(CrmWhatsappFulfillmentStatus.FILM_PAYMENT_PENDING);
        fulfillments.save(fulfillment);
        if (account == null) {
            addon.setStatus(CrmWhatsappFilmStatus.PAYMENT_CONFIGURATION_REQUIRED);
            addon.setResolvedAt(now);
            addon=addons.save(addon);
            escalation.escalate(sessionId,addon.getIdFilmAddon(),"No existe una cuenta Yape activa para cobrar el film obligatorio");
            return result(addon, false);
        }
        addon.setPaymentAccount(account); addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PAYMENT);
        addon.setPaymentStartedAt(now); addon.setExpiresAt(now.plusHours(1)); addon.setResolvedAt(null);
        ensureWaitingForFilmPayment(session);
        return result(addons.save(addon), false);
    }

    /** Creates the film record after the sale and waits for the previous-order answer. */
    @Transactional
    public FilmResult awaitPreviousOrderDecision(Long sessionId, Integer saleId) {
        CrmWhatsappAgentSession session = requireSession(sessionId);
        CrmWhatsappFulfillment fulfillment = requireShalom(sessionId);
        CrmWhatsappFilmAddon addon = addons.findByAgentSession_IdAgentSession(sessionId).orElseGet(() -> {
            CrmWhatsappFilmAddon created = new CrmWhatsappFilmAddon();
            created.setAgentSession(session); created.setFulfillment(fulfillment);
            created.setConversation(session.getConversation()); created.setAmount(PRICE);
            created.setStatus(CrmWhatsappFilmStatus.AWAITING_PREVIOUS_ORDER_DECISION);
            created.setIdempotencyKey(hash("film:" + sessionId));
            return addons.save(created);
        });
        if (addon.getVenta() == null) addon.setVenta(sales.findById(saleId)
                .orElseThrow(() -> new IllegalStateException("La venta no existe")));
        if (addon.getStatus() == CrmWhatsappFilmStatus.OFFERED) {
            addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PREVIOUS_ORDER_DECISION);
        }
        fulfillment.setStatus(CrmWhatsappFulfillmentStatus.FILM_PAYMENT_PENDING);
        fulfillments.save(fulfillment);
        if (session.getState() == CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION) {
            transition(session, CrmWhatsappAgentEvent.CONSULTA_PEDIDO_ANTERIOR_INICIADA);
        } else if (session.getState() != CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR) {
            throw new IllegalStateException("La venta no esta lista para consultar pedidos anteriores");
        }
        return result(addons.save(addon), false);
    }

    @Transactional
    public FilmResult confirmPreviousOrder(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId);
        CrmWhatsappFilmAddon addon = addons.findByAgentSession_IdAgentSession(sessionId).orElseThrow();
        if (addon.getStatus() == CrmWhatsappFilmStatus.ADVISOR_REVIEW_REQUIRED
                || addon.getStatus() == CrmWhatsappFilmStatus.COVERED_BY_PREVIOUS_ORDER) return result(addon, false);
        requireState(session, CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR);
        addon.setStatus(CrmWhatsappFilmStatus.ADVISOR_REVIEW_REQUIRED);
        addon.setExpiresAt(null); addon.setResolvedAt(null); addon.setResolutionSource(null);
        transition(session, CrmWhatsappAgentEvent.PEDIDO_ANTERIOR_CONFIRMADO);
        return result(addons.save(addon), false);
    }

    @Transactional
    public FilmResult confirmNoPreviousOrder(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId);
        CrmWhatsappFilmAddon addon = addons.findByAgentSession_IdAgentSession(sessionId).orElseThrow();
        if (addon.getStatus() == CrmWhatsappFilmStatus.AWAITING_PAYMENT) return result(addon, false);
        requireState(session, CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR);
        transition(session, CrmWhatsappAgentEvent.SIN_PEDIDO_ANTERIOR_CONFIRMADO);
        MetodoPagoCuenta account = findYapeAccount(session);
        LocalDateTime now = LocalDateTime.now();
        if (account == null) {
            addon.setStatus(CrmWhatsappFilmStatus.PAYMENT_CONFIGURATION_REQUIRED);
            addon.setResolvedAt(now); addons.save(addon);
            transition(session, CrmWhatsappAgentEvent.PAGO_FILM_NO_DISPONIBLE);
            escalation.escalate(sessionId, addon.getIdFilmAddon(),
                    "No existe una cuenta Yape activa para cobrar el film obligatorio");
            return result(addon, false);
        }
        addon.setPaymentAccount(account); addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PAYMENT);
        addon.setPaymentStartedAt(now); addon.setExpiresAt(now.plusHours(1)); addon.setResolvedAt(null);
        return result(addons.save(addon), false);
    }

    @Transactional
    public FilmResult accept(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId);
        requireState(session, CrmWhatsappAgentState.ESPERANDO_DECISION_FILM);
        CrmWhatsappFilmAddon addon = offer(sessionId).addonId() == null ? null
                : addons.findByAgentSession_IdAgentSession(sessionId).orElseThrow();
        MetodoPagoCuenta account = findYapeAccount(session);
        if (account == null) {
            addon.setStatus(CrmWhatsappFilmStatus.PAYMENT_CONFIGURATION_REQUIRED);
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
        throw new IllegalStateException("El film protector es obligatorio para los envios por Shalom");
    }

    @Transactional
    public FilmResult reactivate(Long addonId) {
        CrmWhatsappFilmAddon addon=addons.findForUpdate(addonId).orElseThrow();
        if(addon.getStatus()!=CrmWhatsappFilmStatus.PAYMENT_OVERDUE
                && addon.getStatus()!=CrmWhatsappFilmStatus.PAYMENT_CONFIGURATION_REQUIRED)
            throw new IllegalStateException("El cobro del film no se puede reactivar en su estado actual");
        MetodoPagoCuenta account=findYapeAccount(addon.getAgentSession());
        if(account==null)throw new IllegalStateException("No existe una cuenta Yape activa");
        LocalDateTime now=LocalDateTime.now();addon.setPaymentAccount(account);
        addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PAYMENT);addon.setPaymentStartedAt(now);
        addon.setExpiresAt(now.plusHours(1));addon.setResolvedAt(null);
        addon.getFulfillment().setStatus(CrmWhatsappFulfillmentStatus.FILM_PAYMENT_PENDING);
        return result(addons.save(addon),false);
    }

    @Transactional
    public FilmResult confirmManually(Long addonId) {
        return confirmManually(addonId, null, null);
    }

    @Transactional
    public FilmResult confirmManually(Long addonId, Usuario actor, Integer coveredBySaleId) {
        CrmWhatsappFilmAddon addon=addons.findForUpdate(addonId).orElseThrow();
        if(addon.getVenta()==null)throw new IllegalStateException("El film no está vinculado a una venta");
        if (addon.getStatus() == CrmWhatsappFilmStatus.CONFIRMED
                || addon.getStatus() == CrmWhatsappFilmStatus.COVERED_BY_PREVIOUS_ORDER) return result(addon, false);
        if (addon.getStatus() == CrmWhatsappFilmStatus.ORDER_CANCELLED)
            throw new IllegalStateException("La venta fue anulada");
        LocalDateTime now = LocalDateTime.now();
        if (coveredBySaleId != null) {
            CrmWhatsappFilmAddon previous = addons.findByVenta_IdVenta(coveredBySaleId)
                    .filter(value -> value.getStatus() == CrmWhatsappFilmStatus.CONFIRMED
                            || value.getStatus() == CrmWhatsappFilmStatus.COVERED_BY_PREVIOUS_ORDER)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El pedido anterior no tiene un film pagado confirmado"));
            if (previous.getVenta().getIdVenta().equals(addon.getVenta().getIdVenta()))
                throw new IllegalArgumentException("Selecciona un pedido anterior distinto");
            if (previous.getVenta().getCliente() == null || addon.getVenta().getCliente() == null
                    || !previous.getVenta().getCliente().getIdCliente()
                            .equals(addon.getVenta().getCliente().getIdCliente()))
                throw new IllegalArgumentException("El pedido anterior no pertenece al mismo cliente");
            addon.setStatus(CrmWhatsappFilmStatus.COVERED_BY_PREVIOUS_ORDER);
            addon.setCoveredBySale(previous.getVenta());
            addon.setResolutionSource("PREVIOUS_ORDER");
        } else {
            addon.setStatus(CrmWhatsappFilmStatus.CONFIRMED); addon.setPaidAt(now);
            addon.setResolutionSource("MANUAL_CRM");
            jdbc.update("""
                    INSERT INTO crm_whatsapp_film_payment
                      (id_film_addon,id_venta,id_film_evidence,confirmed_by,confirmation_source,
                       amount,currency,provider,operation_code,paid_at)
                    VALUES (?,?,NULL,?, 'MANUAL_CRM',?,'PEN','YAPE',NULL,?)
                    ON DUPLICATE KEY UPDATE id_film_payment=id_film_payment
                    """, addon.getIdFilmAddon(), addon.getVenta().getIdVenta(),
                    actor == null ? null : actor.getIdUsuario(), PRICE, now);
        }
        addon.setResolvedBy(actor); addon.setResolvedAt(now);
        addon.getFulfillment().setStatus(CrmWhatsappFulfillmentStatus.READY);
        CrmWhatsappAgentSession session=addon.getAgentSession();
        if(session.getState()==CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR)
            transition(session,CrmWhatsappAgentEvent.PEDIDO_ANTERIOR_CONFIRMADO);
        else if(stateMachine.canTransition(session.getState(),CrmWhatsappAgentEvent.PAGO_FILM_APROBADO))
            transition(session,CrmWhatsappAgentEvent.PAGO_FILM_APROBADO);
        return result(addons.save(addon),false);
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
                    && (f.getStatus() == CrmWhatsappFulfillmentStatus.READY
                        || f.getStatus() == CrmWhatsappFulfillmentStatus.FILM_PAYMENT_PENDING
                        || f.getStatus() == CrmWhatsappFulfillmentStatus.FILM_PAYMENT_OVERDUE))
            .orElseThrow(() -> new IllegalStateException("El envio Shalom aun no esta confirmado")); }
    private void requireState(CrmWhatsappAgentSession s, CrmWhatsappAgentState state) {
        if (s.getState() != state) throw new IllegalStateException("La sesion no esta en " + state);
    }
    private void transition(CrmWhatsappAgentSession s, CrmWhatsappAgentEvent e) {
        LocalDateTime now = LocalDateTime.now(); s.setState(stateMachine.transition(s.getState(), e));
        s.setLastEvent(e); s.setStateChangedAt(now); s.setLastActivityAt(now); sessions.save(s);
    }
    private void ensureWaitingForFilmPayment(CrmWhatsappAgentSession session) {
        if (session.getState() == CrmWhatsappAgentState.ESPERANDO_PAGO_FILM) return;
        if (session.getState() != CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION)
            throw new IllegalStateException("La venta no esta lista para esperar el pago del film");
        transition(session, CrmWhatsappAgentEvent.FILM_ACEPTADO);
    }
    private FilmResult result(CrmWhatsappFilmAddon a, boolean continueWithoutFilm) {
        MetodoPagoCuenta c = a.getPaymentAccount();
        return new FilmResult(a.getIdFilmAddon(), a.getStatus().name(), a.getAmount(),
                c == null ? null : c.getNumeroCuenta(), c == null ? null : c.getTitular(),
                a.getExpiresAt(), continueWithoutFilm,
                a.getVenta() == null ? null : a.getVenta().getIdVenta(),
                a.getFulfillment().getShalomAgency(), a.getFulfillment().getShalomAgencyAddress());
    }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public record FilmResult(Long addonId, String status, BigDecimal amount, String accountNumber,
            String accountHolder, LocalDateTime expiresAt, boolean continueWithoutFilm,
            Integer saleId, String shalomAgency, String shalomAgencyAddress) {}
}
