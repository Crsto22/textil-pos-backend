package com.sistemapos.sistematextil.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentReviewAuditRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoCuentaRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappPaymentEvidenceRegistrationTest {

    @Mock private CrmWhatsappPaymentRequestRepository requestRepository;
    @Mock private CrmWhatsappPaymentEvidenceRepository evidenceRepository;
    @Mock private CrmWhatsappPaymentReviewAuditRepository reviewAuditRepository;
    @Mock private CrmWhatsappAgentSessionRepository agentSessionRepository;
    @Mock private CrmWhatsappFulfillmentRepository fulfillmentRepository;
    @Mock private CrmWhatsappFilmAddonRepository filmAddonRepository;
    @Mock private CrmWhatsappConversationRepository conversationRepository;
    @Mock private CrmWhatsappMessageRepository messageRepository;
    @Mock private SucursalMetodoPagoConfigRepository branchPaymentRepository;
    @Mock private MetodoPagoCuentaRepository accountRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private VentaDetalleRepository ventaDetalleRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private S3StorageService storageService;
    @Mock private CrmWhatsappEventService eventService;
    @Mock private ObjectProvider<CrmWhatsappChatService> chatServiceProvider;
    @Mock private CrmWhatsappPaymentReservationService reservationService;
    @Mock private VentaAnulacionService ventaAnulacionService;
    @Mock private VentaService ventaService;
    @Mock private CrmWhatsappAgentStateMachine agentStateMachine;

    @InjectMocks private CrmWhatsappPaymentEvidenceService service;

    @Test
    void ignoresWhatsappStickersEvenWhenTheirMimeTypeIsWebp() {
        CrmWhatsappMessage sticker = mediaMessage("STICKER", "image/webp");
        when(messageRepository.findForUpdate(53L)).thenReturn(Optional.of(sticker));
        when(evidenceRepository.findByMessage_IdMessage(53L)).thenReturn(Optional.empty());

        service.registerIncomingMedia(53L);

        verifyNoInteractions(requestRepository, storageService);
        verify(evidenceRepository, never()).save(any());
    }

    @Test
    void keepsTheAcceptedReceiptWhenAnotherImageArrivesLater() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(19826L);
        CrmWhatsappMessage laterImage = mediaMessage("IMAGE", "image/jpeg");
        laterImage.setConversation(conversation);
        CrmWhatsappPaymentRequest request = new CrmWhatsappPaymentRequest();
        request.setIdPaymentRequest(80L);

        when(messageRepository.findForUpdate(53L)).thenReturn(Optional.of(laterImage));
        when(evidenceRepository.findByMessage_IdMessage(53L)).thenReturn(Optional.empty());
        when(requestRepository.findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(19826L), any())).thenReturn(Optional.of(request));
        when(evidenceRepository
                .findFirstByPaymentRequest_IdPaymentRequestAndValidationStatusOrderByCreatedAtDesc(
                        80L, CrmWhatsappPaymentEvidenceStatus.VALIDADO))
                .thenReturn(Optional.empty());
        when(evidenceRepository
                .findFirstByPaymentRequest_IdPaymentRequestAndValidationStatusOrderByCreatedAtDesc(
                        80L, CrmWhatsappPaymentEvidenceStatus.ACEPTABLE))
                .thenReturn(Optional.of(new CrmWhatsappPaymentEvidence()));

        service.registerIncomingMedia(53L);

        verifyNoInteractions(storageService);
        verify(evidenceRepository, never()).save(any());
    }

    private CrmWhatsappMessage mediaMessage(String type, String mimeType) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setDirection("INCOMING");
        message.setMessageType(type);
        message.setMediaMimeType(mimeType);
        message.setMediaStoragePath("crm/media/file");
        return message;
    }
}
