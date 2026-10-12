package com.sistemapos.sistematextil.services.agent.context;

import java.util.Locale;
import java.util.Optional;

import com.sistemapos.sistematextil.services.agent.context.AgentContext.Message;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Role;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource.MessageSource;

final class AgentMessageContextPolicy {
    private AgentMessageContextPolicy() {
    }

    static Optional<Message> include(MessageSource source) {
        if (source == null || source.message() == null) return Optional.empty();
        String direction = normalize(source.direction());
        String origin = normalize(source.origin());
        if ("INCOMING".equals(direction) && !"CRM_SYSTEM".equals(origin)) {
            return Optional.of(withRole(source.message(), Role.CUSTOMER));
        }
        if ("OUTGOING".equals(direction)
                && ("AGENT".equals(origin) || "AI".equals(origin) || "AI_KIMENTS".equals(origin))) {
            return Optional.of(withRole(source.message(), Role.ASSISTANT));
        }
        return Optional.empty();
    }

    private static Message withRole(Message message, Role role) {
        return new Message(message.id(), role, message.messageType(), message.content(), message.createdAt());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
