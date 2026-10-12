package com.sistemapos.sistematextil.services.agent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductSearchResult;
import com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability;

import static org.mockito.Mockito.mock;

class AgentCatalogToolsTest {

    @Test
    void exposesExactlyFiveReadOnlyCatalogToolsWithoutBranchParameter() {
        Set<String> toolNames = Arrays.stream(AgentCatalogTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(method -> method.getAnnotation(Tool.class).name())
                .collect(Collectors.toSet());

        assertEquals(Set.of("buscar_productos", "obtener_producto", "consultar_variantes", "consultar_stock",
                "obtener_imagen_producto"), toolNames);
        assertFalse(Arrays.stream(AgentCatalogTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(Method::getParameters).flatMap(Arrays::stream)
                .anyMatch(parameter -> parameter.getName().toLowerCase().contains("sucursal")
                        || parameter.getName().toLowerCase().contains("branch")));

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new AgentCatalogTools(mock(AgentCatalogQueryService.class))).build().getToolCallbacks();
        assertEquals(5, callbacks.length);
        assertFalse(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().inputSchema().toLowerCase())
                .anyMatch(schema -> schema.contains("sucursal") || schema.contains("branchid")
                        || schema.contains(AgentCatalogTools.SESSION_ID_CONTEXT_KEY.toLowerCase())));
    }

    @Test
    void obtainsSessionOnlyFromHiddenToolContext() {
        AgentCatalogQueryService service = mock(AgentCatalogQueryService.class);
        AgentCatalogTools tools = new AgentCatalogTools(service);
        ProductSearchResult expected = new ProductSearchResult("alicee", 7, java.util.List.of(), false);
        when(service.searchProducts(99L, "alicee", 5, AgentCatalogAvailability.ALL)).thenReturn(expected);

        ProductSearchResult result = tools.buscarProductos("alicee", 5, AgentCatalogAvailability.ALL,
                new ToolContext(Map.of(AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 99L)));

        assertEquals(expected, result);
        verify(service).searchProducts(99L, "alicee", 5, AgentCatalogAvailability.ALL);
    }

    @Test
    void rejectsCallsWithoutTrustedSessionContext() {
        AgentCatalogTools tools = new AgentCatalogTools(mock(AgentCatalogQueryService.class));
        assertThrows(IllegalStateException.class,
                () -> tools.buscarProductos("emma", 5, AgentCatalogAvailability.ALL,
                        new ToolContext(Map.of())));
    }
}
