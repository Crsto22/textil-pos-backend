package com.sistemapos.sistematextil.services.agent.visibility;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentConversationAccessGuard {

    private final CrmWhatsappChatService chatService;

    public void requireVisible(Long conversationId, Usuario actor) {
        chatService.obtenerConversacion(conversationId, actor);
    }
}
