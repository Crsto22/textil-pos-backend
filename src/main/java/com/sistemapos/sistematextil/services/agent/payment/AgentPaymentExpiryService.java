package com.sistemapos.sistematextil.services.agent.payment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;

@Service
public class AgentPaymentExpiryService {
    private static final List<CrmWhatsappPaymentRequestStatus> EXPIRABLE = List.of(
            CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE);

    private final CrmWhatsappPaymentRequestRepository requestRepository;
    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final UsuarioRepository userRepository;
    private final CrmWhatsappPaymentReservationService reservationService;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final Clock clock;
    private final AgentOutboxService outbox;

    @Autowired
    public AgentPaymentExpiryService(
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository,
            UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentOutboxService outbox) {
        this(requestRepository, sessionRepository, userRepository, reservationService, stateMachine,
                Clock.systemDefaultZone(), outbox);
    }

    AgentPaymentExpiryService(
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository,
            UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine,
            Clock clock) {
        this(requestRepository, sessionRepository, userRepository, reservationService, stateMachine, clock, null);
    }

    AgentPaymentExpiryService(
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository,
            UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine,
            Clock clock,
            AgentOutboxService outbox) {
        this.requestRepository = requestRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.reservationService = reservationService;
        this.stateMachine = stateMachine;
        this.clock = clock;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<Long> findDueRequestIds(int limit) {
        return requestRepository.findExpiredAgentRequestIds(EXPIRABLE, now(),
                PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long requestId) {
        if (requestId == null) return false;
        LocalDateTime now = now();
        CrmWhatsappPaymentRequest snapshot = requestRepository.findById(requestId).orElse(null);
        if (snapshot == null || snapshot.getAgentSession() == null || !EXPIRABLE.contains(snapshot.getStatus())
                || snapshot.getExpiresAt() == null || snapshot.getExpiresAt().isAfter(now)) {
            return false;
        }
        Long sessionId = snapshot.getAgentSession().getIdAgentSession();
        CrmWhatsappAgentSession session = sessionRepository.findForUpdate(sessionId).orElse(null);
        CrmWhatsappPaymentRequest request = requestRepository.findForUpdate(requestId).orElse(null);
        if (request == null || request.getAgentSession() == null || !EXPIRABLE.contains(request.getStatus())
                || request.getExpiresAt() == null || request.getExpiresAt().isAfter(now)) {
            return false;
        }
        Usuario actor = userRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local")
                .or(() -> userRepository.findByCorreoAndDeletedAtIsNull("sistema@gmail.com"))
                .orElseThrow(() -> new IllegalStateException(
                        "No existe un usuario tecnico para liberar la reserva vencida"));
        reservationService.release(request, actor, "Solicitud de pago vencida a los cuatro minutos");
        request.setStatus(CrmWhatsappPaymentRequestStatus.EXPIRED);
        requestRepository.save(request);

        if (session != null && Boolean.TRUE.equals(session.getActiveSlot())
                && stateMachine.canTransition(session.getState(), CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA)) {
            session.setState(stateMachine.transition(
                    session.getState(), CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA));
            session.setLastEvent(CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA);
            session.setLastActivityAt(now);
            session.setStateChangedAt(now);
            sessionRepository.save(session);
        }
        if (session != null && outbox != null) {
            int ordinal = Math.floorMod(java.util.Objects.hash("PAYMENT_EXPIRED", requestId), Integer.MAX_VALUE);
            outbox.enqueueText(session.getIdAgentSession(), null, null, ordinal,
                    "⏱️ Tu reserva de pago venció y el stock fue liberado.\n\n"
                    + "Si todavía deseas realizar la compra, indícame que deseas generar una nueva reserva.");
        }
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
