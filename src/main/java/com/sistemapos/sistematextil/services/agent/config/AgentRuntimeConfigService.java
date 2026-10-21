package com.sistemapos.sistematextil.services.agent.config;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentRuntimeConfigService {
    public static final String DEFAULT_AGENT_BEHAVIOR_INSTRUCTIONS = """
            Habla como una asesora de ventas femenina, cálida, amable y cercana.
            Puedes llamar a la clienta “bella”, “hermosa” o “linda” de manera moderada y natural, sin repetirlo en cada mensaje.
            Responde en español, de forma breve, clara y profesional, con pocos emojis.
            Acompaña a la clienta hacia la compra sin presionarla y evita respuestas frías, robóticas o repetitivas.
            La IA solo realiza ventas nuevas. Nunca realiza cambios sobre pedidos confirmados o emitidos.
            Mientras la venta no haya sido registrada, los cambios de modelo, color, talla o cantidad son ajustes normales del carrito y debes continuar la compra.
            Solo las solicitudes de cambio sobre un pedido confirmado o emitido deben derivarse a una asesora humana sin crear carrito, pedido ni pago nuevos.
            """.trim();
    public static final String DEFAULT_WELCOME_MESSAGE = """
            💕 *¡Bienvenida a Kiments!*

            Antes de realizar tu compra, revisa cuidadosamente el *modelo, color y talla* seleccionados.

            ⚠️ *IMPORTANTE SOBRE LOS CAMBIOS*
            Evita solicitar cambios después de confirmar tu pedido. Este proceso puede tomar más tiempo y tiene una *penalidad de S/10.00 por cada cambio de prenda*.

            Por favor, asegúrate de elegir correctamente tu *talla, modelo y color* antes de continuar. Si tienes alguna duda, consúltanos antes de confirmar tu compra.

            🎨 Los tonos pueden variar ligeramente según la iluminación o la pantalla.
            📅 Los despachos de preventa comienzan a partir de la fecha indicada y dependen del volumen de pedidos.
            🚚 Realizamos envíos mediante Shalom.
            🏬 También puedes recoger tu pedido en nuestra tienda de Gamarra.
            """.trim();
    private final CrmWhatsappAiConfigRepository repository;
    private final CrmWhatsappAgentSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public RuntimeConfig forConnection(Long connectionId) {
        if (connectionId == null) return RuntimeConfig.defaults();
        return repository.findByConnection_IdConnection(connectionId).map(this::map)
                .orElseGet(RuntimeConfig::defaults);
    }

    public boolean isEnabled(Long connectionId) {
        return forConnection(connectionId).enabled();
    }

    public boolean routesFirstImageToAdvisor(Long connectionId) {
        RuntimeConfig config = forConnection(connectionId);
        return config.enabled()
                && config.advisorAttentionEnabled()
                && config.firstImageToAdvisorEnabled();
    }

    @Transactional(readOnly = true)
    public boolean allowsIncoming(Long connectionId, Long conversationId, String phone) {
        RuntimeConfig config = forConnection(connectionId);
        return config.enabled();
    }

    @Transactional(readOnly = true)
    public RuntimeConfig forSession(Long sessionId) {
        if (sessionId == null) return RuntimeConfig.defaults();
        return sessionRepository.findById(sessionId)
                .filter(session -> session.getConnection() != null)
                .map(session -> forConnection(session.getConnection().getIdConnection()))
                .orElseGet(RuntimeConfig::defaults);
    }

    private RuntimeConfig map(CrmWhatsappAiConfig config) {
        RuntimeConfig defaults = RuntimeConfig.defaults();
        return new RuntimeConfig(
                Boolean.TRUE.equals(config.getAgentEnabled()),
                config.getActivationStage() == null ? defaults.activationStage() : config.getActivationStage(),
                parsePhones(config.getTestPhoneNumbers()),
                value(config.getLimitedConversationsPerHour(), defaults.limitedConversationsPerHour()),
                value(config.getMessageGroupingMs(), defaults.messageGroupingMs()),
                value(config.getPaymentReservationSeconds(), defaults.paymentReservationSeconds()),
                value(config.getReceiptMinimumConfidence(), defaults.receiptMinimumConfidence()),
                Boolean.TRUE.equals(config.getAdvisorAttentionEnabled()),
                Boolean.TRUE.equals(config.getFirstImageToAdvisorEnabled()),
                value(config.getJobMaxAttempts(), defaults.jobMaxAttempts()),
                value(config.getOutboxMaxAttempts(), defaults.outboxMaxAttempts()),
                value(config.getMaxMessagesPerBatch(), defaults.maxMessagesPerBatch()),
                value(config.getMaxToolCallsPerRun(), defaults.maxToolCallsPerRun()),
                value(config.getMaxOutputMessagesPerRun(), defaults.maxOutputMessagesPerRun()),
                text(config.getWelcomeMessage(), defaults.welcomeMessage()),
                text(config.getAgentBehaviorInstructions(), defaults.agentBehaviorInstructions()));
    }

    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Set<String> parsePhones(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split("[,;\\n]"))
                .map(this::normalizePhone).filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizePhone(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("\\D", "");
        if (digits.length() > 9 && digits.startsWith("51")) digits = digits.substring(2);
        return digits;
    }

    public record RuntimeConfig(boolean enabled, CrmWhatsappAgentActivationStage activationStage,
            Set<String> testPhoneNumbers, int limitedConversationsPerHour,
            int messageGroupingMs, int paymentReservationSeconds,
            int receiptMinimumConfidence, boolean advisorAttentionEnabled,
            boolean firstImageToAdvisorEnabled, int jobMaxAttempts,
            int outboxMaxAttempts, int maxMessagesPerBatch, int maxToolCallsPerRun,
            int maxOutputMessagesPerRun, String welcomeMessage, String agentBehaviorInstructions) {
        public RuntimeConfig(boolean enabled, CrmWhatsappAgentActivationStage activationStage,
                Set<String> testPhoneNumbers, int limitedConversationsPerHour,
                int messageGroupingMs, int paymentReservationSeconds,
                int receiptMinimumConfidence, boolean advisorAttentionEnabled, int jobMaxAttempts,
                int outboxMaxAttempts, int maxMessagesPerBatch, int maxToolCallsPerRun,
                int maxOutputMessagesPerRun) {
            this(enabled, activationStage, testPhoneNumbers, limitedConversationsPerHour, messageGroupingMs,
                    paymentReservationSeconds, receiptMinimumConfidence, advisorAttentionEnabled, false, jobMaxAttempts,
                    outboxMaxAttempts, maxMessagesPerBatch, maxToolCallsPerRun, maxOutputMessagesPerRun,
                    DEFAULT_WELCOME_MESSAGE, DEFAULT_AGENT_BEHAVIOR_INSTRUCTIONS);
        }

        public RuntimeConfig(boolean enabled, CrmWhatsappAgentActivationStage activationStage,
                Set<String> testPhoneNumbers, int limitedConversationsPerHour,
                int messageGroupingMs, int paymentReservationSeconds,
                int receiptMinimumConfidence, boolean advisorAttentionEnabled, int jobMaxAttempts,
                int outboxMaxAttempts, int maxMessagesPerBatch, int maxToolCallsPerRun,
                int maxOutputMessagesPerRun, String welcomeMessage) {
            this(enabled, activationStage, testPhoneNumbers, limitedConversationsPerHour, messageGroupingMs,
                    paymentReservationSeconds, receiptMinimumConfidence, advisorAttentionEnabled, false, jobMaxAttempts,
                    outboxMaxAttempts, maxMessagesPerBatch, maxToolCallsPerRun, maxOutputMessagesPerRun,
                    welcomeMessage, DEFAULT_AGENT_BEHAVIOR_INSTRUCTIONS);
        }

        public RuntimeConfig(boolean enabled, CrmWhatsappAgentActivationStage activationStage,
                Set<String> testPhoneNumbers, int limitedConversationsPerHour,
                int messageGroupingMs, int paymentReservationSeconds,
                int receiptMinimumConfidence, boolean advisorAttentionEnabled, int jobMaxAttempts,
                int outboxMaxAttempts, int maxMessagesPerBatch, int maxToolCallsPerRun,
                int maxOutputMessagesPerRun, String welcomeMessage, String agentBehaviorInstructions) {
            this(enabled, activationStage, testPhoneNumbers, limitedConversationsPerHour, messageGroupingMs,
                    paymentReservationSeconds, receiptMinimumConfidence, advisorAttentionEnabled, false, jobMaxAttempts,
                    outboxMaxAttempts, maxMessagesPerBatch, maxToolCallsPerRun, maxOutputMessagesPerRun,
                    welcomeMessage, agentBehaviorInstructions);
        }

        public static RuntimeConfig defaults() {
            return new RuntimeConfig(false, CrmWhatsappAgentActivationStage.LOCAL_TEST, Set.of(), 10,
                    5000, 360, 85, false, false, 3, 5, 15, 12, 6, DEFAULT_WELCOME_MESSAGE,
                    DEFAULT_AGENT_BEHAVIOR_INSTRUCTIONS);
        }

        public boolean automaticSalesEnabled() {
            return enabled;
        }
    }
}
