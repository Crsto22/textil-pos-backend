package com.sistemapos.sistematextil.services;

import java.util.Map;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiAuditEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiAuditService {
    private final CrmWhatsappAiAuditEventRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public void record(CrmWhatsappConnection connection, CrmWhatsappConversation conversation,
            CrmWhatsappAiRun run, Usuario actor, String type, String severity, String reason,
            Map<String, ?> metadata) {
        if (connection == null) return;
        CrmWhatsappAiAuditEvent event = new CrmWhatsappAiAuditEvent();
        event.setConnection(connection);
        event.setConversation(conversation);
        event.setRun(run);
        event.setActor(actor);
        event.setEventType(clean(type, 50));
        event.setSeverity(clean(severity, 20));
        event.setReason(clean(reason, 500));
        try {
            event.setMetadataJson(metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata));
        } catch (Exception ignored) {
            event.setMetadataJson(null);
        }
        repository.save(event);
    }

    private String clean(String value, int max) {
        String result = value == null ? "" : value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }
}
