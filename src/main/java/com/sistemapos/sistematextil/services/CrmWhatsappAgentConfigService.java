package com.sistemapos.sistematextil.services;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import static com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService.DEFAULT_WELCOME_MESSAGE;
import static com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService.DEFAULT_AGENT_BEHAVIOR_INSTRUCTIONS;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService.RuntimeConfig;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeControlService;
import com.sistemapos.sistematextil.services.agent.config.AgentActivationMonitorService;
import com.sistemapos.sistematextil.services.agent.config.AgentActivationMonitorService.MonitorSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAgentConfigService {
    private final CrmWhatsappAiConfigRepository repository;
    private final CrmWhatsappConnectionRepository connectionRepository;
    private final CrmWhatsappAiCredentialService credentialService;
    private final AgentRuntimeConfigService runtimeConfigService;
    private final AgentRuntimeControlService runtimeControlService;
    private final AgentActivationMonitorService monitorService;

    @Transactional(readOnly = true)
    public AgentConfigResponse get(Usuario actor) {
        Long connectionId = credentialService.requireAdminConnectionId(actor);
        RuntimeConfig runtime = runtimeConfigService.forConnection(connectionId);
        return response(connectionId, runtime, credentialService.configuredModel(connectionId));
    }

    @Transactional
    public AgentConfigResponse save(AgentConfigRequest request, Usuario actor) {
        if (request == null) throw badRequest("La configuracion del agente es obligatoria");
        Long connectionId = credentialService.requireAdminConnectionId(actor);
        CrmWhatsappConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> badRequest("La conexion de WhatsApp no existe"));
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        if (enabled && !credentialService.hasUsableCredential(connectionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Configura y prueba una credencial de Gemini antes de activar el agente");
        }
        if (enabled && connection.getSucursal() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vincula una sucursal de venta antes de activar el agente");
        }
        int grouping = range(request.messageGroupingMs(), 500, 60000, "Agrupacion de mensajes");
        int reservation = request.paymentReservationSeconds() == null ? 240 : request.paymentReservationSeconds();
        if (reservation != 240) throw badRequest("La reserva de pago debe mantenerse en cuatro minutos");
        int confidence = range(request.receiptMinimumConfidence(), 50, 100, "Confianza del comprobante");
        int jobAttempts = range(request.jobMaxAttempts(), 1, 10, "Reintentos de interpretacion");
        int outboxAttempts = range(request.outboxMaxAttempts(), 1, 10, "Reintentos de envio");
        int maxMessages = range(request.maxMessagesPerBatch(), 1, 50, "Mensajes por lote");
        int maxTools = range(request.maxToolCallsPerRun(), 1, 30, "Herramientas por ejecucion");
        int maxOutputs = range(request.maxOutputMessagesPerRun(), 1, 10, "Salidas por ejecucion");
        String model = normalizeModel(request.model());
        String welcomeMessage = normalizeWelcomeMessage(request.welcomeMessage());
        String behaviorInstructions = normalizeBehaviorInstructions(request.agentBehaviorInstructions());

        CrmWhatsappAiConfig config = repository.findByConnection_IdConnection(connectionId).orElseGet(() -> {
            CrmWhatsappAiConfig created = new CrmWhatsappAiConfig();
            created.setConnection(connection);
            return created;
        });
        config.setAgentEnabled(enabled);
        config.setActivationStage(CrmWhatsappAgentActivationStage.AUTOMATIC_SALES);
        config.setTestPhoneNumbers(null);
        config.setLimitedConversationsPerHour(500);
        config.setGeminiModel(model);
        config.setMessageGroupingMs(grouping);
        config.setPaymentReservationSeconds(reservation);
        config.setReceiptMinimumConfidence(confidence);
        config.setAdvisorAttentionEnabled(Boolean.TRUE.equals(request.advisorAttentionEnabled()));
        config.setJobMaxAttempts(jobAttempts);
        config.setOutboxMaxAttempts(outboxAttempts);
        config.setMaxMessagesPerBatch(maxMessages);
        config.setMaxToolCallsPerRun(maxTools);
        config.setMaxOutputMessagesPerRun(maxOutputs);
        config.setWelcomeMessage(welcomeMessage);
        config.setAgentBehaviorInstructions(behaviorInstructions);
        repository.saveAndFlush(config);
        if (!enabled) {
            runtimeControlService.stop(connectionId, "Agente desactivado desde conexiones");
        }
        return response(connectionId, runtimeConfigService.forConnection(connectionId), model);
    }

    private AgentConfigResponse response(Long connectionId, RuntimeConfig config, String model) {
        return new AgentConfigResponse(connectionId, config.enabled(), model, config.activationStage().name(),
                List.copyOf(config.testPhoneNumbers()), config.limitedConversationsPerHour(),
                config.automaticSalesEnabled(), config.messageGroupingMs(),
                config.paymentReservationSeconds(), config.receiptMinimumConfidence(),
                config.advisorAttentionEnabled(), config.jobMaxAttempts(), config.outboxMaxAttempts(),
                config.maxMessagesPerBatch(), config.maxToolCallsPerRun(), config.maxOutputMessagesPerRun(),
                config.welcomeMessage(), config.agentBehaviorInstructions(), monitorService.snapshot(connectionId));
    }

    private int range(Integer value, int min, int max, String field) {
        if (value == null || value < min || value > max) {
            throw badRequest(field + " debe estar entre " + min + " y " + max);
        }
        return value;
    }
    private String normalizeModel(String value) {
        String model = value == null ? "" : value.trim();
        if (model.isBlank()) model = "gemini-3.8-flash";
        if (!model.matches("[A-Za-z0-9._-]{3,100}")) throw badRequest("El modelo de Gemini no es valido");
        return model;
    }
    private String normalizeWelcomeMessage(String value) {
        String message = value == null ? DEFAULT_WELCOME_MESSAGE : value.trim();
        if (message.isBlank()) throw badRequest("El mensaje de bienvenida no puede estar vacio");
        if (message.length() > 4000) throw badRequest("El mensaje de bienvenida no puede superar 4000 caracteres");
        return message;
    }
    private String normalizeBehaviorInstructions(String value) {
        String instructions = value == null ? DEFAULT_AGENT_BEHAVIOR_INSTRUCTIONS : value.trim();
        if (instructions.isBlank()) throw badRequest("Las instrucciones de comportamiento no pueden estar vacias");
        if (instructions.length() > 4000) {
            throw badRequest("Las instrucciones de comportamiento no pueden superar 4000 caracteres");
        }
        return instructions;
    }
    private CrmWhatsappAgentActivationStage parseStage(String value, CrmWhatsappAgentActivationStage fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return CrmWhatsappAgentActivationStage.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException error) {
            throw badRequest("La fase de activacion no es valida");
        }
    }
    private Set<String> normalizePhones(List<String> values) {
        if (values == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String digits = value == null ? "" : value.replaceAll("\\D", "");
            if (digits.length() > 9 && digits.startsWith("51")) digits = digits.substring(2);
            if (!digits.isBlank() && !digits.matches("\\d{9,15}")) {
                throw badRequest("Los celulares de prueba deben contener entre 9 y 15 digitos");
            }
            if (!digits.isBlank()) result.add(digits);
        }
        if (result.size() > 20) throw badRequest("Solo se permiten 20 celulares de prueba");
        return result;
    }
    private Set<String> normalizePhones(String values) {
        return values == null || values.isBlank() ? Set.of()
                : normalizePhones(List.of(values.split("[,;\\n]")));
    }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record AgentConfigRequest(Boolean enabled, String model, String activationStage,
            List<String> testPhoneNumbers, Integer limitedConversationsPerHour, Integer messageGroupingMs,
            Integer paymentReservationSeconds, Integer receiptMinimumConfidence,
            Boolean advisorAttentionEnabled, Integer jobMaxAttempts, Integer outboxMaxAttempts,
            Integer maxMessagesPerBatch, Integer maxToolCallsPerRun, Integer maxOutputMessagesPerRun,
            String welcomeMessage, String agentBehaviorInstructions) {}

    public record AgentConfigResponse(Long connectionId, boolean enabled, String model, String activationStage,
            List<String> testPhoneNumbers, int limitedConversationsPerHour, boolean automaticSalesEnabled,
            int messageGroupingMs, int paymentReservationSeconds, int receiptMinimumConfidence,
            boolean advisorAttentionEnabled, int jobMaxAttempts, int outboxMaxAttempts,
            int maxMessagesPerBatch, int maxToolCallsPerRun, int maxOutputMessagesPerRun,
            String welcomeMessage, String agentBehaviorInstructions, MonitorSnapshot monitoring) {}
}
