package com.sistemapos.sistematextil.services.agent.context;

import java.util.List;
import java.util.Optional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Cart;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Fulfillment;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Message;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Payment;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.ToolResult;

public interface AgentContextDataSource {
    SessionSnapshot requireSession(Long sessionId);

    List<MessageSource> findRecentMessages(Long conversationId, Long anchorMessageId, int limit);

    Optional<Cart> findActiveCart(Long sessionId);

    Optional<Payment> findLatestPayment(Long sessionId);

    Optional<Fulfillment> findFulfillment(Long sessionId);

    List<ToolResult> findRecentToolResults(Long sessionId, int limit);

    record SessionSnapshot(
            Long sessionId,
            String sessionKey,
            Long conversationId,
            Long connectionId,
            Integer branchId,
            Long lastCustomerMessageId,
            CrmWhatsappAgentState state,
            CrmWhatsappAgentState resumeState,
            CrmWhatsappAgentEvent lastEvent,
            String summary,
            Integer customerId,
            String customerName,
            String customerPhone) {
    }

    record MessageSource(Message message, String direction, String origin) {
    }
}
