package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.ai.AiSecretEncryptionService;
import com.sistemapos.sistematextil.services.ai.AiSecretEncryptionService.EncryptedSecret;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeControlService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiCredentialService {

    private static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private final CrmWhatsappAiConfigRepository configRepository;
    private final CrmWhatsappConnectionService connectionService;
    private final UsuarioRepository usuarioRepository;
    private final AiSecretEncryptionService encryptionService;
    private final AgentRuntimeControlService runtimeControlService;

    @Value("${crm.whatsapp.ai.gemini.api-key:}")
    private String environmentApiKey;

    @Value("${crm.whatsapp.ai.gemini.model:gemini-3.8-flash}")
    private String environmentModel;

    @Transactional(readOnly = true)
    public CredentialResponse getStatus(Usuario sessionUser) {
        Usuario actor = requireAdmin(sessionUser);
        CrmWhatsappConnection connection = connectionService.buscarConexionConfigurada(actor);
        if (connection == null) return response(null);
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(connection.getIdConnection()).orElse(null);
        return response(config);
    }

    @Transactional
    public CredentialResponse save(CredentialRequest request, Usuario sessionUser) {
        Usuario actor = requireAdmin(sessionUser);
        CrmWhatsappConnection connection = connectionService.requireConexionConfigurada(actor);
        if (request == null) throw badRequest("Ingrese la credencial de Gemini");
        String apiKey = clean(request.apiKey());
        String model = normalizeModel(request.model());
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(connection.getIdConnection())
                .orElse(null);
        boolean replacingKey = !apiKey.isBlank();
        if (replacingKey && (apiKey.length() < 20 || apiKey.length() > 500)) {
            throw badRequest("La API key de Gemini no tiene un formato valido");
        }
        if (!replacingKey && !hasStoredKey(config) && clean(environmentApiKey).isBlank()) {
            throw badRequest("Ingrese una API key de Gemini antes de configurar el modelo");
        }
        if (replacingKey && !encryptionService.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configure AI_SECRETS_MASTER_KEY en el backend antes de guardar claves desde el CRM");
        }

        if (config == null) config = defaultConfig(connection);
        if (replacingKey) {
            EncryptedSecret encrypted = encryptionService.encrypt(apiKey, connection.getIdConnection());
            config.setGeminiApiKeyCiphertext(encrypted.ciphertext());
            config.setGeminiApiKeyNonce(encrypted.nonce());
        }
        config.setGeminiModel(model);
        config.setApiKeyUpdatedAt(LocalDateTime.now());
        config.setApiKeyUpdatedBy(actor);
        CrmWhatsappAiConfig saved = configRepository.save(config);
        return response(saved);
    }

    @Transactional
    public CredentialResponse delete(Usuario sessionUser) {
        Usuario actor = requireAdmin(sessionUser);
        CrmWhatsappConnection connection = connectionService.requireConexionConfigurada(actor);
        configRepository.findByConnection_IdConnection(connection.getIdConnection()).ifPresent(config -> {
            config.setGeminiApiKeyCiphertext(null);
            config.setGeminiApiKeyNonce(null);
            config.setGeminiModel(null);
            config.setAgentEnabled(false);
            config.setApiKeyUpdatedAt(LocalDateTime.now());
            config.setApiKeyUpdatedBy(actor);
            configRepository.save(config);
            runtimeControlService.stop(connection.getIdConnection(), "Credencial de Gemini eliminada");
        });
        return response(configRepository.findByConnection_IdConnection(connection.getIdConnection()).orElse(null));
    }

    @Transactional(readOnly = true)
    public CredentialMaterial resolve(Long connectionId) {
        if (connectionId != null) {
            CrmWhatsappAiConfig config = configRepository.findByConnection_IdConnection(connectionId).orElse(null);
            if (hasStoredKey(config)) {
                String key = encryptionService.decrypt(
                        config.getGeminiApiKeyCiphertext(), config.getGeminiApiKeyNonce(), connectionId);
                return new CredentialMaterial(key, normalizeModel(config.getGeminiModel()), "DATABASE");
            }
        }
        String key = clean(environmentApiKey);
        if (!key.isBlank()) {
            String model = connectionId == null ? environmentModel : configRepository.findByConnection_IdConnection(connectionId)
                    .map(CrmWhatsappAiConfig::getGeminiModel)
                    .filter(value -> !clean(value).isBlank())
                    .orElse(environmentModel);
            return new CredentialMaterial(key, normalizeModel(model), "ENVIRONMENT");
        }
        throw new IllegalStateException("La API key de Gemini no esta configurada");
    }

    @Transactional(readOnly = true)
    public String configuredModel(Long connectionId) {
        if (connectionId != null) {
            CrmWhatsappAiConfig config = configRepository.findByConnection_IdConnection(connectionId).orElse(null);
            if (config != null && !clean(config.getGeminiModel()).isBlank()) {
                return normalizeModel(config.getGeminiModel());
            }
        }
        return normalizeModel(environmentModel);
    }

    @Transactional(readOnly = true)
    public Long requireAdminConnectionId(Usuario sessionUser) {
        Usuario actor = requireAdmin(sessionUser);
        return connectionService.requireConexionConfigurada(actor).getIdConnection();
    }

    @Transactional(readOnly = true)
    public boolean hasUsableCredential(Long connectionId) {
        CrmWhatsappAiConfig config = connectionId == null
                ? null : configRepository.findByConnection_IdConnection(connectionId).orElse(null);
        if (hasStoredKey(config)) return encryptionService.isConfigured();
        return !clean(environmentApiKey).isBlank();
    }

    private CredentialResponse response(CrmWhatsappAiConfig config) {
        boolean stored = hasStoredKey(config);
        boolean environment = !clean(environmentApiKey).isBlank();
        String source = stored ? "DATABASE" : environment ? "ENVIRONMENT" : "NONE";
        String configuredModel = config == null ? "" : clean(config.getGeminiModel());
        String model = !configuredModel.isBlank()
                ? normalizeModel(configuredModel)
                : normalizeModel(environmentModel);
        String updatedBy = config == null || config.getApiKeyUpdatedBy() == null
                ? null
                : clean(config.getApiKeyUpdatedBy().getNombre() + " " + config.getApiKeyUpdatedBy().getApellido());
        return new CredentialResponse(
                stored || environment,
                source,
                stored || environment ? "********" : "",
                model,
                config == null ? null : config.getApiKeyUpdatedAt(),
                updatedBy,
                encryptionService.isConfigured());
    }

    private CrmWhatsappAiConfig defaultConfig(CrmWhatsappConnection connection) {
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setConnection(connection);
        return config;
    }

    private Usuario requireAdmin(Usuario sessionUser) {
        if (sessionUser == null || sessionUser.getIdUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        Usuario actor = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(sessionUser.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado"));
        if (!"ACTIVO".equalsIgnoreCase(clean(actor.getEstado())) || actor.getRol() != Rol.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede gestionar la API key");
        }
        return actor;
    }

    private boolean hasStoredKey(CrmWhatsappAiConfig config) {
        return config != null
                && !clean(config.getGeminiApiKeyCiphertext()).isBlank()
                && !clean(config.getGeminiApiKeyNonce()).isBlank();
    }

    private String normalizeModel(String value) {
        String model = clean(value);
        if (model.isBlank()) model = DEFAULT_MODEL;
        if (!model.matches("[A-Za-z0-9._-]{3,100}")) {
            throw badRequest("El modelo de Gemini no es valido");
        }
        return model;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record CredentialRequest(String apiKey, String model) {}
    public record CredentialMaterial(String apiKey, String model, String source) {}
    public record CredentialResponse(
            boolean configured,
            String source,
            String maskedKey,
            String model,
            LocalDateTime updatedAt,
            String updatedBy,
            boolean masterKeyConfigured) {}
}
