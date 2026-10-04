package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJob;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMemory;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiFeedback;
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
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiJobService {
    private static final String AUTOMATIC_RESPONSE_LIMIT_REASON =
            "Se alcanzo el limite maximo de respuestas automaticas para este chat";

    private final CrmWhatsappAiJobRepository jobRepository;
    private final CrmWhatsappAiRunRepository runRepository;
    private final CrmWhatsappAiConfigRepository configRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final UsuarioRepository usuarioRepository;
    private final CrmWhatsappAiSaleDraftService saleDraftService;
    private final CrmWhatsappAiOperationsService operationsService;
    private final CrmWhatsappAiAuditService auditService;
    private final CrmWhatsappAiMemoryRepository memoryRepository;
    private final CrmWhatsappEventService eventService;
    private final CrmWhatsappConnectionStateService connectionStateService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional
    public CrmWhatsappAiJob enqueueAutomatic(CrmWhatsappMessage message) {
        if (!isIncomingActive(message)) return null;
        CrmWhatsappConversation conversation = message.getConversation();
        if (conversation == null || conversation.getConnection() == null || conversation.getAssignedUser() != null) return null;
        if (!connectionStateService.isOperational(conversation.getConnection())) return null;
        if (conversation.getAiAttentionMode() == CrmWhatsappAiAttentionMode.HUMANA) return null;
        if (saleDraftService.blocksAutomation(conversation.getIdConversation())) return null;
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(conversation.getConnection().getIdConnection())
                .orElse(null);
        if (config == null || config.getModo() != CrmWhatsappAiMode.AUTOMATICA) return null;
        if (automaticResponseLimitReached(config, conversation)) {
            Map<String, Object> event = Map.of(
                    "type", "ai.limit.reached",
                    "conversationId", conversation.getIdConversation(),
                    "reason", AUTOMATIC_RESPONSE_LIMIT_REASON,
                    "maxResponses", config.getMaxRespuestasAutomaticas());
            eventService.publishAfterCommit(event, event, null, true);
            return null;
        }
        if (!operationsService.automaticAllowedForConversation(config, conversation)) {
            var snapshot = operationsService.snapshot(config);
            if (snapshot.limited() || CrmWhatsappAiOperationsService.EMERGENCY_STOP.equals(snapshot.status())) {
                auditService.record(conversation.getConnection(), conversation, null, null, "LIMIT_BLOCKED", "WARN",
                        "Procesamiento automatico bloqueado por control operativo", Map.of("status", snapshot.status()));
            }
            return null;
        }
        return enqueue(message, conversation, "AUTOMATIC", config.getEsperaRespuestaSegundos());
    }

    @Transactional(readOnly = true)
    public AutomaticAvailability automaticAvailability(CrmWhatsappConversation conversation) {
        if (conversation == null || conversation.getConnection() == null) {
            return new AutomaticAvailability(false, "La conversacion no tiene una conexion configurada");
        }
        if (!connectionStateService.isOperational(conversation.getConnection())) {
            return new AutomaticAvailability(false, connectionStateService.toResponse(conversation.getConnection()).blockedReason());
        }
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(conversation.getConnection().getIdConnection())
                .orElse(null);
        if (config == null || config.getModo() == CrmWhatsappAiMode.DESACTIVADA) {
            return new AutomaticAvailability(false, "La IA global esta desactivada");
        }
        if (config.getModo() != CrmWhatsappAiMode.AUTOMATICA) {
            return new AutomaticAvailability(false, "La conexion esta configurada solo para sugerencias");
        }
        if (saleDraftService.blocksAutomation(conversation.getIdConversation())) {
            return new AutomaticAvailability(false, "Existe un pedido que requiere atencion humana");
        }
        if (automaticResponseLimitReached(config, conversation)) {
            return new AutomaticAvailability(false, AUTOMATIC_RESPONSE_LIMIT_REASON);
        }
        if (!operationsService.automaticAllowedForConversation(config, conversation)) {
            var snapshot = operationsService.snapshot(config);
            String reason = CrmWhatsappAiOperationsService.EMERGENCY_STOP.equals(snapshot.status())
                    ? "La IA fue detenida por un administrador"
                    : snapshot.limited()
                            ? "La IA esta limitada por consumo o presupuesto"
                            : "Esta conversacion no esta incluida en el despliegue automatico";
            return new AutomaticAvailability(false, reason);
        }
        return new AutomaticAvailability(true, "");
    }

    @Transactional(readOnly = true)
    public boolean isAutomaticModeDisabled(CrmWhatsappConversation conversation) {
        if (conversation == null || conversation.getConnection() == null) return true;
        return configRepository.findByConnection_IdConnection(conversation.getConnection().getIdConnection())
                .map(config -> config.getModo() != CrmWhatsappAiMode.AUTOMATICA)
                .orElse(true);
    }

    private boolean automaticResponseLimitReached(
            CrmWhatsappAiConfig config,
            CrmWhatsappConversation conversation) {
        if (config == null || config.getMaxRespuestasAutomaticas() == null
                || conversation == null || conversation.getIdConversation() == null) return false;
        CrmWhatsappAiMemory memory = memoryRepository
                .findByConversation_IdConversation(conversation.getIdConversation()).orElse(null);
        return memory != null && memory.getConsecutiveAutoResponses() != null
                && memory.getConsecutiveAutoResponses() >= config.getMaxRespuestasAutomaticas();
    }

    @Transactional
    public JobResponse enqueueManual(Long conversationId, DraftRequest request, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        requireCanRead(conversation, actor);
        if (conversation.getConnection() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La conversacion no tiene conexion configurada");
        }
        if (!connectionStateService.isOperational(conversation.getConnection())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    connectionStateService.toResponse(conversation.getConnection()).blockedReason());
        }
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(conversation.getConnection().getIdConnection())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configure la IA de esta conexion"));
        if (config.getModo() == CrmWhatsappAiMode.DESACTIVADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La IA esta desactivada");
        }
        if (!operationsService.manualAllowed(config, conversationId)) {
            String message = CrmWhatsappAiOperationsService.EMERGENCY_STOP.equals(config.getOperationalStatus())
                    ? "La IA esta detenida por un administrador"
                    : "Se alcanzo el limite temporal de sugerencias para este chat";
            auditService.record(conversation.getConnection(), conversation, null, actor, "RATE_LIMIT_BLOCKED", "WARN",
                    message, Map.of("trigger", "MANUAL"));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
        CrmWhatsappMessage message = resolveIncomingMessage(conversationId, request == null ? null : request.messageId());
        CrmWhatsappAiJob job = enqueue(message, conversation, "MANUAL", 0,
                request != null && Boolean.TRUE.equals(request.regenerate()));
        return toJobResponse(job);
    }

    @Transactional(readOnly = true)
    public RunsResponse listRuns(Long conversationId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        requireCanRead(conversation, actor);
        CrmWhatsappAiConfig config = conversation.getConnection() == null ? null : configRepository
                .findByConnection_IdConnection(conversation.getConnection().getIdConnection())
                .orElse(null);
        List<RunResponse> runs = runRepository.findTop20ByConversation_IdConversationOrderByCreatedAtDesc(conversationId).stream()
                .map(this::toRunResponse)
                .toList();
        String mode = config == null ? CrmWhatsappAiMode.DESACTIVADA.name() : config.getModo().name();
        boolean canGenerate = config != null
                && config.getModo() != CrmWhatsappAiMode.DESACTIVADA
                && !CrmWhatsappAiOperationsService.EMERGENCY_STOP.equals(config.getOperationalStatus())
                && connectionStateService.isOperational(conversation.getConnection())
                && conversation.getAssignedUser() != null
                && !"RESUELTO".equals(conversation.getStatus());
        return new RunsResponse(mode, canGenerate, runs);
    }

    private CrmWhatsappAiJob enqueue(
            CrmWhatsappMessage message,
            CrmWhatsappConversation conversation,
            String trigger,
            Integer delaySeconds) {
        return enqueue(message, conversation, trigger, delaySeconds, false);
    }

    private CrmWhatsappAiJob enqueue(
            CrmWhatsappMessage message,
            CrmWhatsappConversation conversation,
            String trigger,
            Integer delaySeconds,
            boolean regenerate) {
        CrmWhatsappAiJob existing = jobRepository.findByMessage_IdMessage(message.getIdMessage()).orElse(null);
        if (existing != null) {
            if ("MANUAL".equals(trigger)
                    && existing.getStatus() != CrmWhatsappAiJobStatus.PENDING
                    && existing.getStatus() != CrmWhatsappAiJobStatus.PROCESSING
                    && (regenerate
                        || existing.getStatus() == CrmWhatsappAiJobStatus.FAILED
                        || existing.getStatus() == CrmWhatsappAiJobStatus.SKIPPED
                        || existing.getStatus() == CrmWhatsappAiJobStatus.SUPERSEDED)) {
                existing.setStatus(CrmWhatsappAiJobStatus.PENDING);
                existing.setTriggerType("MANUAL");
                existing.setAttempts(0);
                existing.setAvailableAt(LocalDateTime.now());
                existing.setLockedAt(null);
                existing.setProcessedAt(null);
                existing.setLastError(null);
                return jobRepository.save(existing);
            }
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        jobRepository.supersedePending(
                conversation.getIdConversation(),
                CrmWhatsappAiJobStatus.PENDING,
                CrmWhatsappAiJobStatus.SUPERSEDED,
                now);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setMessage(message);
        job.setConversation(conversation);
        job.setStatus(CrmWhatsappAiJobStatus.PENDING);
        job.setTriggerType(trigger);
        job.setAttempts(0);
        job.setMaxAttempts(3);
        job.setAvailableAt(now.plusSeconds(Math.max(0, delaySeconds == null ? 0 : delaySeconds)));
        try {
            return jobRepository.save(job);
        } catch (DataIntegrityViolationException duplicate) {
            return jobRepository.findByMessage_IdMessage(message.getIdMessage()).orElseThrow(() -> duplicate);
        }
    }

    private CrmWhatsappMessage resolveIncomingMessage(Long conversationId, Long requestedMessageId) {
        if (requestedMessageId != null) {
            CrmWhatsappMessage message = messageRepository.findById(requestedMessageId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
            if (!conversationId.equals(message.getConversation().getIdConversation()) || !isIncomingActive(message)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione un mensaje entrante activo");
            }
            return message;
        }
        return messageRepository.findRecentIncomingMessages(conversationId, PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La conversacion no tiene mensajes entrantes"));
    }

    private boolean isIncomingActive(CrmWhatsappMessage message) {
        return message != null && message.getIdMessage() != null
                && "INCOMING".equals(message.getDirection()) && message.getDeletedAt() == null;
    }

    private Usuario requireCrmUser(Usuario sessionUser) {
        if (sessionUser == null || sessionUser.getIdUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        Usuario actor = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(sessionUser.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado"));
        if (!"ACTIVO".equalsIgnoreCase(clean(actor.getEstado()))
                || (actor.getRol() != Rol.ADMINISTRADOR && !Boolean.TRUE.equals(actor.getAccesoCrm()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso al CRM");
        }
        return actor;
    }

    private void requireCanRead(CrmWhatsappConversation conversation, Usuario actor) {
        boolean admin = actor.getRol() == Rol.ADMINISTRADOR;
        boolean assigned = conversation.getAssignedUser() != null
                && actor.getIdUsuario().equals(conversation.getAssignedUser().getIdUsuario());
        boolean sharedUnassigned = conversation.getAssignedUser() == null
                && !"RESUELTO".equals(conversation.getStatus());
        if (!admin && !assigned && !sharedUnassigned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes ver esta conversacion");
        }
        if (conversation.getConnection() != null && actor.getSucursal() != null
                && actor.getSucursal().getEmpresa() != null
                && !actor.getSucursal().getEmpresa().getIdEmpresa().equals(
                        conversation.getConnection().getEmpresa().getIdEmpresa())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La conversacion pertenece a otra empresa");
        }
    }

    private JobResponse toJobResponse(CrmWhatsappAiJob job) {
        return new JobResponse(job.getIdAiJob(), job.getMessage().getIdMessage(), job.getStatus().name(), job.getAvailableAt());
    }

    private RunResponse toRunResponse(CrmWhatsappAiRun run) {
        return new RunResponse(
                run.getIdAiRun(), run.getMessage().getIdMessage(), run.getOutcome().name(), run.getIntent(),
                run.getConfidence(), Boolean.TRUE.equals(run.getRequiresHuman()), run.getReason(),
                run.getDraftResponse(), readList(run.getEvidenceJson()), readList(run.getSuggestedMediaJson()),
                toFeedback(run.getFeedback()), run.getJob().getIdAiJob(), run.getJob().getStatus().name(),
                run.getProvider(), run.getModel(), run.getInputTokens(),
                run.getOutputTokens(), run.getTotalTokens(), run.getLatencyMs(), run.getCreatedAt());
    }

    private List<Map<String, Object>> readList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private FeedbackResponse toFeedback(CrmWhatsappAiFeedback feedback) {
        if (feedback == null) return null;
        return new FeedbackResponse(
                feedback.getDecision().name(),
                feedback.getFinalText(),
                feedback.getSimilarityPercentage(),
                feedback.getChangedCharacters(),
                feedback.getDiscardReason(),
                feedback.getSendStatus(),
                feedback.getReviewer().getIdUsuario(),
                feedback.getReviewedAt(),
                feedback.getOutgoingMessage() == null ? null : feedback.getOutgoingMessage().getIdMessage());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record DraftRequest(Long messageId, Boolean regenerate) {}
    public record AutomaticAvailability(boolean available, String reason) {}
    public record JobResponse(Long idJob, Long idMessage, String status, LocalDateTime availableAt) {}
    public record RunsResponse(String mode, boolean canGenerate, List<RunResponse> content) {}
    public record RunResponse(
            Long idRun, Long idMessage, String outcome, String intent, Integer confidence,
            boolean requiresHuman, String reason, String draft, List<Map<String, Object>> evidence,
            List<Map<String, Object>> suggestedMedia, FeedbackResponse feedback, Long idJob,
            String jobStatus, String provider, String model,
            Integer inputTokens, Integer outputTokens, Integer totalTokens, Long latencyMs,
            LocalDateTime createdAt) {}
    public record FeedbackResponse(
            String decision, String finalText, java.math.BigDecimal similarityPercentage,
            Integer changedCharacters, String discardReason, String sendStatus,
            Integer reviewerId, LocalDateTime reviewedAt, Long outgoingMessageId) {}
}
