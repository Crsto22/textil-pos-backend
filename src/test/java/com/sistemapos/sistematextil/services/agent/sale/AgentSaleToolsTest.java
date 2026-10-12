package com.sistemapos.sistematextil.services.agent.sale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;

class AgentSaleToolsTest {
    @Test
    void exposesOnlySafeSalesNoteToolWithoutEditableSaleArguments() {
        AgentSaleTools tools = new AgentSaleTools(mock(AgentSaleService.class));
        Tool annotation = Arrays.stream(AgentSaleTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
        assertEquals("generar_nota_venta", annotation.name());

        ToolCallbackProvider callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        String schema = callbacks.getToolCallbacks()[0].getToolDefinition().inputSchema().toLowerCase();
        assertFalse(schema.contains("precio"));
        assertFalse(schema.contains("monto"));
        assertFalse(schema.contains("producto"));
        assertFalse(schema.contains("agentsessionid"));
        assertFalse(schema.contains("agentoperationid"));
    }

    @Test
    void passesOnlyTrustedContextToSalesService() {
        AgentSaleService service = mock(AgentSaleService.class);
        AgentSaleTools tools = new AgentSaleTools(service);
        ToolContext context = new ToolContext(Map.of(
                AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 41L,
                AgentCartTools.OPERATION_ID_CONTEXT_KEY, "sale-1"));

        tools.generarNotaVenta(context);

        verify(service).createSalesNote(41L, "sale-1");
    }
}
