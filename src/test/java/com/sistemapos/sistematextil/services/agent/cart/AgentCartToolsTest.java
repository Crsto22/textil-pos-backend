package com.sistemapos.sistematextil.services.agent.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;

class AgentCartToolsTest {

    @Test
    void exposesEightToolsWithoutPriceBranchSessionOrOperationInputs() {
        AgentCartTools tools = new AgentCartTools(mock(AgentCartService.class));
        Set<String> names = Arrays.stream(AgentCartTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(method -> method.getAnnotation(Tool.class).name()).collect(Collectors.toSet());
        assertEquals(Set.of("agregar_producto_carrito", "agregar_combo_carrito", "cambiar_cantidad_carrito",
                "cambiar_variante_carrito",
                "eliminar_producto_carrito", "vaciar_carrito", "recalcular_carrito", "confirmar_carrito"), names);

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
        assertEquals(8, callbacks.length);
        assertFalse(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().inputSchema().toLowerCase())
                .anyMatch(schema -> schema.contains("price") || schema.contains("precio")
                        || schema.contains("branch") || schema.contains("sucursal")
                        || schema.contains("agentsessionid") || schema.contains("agentoperationid")));
    }

    @Test
    void injectsSessionAndOperationIdsFromTrustedContext() {
        AgentCartService service = mock(AgentCartService.class);
        AgentCartTools tools = new AgentCartTools(service);
        CartResult expected = emptyResult();
        when(service.addProduct(8L, 101, 2, "tool-call-9")).thenReturn(expected);
        ToolContext context = new ToolContext(Map.of(
                AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 8L,
                AgentCartTools.OPERATION_ID_CONTEXT_KEY, "tool-call-9"));

        assertEquals(expected, tools.agregarProducto(101, 2, context));
        verify(service).addProduct(8L, 101, 2, "tool-call-9");
    }

    @Test
    void changesSizeOrColorUsingOneTrustedAtomicOperation() {
        AgentCartService service = mock(AgentCartService.class);
        AgentCartTools tools = new AgentCartTools(service);
        CartResult expected = emptyResult();
        when(service.changeVariant(8L, 101, 102, "tool-call-10")).thenReturn(expected);
        ToolContext context = new ToolContext(Map.of(
                AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 8L,
                AgentCartTools.OPERATION_ID_CONTEXT_KEY, "tool-call-10"));

        assertEquals(expected, tools.cambiarVariante(101, 102, context));
        verify(service).changeVariant(8L, 101, 102, "tool-call-10");
    }

    @Test
    void refusesMutationWithoutOperationId() {
        AgentCartTools tools = new AgentCartTools(mock(AgentCartService.class));
        ToolContext context = new ToolContext(Map.of(AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 8L));
        assertThrows(IllegalStateException.class, () -> tools.vaciarCarrito(context));
    }

    private CartResult emptyResult() {
        return new CartResult(null, "EMPTY", "PEN", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of());
    }
}
