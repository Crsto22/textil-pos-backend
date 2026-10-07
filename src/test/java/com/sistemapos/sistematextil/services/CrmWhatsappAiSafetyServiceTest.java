package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;

class CrmWhatsappAiSafetyServiceTest {

    private final CrmWhatsappAiAuditService audit = mock(CrmWhatsappAiAuditService.class);
    private final CrmWhatsappAiSafetyService service = new CrmWhatsappAiSafetyService(audit);
    private final CrmWhatsappConversation conversation = conversation();

    @Test
    void aceptaProductoImporteYEnlaceRespaldadosPorHerramientas() {
        List<Map<String, Object>> tools = List.of(Map.of(
                "tool", "buscar_productos",
                "products", List.of(Map.of(
                        "name", "BELEN",
                        "price", 75.00,
                        "ecommerceUrl", "https://kiments.com.pe/productos/belen"))));

        String reason = service.validateGroundedOutput(conversation,
                "👗 *BELEN*\n💰 S/75.00\nhttps://kiments.com.pe/productos/belen", tools);

        assertNull(reason);
    }

    @Test
    void bloqueaImporteQueNoExisteEnLosResultados() {
        List<Map<String, Object>> tools = List.of(Map.of(
                "tool", "buscar_productos",
                "products", List.of(Map.of("name", "BELEN", "price", 75.00))));

        String reason = service.validateGroundedOutput(conversation,
                "👗 *BELEN*\n💰 S/99.00", tools);

        assertTrue(reason.contains("importe"));
        verify(audit).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private CrmWhatsappConversation conversation() {
        CrmWhatsappConversation value = new CrmWhatsappConversation();
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        value.setConnection(connection);
        value.setIdConversation(10L);
        return value;
    }
}
