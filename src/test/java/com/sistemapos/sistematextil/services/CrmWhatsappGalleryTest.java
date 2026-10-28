package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationTagRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
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
import com.sistemapos.sistematextil.util.usuario.Rol;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappGalleryTest {
    @Mock CrmWhatsappConversationRepository conversationRepository;
    @Mock CrmWhatsappAgentSessionRepository agentSessionRepository;
    @Mock CrmWhatsappConversationTagRepository conversationTagRepository;
    @Mock CrmWhatsappFulfillmentRepository fulfillmentRepository;
    @Mock CrmWhatsappFilmAddonRepository filmAddonRepository;
    @Mock CrmWhatsappMessageRepository messageRepository;
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

    private Usuario actor;
    private CrmWhatsappConversation conversation;

    @BeforeEach
    void setUp() {
        actor = new Usuario();
        actor.setIdUsuario(7);
        actor.setEstado("ACTIVO");
        actor.setRol(Rol.VENTAS);
        actor.setAccesoCrm(true);

        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setArea(CrmWhatsappArea.SALES);
        conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(31L);
        conversation.setConnection(connection);

        when(usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(7)).thenReturn(Optional.of(actor));
        when(conversationRepository.findById(31L)).thenReturn(Optional.of(conversation));
    }

    @Test
    void returnsMediaPageWithCursorWithoutDuplicatingLookahead() {
        CrmWhatsappMessage newest = media(105L, "IMAGE", "image/jpeg", "foto.jpg");
        CrmWhatsappMessage second = media(104L, "VIDEO", "video/mp4", "video.mp4");
        CrmWhatsappMessage lookahead = media(103L, "DOCUMENT", "application/pdf", "archivo.pdf");
        when(messageRepository.findGalleryPage(eq(31L), eq(null), any(Pageable.class)))
                .thenReturn(List.of(newest, second, lookahead));

        var page = service.listarGaleria(31L, null, 2, actor);

        assertEquals(List.of(105L, 104L), page.content().stream().map(item -> item.id()).toList());
        assertEquals(104L, page.oldestId());
        assertTrue(page.hasMoreBefore());
        assertEquals("/api/crm/whatsapp/messages/105/media", page.content().getFirst().mediaUrl());
    }

    @Test
    void rejectsShippingConversationFromSalesGallery() {
        conversation.getConnection().setArea(CrmWhatsappArea.SHIPPING);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.listarGaleria(31L, null, 24, actor));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(messageRepository, never()).findGalleryPage(any(), any(), any());
    }

    private CrmWhatsappMessage media(Long id, String type, String mime, String fileName) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(id);
        message.setConversation(conversation);
        message.setMessageType(type);
        message.setMediaMimeType(mime);
        message.setMediaFileName(fileName);
        message.setMediaStoragePath("crm/gallery/" + fileName);
        message.setBody(fileName);
        message.setDirection("INCOMING");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
