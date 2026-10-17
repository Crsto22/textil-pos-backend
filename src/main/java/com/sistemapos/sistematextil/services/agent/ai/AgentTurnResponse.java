package com.sistemapos.sistematextil.services.agent.ai;

import java.util.List;

import com.sistemapos.sistematextil.services.agent.context.AgentMissingData;

public record AgentTurnResponse(
        String customerMessage,
        String conversationSummary,
        boolean requiresHumanHelp,
        List<AgentMediaRequest> media,
        String pendingStepReminder,
        List<AgentMissingData> remindedPendingData) {

    public AgentTurnResponse {
        media = media == null ? List.of()
                : media.stream().filter(java.util.Objects::nonNull).toList();
        pendingStepReminder = pendingStepReminder == null ? "" : pendingStepReminder.trim();
        remindedPendingData = remindedPendingData == null ? List.of()
                : remindedPendingData.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    public AgentTurnResponse(String customerMessage, String conversationSummary,
            boolean requiresHumanHelp, List<AgentMediaRequest> media) {
        this(customerMessage, conversationSummary, requiresHumanHelp, media, "", List.of());
    }
}
