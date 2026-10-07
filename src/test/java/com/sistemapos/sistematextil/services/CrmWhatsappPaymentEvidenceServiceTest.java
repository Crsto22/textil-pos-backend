package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappPaymentEvidenceServiceTest {

    @Mock private CrmWhatsappConversationRepository conversationRepository;
    @Mock private CrmWhatsappAiMemoryService aiMemoryService;
    @InjectMocks private CrmWhatsappPaymentEvidenceService service;

    @Test
    void comprobanteRegistradoPasaAEsperaDePagoYDesactivaLaIa() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setStatus("ATENDIDO");
        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);

        ReflectionTestUtils.invokeMethod(service, "markPaymentVerification", conversation);

        assertEquals("ESPERA", conversation.getStatus());
        assertEquals(CrmWhatsappAiAttentionMode.HUMANA, conversation.getAiAttentionMode());
        assertTrue(conversation.getAiAttentionModeExplicit());
        assertEquals(CrmWhatsappWaitingReason.PAYMENT_VERIFICATION, conversation.getWaitingReason());
        verify(conversationRepository).save(conversation);
        verify(aiMemoryService).pauseForHuman(conversation,
                "Comprobante registrado; requiere validacion de una asesora");
    }
}
