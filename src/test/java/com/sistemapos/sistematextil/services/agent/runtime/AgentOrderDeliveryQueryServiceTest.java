package com.sistemapos.sistematextil.services.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;

class AgentOrderDeliveryQueryServiceTest {
    private final CrmWhatsappFulfillmentRepository fulfillments = mock(CrmWhatsappFulfillmentRepository.class);
    private final CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
    private final VentaDetalleRepository details = mock(VentaDetalleRepository.class);
    private final AgentOrderDeliveryQueryService service = new AgentOrderDeliveryQueryService(
            fulfillments, sessions, details);

    @Test
    void reportsAnOrderThatIsNotLinkedToTheAgentSession() {
        when(fulfillments.findByAgentSession_IdAgentSession(9L)).thenReturn(Optional.empty());
        when(sessions.findById(9L)).thenReturn(Optional.empty());

        var result = service.findForAgentSession(9L);

        assertFalse(result.agentSaleFound());
        assertEquals(AgentOrderDeliveryQueryService.DeliveryPolicy.NOT_LINKED_TO_AGENT, result.policy());
    }

    @Test
    void recognizesTheLatestAgentSaleFromTheSameChatAfterACommercialSessionChange() {
        when(fulfillments.findByAgentSession_IdAgentSession(9L)).thenReturn(Optional.empty());
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(25L);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setConversation(conversation);
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        Venta sale = new Venta();
        sale.setIdVenta(11620);
        CrmWhatsappFulfillment previous = new CrmWhatsappFulfillment();
        previous.setVenta(sale);
        when(fulfillments.findFirstByConversation_IdConversationAndVentaIsNotNullOrderByIdFulfillmentDesc(25L))
                .thenReturn(Optional.of(previous));
        when(details.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(11620))
                .thenReturn(List.of(detail("EMMA", false)));

        var result = service.findForAgentSession(9L);

        assertTrue(result.agentSaleFound());
        assertEquals(11620, result.saleId());
        assertEquals(AgentOrderDeliveryQueryService.DeliveryPolicy.READY_STOCK, result.policy());
    }

    @Test
    void usesReadyStockPolicyWhenAllSaleProductsAreImmediatelyAvailable() {
        linkSale(9L, 11618);
        when(details.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(11618))
                .thenReturn(List.of(detail("EMMA", false), detail("ALICE LISO", false)));

        var result = service.findForAgentSession(9L);

        assertTrue(result.agentSaleFound());
        assertEquals(11618, result.saleId());
        assertEquals(AgentOrderDeliveryQueryService.DeliveryPolicy.READY_STOCK, result.policy());
        assertEquals(List.of("EMMA", "ALICE LISO"), result.products());
    }

    @Test
    void usesPreorderPolicyWhenAtLeastOneSaleProductIsPreorder() {
        linkSale(9L, 11619);
        when(details.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(11619))
                .thenReturn(List.of(detail("EMMA", false), detail("MELINA", true)));

        var result = service.findForAgentSession(9L);

        assertEquals(AgentOrderDeliveryQueryService.DeliveryPolicy.PREORDER, result.policy());
        assertEquals(List.of("EMMA", "MELINA"), result.products());
    }

    private void linkSale(Long sessionId, Integer saleId) {
        Venta sale = new Venta();
        sale.setIdVenta(saleId);
        CrmWhatsappFulfillment fulfillment = new CrmWhatsappFulfillment();
        fulfillment.setVenta(sale);
        when(fulfillments.findByAgentSession_IdAgentSession(sessionId)).thenReturn(Optional.of(fulfillment));
    }

    private VentaDetalle detail(String name, boolean preorder) {
        Producto product = new Producto();
        product.setNombre(name);
        product.setPreventa(preorder);
        ProductoVariante variant = new ProductoVariante();
        variant.setProducto(product);
        VentaDetalle detail = new VentaDetalle();
        detail.setProductoVariante(variant);
        return detail;
    }
}
