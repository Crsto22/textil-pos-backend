package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiFeedback;
import com.sistemapos.sistematextil.model.CrmWhatsappAiFeedbackDecision;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiFeedbackRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiRunRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiFeedbackService.DecisionRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.MessageResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

class CrmWhatsappAiFeedbackServiceTest {

    private final CrmWhatsappAiRunRepository runs = mock(CrmWhatsappAiRunRepository.class);
    private final CrmWhatsappAiFeedbackRepository feedback = mock(CrmWhatsappAiFeedbackRepository.class);
    private final CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final CrmWhatsappChatService chat = mock(CrmWhatsappChatService.class);
    private final CrmWhatsappAiFeedbackService service = new CrmWhatsappAiFeedbackService(
            runs, feedback, messages, users, chat);

    private Usuario actor;
    private CrmWhatsappAiRun run;

    @BeforeEach
    void setup() {
        actor = actor();
        run = run(actor);
        when(users.findByIdUsuarioAndDeletedAtIsNull(5)).thenReturn(Optional.of(actor));
        when(runs.findForDecision(70L)).thenReturn(Optional.of(run));
        when(feedback.findByRun_IdAiRun(70L)).thenReturn(Optional.empty());
        when(feedback.save(any(CrmWhatsappAiFeedback.class))).thenAnswer(invocation -> {
            CrmWhatsappAiFeedback saved = invocation.getArgument(0);
            saved.setIdAiFeedback(90L);
            return saved;
        });
    }

    @Test
    void apruebaYAsociaElMensajeEnviado() {
        CrmWhatsappMessage outgoing = outgoing();
        when(messages.existsActiveChatMessageAfter(10L, 20L)).thenReturn(false);
        when(chat.enviarMensaje(eq(10L), any(), eq(actor))).thenReturn(messageResponse(outgoing));
        when(messages.findById(80L)).thenReturn(Optional.of(outgoing));

        var response = service.decide(10L, 70L,
                new DecisionRequest("SEND", "Hola, tenemos stock.", null), actor);

        assertEquals("APPROVED", response.decision());
        assertEquals(100, response.similarityPercentage().intValue());
        assertEquals(80L, response.message().id());
        verify(feedback).save(any(CrmWhatsappAiFeedback.class));
    }

    @Test
    void registraLaCorreccionDelAsesor() {
        CrmWhatsappMessage outgoing = outgoing();
        when(messages.existsActiveChatMessageAfter(10L, 20L)).thenReturn(false);
        when(chat.enviarMensaje(eq(10L), any(), eq(actor))).thenReturn(messageResponse(outgoing));
        when(messages.findById(80L)).thenReturn(Optional.of(outgoing));

        var response = service.decide(10L, 70L,
                new DecisionRequest("SEND", "Hola, tenemos dos unidades.", null), actor);

        assertEquals("EDITED", response.decision());
        verify(feedback).save(any(CrmWhatsappAiFeedback.class));
    }

    @Test
    void descartaSinEnviarMensaje() {
        var response = service.decide(10L, 70L,
                new DecisionRequest("DISCARD", null, "No corresponde"), actor);

        assertEquals("DISCARDED", response.decision());
        assertNull(response.message());
        verify(chat, never()).enviarMensaje(any(), any(), any());
    }

    @Test
    void bloqueaUnBorradorCuandoElChatCambio() {
        when(messages.existsActiveChatMessageAfter(10L, 20L)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.decide(
                10L, 70L, new DecisionRequest("SEND", "Hola, tenemos stock.", null), actor));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(chat, never()).enviarMensaje(any(), any(), any());
    }

    @Test
    void unaDecisionRepetidaNoVuelveAEnviar() {
        CrmWhatsappAiFeedback existing = new CrmWhatsappAiFeedback();
        existing.setRun(run);
        existing.setConversation(run.getConversation());
        existing.setReviewer(actor);
        existing.setDecision(CrmWhatsappAiFeedbackDecision.APPROVED);
        existing.setOriginalText(run.getDraftResponse());
        existing.setFinalText(run.getDraftResponse());
        existing.setSendStatus("SENT");
        existing.setReviewedAt(LocalDateTime.now());
        when(feedback.findByRun_IdAiRun(70L)).thenReturn(Optional.of(existing));

        var response = service.decide(10L, 70L,
                new DecisionRequest("SEND", "Hola, tenemos stock.", null), actor);

        assertEquals("APPROVED", response.decision());
        verify(chat, never()).enviarMensaje(any(), any(), any());
    }

    private Usuario actor() {
        Empresa company = new Empresa();
        company.setIdEmpresa(1);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        branch.setEmpresa(company);
        Usuario user = new Usuario();
        user.setIdUsuario(5);
        user.setNombre("Ana");
        user.setApellido("Perez");
        user.setEstado("ACTIVO");
        user.setRol(Rol.ADMINISTRADOR);
        user.setAccesoCrm(true);
        user.setSucursal(branch);
        return user;
    }

    private CrmWhatsappAiRun run(Usuario assigned) {
        Empresa company = assigned.getSucursal().getEmpresa();
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        connection.setEmpresa(company);
        connection.setSucursal(assigned.getSucursal());
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setConnection(connection);
        conversation.setAssignedUser(assigned);
        conversation.setStatus("ATENDIDO");
        CrmWhatsappMessage incoming = new CrmWhatsappMessage();
        incoming.setIdMessage(20L);
        incoming.setConversation(conversation);
        incoming.setDirection("INCOMING");
        CrmWhatsappAiRun value = new CrmWhatsappAiRun();
        value.setIdAiRun(70L);
        value.setConversation(conversation);
        value.setMessage(incoming);
        value.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        value.setDraftResponse("Hola, tenemos stock.");
        return value;
    }

    private CrmWhatsappMessage outgoing() {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(80L);
        message.setConversation(run.getConversation());
        message.setDirection("OUTGOING");
        message.setMessageType("TEXT");
        message.setBody("Hola, tenemos stock.");
        message.setMessageStatus("sent");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private MessageResponse messageResponse(CrmWhatsappMessage message) {
        return new MessageResponse(
                message.getIdMessage(), "OUTGOING", "HUMAN", "TEXT", message.getBody(), "sent",
                message.getCreatedAt(), null, null, null, null, null, false);
    }
}
