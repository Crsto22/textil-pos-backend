package com.sistemapos.sistematextil.services.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

class AgentBootstrapToolsTest {

    @Test
    void exposesAReadOnlyToolAndCountsItsInvocation() throws Exception {
        AgentBootstrapTools tools = new AgentBootstrapTools();
        Method method = AgentBootstrapTools.class.getMethod("consultarCapacidadesAgente");
        Tool annotation = method.getAnnotation(Tool.class);

        AgentBootstrapTools.AgentCapabilities capabilities = tools.consultarCapacidadesAgente();

        assertEquals(AgentBootstrapTools.TOOL_NAME, annotation.name());
        assertEquals(1, tools.callCount());
        assertEquals("INTERPRETACION_AISLADA", capabilities.phase());
        assertFalse(capabilities.mutationsAllowed());
        assertFalse(capabilities.realMessagesAllowed());
    }
}
