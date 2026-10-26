package com.sistemapos.sistematextil.services.agent.fulfillment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgentFulfillmentTimeoutService {
    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
    static final List<CrmWhatsappAgentState> TIMEOUT_STATES = List.of(
            CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA,
            CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM,
            CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,
            CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR);
    private static final List<CrmWhatsappPaymentRequestStatus> APPROVED_PAYMENT_STATUSES = List.of(
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
            CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);

    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappFulfillmentRepository fulfillments;
    private final CrmWhatsappPaymentRequestRepository payments;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentSaleService sales;
    private final AgentOutboxService outbox;
    private final Clock clock;
    private final Duration timeout;

    @Autowired
    public AgentFulfillmentTimeoutService(
            CrmWhatsappAgentSessionRepository sessions,
            CrmWhatsappFulfillmentRepository fulfillments,
            CrmWhatsappPaymentRequestRepository payments,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentSaleService sales,
            AgentOutboxService outbox,
            @Value("${crm.whatsapp.agent.fulfillment-timeout-minutes:30}") long timeoutMinutes) {
        this(sessions, fulfillments, payments, stateMachine, sales, outbox,
                Clock.system(ZoneId.of("America/Lima")), timeoutMinutes);
    }

    AgentFulfillmentTimeoutService(
            CrmWhatsappAgentSessionRepository sessions,
            CrmWhatsappFulfillmentRepository fulfillments,
            CrmWhatsappPaymentRequestRepository payments,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentSaleService sales,
            AgentOutboxService outbox,
            Clock clock,
            @Value("${crm.whatsapp.agent.fulfillment-timeout-minutes:30}") long timeoutMinutes) {
        this.sessions = sessions;
        this.fulfillments = fulfillments;
        this.payments = payments;
        this.stateMachine = stateMachine;
        this.sales = sales;
        this.outbox = outbox;
        this.clock = clock;
        this.timeout = Duration.ofMinutes(Math.max(1, timeoutMinutes));
    }

    @Transactional(readOnly = true)
    public List<Long> readyIds(int limit) {
        return sessions.findDeliveryTimeoutCandidateIds(TIMEOUT_STATES, now().minus(timeout),
                PageRequest.of(0, Math.max(1, limit)));
    }

    @Transactional
    public boolean completeTimedOutDelivery(Long sessionId) {
        LocalDateTime now = now();
        CrmWhatsappAgentSession session = sessions.findForUpdate(sessionId).orElse(null);
        if (session == null || !Boolean.TRUE.equals(session.getActiveSlot())
                || !TIMEOUT_STATES.contains(session.getState())
                || session.getLastActivityAt() == null
                || session.getLastActivityAt().isAfter(now.minus(timeout))) {
            return false;
        }

        CrmWhatsappFulfillment fulfillment = fulfillments.findForUpdateBySessionId(sessionId).orElse(null);
        if (session.getState() == CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR) {
            if (fulfillment == null || fulfillment.getVenta() == null) return false;
            markPendingAdvisor(fulfillment, "La clienta no respondió la consulta de envío dentro de 30 minutos");
            fulfillments.save(fulfillment);
            apply(session, CrmWhatsappAgentEvent.ENTREGA_DERIVADA_A_ASESORA, now);
            enqueueFinalMessage(sessionId, fulfillment.getVenta().getIdVenta(), fulfillment.getMethod());
            return true;
        }

        CrmWhatsappPaymentRequest payment = payments
                .findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(
                        sessionId, APPROVED_PAYMENT_STATUSES)
                .flatMap(item -> payments.findForUpdate(item.getIdPaymentRequest()))
                .orElse(null);
        if (payment == null || payment.getCliente() == null) return false;

        if (fulfillment == null) {
            fulfillment = new CrmWhatsappFulfillment();
            fulfillment.setAgentSession(session);
            fulfillment.setConversation(session.getConversation());
            fulfillment.setPaymentRequest(payment);
            fulfillment.setIdempotencyKey(sha256("fulfillment-timeout:" + sessionId));
            fulfillment.setMethod(CrmWhatsappFulfillmentMethod.PENDING);
        } else if (fulfillment.getMethod() == null) {
            fulfillment.setMethod(CrmWhatsappFulfillmentMethod.PENDING);
        }
        if (fulfillment.getPaymentRequest() == null) fulfillment.setPaymentRequest(payment);
        markPendingAdvisor(fulfillment, "Datos de entrega no completados dentro de 30 minutos");
        fulfillment = fulfillments.save(fulfillment);
        apply(session, CrmWhatsappAgentEvent.ENTREGA_DERIVADA_A_ASESORA, now);

        var sale = sales.createSalesNote(sessionId, "automatic-delivery-timeout:" + sessionId);
        enqueueFinalMessage(sessionId, sale.saleId(), fulfillment.getMethod());
        return true;
    }

    private void markPendingAdvisor(CrmWhatsappFulfillment fulfillment, String note) {
        fulfillment.setStatus(CrmWhatsappFulfillmentStatus.PENDING_ADVISOR);
        fulfillment.setNotes(note);
        fulfillment.setConfirmedAt(null);
    }

    private void apply(CrmWhatsappAgentSession session, CrmWhatsappAgentEvent event, LocalDateTime now) {
        session.setState(stateMachine.transition(session.getState(), event));
        session.setLastEvent(event);
        session.setLastActivityAt(now);
        session.setStateChangedAt(now);
        sessions.save(session);
    }

    private void enqueueFinalMessage(Long sessionId, Integer saleId, CrmWhatsappFulfillmentMethod method) {
        boolean shalom = method == CrmWhatsappFulfillmentMethod.SHALOM;
        String message = "Entendido 💛\n\n"
                + "✅ Tu pedido fue registrado correctamente.\n"
                + "🧾 Número de pedido: " + saleId + "\n"
                + (shalom
                        ? "🚌 Tipo de entrega: Envío por Shalom\n"
                                + "📍 Agencia y datos de envío: Pendientes de completar\n\n"
                        : "📦 Tipo de entrega: Pendiente de confirmar\n"
                                + "📍 Datos de entrega: No mencionados\n\n")
                + "Una asesora del área de envíos se comunicará contigo para completar y confirmar "
                + "todos los datos de entrega.\n\n¡Gracias por confiar en Kiments! 💛";
        int ordinal = Math.floorMod(java.util.Objects.hash("DELIVERY_TIMEOUT", sessionId, saleId),
                Integer.MAX_VALUE);
        outbox.enqueueText(sessionId, null, null, ordinal, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo crear la clave de entrega pendiente", error);
        }
    }
}
