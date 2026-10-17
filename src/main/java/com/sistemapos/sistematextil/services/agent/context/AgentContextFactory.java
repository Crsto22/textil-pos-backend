package com.sistemapos.sistematextil.services.agent.context;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Cart;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Customer;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Fulfillment;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Message;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Payment;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource.SessionSnapshot;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;

@Service
public class AgentContextFactory {
    private final AgentContextDataSource dataSource;
    private final ObjectMapper objectMapper;
    private final int recentMessageLimit;
    private final int toolResultLimit;

    @Autowired
    public AgentContextFactory(
            AgentContextDataSource dataSource,
            @Value("${crm.whatsapp.agent.context.recent-message-limit:30}") int recentMessageLimit,
            @Value("${crm.whatsapp.agent.context.tool-result-limit:12}") int toolResultLimit) {
        this(dataSource, new ObjectMapper().findAndRegisterModules(), recentMessageLimit, toolResultLimit);
    }

    AgentContextFactory(
            AgentContextDataSource dataSource,
            ObjectMapper objectMapper,
            int recentMessageLimit,
            int toolResultLimit) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.recentMessageLimit = clamp(recentMessageLimit, 1, 100);
        this.toolResultLimit = clamp(toolResultLimit, 1, 50);
    }

    @Transactional(readOnly = true)
    public AgentContext build(Long sessionId) {
        return build(sessionId, null);
    }

    @Transactional(readOnly = true)
    public AgentContext build(Long sessionId, Long anchorMessageId) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId es obligatorio");
        SessionSnapshot session = dataSource.requireSession(sessionId);
        Long effectiveAnchor = anchorMessageId == null ? session.lastCustomerMessageId() : anchorMessageId;

        List<Message> messages = dataSource
                .findRecentMessages(session.conversationId(), effectiveAnchor, recentMessageLimit)
                .stream()
                .map(AgentMessageContextPolicy::include)
                .flatMap(java.util.Optional::stream)
                .toList();
        Cart cart = dataSource.findActiveCart(sessionId).orElseGet(Cart::empty);
        Payment payment = dataSource.findLatestPayment(sessionId).orElse(null);
        Fulfillment fulfillment = dataSource.findFulfillment(sessionId).orElse(null);
        var toolResults = List.copyOf(dataSource.findRecentToolResults(sessionId, toolResultLimit));
        String customerPhone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(session.customerPhone());
        Customer customer = new Customer(session.customerId(), clean(session.customerName()),
                customerPhone.isBlank() ? null : customerPhone);
        List<AgentMissingData> missing = missingData(session.state(), customer, cart, payment, fulfillment);

        AgentContext unsigned = new AgentContext(
                session.sessionId(),
                session.sessionKey(),
                session.conversationId(),
                session.connectionId(),
                session.branchId(),
                effectiveAnchor,
                session.state(),
                session.resumeState(),
                session.lastEvent(),
                clean(session.summary()),
                customer,
                List.copyOf(messages),
                cart,
                missing,
                payment,
                fulfillment,
                toolResults,
                "");
        return new AgentContext(
                unsigned.sessionId(), unsigned.sessionKey(), unsigned.conversationId(), unsigned.connectionId(),
                unsigned.branchId(), unsigned.anchorMessageId(), unsigned.state(), unsigned.resumeState(),
                unsigned.lastEvent(), unsigned.previousSummary(), unsigned.customer(), unsigned.recentMessages(),
                unsigned.cart(), unsigned.missingData(), unsigned.payment(), unsigned.fulfillment(),
                unsigned.toolResults(), fingerprint(unsigned));
    }

    private List<AgentMissingData> missingData(
            CrmWhatsappAgentState state, Customer customer, Cart cart, Payment payment, Fulfillment fulfillment) {
        List<AgentMissingData> missing = new ArrayList<>();
        if (state == null) return List.of();
        switch (state) {
            case ARMANDO_CARRITO -> {
                if (cart.items().isEmpty()) missing.add(AgentMissingData.CART_ITEMS);
                else missing.add(AgentMissingData.CART_CONFIRMATION);
            }
            case ESPERANDO_DATOS_CLIENTE -> {
                if (blank(customer.name())) missing.add(AgentMissingData.CUSTOMER_NAME);
                if (blank(customer.phone())) missing.add(AgentMissingData.CUSTOMER_PHONE);
            }
            case ESPERANDO_METODO_PAGO -> {
                if (payment == null || blank(payment.method())) missing.add(AgentMissingData.PAYMENT_METHOD);
            }
            case ESPERANDO_COMPROBANTE, VALIDANDO_COMPROBANTE -> {
                if (payment == null || payment.latestEvidence() == null) {
                    missing.add(AgentMissingData.PAYMENT_EVIDENCE);
                }
            }
            case ESPERANDO_METODO_ENTREGA -> {
                if (fulfillment == null || blank(fulfillment.method())) {
                    missing.add(AgentMissingData.DELIVERY_METHOD);
                }
            }
            case ESPERANDO_DATOS_SHALOM -> addMissingShalomData(missing, fulfillment);
            case ESPERANDO_CONFIRMACION_AGENCIA_SHALOM -> {
                if (fulfillment == null || blank(fulfillment.shalomCandidateIds())) {
                    missing.add(AgentMissingData.SHALOM_AGENCY);
                }
            }
            case ESPERANDO_PEDIDO_ANTERIOR -> missing.add(AgentMissingData.PREVIOUS_ORDER_DECISION);
            case ESPERANDO_DECISION_FILM -> missing.add(AgentMissingData.FILM_DECISION);
            case ESPERANDO_PAGO_FILM -> missing.add(AgentMissingData.FILM_EVIDENCE);
            default -> {
                // Los demás estados no esperan un dato concreto del cliente.
            }
        }
        return List.copyOf(missing);
    }

    private void addMissingShalomData(List<AgentMissingData> missing, Fulfillment fulfillment) {
        if (fulfillment == null || blank(fulfillment.recipientName())) {
            missing.add(AgentMissingData.SHALOM_RECIPIENT_NAME);
        }
        if (fulfillment == null || blank(fulfillment.documentNumber())) {
            missing.add(AgentMissingData.SHALOM_DOCUMENT_NUMBER);
        }
        if (fulfillment == null || blank(fulfillment.phone())) {
            missing.add(AgentMissingData.SHALOM_PHONE);
        }
        if (fulfillment == null || fulfillment.shalomAgencyId() == null
                && blank(fulfillment.shalomCandidateIds())) {
            missing.add(AgentMissingData.SHALOM_AGENCY);
        }
    }

    private String fingerprint(AgentContext context) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(context);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("No se pudo generar la huella reproducible del contexto", error);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
