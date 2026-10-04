package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappBusinessHoursRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiDeliveryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiConfigService.AiConfigRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAiConfigService.BusinessHoursRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

class CrmWhatsappAiConfigServiceTest {

    private final CrmWhatsappAiConfigRepository repository = mock(CrmWhatsappAiConfigRepository.class);
    private final CrmWhatsappConnectionService connectionService = mock(CrmWhatsappConnectionService.class);
    private final CrmWhatsappBusinessHoursRepository businessHours = mock(CrmWhatsappBusinessHoursRepository.class);
    private final CrmWhatsappAiCredentialService credentialService = mock(CrmWhatsappAiCredentialService.class);
    private final CrmWhatsappAiAuditService auditService = mock(CrmWhatsappAiAuditService.class);
    private final CrmWhatsappAiOperationsService operationsService = mock(CrmWhatsappAiOperationsService.class);
    private final CrmWhatsappConversationRepository conversationRepository = mock(CrmWhatsappConversationRepository.class);
    private final CrmWhatsappAiJobRepository jobRepository = mock(CrmWhatsappAiJobRepository.class);
    private final CrmWhatsappAiDeliveryRepository deliveryRepository = mock(CrmWhatsappAiDeliveryRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CrmWhatsappChatService> chatServiceProvider = mock(ObjectProvider.class);
    private final CrmWhatsappAiConfigService service = new CrmWhatsappAiConfigService(
            repository, businessHours, connectionService, credentialService, auditService, operationsService,
            conversationRepository, jobRepository, deliveryRepository, chatServiceProvider);
    private final Usuario actor = mock(Usuario.class);

    @BeforeEach
    void setup() { when(actor.getRol()).thenReturn(Rol.ADMINISTRADOR); }

    @Test
    void devuelveConfiguracionSeguraPorDefectoSinConexion() {
        when(connectionService.buscarConexionConfigurada(actor)).thenReturn(null);

        var response = service.obtener(actor);

        assertEquals("DESACTIVADA", response.modo());
        assertEquals("America/Lima", response.zonaHoraria());
        assertEquals(6, response.diasAtencion().size());
        assertTrue(response.intencionesPermitidas().contains("GUIA_TALLAS"));
        assertTrue(response.transferirSolicitudHumana());
        assertTrue(response.transferirAsuntoSensible());
    }

    @Test
    void rechazaLimitesFueraDelRangoPermitido() {
        CrmWhatsappConnection connection = connection();
        when(connectionService.requireConexionConfigurada(actor)).thenReturn(connection);

        AiConfigRequest request = request(2, 5, 75);

        assertThrows(ResponseStatusException.class, () -> service.guardar(request, actor));
    }

    @Test
    void conservaTransferenciasObligatoriasAlGuardar() {
        CrmWhatsappConnection connection = connection();
        when(connectionService.requireConexionConfigurada(actor)).thenReturn(connection);
        when(credentialService.hasUsableCredential(10L)).thenReturn(true);
        when(repository.findByConnection_IdConnection(10L)).thenReturn(Optional.empty());
        when(repository.save(any(CrmWhatsappAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.guardar(request(5, 5, 75), actor);

        assertEquals("SUGERENCIAS", response.modo());
        assertTrue(response.transferirSolicitudHumana());
        assertTrue(response.transferirAsuntoSensible());
    }

    @Test
    void guardaEsperaYLimiteDeRespuestasConfigurados() {
        CrmWhatsappConnection connection = connection();
        when(connectionService.requireConexionConfigurada(actor)).thenReturn(connection);
        when(credentialService.hasUsableCredential(10L)).thenReturn(true);
        when(repository.findByConnection_IdConnection(10L)).thenReturn(Optional.empty());
        when(repository.save(any(CrmWhatsappAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.guardar(request(12, 20, 75), actor);

        assertEquals(12, response.esperaRespuestaSegundos());
        assertEquals(20, response.maxRespuestasAutomaticas());
    }

    @Test
    void cambiarModoNoMueveConversacionesHastaElSiguienteMensaje() {
        CrmWhatsappConnection connection = connection();
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setConnection(connection);
        config.setModo(CrmWhatsappAiMode.AUTOMATICA);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setAttentionQueue(CrmWhatsappAttentionQueue.AI_ACTIVE);
        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);
        when(connectionService.requireConexionConfigurada(actor)).thenReturn(connection);
        when(repository.findByConnection_IdConnection(10L)).thenReturn(Optional.of(config));
        when(repository.save(any(CrmWhatsappAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.guardar(request("DESACTIVADA", 5, 5, 75), actor);

        assertEquals(null, conversation.getWaitingReason());
        verify(conversationRepository, never()).saveAll(any());
    }

    private CrmWhatsappConnection connection() {
        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(3);
        sucursal.setNombre("Kiments Centro");
        sucursal.setTipo(SucursalTipo.VENTA);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(10L);
        connection.setSucursal(sucursal);
        return connection;
    }

    private AiConfigRequest request(int espera, int maxRespuestas, int confianza) {
        return request("SUGERENCIAS", espera, maxRespuestas, confianza);
    }

    private AiConfigRequest request(String mode, int espera, int maxRespuestas, int confianza) {
        return new AiConfigRequest(
                mode,
                "America/Lima",
                List.of("LUNES", "MARTES"),
                "09:00",
                "19:00",
                espera,
                "CERCANO",
                "",
                List.of("SALUDO", "PRODUCTOS", "PRECIO"),
                maxRespuestas,
                confianza,
                true,
                null, null, null, null, null, 0,
                List.of(
                        new BusinessHoursRequest("LUNES", false, "09:00", "19:00"),
                        new BusinessHoursRequest("MARTES", false, "09:00", "19:00"),
                        new BusinessHoursRequest("MIERCOLES", false, "09:00", "19:00"),
                        new BusinessHoursRequest("JUEVES", false, "09:00", "19:00"),
                        new BusinessHoursRequest("VIERNES", false, "09:00", "19:00"),
                        new BusinessHoursRequest("SABADO", false, "09:00", "19:00"),
                        new BusinessHoursRequest("DOMINGO", true, "", "")));
    }
}
