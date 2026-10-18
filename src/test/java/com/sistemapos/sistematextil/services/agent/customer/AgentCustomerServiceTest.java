package com.sistemapos.sistematextil.services.agent.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerService.CustomerResult;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerService.CustomerSearchResult;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerOperationStore.OperationRow;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

class AgentCustomerServiceTest {
    private CrmWhatsappAgentSessionRepository sessionRepository;
    private CrmWhatsappConversationRepository conversationRepository;
    private ClienteRepository customerRepository;
    private EmpresaRepository companyRepository;
    private UsuarioRepository userRepository;
    private AgentCustomerOperationStore operationStore;
    private AgentCustomerService service;
    private CrmWhatsappAgentSession session;
    private CrmWhatsappConversation conversation;
    private Empresa company;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(CrmWhatsappAgentSessionRepository.class);
        conversationRepository = mock(CrmWhatsappConversationRepository.class);
        customerRepository = mock(ClienteRepository.class);
        companyRepository = mock(EmpresaRepository.class);
        userRepository = mock(UsuarioRepository.class);
        operationStore = mock(AgentCustomerOperationStore.class);
        service = new AgentCustomerService(sessionRepository, conversationRepository, customerRepository,
                companyRepository, userRepository, new CrmWhatsappAgentStateMachine(), operationStore,
                new ObjectMapper().findAndRegisterModules());

