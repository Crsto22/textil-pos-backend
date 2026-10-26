package com.sistemapos.sistematextil.services.agent.runtime;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.util.producto.ProductoPreventaPolicy;

import lombok.RequiredArgsConstructor;

/** Reads only sales explicitly linked to the current AI sales session. */
@Service
@RequiredArgsConstructor
public class AgentOrderDeliveryQueryService {
    private final CrmWhatsappFulfillmentRepository fulfillments;
    private final CrmWhatsappAgentSessionRepository sessions;
    private final VentaDetalleRepository saleDetails;

    @Transactional(readOnly = true)
    public OrderDeliveryGuidance findForAgentSession(Long sessionId) {
        CrmWhatsappFulfillment fulfillment = fulfillmentLinkedToSessionOrConversation(sessionId);
        if (fulfillment == null) {
            return new OrderDeliveryGuidance(false, null, DeliveryPolicy.NOT_LINKED_TO_AGENT, List.of());
        }
        Integer saleId = fulfillment.getVenta().getIdVenta();
        List<VentaDetalle> details = saleDetails
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(saleId);
        List<com.sistemapos.sistematextil.model.Producto> saleProducts = details.stream()
                .map(VentaDetalle::getProductoVariante)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.getProducto())
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean preorder = saleProducts.stream().anyMatch(ProductoPreventaPolicy::activa);
        boolean readyStock = saleProducts.stream().anyMatch(product -> !ProductoPreventaPolicy.activa(product));
        List<String> products = saleProducts.stream()
                .map(product -> product.getNombre())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        DeliveryPolicy policy = preorder && readyStock ? DeliveryPolicy.MIXED
                : preorder ? DeliveryPolicy.PREORDER : DeliveryPolicy.READY_STOCK;
        return new OrderDeliveryGuidance(true, saleId, policy, products);
    }

    private CrmWhatsappFulfillment fulfillmentLinkedToSessionOrConversation(Long sessionId) {
        CrmWhatsappFulfillment direct = fulfillments.findByAgentSession_IdAgentSession(sessionId)
                .filter(this::hasSale)
                .orElse(null);
        if (direct != null) return direct;
        CrmWhatsappAgentSession session = sessions.findById(sessionId).orElse(null);
        if (session == null || session.getConversation() == null
                || session.getConversation().getIdConversation() == null) return null;
        return fulfillments
                .findFirstByConversation_IdConversationAndVentaIsNotNullOrderByIdFulfillmentDesc(
                        session.getConversation().getIdConversation())
                .filter(this::hasSale)
                .orElse(null);
    }

    private boolean hasSale(CrmWhatsappFulfillment fulfillment) {
        return fulfillment != null && fulfillment.getVenta() != null
                && fulfillment.getVenta().getIdVenta() != null;
    }

    public enum DeliveryPolicy {
        READY_STOCK,
        PREORDER,
        MIXED,
        NOT_LINKED_TO_AGENT
    }

    public record OrderDeliveryGuidance(
            boolean agentSaleFound,
            Integer saleId,
            DeliveryPolicy policy,
            List<String> products) {
        public OrderDeliveryGuidance {
            products = products == null ? List.of() : List.copyOf(products);
        }
    }
}
