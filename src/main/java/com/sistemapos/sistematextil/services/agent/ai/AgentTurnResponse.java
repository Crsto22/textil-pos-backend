package com.sistemapos.sistematextil.services.agent.ai;

import java.util.List;

public record AgentTurnResponse(
        String customerMessage,
        String conversationSummary,
        boolean requiresHumanHelp,
        List<AgentMediaRequest> media) {
}
