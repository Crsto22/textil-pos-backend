package com.sistemapos.sistematextil.services.agent.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class AgentFulfillmentToolsTest {
    @Test
    void exposesDeliveryToolsWithoutSecureSessionArguments() {
        AgentFulfillmentTools tools = new AgentFulfillmentTools(mock(AgentFulfillmentService.class));
        Set<String> names = Arrays.stream(AgentFulfillmentTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(java.util.Objects::nonNull)
                .map(Tool::name)
                .collect(Collectors.toSet());
        assertEquals(Set.of("listar_sucursales_recojo", "seleccionar_recojo_tienda",
                "seleccionar_envio_shalom", "buscar_agencias_shalom", "obtener_agencia_shalom",
                "consultar_datos_shalom_guardados", "guardar_datos_shalom",
                "confirmar_agencia_shalom", "reutilizar_datos_shalom", "editar_datos_shalom"), names);

        ToolCallbackProvider callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        Arrays.stream(callbacks.getToolCallbacks()).forEach(callback -> {
            String schema = callback.getToolDefinition().inputSchema().toLowerCase();
            assertFalse(schema.contains("agentsessionid"));
            assertFalse(schema.contains("agentoperationid"));
            assertFalse(schema.contains("paymentrequestid"));
        });
    }

    @Test
    void passesTrustedContextWhenSavingShalomData() {
        AgentFulfillmentService service = mock(AgentFulfillmentService.class);
        AgentFulfillmentTools tools = new AgentFulfillmentTools(service);
        ToolContext context = new ToolContext(Map.of(
                AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 41L,
                AgentCartTools.OPERATION_ID_CONTEXT_KEY, "shalom-data"));

        tools.guardarDatosShalom("Ana Perez", "12345678", "999111222", "Shalom Centro", context);

        verify(service).saveShalomData(41L, "Ana Perez", "12345678", "999111222",
                "Shalom Centro", "shalom-data");
    }
}
