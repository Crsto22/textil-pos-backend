package com.sistemapos.sistematextil.services.agent.promotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSearchResult;

class AgentPromotionToolsTest {

    @Test
    void exposesExactlyThreePromotionToolsWithoutBranchOrSessionInSchema() {
        AgentPromotionTools tools = new AgentPromotionTools(mock(AgentPromotionQueryService.class));
        Set<String> names = Arrays.stream(AgentPromotionTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(method -> method.getAnnotation(Tool.class).name()).collect(Collectors.toSet());
        assertEquals(Set.of("buscar_promociones", "obtener_promocion", "validar_promocion_carrito"), names);

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
        assertEquals(3, callbacks.length);
        assertFalse(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().inputSchema().toLowerCase())
                .anyMatch(schema -> schema.contains("sucursal") || schema.contains("branchid")
                        || schema.contains(AgentCatalogTools.SESSION_ID_CONTEXT_KEY.toLowerCase())));
    }

    @Test
    void injectsSessionFromTrustedToolContext() {
        AgentPromotionQueryService service = mock(AgentPromotionQueryService.class);
        AgentPromotionTools tools = new AgentPromotionTools(service);
        PromotionSearchResult expected = new PromotionSearchResult("combo 42", 7, List.of(), false);
        when(service.searchPromotions(91L, "combo 42", 10)).thenReturn(expected);

        var result = tools.buscarPromociones("combo 42", 10,
                new ToolContext(Map.of(AgentCatalogTools.SESSION_ID_CONTEXT_KEY, 91L)));

        assertEquals(expected, result);
        verify(service).searchPromotions(91L, "combo 42", 10);
    }

    @Test
    void rejectsMissingTrustedSession() {
        AgentPromotionTools tools = new AgentPromotionTools(mock(AgentPromotionQueryService.class));
        assertThrows(IllegalStateException.class,
                () -> tools.validarPromocionCarrito(null, new ToolContext(Map.of())));
    }
}
