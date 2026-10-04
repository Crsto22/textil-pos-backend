package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJob;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMemory;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiRunRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiMemoryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiJobService.DraftRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

class CrmWhatsappAiJobServiceTest {

    private final CrmWhatsappAiJobRepository jobs = mock(CrmWhatsappAiJobRepository.class);
    private final CrmWhatsappAiRunRepository runs = mock(CrmWhatsappAiRunRepository.class);
    private final CrmWhatsappAiConfigRepository configs = mock(CrmWhatsappAiConfigRepository.class);
    private final CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
    private final CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final CrmWhatsappAiSaleDraftService saleDrafts = mock(CrmWhatsappAiSaleDraftService.class);
    private final CrmWhatsappAiOperationsService operations = mock(CrmWhatsappAiOperationsService.class);
    private final CrmWhatsappAiAuditService audit = mock(CrmWhatsappAiAuditService.class);
    private final CrmWhatsappAiMemoryRepository memories = mock(CrmWhatsappAiMemoryRepository.class);
    private final CrmWhatsappEventService events = mock(CrmWhatsappEventService.class);
    private final CrmWhatsappConnectionStateService connectionState = mock(CrmWhatsappConnectionStateService.class);
    private final CrmWhatsappAiJobService service = new CrmWhatsappAiJobService(
            jobs, runs, configs, conversations, messages, users, saleDrafts, operations, audit, memories, events,
            connectionState);

    @BeforeEach
    void setupOperations() {
        when(operations.automaticAllowedForConversation(any(), any())).thenReturn(true);
        when(operations.manualAllowed(any(), any())).thenReturn(true);
        when(connectionState.isOperational(any())).thenReturn(true);
    }

    @Test
    void noEncolaCuandoLaIaEstaDesactivada() {
        CrmWhatsappMessage message = incomingMessage();
        CrmWhatsappAiConfig config = config(CrmWhatsappAiMode.DESACTIVADA);
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));

        assertNull(service.enqueueAutomatic(message));

        verifyNoInteractions(runs);
    }

    @Test
    void sugerenciasNoEstaDisponibleComoAtencionAutomatica() {
        CrmWhatsappConversation conversation = incomingMessage().getConversation();
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(config(CrmWhatsappAiMode.SUGERENCIAS)));

        assertTrue(service.isAutomaticModeDisabled(conversation));
    }

    @Test
    void noEncolaCuandoElClientePrefiereAtencionHumana() {
        CrmWhatsappMessage message = incomingMessage();
        message.getConversation().setAiAttentionMode(CrmWhatsappAiAttentionMode.HUMANA);

        assertNull(service.enqueueAutomatic(message));

        verifyNoInteractions(configs, jobs, runs);
    }

    @Test
    void encolaUnaVezYSuperaElPendienteAnterior() {
        CrmWhatsappMessage message = incomingMessage();
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config(CrmWhatsappAiMode.AUTOMATICA)));
        when(jobs.findByMessage_IdMessage(20L)).thenReturn(Optional.empty());
        when(jobs.save(any(CrmWhatsappAiJob.class))).thenAnswer(invocation -> {
            CrmWhatsappAiJob saved = invocation.getArgument(0);
            saved.setIdAiJob(30L);
            return saved;
        });

        CrmWhatsappAiJob queued = service.enqueueAutomatic(message);

        assertEquals(CrmWhatsappAiJobStatus.PENDING, queued.getStatus());
        assertEquals("AUTOMATIC", queued.getTriggerType());
        verify(jobs).supersedePending(eq(10L), eq(CrmWhatsappAiJobStatus.PENDING),
                eq(CrmWhatsappAiJobStatus.SUPERSEDED), any());
        verify(jobs).save(any(CrmWhatsappAiJob.class));
    }

    @Test
    void reutilizaTrabajoExistenteParaElMismoMensaje() {
        CrmWhatsappMessage message = incomingMessage();
        CrmWhatsappAiJob existing = new CrmWhatsappAiJob();
        existing.setIdAiJob(99L);
        existing.setMessage(message);
        existing.setConversation(message.getConversation());
        existing.setStatus(CrmWhatsappAiJobStatus.PENDING);
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config(CrmWhatsappAiMode.AUTOMATICA)));
        when(jobs.findByMessage_IdMessage(20L)).thenReturn(Optional.of(existing));

        assertEquals(99L, service.enqueueAutomatic(message).getIdAiJob());
    }

    @Test
    void noConsumeIaYPublicaAlertaCuandoAlcanzaElMaximoDeRespuestas() {
        CrmWhatsappMessage message = incomingMessage();
        CrmWhatsappAiConfig config = config(CrmWhatsappAiMode.AUTOMATICA);
        config.setMaxRespuestasAutomaticas(20);
        CrmWhatsappAiMemory memory = new CrmWhatsappAiMemory();
        memory.setConsecutiveAutoResponses(20);
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(memories.findByConversation_IdConversation(10L)).thenReturn(Optional.of(memory));

        assertNull(service.enqueueAutomatic(message));

        verify(events).publishAfterCommit(
                org.mockito.ArgumentMatchers.argThat((java.util.Map<String, Object> event) ->
                        "ai.limit.reached".equals(event.get("type"))),
                org.mockito.ArgumentMatchers.anyMap(), eq(null), eq(true));
        verifyNoInteractions(jobs, runs);
    }

    @Test
    void regeneraUnTrabajoCompletadoSinCrearDuplicado() {
        CrmWhatsappMessage message = incomingMessage();
        Usuario actor = new Usuario();
        actor.setIdUsuario(5);
        actor.setEstado("ACTIVO");
        actor.setRol(Rol.ADMINISTRADOR);
        actor.setAccesoCrm(true);
        message.getConversation().setAssignedUser(actor);
        message.getConversation().setStatus("ATENDIDO");
        CrmWhatsappAiJob existing = new CrmWhatsappAiJob();
        existing.setIdAiJob(99L);
        existing.setMessage(message);
        existing.setConversation(message.getConversation());
        existing.setStatus(CrmWhatsappAiJobStatus.COMPLETED);
        existing.setAttempts(2);
        when(users.findByIdUsuarioAndDeletedAtIsNull(5)).thenReturn(Optional.of(actor));
        when(conversations.findById(10L)).thenReturn(Optional.of(message.getConversation()));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config(CrmWhatsappAiMode.SUGERENCIAS)));
        when(messages.findById(20L)).thenReturn(Optional.of(message));
        when(jobs.findByMessage_IdMessage(20L)).thenReturn(Optional.of(existing));
        when(jobs.save(existing)).thenReturn(existing);

        var response = service.enqueueManual(10L, new DraftRequest(20L, true), actor);

        assertEquals(99L, response.idJob());
        assertEquals(CrmWhatsappAiJobStatus.PENDING, existing.getStatus());
        assertEquals(0, existing.getAttempts());
        verify(jobs).save(existing);
    }

    private CrmWhatsappMessage incomingMessage() {
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setConnection(connection);
        conversation.setStatus("ESPERA");
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(20L);
        message.setConversation(conversation);
        message.setDirection("INCOMING");
        message.setBody("Hola");
        return message;
    }

    private CrmWhatsappAiConfig config(CrmWhatsappAiMode mode) {
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setModo(mode);
        config.setEsperaRespuestaSegundos(5);
        return config;
    }
}
