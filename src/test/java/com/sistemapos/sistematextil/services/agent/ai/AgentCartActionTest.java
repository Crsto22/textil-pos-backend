package com.sistemapos.sistematextil.services.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AgentCartActionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsTheGenericCommercialActionToASafeNoOp() throws Exception {
        assertEquals(AgentCartAction.NONE, mapper.readValue("\"CHANGE_CART\"", AgentCartAction.class));
    }

    @Test
    void keepsKnownActionsAndIgnoresUnknownValuesSafely() throws Exception {
        assertEquals(AgentCartAction.ADD_PRODUCT,
                mapper.readValue("\"ADD_PRODUCT\"", AgentCartAction.class));
        assertEquals(AgentCartAction.REPLACE_CART,
                mapper.readValue("\"REPLACE_CART\"", AgentCartAction.class));
        assertEquals(AgentCartAction.NONE,
                mapper.readValue("\"AN_UNSUPPORTED_ACTION\"", AgentCartAction.class));
    }
}
