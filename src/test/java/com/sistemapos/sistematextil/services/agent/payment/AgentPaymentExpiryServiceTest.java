package com.sistemapos.sistematextil.services.agent.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;

class AgentPaymentExpiryServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-08T19:04:00Z"), ZoneId.of("America/Lima"));

    private CrmWhatsappPaymentRequestRepository requestRepository;
    private CrmWhatsappAgentSessionRepository sessionRepository;
    private UsuarioRepository userRepository;
    private CrmWhatsappPaymentReservationService reservationService;
    private AgentOutboxService outbox;
    private AgentPaymentExpiryService service;
    private CrmWhatsappPaymentRequest request;
    private CrmWhatsappAgentSession session;

    @BeforeEach
    void setUp() {
        requestRepository = mock(CrmWhatsappPaymentRequestRepository.class);
        sessionRepository = mock(CrmWhatsappAgentSessionRepository.class);
        userRepository = mock(UsuarioRepository.class);
        reservationService = mock(CrmWhatsappPaymentReservationService.class);
        outbox = mock(AgentOutboxService.class);
        service = new AgentPaymentExpiryService(requestRepository, sessionRepository, userRepository,
                reservationService, new CrmWhatsappAgentStateMachine(), CLOCK, outbox);
        session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(41L);
        session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.ESPERANDO_COMPROBANTE);
        request = new CrmWhatsappPaymentRequest();
        request.setIdPaymentRequest(77L);
        request.setAgentSession(session);
        request.setStatus(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE);
        request.setExpiresAt(LocalDateTime.of(2026, 10, 8, 14, 4));
        Usuario actor = new Usuario();
        actor.setIdUsuario(99);
        when(requestRepository.findForUpdate(77L)).thenReturn(Optional.of(request));
        when(requestRepository.findById(77L)).thenReturn(Optional.of(request));
        when(sessionRepository.findForUpdate(41L)).thenReturn(Optional.of(session));
        when(userRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local"))
                .thenReturn(Optional.of(actor));
    }

    @Test
    void expiresAtTheExactDeadlineReleasesStockAndCancelsSession() {
        assertTrue(service.expire(77L));

        verify(reservationService).release(request, userRepository
                .findByCorreoAndDeletedAtIsNull("ia-kiments@system.local").orElseThrow(),
                "Solicitud de pago vencida a los cuatro minutos");
        assertTrue(request.getStatus() == CrmWhatsappPaymentRequestStatus.EXPIRED);
        assertTrue(session.getState() == CrmWhatsappAgentState.CANCELADO);
        assertTrue(session.getLastEvent() == CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA);
        verify(outbox).enqueueText(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.contains("reserva de pago venció"));
    }

    @Test
    void doesNotExpireBeforeDeadline() {
        request.setExpiresAt(LocalDateTime.of(2026, 10, 8, 14, 4, 1));

        assertFalse(service.expire(77L));

        verify(reservationService, never()).release(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(sessionRepository, never()).findForUpdate(41L);
    }
}
