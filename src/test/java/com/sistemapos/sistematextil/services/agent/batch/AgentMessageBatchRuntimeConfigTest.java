package com.sistemapos.sistematextil.services.agent.batch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;

class AgentMessageBatchRuntimeConfigTest {
    @Test
    void doesNotCreateASessionOrJobWhileTheAgentIsDisabled() {
        CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
        AgentSessionResolver sessions = mock(AgentSessionResolver.class);
        AgentJobStore jobs = mock(AgentJobStore.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(8L);
        conversation.setConnection(connection);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(10L);
        message.setConversation(conversation);
        message.setDirection("INCOMING");
        when(messages.findById(10L)).thenReturn(Optional.of(message));
        when(runtime.isEnabled(7L)).thenReturn(false);
        AgentMessageBatchScheduler scheduler = new AgentMessageBatchScheduler(messages, sessions, jobs,
                Duration.ofSeconds(5), Clock.systemUTC(), runtime);

        assertFalse(scheduler.schedule(10L).scheduled());
        verify(sessions, never()).resolveForIncoming(message);
        verify(jobs, never()).enqueue(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
