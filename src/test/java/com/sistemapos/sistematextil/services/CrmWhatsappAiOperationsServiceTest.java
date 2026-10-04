package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiDeliveryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiRunRepository;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappAiOperationsServiceTest {

    @Mock private CrmWhatsappAiConfigRepository configRepository;
    @Mock private CrmWhatsappAiRunRepository runRepository;
    @Mock private CrmWhatsappAiJobRepository jobRepository;
    @Mock private CrmWhatsappAiDeliveryRepository deliveryRepository;
    @Mock private CrmWhatsappConnectionService connectionService;
    @Mock private CrmWhatsappAiAuditService auditService;

    private CrmWhatsappAiOperationsService service;
    private CrmWhatsappAiConfig config;

    @BeforeEach
    void setUp() {
        service = new CrmWhatsappAiOperationsService(configRepository, runRepository, jobRepository,
                deliveryRepository, connectionService, auditService);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        config = new CrmWhatsappAiConfig();
        config.setConnection(connection);
        config.setOperationalStatus(CrmWhatsappAiOperationsService.ACTIVE);
        config.setAutomaticRolloutPercent(0);
        when(runRepository.sumUsageSince(eq(7L), any())).thenReturn(new Object[] { 0L, BigDecimal.ZERO });
    }

    @Test
    void modoAutomaticoNoEsBloqueadoPorElRollout() {
        CrmWhatsappConversation conversation = conversation(CrmWhatsappAiAttentionMode.AUTOMATICA);

        assertTrue(service.automaticAllowedForConversation(config, conversation));
    }

    @Test
    void modoHumanoNuncaHabilitaRespuestasAutomaticas() {
        CrmWhatsappConversation conversation = conversation(CrmWhatsappAiAttentionMode.HUMANA);

        assertFalse(service.automaticAllowedForConversation(config, conversation));
    }

    private CrmWhatsappConversation conversation(CrmWhatsappAiAttentionMode mode) {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(38L);
        conversation.setAiAttentionMode(mode);
        return conversation;
    }
}
