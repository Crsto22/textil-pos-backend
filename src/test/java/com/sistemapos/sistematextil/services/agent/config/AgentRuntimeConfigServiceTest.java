package com.sistemapos.sistematextil.services.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;

class AgentRuntimeConfigServiceTest {
    @Test
    void returnsSafeDisabledDefaultsWithoutAStoredConfiguration() {
        CrmWhatsappAiConfigRepository repository = mock(CrmWhatsappAiConfigRepository.class);
        when(repository.findByConnection_IdConnection(9L)).thenReturn(Optional.empty());
        var service = new AgentRuntimeConfigService(repository, mock(CrmWhatsappAgentSessionRepository.class));

        var config = service.forConnection(9L);

        assertFalse(config.enabled());
        assertEquals(5000, config.messageGroupingMs());
        assertEquals(240, config.paymentReservationSeconds());
        assertEquals(AgentRuntimeConfigService.DEFAULT_WELCOME_MESSAGE, config.welcomeMessage());
    }

    @Test
    void mapsEveryOperationalLimitFromTheDatabase() {
        CrmWhatsappAiConfig entity = new CrmWhatsappAiConfig();
        entity.setAgentEnabled(true);
        entity.setActivationStage(CrmWhatsappAgentActivationStage.WHATSAPP_TEST);
        entity.setTestPhoneNumbers("+51 999-111-222,988777666");
        entity.setLimitedConversationsPerHour(12);
        entity.setMessageGroupingMs(8000);
        entity.setPaymentReservationSeconds(240);
        entity.setReceiptMinimumConfidence(92);
        entity.setAdvisorAttentionEnabled(true);
        entity.setJobMaxAttempts(4);
        entity.setOutboxMaxAttempts(7);
        entity.setMaxMessagesPerBatch(20);
        entity.setMaxToolCallsPerRun(16);
        entity.setMaxOutputMessagesPerRun(8);
        entity.setWelcomeMessage("Bienvenida personalizada");
        CrmWhatsappAiConfigRepository repository = mock(CrmWhatsappAiConfigRepository.class);
        when(repository.findByConnection_IdConnection(9L)).thenReturn(Optional.of(entity));
        var service = new AgentRuntimeConfigService(repository, mock(CrmWhatsappAgentSessionRepository.class));

        var config = service.forConnection(9L);

        assertTrue(config.enabled());
        assertEquals(CrmWhatsappAgentActivationStage.WHATSAPP_TEST, config.activationStage());
        assertTrue(config.testPhoneNumbers().contains("999111222"));
        assertEquals(12, config.limitedConversationsPerHour());
        assertEquals(8000, config.messageGroupingMs());
        assertEquals(92, config.receiptMinimumConfidence());
        assertEquals(7, config.outboxMaxAttempts());
        assertEquals(8, config.maxOutputMessagesPerRun());
        assertEquals("Bienvenida personalizada", config.welcomeMessage());
    }

    @Test
    void legacyWhatsappTestStageDoesNotRestrictProductionTraffic() {
        CrmWhatsappAiConfig entity = configured(CrmWhatsappAgentActivationStage.WHATSAPP_TEST);
        entity.setTestPhoneNumbers("999111222");
        CrmWhatsappAiConfigRepository repository = mock(CrmWhatsappAiConfigRepository.class);
        when(repository.findByConnection_IdConnection(9L)).thenReturn(Optional.of(entity));
        var service = new AgentRuntimeConfigService(repository, mock(CrmWhatsappAgentSessionRepository.class));

        assertTrue(service.allowsIncoming(9L, 20L, "+51 999 111 222"));
        assertTrue(service.allowsIncoming(9L, 21L, "988777666@s.whatsapp.net"));
    }

    @Test
    void legacyLimitedStageDoesNotCapProductionTraffic() {
        CrmWhatsappAiConfig entity = configured(CrmWhatsappAgentActivationStage.LIMITED);
        entity.setLimitedConversationsPerHour(2);
        CrmWhatsappAiConfigRepository repository = mock(CrmWhatsappAiConfigRepository.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        when(repository.findByConnection_IdConnection(9L)).thenReturn(Optional.of(entity));
        when(sessions.existsByConversation_IdConversation(20L)).thenReturn(true);
        when(sessions.existsByConversation_IdConversation(21L)).thenReturn(false);
        when(sessions.countByConnection_IdConnectionAndCreatedAtGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        var service = new AgentRuntimeConfigService(repository, sessions);

        assertTrue(service.allowsIncoming(9L, 20L, "999111222"));
        assertTrue(service.allowsIncoming(9L, 21L, "988777666"));
    }

    private CrmWhatsappAiConfig configured(CrmWhatsappAgentActivationStage stage) {
        CrmWhatsappAiConfig entity = new CrmWhatsappAiConfig();
        entity.setAgentEnabled(true);
        entity.setActivationStage(stage);
        entity.setLimitedConversationsPerHour(10);
        return entity;
    }
}
