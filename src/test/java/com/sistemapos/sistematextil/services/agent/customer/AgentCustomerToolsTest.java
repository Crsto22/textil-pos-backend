package com.sistemapos.sistematextil.services.agent.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
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

class AgentCustomerToolsTest {

    @Test
    void exposesOnlyCustomerBusinessArgumentsToTheModel() {
        AgentCustomerTools tools = new AgentCustomerTools(mock(AgentCustomerService.class));
        Set<String> names = Arrays.stream(AgentCustomerTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(java.util.Objects::nonNull)
                .map(Tool::name)
                .collect(Collectors.toSet());

        assertEquals(Set.of("buscar_cliente", "registrar_cliente", "actualizar_datos_cliente",
                "relacionar_cliente_conversacion"), names);
        ToolCallbackProvider callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        Arrays.stream(callbacks.getToolCallbacks()).forEach(callback -> {
            String schema = callback.getToolDefinition().inputSchema().toLowerCase();
            assertFalse(schema.contains("agentsessionid"));
            assertFalse(schema.contains("agentoperationid"));
            assertFalse(schema.contains("idempresa"));
        });
    }

    @Test
    void passesSecureSessionAndOperationContextToRegistration() {
        AgentCustomerService service = mock(AgentCustomerService.class);
        AgentCustomerTools tools = new AgentCustomerTools(service);
        ToolContext context = new ToolContext(Map.of(
                AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 44L,
                AgentCartTools.OPERATION_ID_CONTEXT_KEY, "call-10"));

        tools.registrarCliente("Ana Perez", "999 111 222", null, null, context);

        verify(service).register(44L, "Ana Perez", "999 111 222", null, null, "call-10");
    }

    @Test
    void rejectsMutationWithoutOperationId() throws Exception {
        AgentCustomerTools tools = new AgentCustomerTools(mock(AgentCustomerService.class));
        Method method = AgentCustomerTools.class.getDeclaredMethod("relacionarCliente", Integer.class,
                ToolContext.class);
        assertEquals("relacionar_cliente_conversacion", method.getAnnotation(Tool.class).name());
        ToolContext context = new ToolContext(Map.of(AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 44L));

        assertThrows(IllegalStateException.class, () -> tools.relacionarCliente(7, context));
    }
}
