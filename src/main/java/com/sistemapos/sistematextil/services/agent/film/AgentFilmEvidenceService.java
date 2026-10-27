package com.sistemapos.sistematextil.services.agent.film;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistemapos.sistematextil.model.CrmWhatsappFilmAddon;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService;
import com.sistemapos.sistematextil.services.S3StorageService;

@Service
public class AgentFilmEvidenceService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<CrmWhatsappFilmStatus> REVIEWABLE_STATUSES = Set.of(
            CrmWhatsappFilmStatus.AWAITING_PAYMENT,
            CrmWhatsappFilmStatus.PAYMENT_OVERDUE,
            CrmWhatsappFilmStatus.PAID_PENDING_REVIEW);
    static final String MANUAL_REVIEW_MESSAGE = "✅ Recibimos tu comprobante del pago de S/2 por el film "
            + "protector para embalaje. No te preocupes: el área de envíos se encargará de revisarlo "
            + "y continuará con la coordinación de tu pedido.";

    private final CrmWhatsappFilmAddonRepository addons;
    private final CrmWhatsappFilmEvidenceRepository evidence;
    private final CrmWhatsappMessageRepository messages;
    private final CrmWhatsappAgentSessionRepository sessions;
    private final S3StorageService storage;
    private final AgentFilmEscalationService escalation;
    private final ObjectProvider<CrmWhatsappChatService> chatServiceProvider;
    private final TransactionTemplate transactions;

    public AgentFilmEvidenceService(
            CrmWhatsappFilmAddonRepository addons,
            CrmWhatsappFilmEvidenceRepository evidence,
            CrmWhatsappMessageRepository messages,
            CrmWhatsappAgentSessionRepository sessions,
            S3StorageService storage,
            AgentFilmEscalationService escalation,
            ObjectProvider<CrmWhatsappChatService> chatServiceProvider,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.addons = addons;
        this.evidence = evidence;
        this.messages = messages;
        this.sessions = sessions;
        this.storage = storage;
        this.escalation = escalation;
        this.chatServiceProvider = chatServiceProvider;
        this.transactions = new TransactionTemplate(manager);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerIncomingMedia(Long messageId) {
        if (messageId == null) return false;
        if (evidence.findByMessage_IdMessage(messageId).isPresent()) return true;
        CrmWhatsappMessage message = messages.findForUpdate(messageId).orElse(null);
        if (message == null || !"INCOMING".equals(message.getDirection()) || message.getDeletedAt() != null
                || !supported(message) || message.getConversation() == null) return false;
        Long conversationId = message.getConversation().getIdConversation();
        CrmWhatsappFilmAddon addon = findReviewableAddon(conversationId);
        if (addon == null) return false;
        byte[] bytes = storage.readBytes(message.getMediaStoragePath());
        if (bytes.length == 0 || bytes.length > MAX_BYTES) return false;
        String stored = storage.upload(bytes,
                "crm/film-evidence/" + conversationId + "/" + UUID.randomUUID()
                        + extension(message.getMediaMimeType()),
                message.getMediaMimeType());
        CrmWhatsappFilmEvidence item = new CrmWhatsappFilmEvidence();
        item.setFilmAddon(addon);
        item.setMessage(message);
        item.setStoragePath(stored);
        item.setMimeType(message.getMediaMimeType());
        item.setFileName(message.getMediaFileName());
        item.setSha256(hash(bytes));
        item.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE);
        item.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
        item.setWarningsJson("[\"Pendiente de revisión manual por el área de envíos\"]");
        try {
            evidence.save(item);
            markForManualReview(addon, conversationId);
            return true;
        } catch (RuntimeException error) {
            storage.deleteByKey(stored);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public List<Long> readyIds(int limit) {
        return evidence.findReadyIds(CrmWhatsappPaymentProcessingStatus.QUEUED,
                LocalDateTime.now(), PageRequest.of(0, limit));
    }

    /**
     * Compatibilidad con evidencias que quedaron en cola antes del despliegue:
     * también pasan a revisión humana y nunca se envían a Gemini Vision.
     */
    public void process(Long evidenceId) {
        transactions.executeWithoutResult(status -> moveQueuedEvidenceToManualReview(evidenceId));
    }

    private CrmWhatsappFilmAddon findReviewableAddon(Long conversationId) {
        Long sessionId = activeSessionId(conversationId);
        CrmWhatsappFilmAddon active = addons.findByAgentSession_IdAgentSession(sessionId)
                .filter(value -> REVIEWABLE_STATUSES.contains(value.getStatus()))
                .orElse(null);
        CrmWhatsappFilmAddon candidate = active != null ? active
                : addons.findPendingForConversation(conversationId, REVIEWABLE_STATUSES, PageRequest.of(0, 1))
                        .stream().findFirst().orElse(null);
        if (candidate == null) return null;
        return addons.findForUpdate(candidate.getIdFilmAddon())
                .filter(value -> REVIEWABLE_STATUSES.contains(value.getStatus()))
                .orElse(null);
    }

    private void markForManualReview(CrmWhatsappFilmAddon addon, Long conversationId) {
        addon.setStatus(CrmWhatsappFilmStatus.PAID_PENDING_REVIEW);
        addon.setExpiresAt(null);
        addon.setResolvedAt(null);
        addon.setResolutionSource(null);
        addon.getFulfillment().setStatus(CrmWhatsappFulfillmentStatus.FILM_PAYMENT_PENDING);
        addons.save(addon);
        escalation.escalate(addon.getAgentSession().getIdAgentSession(), addon.getIdFilmAddon(),
                "Comprobante del pago de S/2 por film para embalaje pendiente de revisión del área de envíos");
        notifyAfterCommit(conversationId, MANUAL_REVIEW_MESSAGE);
    }

    private void moveQueuedEvidenceToManualReview(Long evidenceId) {
        CrmWhatsappFilmEvidence queued = evidence.findForUpdate(evidenceId).orElse(null);
        if (queued == null || queued.getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.QUEUED) return;
        CrmWhatsappFilmAddon addon = addons.findForUpdate(queued.getFilmAddon().getIdFilmAddon()).orElse(null);
        if (addon == null || !REVIEWABLE_STATUSES.contains(addon.getStatus())) return;
        queued.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE);
        queued.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
        queued.setWarningsJson("[\"Pendiente de revisión manual por el área de envíos\"]");
        queued.setLastError(null);
        evidence.save(queued);
        markForManualReview(addon, queued.getMessage().getConversation().getIdConversation());
    }

    private void notifyAfterCommit(Long conversationId, String text) {
        Runnable notification = () -> {
            try {
                chatServiceProvider.getObject().enviarAvisoSistemaAutomatico(conversationId, text);
            } catch (RuntimeException ignored) {
                // La evidencia permanece visible para la asesora aunque WhatsApp no esté disponible.
            }
        };
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            notification.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notification.run();
            }
        });
    }

    static List<String> validateFilmAmount(CrmWhatsappFilmEvidence item) {
        if (item.getDetectedAmount() != null
                && item.getDetectedAmount().compareTo(AgentFilmService.PRICE) == 0) return List.of();
        return List.of("el monto debe ser exactamente S/2.00");
    }

    private Long activeSessionId(Long conversationId) {
        return sessions.findByConversation_IdConversationAndActiveSlotTrue(conversationId)
                .map(session -> session.getIdAgentSession()).orElse(-1L);
    }

    private boolean supported(CrmWhatsappMessage message) {
        String mime = clean(message.getMediaMimeType()).toLowerCase();
        return message.getMediaStoragePath() != null
                && (mime.equals("image/jpeg") || mime.equals("image/png")
                        || mime.equals("image/webp") || mime.equals("application/pdf"));
    }

    private String extension(String mime) {
        return switch (clean(mime).toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
