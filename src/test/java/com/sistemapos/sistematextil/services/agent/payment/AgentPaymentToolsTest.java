package com.sistemapos.sistematextil.services.agent.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;

class AgentPaymentToolsTest {

    @Test
    void exposesPaymentToolsWithoutSecureContextArguments() {
        AgentPaymentTools tools = new AgentPaymentTools(mock(AgentPaymentService.class));
        Set<String> names = Arrays.stream(AgentPaymentTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(java.util.Objects::nonNull)
                .map(Tool::name)
                .collect(Collectors.toSet());
        assertEquals(Set.of("listar_metodos_pago", "seleccionar_metodo_pago", "crear_solicitud_pago"), names);

        ToolCallbackProvider callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        Arrays.stream(callbacks.getToolCallbacks()).forEach(callback -> {
            String schema = callback.getToolDefinition().inputSchema().toLowerCase();
            assertFalse(schema.contains("agentsessionid"));
            assertFalse(schema.contains("agentoperationid"));
            assertFalse(schema.contains("expectedamount"));
            assertFalse(schema.contains("expiresat"));
        });
    }

    @Test
    void passesSecureContextToPaymentRequest() {
        AgentPaymentService service = mock(AgentPaymentService.class);
        AgentPaymentTools tools = new AgentPaymentTools(service);
        ToolContext context = new ToolContext(Map.of(
                AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 41L,
                AgentCartTools.OPERATION_ID_CONTEXT_KEY, "call-payment"));

        tools.crearSolicitudPago(context);

        verify(service).createRequest(41L, "call-payment");
    }

    @Test
    void rejectsSelectionWithoutOperationId() {
        AgentPaymentTools tools = new AgentPaymentTools(mock(AgentPaymentService.class));
        ToolContext context = new ToolContext(Map.of(AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 41L));
        assertThrows(IllegalStateException.class, () -> tools.seleccionarMetodoPago(2, null, context));
    }
}