        company = new Empresa();
        company.setIdEmpresa(3);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(8);
        branch.setEmpresa(company);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(2L);
        connection.setEmpresa(company);
        connection.setSucursal(branch);
        conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(12L);
        conversation.setConnection(connection);
        session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(41L);
        session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE);
        session.setConnection(connection);
        session.setConversation(conversation);

        when(sessionRepository.findById(41L)).thenReturn(Optional.of(session));
        when(sessionRepository.findForUpdate(41L)).thenReturn(Optional.of(session));
        when(companyRepository.findForUpdate(3)).thenReturn(Optional.of(company));
        when(conversationRepository.findForUpdateById(12L)).thenReturn(Optional.of(conversation));
        when(operationStore.find(anyString())).thenReturn(Optional.empty());
        when(operationStore.claim(any(), anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(customerRepository.saveAndFlush(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void searchNormalizesCountryCodeAndStaysInsideConnectionCompany() {
        Cliente customer = customer(20, "Ana Perez", "999111222");
        when(customerRepository.findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                3, List.of("999111222", "51999111222"))).thenReturn(List.of(customer));

        CustomerSearchResult result = service.search(41L, "+51 999 111 222", null, null);

        assertEquals("FOUND", result.status());
        assertEquals(20, result.customers().getFirst().customerId());
        verify(customerRepository)
                .findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                        3, List.of("999111222", "51999111222"));
    }

    @Test
    void reusesQuickCustomerCompletesNameLinksBothOwnersAndAdvancesToPayment() {
        Cliente existing = customer(20, "CLIENTE 999111222", "999111222");
        when(customerRepository.findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                3, List.of("999111222", "51999111222"))).thenReturn(List.of(existing));

        CustomerResult result = service.register(41L, "Ana Perez", "999111222", null, null, "op-register");

        assertEquals("REUSED", result.outcome());
        assertEquals("Ana Perez", existing.getNombres());
        assertSame(existing, session.getCliente());
        assertSame(existing, conversation.getCliente());
        assertTrue(result.validForPayment());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA, session.getState());
        assertEquals(CrmWhatsappAgentEvent.DATOS_CLIENTE_COMPLETADOS, session.getLastEvent());
        verify(operationStore).complete(anyString(), anyString(), any());
    }

    @Test
    void registersCustomerFromCombinedShalomDataWithoutChangingTheShalomState() {
        session.setState(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM);
        Usuario actor=new Usuario();actor.setIdUsuario(5);actor.setEstado("ACTIVO");
        when(userRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local"))
                .thenReturn(Optional.of(actor));
        Cliente created=customer(24,"Ana Perez","999111222");
        created.setTipoDocumento(TipoDocumento.DNI);
        created.setNroDocumento("12345678");
        when(customerRepository.saveAndFlush(any(Cliente.class))).thenReturn(created);

        CustomerResult result=service.registerFromShalom(41L,"Ana Perez","999111222",
                TipoDocumento.DNI,"12345678","combined-customer");

        assertEquals("CREATED",result.outcome());
        assertSame(created,session.getCliente());
        assertSame(created,conversation.getCliente());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
    }

    @Test
    void refusesToMergePhoneAndDocumentBelongingToDifferentCustomers() {
        Cliente byPhone = customer(20, "Ana Perez", "999111222");
        Cliente byDocument = customer(21, "Maria Diaz", "988777666");
        byDocument.setTipoDocumento(TipoDocumento.DNI);
        byDocument.setNroDocumento("12345678");
        when(customerRepository.findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                3, List.of("999111222", "51999111222"))).thenReturn(List.of(byPhone));
        when(customerRepository
                .findFirstByEmpresa_IdEmpresaAndTipoDocumentoAndNroDocumentoAndDeletedAtIsNullOrderByIdClienteAsc(
                        3, TipoDocumento.DNI, "12345678"))
                .thenReturn(Optional.of(byDocument));

        assertThrows(IllegalStateException.class, () -> service.register(
                41L, "Ana Perez", "999111222", TipoDocumento.DNI, "12345678", "op-conflict"));

        verify(conversationRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void linkingIncompleteCustomerDoesNotAdvanceToPayment() {
        Cliente incomplete = customer(20, "CLIENTE 999111222", "999111222");
        when(customerRepository.findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(20, 3))
                .thenReturn(Optional.of(incomplete));

        CustomerResult result = service.link(41L, 20, "op-link");

        assertEquals(CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE, session.getState());
        assertEquals(List.of("nombres"), result.missingFields());
        assertEquals(false, result.validForPayment());
    }

    @Test
    void completesOnlyMissingFieldsAndThenAdvances() {
        Cliente incomplete = customer(20, "CLIENTE 999111222", "999111222");
        session.setCliente(incomplete);
        conversation.setCliente(incomplete);
        when(customerRepository.findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(20, 3))
                .thenReturn(Optional.of(incomplete));
        when(customerRepository.findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                3, List.of("999111222", "51999111222"))).thenReturn(List.of(incomplete));

        CustomerResult result = service.completeMissing(41L, "Ana Perez", null,
                TipoDocumento.DNI, "12345678", "op-complete");

        assertEquals("Ana Perez", incomplete.getNombres());
        assertEquals(TipoDocumento.DNI, incomplete.getTipoDocumento());
        assertEquals("12345678", incomplete.getNroDocumento());
        assertTrue(result.validForPayment());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA, session.getState());
    }

    @Test
    void replaysCompletedMutationWithoutLockingOrLinkingAgain() throws Exception {
        CustomerResult prior = new CustomerResult("LINKED", 20, "Ana Perez", "999111222",
                TipoDocumento.SIN_DOC, null, true, true, "ESPERANDO_METODO_PAGO", List.of());
        String key = sha256("customer:41:retry-link");
        String fingerprint = sha256("relacionar_cliente_conversacion:20");
        when(operationStore.find(key)).thenReturn(Optional.of(new OperationRow(key,
                "relacionar_cliente_conversacion", "COMPLETED", fingerprint,
                new ObjectMapper().writeValueAsString(prior))));

        CustomerResult replayed = service.link(41L, 20, "retry-link");

        assertEquals(prior, replayed);
        verify(sessionRepository, never()).findForUpdate(any());
        verify(conversationRepository, never()).save(any());
    }

    private Cliente customer(int id, String names, String phone) {
        Usuario actor = new Usuario();
        actor.setIdUsuario(5);
        Cliente customer = new Cliente();
        customer.setIdCliente(id);
        customer.setEmpresa(company);
        customer.setUsuarioCreacion(actor);
        customer.setNombres(names);
        customer.setTelefono(phone);
        customer.setTipoDocumento(TipoDocumento.SIN_DOC);
        customer.setEstado("ACTIVO");
        return customer;
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
