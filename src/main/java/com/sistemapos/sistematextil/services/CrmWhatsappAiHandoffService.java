package com.sistemapos.sistematextil.services;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiHandoffService {

    private static final int MAX_REASON_LENGTH = 500;

    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappAiMemoryService memoryService;
    private final CrmWhatsappEventService eventService;

    @Transactional
    public boolean requireAdvisorForFailure(Long conversationId, String reason, Long runId) {
        return requireAdvisor(conversationId, CrmWhatsappWaitingReason.AI_RESPONSE_FAILED, reason, runId);
    }

    @Transactional
    public boolean requireAdvisor(Long conversationId, CrmWhatsappWaitingReason waitingReason,
            String reason, Long runId) {
        if (conversationId == null) return false;
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId).orElse(null);
        if (conversation == null || "RESUELTO".equals(conversation.getStatus())
                || conversation.getAssignedUser() != null) return false;

        String detail = limit(clean(reason).isBlank()
                ? "La respuesta de IA no pudo enviarse al cliente"
                : clean(reason));
        conversation.setStatus("ESPERA");
        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.HUMANA);
        conversation.setAiAttentionModeExplicit(true);
        conversation.setWaitingReason(waitingReason == null
                ? CrmWhatsappWaitingReason.AI_RESPONSE_FAILED
                : waitingReason);
        conversation.setWaitingDetail(detail);
        conversation = conversationRepository.save(conversation);
        memoryService.pauseForHuman(conversation, detail);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ai.handoff.required");
        event.put("conversationId", conversationId);
        event.put("reason", detail);
        event.put("waitingReason", conversation.getWaitingReason().name());
        if (runId != null) event.put("runId", runId);
        eventService.publishAfterCommit(event, event, null, true);
        return true;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value) {
        return value.length() <= MAX_REASON_LENGTH ? value : value.substring(0, MAX_REASON_LENGTH);
    }
}
