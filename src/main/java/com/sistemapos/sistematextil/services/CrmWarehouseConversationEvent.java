package com.sistemapos.sistematextil.services;

public record CrmWarehouseConversationEvent(Long conversationId, Long messageId, String messageStatus) {
    public CrmWarehouseConversationEvent(Long conversationId) {
        this(conversationId, null, null);
    }
}
