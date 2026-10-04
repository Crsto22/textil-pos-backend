package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialRequest;
import com.sistemapos.sistematextil.services.ai.AiSecretEncryptionService;
import com.sistemapos.sistematextil.util.usuario.Rol;

class CrmWhatsappAiCredentialServiceTest {

    private final CrmWhatsappAiConfigRepository configRepository = mock(CrmWhatsappAiConfigRepository.class);
    private final CrmWhatsappConnectionService connectionService = mock(CrmWhatsappConnectionService.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final CrmWhatsappAiAuditService auditService = mock(CrmWhatsappAiAuditService.class);
    private final CrmWhatsappAiCredentialService service = new CrmWhatsappAiCredentialService(
            configRepository,
            connectionService,
            usuarioRepository,
            new AiSecretEncryptionService("clave-maestra-local-de-pruebas-1234567890"),
            auditService);

    @BeforeEach
    void configurarEntorno() {
        ReflectionTestUtils.setField(service, "environmentApiKey", "");
        ReflectionTestUtils.setField(service, "environmentModel", "gemini-3.8-flash");
    }

    @Test
    void soloAdministradorPuedeConsultarCredenciales() {
        Usuario session = usuario(1, Rol.VENTAS);
        when(usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(1)).thenReturn(Optional.of(session));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.getStatus(session));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void guardaLaApiKeyCifradaYSoloDevuelveMascara() {
        Usuario admin = usuario(1, Rol.ADMINISTRADOR);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(10L);
        AtomicReference<CrmWhatsappAiConfig> saved = new AtomicReference<>();
        when(usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(1)).thenReturn(Optional.of(admin));
        when(connectionService.requireConexionConfigurada(admin)).thenReturn(connection);
        when(configRepository.findByConnection_IdConnection(10L)).thenReturn(Optional.empty());
        when(configRepository.save(any(CrmWhatsappAiConfig.class))).thenAnswer(invocation -> {
            CrmWhatsappAiConfig config = invocation.getArgument(0);
            saved.set(config);
            return config;
        });

        var response = service.save(
                new CredentialRequest("AIza-clave-secreta-de-prueba", "gemini-3.8-flash"), admin);

        assertEquals("********", response.maskedKey());
        assertEquals("DATABASE", response.source());
        assertNotEquals("AIza-clave-secreta-de-prueba", saved.get().getGeminiApiKeyCiphertext());
    }

    @Test
    void actualizaSoloElModeloYConservaLaApiKeyGuardada() {
        Usuario admin = usuario(1, Rol.ADMINISTRADOR);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(10L);
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setConnection(connection);
        config.setGeminiApiKeyCiphertext("ciphertext-existente");
        config.setGeminiApiKeyNonce("nonce-existente");
        config.setGeminiModel("gemini-3.8-flash");
        when(usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(1)).thenReturn(Optional.of(admin));
        when(connectionService.requireConexionConfigurada(admin)).thenReturn(connection);
        when(configRepository.findByConnection_IdConnection(10L)).thenReturn(Optional.of(config));
        when(configRepository.save(config)).thenReturn(config);

        var response = service.save(new CredentialRequest("", "gemini-3.1-flash-lite"), admin);

        assertEquals("gemini-3.1-flash-lite", response.model());
        assertEquals("gemini-3.1-flash-lite", config.getGeminiModel());
        assertEquals("ciphertext-existente", config.getGeminiApiKeyCiphertext());
        assertEquals("nonce-existente", config.getGeminiApiKeyNonce());
    }

    private Usuario usuario(int id, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setEstado("ACTIVO");
        usuario.setRol(rol);
        usuario.setNombre("Admin");
        usuario.setApellido("CRM");
        return usuario;
    }
}
