package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;

class CrmWhatsappIncomingImageRoutingServiceTest {

    private final CrmWhatsappAiConfigRepository configs = mock(CrmWhatsappAiConfigRepository.class);
    private final CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
    private final CrmWhatsappAiMemoryService memory = mock(CrmWhatsappAiMemoryService.class);
    private final CrmWhatsappAiSaleDraftService saleDrafts = mock(CrmWhatsappAiSaleDraftService.class);
    private final CrmWhatsappIncomingImageRoutingService service = new CrmWhatsappIncomingImageRoutingService(
            configs, conversations, memory, saleDrafts);

    @Test
    void derivaImagenSilenciosamenteCuandoLaOpcionEstaHabilitada() {
        CrmWhatsappConversation conversation = conversation();
        CrmWhatsappMessage message = image(conversation);
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setTransferirImagenesAsesora(true);
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(conversations.save(conversation)).thenReturn(conversation);

        assertTrue(service.routeIfEnabled(conversation, message));

        assertEquals(CrmWhatsappAiAttentionMode.HUMANA, conversation.getAiAttentionMode());
        assertTrue(conversation.getAiAttentionModeExplicit());
        assertEquals("ESPERA", conversation.getStatus());
        assertEquals(CrmWhatsappWaitingReason.IMAGE_RECEIVED, conversation.getWaitingReason());
        verify(memory).registerIncoming(conversation, message);
        verify(memory).pauseForHuman(conversation, "Imagen recibida; requiere atencion de una asesora");
    }

    @Test
    void conservaFlujoAnteriorCuandoLaOpcionEstaDeshabilitada() {
        CrmWhatsappConversation conversation = conversation();
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setTransferirImagenesAsesora(false);
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));

        assertFalse(service.routeIfEnabled(conversation, image(conversation)));

        verifyNoInteractions(memory);
        verify(conversations, never()).save(conversation);
    }

    @Test
    void comprobanteConPagoActivoNoSeDerivaAlFlujoGeneral() {
        CrmWhatsappConversation conversation = conversation();
        when(saleDrafts.hasActivePaymentFlow(10L)).thenReturn(true);

        assertFalse(service.routeIfEnabled(conversation, image(conversation)));

        verifyNoInteractions(configs, memory);
        verify(conversations, never()).save(conversation);
    }

    @Test
    void imagenEnChatAsignadoNoCambiaLaAtencion() {
        CrmWhatsappConversation conversation = conversation();
        conversation.setAssignedUser(new Usuario());

        assertFalse(service.routeIfEnabled(conversation, image(conversation)));

        verifyNoInteractions(configs, memory, saleDrafts);
        verify(conversations, never()).save(conversation);
    }

    private CrmWhatsappConversation conversation() {
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setConnection(connection);
        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);
        return conversation;
    }

    private CrmWhatsappMessage image(CrmWhatsappConversation conversation) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(20L);
        message.setConversation(conversation);
        message.setDirection("INCOMING");
        message.setMessageType("IMAGE");
        return message;
    }
}
