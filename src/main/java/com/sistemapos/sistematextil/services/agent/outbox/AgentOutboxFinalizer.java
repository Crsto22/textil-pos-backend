package com.sistemapos.sistematextil.services.agent.outbox;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.services.CrmWhatsappChatService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentOutboxFinalizer {
    private final AgentOutboxStore store;
    private final CrmWhatsappChatService chatService;

    @Transactional
    public void complete(AgentOutboxStore.OutboxLease lease,
            AgentWhatsappOutboxDeliveryService.DeliveryReceipt receipt, LocalDateTime sentAt) {
        AgentOutboxStore.OutboxEntry entry = lease.entry();
        String visibleBody = "TEXT".equals(entry.messageType()) ? entry.body() : entry.caption();
        String origin = AgentOutboxService.OPERATIONAL_ORIGIN_MARKER.equals(entry.caption())
                ? AgentOutboxService.OPERATIONAL_ORIGIN_MARKER : "AI_KIMENTS";
        Long messageId = chatService.registrarEntregaAgente(new CrmWhatsappChatService.AgentOutgoingDelivery(
                entry.conversationId(), entry.replyToMessageId(), entry.messageType(), visibleBody,
                entry.mediaStoragePath(), entry.mediaFileName(), entry.mediaMimeType(),
                receipt.whatsappMessageId(), receipt.messageKeyJson(), receipt.baileysMessageJson(), origin));
        if (!store.complete(entry.outboxId(), lease.workerId(), receipt.whatsappMessageId(), messageId, sentAt)) {
            throw new IllegalStateException("La entrega perdio su lease antes de finalizar");
        }
    }
}
