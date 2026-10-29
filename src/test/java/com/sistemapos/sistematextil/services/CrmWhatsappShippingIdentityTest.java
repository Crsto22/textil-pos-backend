package com.sistemapos.sistematextil.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappMessageReaction;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationTagRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageReactionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappTagRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappShippingIdentityTest {
    @Mock CrmWhatsappConversationRepository conversationRepository;
    @Mock CrmWhatsappAgentSessionRepository agentSessionRepository;
    @Mock CrmWhatsappConversationTagRepository conversationTagRepository;
    @Mock CrmWhatsappFulfillmentRepository fulfillmentRepository;
    @Mock CrmWhatsappFilmAddonRepository filmAddonRepository;
    @Mock CrmWhatsappMessageRepository messageRepository;
    @Mock CrmWhatsappMessageReactionRepository reactionRepository;
    @Mock CrmWhatsappPaymentEvidenceRepository paymentEvidenceRepository;
    @Mock CrmWhatsappPaymentRequestRepository paymentRequestRepository;
    @Mock CrmWhatsappTagRepository tagRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock EmpresaRepository empresaRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock ComprobanteConfigRepository comprobanteConfigRepository;
    @Mock VentaRepository ventaRepository;
    @Mock VentaDetalleRepository ventaDetalleRepository;
    @Mock PagoRepository pagoRepository;
    @Mock SucursalMetodoPagoConfigRepository sucursalMetodoPagoConfigRepository;
    @Mock UsuarioSucursalAccessService usuarioSucursalAccessService;
    @Mock ProductoService productoService;
    @Mock ProductoVarianteService productoVarianteService;
    @Mock VentaService ventaService;
    @Mock CrmWhatsappBridgeService bridgeService;
    @Mock CrmWhatsappEventService eventService;
    @Mock CrmWhatsappConnectionService connectionService;
    @Mock CrmWhatsappNumberChangeNoticeService numberChangeNoticeService;
    @Mock CrmWhatsappClientWriter clientWriter;
    @Mock CrmWhatsappPaymentReservationService paymentReservationService;
    @Mock S3StorageService storageService;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock AgentRuntimeConfigService agentRuntimeConfigService;
    @Mock AgentJobStore agentJobStore;
    @InjectMocks CrmWhatsappChatService service;

    @Test
    void emptyTextDoesNotCreateOrMoveAConversation() {
        service.recibirWebhook(new CrmWhatsappChatService.WebhookRequest(
                "kiments-sales", "message.received", "empty-1", "51999999999@s.whatsapp.net", null,
                null, "Cliente", "", "2026-10-24T15:00:00Z", null,
                "TEXT", "INCOMING", null, null, null, false, false, false, false,
                null, null, Map.of(), Map.of("message", Map.of("conversation", ""))));

        verify(connectionService, never()).buscarPorClientId(any());
        verify(conversationRepository, never()).insertIfAbsent(any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void ephemeralProtocolDoesNotCreateOrMoveAConversation() {
        service.recibirWebhook(new CrmWhatsappChatService.WebhookRequest(
                "kiments-sales", "message.received", "protocol-1", "51999999999@s.whatsapp.net", null,
                null, "Cliente", "", "2026-10-24T15:00:00Z", null,
                null, "INCOMING", null, null, null, false, false, false, false,
                null, null, Map.of(), Map.of("message", Map.of("protocolMessage",
                        Map.of("type", "EPHEMERAL_SYNC_RESPONSE")))));

        verify(connectionService, never()).buscarPorClientId(any());
        verify(conversationRepository, never()).insertIfAbsent(any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void reactionUpdatesItsTargetWithoutCreatingAConversationOrUnreadMessage() {
        CrmWhatsappConnection sales = new CrmWhatsappConnection();
        sales.setIdConnection(10L);
        sales.setArea(CrmWhatsappArea.SALES);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(7L);
        conversation.setConnection(sales);
        CrmWhatsappMessage target = new CrmWhatsappMessage();
        target.setIdMessage(90L);
        target.setConversation(conversation);
        target.setWhatsappMessageId("target-1");

        when(connectionService.buscarPorClientId("kiments-sales")).thenReturn(sales);
        when(messageRepository.findFirstByWhatsappMessageId("target-1")).thenReturn(Optional.of(target));
        when(reactionRepository.findFirstByMessage_IdMessageAndDirection(90L, "INCOMING"))
                .thenReturn(Optional.empty());
        when(reactionRepository.save(any(CrmWhatsappMessageReaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reactionRepository.findActiveByMessageIds(any())).thenReturn(List.of());

        service.recibirWebhook(new CrmWhatsappChatService.WebhookRequest(
                "kiments-sales", "message.reaction", "reaction-1", "51999999999@s.whatsapp.net", null,
                null, "Cliente", null, "2026-10-24T15:00:00Z", null,
                null, "INCOMING", "target-1", "👍", null, false, false, false, false,
                null, null, Map.of(), Map.of()));

        verify(reactionRepository).save(any(CrmWhatsappMessageReaction.class));
        verify(conversationRepository, never()).insertIfAbsent(any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void syncedLidReusesCanonicalShippingConversationByNormalizedPhone() {
        CrmWhatsappConnection shipping = new CrmWhatsappConnection();
        shipping.setIdConnection(20L);
        shipping.setArea(CrmWhatsappArea.SHIPPING);

        CrmWhatsappConversation canonical = new CrmWhatsappConversation();
        canonical.setIdConversation(44L);
        canonical.setConnection(shipping);
        canonical.setPhone("51924127067@s.whatsapp.net");
        canonical.setPhoneNumber("924127067");

        when(connectionService.buscarPorClientId("kiments-shipping")).thenReturn(shipping);
        when(conversationRepository.findShippingIdentityCandidatesForUpdate(
                eq(20L), eq("924127067"), eq("51924127067@s.whatsapp.net"), any()))
                .thenReturn(List.of(canonical));
        when(conversationRepository.save(any(CrmWhatsappConversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recibirWebhook(new CrmWhatsappChatService.WebhookRequest(
                "kiments-shipping", "chat.synced", null, "163569568059625@lid", "51924127067",
                "924127067", "CLIENTE 924127067", null, "2026-10-24T15:00:00", null,
                null, null, null, null, null, false, false, false, true, null, null, null, null));

        verify(conversationRepository, never()).insertIfAbsent(any(), any());
        verify(applicationEventPublisher).publishEvent(any(CrmWarehouseConversationEvent.class));
    }
}
