package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiFeedback;
import com.sistemapos.sistematextil.model.CrmWhatsappAiFeedbackDecision;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiFeedbackRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiRunRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.MessageResponse;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.SendMessageRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiFeedbackService {

    private static final int MAX_TEXT_LENGTH = 4000;

    private final CrmWhatsappAiRunRepository runRepository;
    private final CrmWhatsappAiFeedbackRepository feedbackRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final UsuarioRepository usuarioRepository;
    private final CrmWhatsappChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional
    public DecisionResponse decide(
            Long conversationId,
            Long runId,
            DecisionRequest request,
            Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappAiRun run = runRepository.findForDecision(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrador de IA no encontrado"));
        CrmWhatsappConversation conversation = run.getConversation();
        if (conversation == null || !conversationId.equals(conversation.getIdConversation())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrador de IA no encontrado");
        }
        requireCanOperate(conversation, actor);

        CrmWhatsappAiFeedback existing = feedbackRepository.findByRun_IdAiRun(runId).orElse(null);
        if (existing != null) return toResponse(existing, null);
        if (run.getOutcome() != CrmWhatsappAiRunOutcome.DRAFT_READY || clean(run.getDraftResponse()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta ejecucion no contiene un borrador enviable");
        }

        String action = clean(request == null ? null : request.action()).toUpperCase(Locale.ROOT);
        if ("DISCARD".equals(action)) {
            CrmWhatsappAiFeedback feedback = baseFeedback(run, conversation, actor);
            feedback.setDecision(CrmWhatsappAiFeedbackDecision.DISCARDED);
            feedback.setOriginalText(run.getDraftResponse());
            feedback.setDiscardReason(truncate(request == null ? null : request.discardReason(), 300));
            feedback.setSendStatus("NOT_SENT");
            return toResponse(feedbackRepository.save(feedback), null);
        }
        if (!"SEND".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accion permitida: SEND o DISCARD");
        }
        if (messageRepository.existsActiveChatMessageAfter(conversationId, run.getMessage().getIdMessage())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El chat cambio desde que se genero el borrador. Genera una respuesta nueva");
        }

        String finalText = clean(request == null ? null : request.text());
        if (finalText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La respuesta no puede estar vacia");
        }
        if (finalText.length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La respuesta supera los 4000 caracteres");
        }

        String original = compact(run.getDraftResponse());
        String normalizedFinal = compact(finalText);
        int distance = levenshtein(original, normalizedFinal);
        int maxLength = Math.max(original.length(), normalizedFinal.length());
        BigDecimal similarity = maxLength == 0
                ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf((1D - ((double) distance / maxLength)) * 100D)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        SuggestedMedia sizeGuide = sizeGuide(run);
        MessageResponse message = sizeGuide == null
                ? chatService.enviarMensaje(
                        conversationId,
                        new SendMessageRequest(finalText, run.getMessage().getIdMessage()),
                        actor)
                : chatService.enviarMediaDesdeStorage(conversationId, sizeGuide.url(), sizeGuide.fileName(),
                        sizeGuide.mimeType(), finalText, run.getMessage().getIdMessage(), actor);
        CrmWhatsappMessage outgoing = messageRepository.findById(message.id())
                .orElseThrow(() -> new IllegalStateException("No se encontro el mensaje enviado"));

        CrmWhatsappAiFeedback feedback = baseFeedback(run, conversation, actor);
        feedback.setDecision(original.equals(normalizedFinal)
                ? CrmWhatsappAiFeedbackDecision.APPROVED
                : CrmWhatsappAiFeedbackDecision.EDITED);
        feedback.setOriginalText(run.getDraftResponse());
        feedback.setFinalText(finalText);
        feedback.setSimilarityPercentage(similarity);
        feedback.setChangedCharacters(distance);
        feedback.setOutgoingMessage(outgoing);
        feedback.setSendStatus("SENT");
        return toResponse(feedbackRepository.save(feedback), message);
    }

    private CrmWhatsappAiFeedback baseFeedback(
            CrmWhatsappAiRun run,
            CrmWhatsappConversation conversation,
            Usuario actor) {
        CrmWhatsappAiFeedback feedback = new CrmWhatsappAiFeedback();
        feedback.setRun(run);
        feedback.setConversation(conversation);
        feedback.setReviewer(actor);
        feedback.setReviewedAt(LocalDateTime.now());
        return feedback;
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

    private void requireCanOperate(CrmWhatsappConversation conversation, Usuario actor) {
        if (conversation.getAssignedUser() == null || "RESUELTO".equals(conversation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La conversacion debe estar atendida");
        }
        boolean admin = actor.getRol() == Rol.ADMINISTRADOR;
        boolean assigned = actor.getIdUsuario().equals(conversation.getAssignedUser().getIdUsuario());
        if (!admin && !assigned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes operar esta conversacion");
        }
        if (conversation.getConnection() != null && conversation.getConnection().getEmpresa() != null
                && actor.getSucursal() != null && actor.getSucursal().getEmpresa() != null
                && !actor.getSucursal().getEmpresa().getIdEmpresa().equals(
                        conversation.getConnection().getEmpresa().getIdEmpresa())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La conversacion pertenece a otra empresa");
        }
    }

    private DecisionResponse toResponse(CrmWhatsappAiFeedback feedback, MessageResponse message) {
        return new DecisionResponse(
                feedback.getRun().getIdAiRun(),
                feedback.getDecision().name(),
                feedback.getFinalText(),
                feedback.getSimilarityPercentage(),
                feedback.getChangedCharacters(),
                feedback.getDiscardReason(),
                feedback.getSendStatus(),
                feedback.getReviewer().getIdUsuario(),
                feedback.getReviewedAt(),
                message);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private String compact(String value) {
        return clean(value).replaceAll("\\s+", " ");
    }

    private String truncate(String value, int max) {
        String cleaned = clean(value);
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private SuggestedMedia sizeGuide(CrmWhatsappAiRun run) {
        if (!"GUIA_TALLAS".equals(clean(run.getIntent())) || clean(run.getSuggestedMediaJson()).isBlank()) return null;
        try {
            List<Map<String, Object>> values = objectMapper.readValue(run.getSuggestedMediaJson(), new TypeReference<>() {});
            return values.stream()
                    .filter(item -> "SIZE_GUIDE".equals(clean(String.valueOf(item.get("type")))))
                    .map(item -> item.get("url") == null ? "" : clean(String.valueOf(item.get("url"))))
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .map(value -> new SuggestedMedia(value, fileName(value), mimeType(value)))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String mimeType(String reference) {
        String value = reference.toLowerCase(Locale.ROOT).replaceAll("[?#].*$", "");
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) return "image/jpeg";
        return "image/webp";
    }

    private String fileName(String reference) {
        String value = reference.replaceAll("[?#].*$", "");
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return name.isBlank() ? "guia-tallas.webp" : name;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record DecisionRequest(String action, String text, String discardReason) {}

    public record DecisionResponse(
            Long idRun,
            String decision,
            String finalText,
            BigDecimal similarityPercentage,
            Integer changedCharacters,
            String discardReason,
            String sendStatus,
            Integer reviewerId,
            LocalDateTime reviewedAt,
            MessageResponse message) {}
    private record SuggestedMedia(String url, String fileName, String mimeType) {}
}
