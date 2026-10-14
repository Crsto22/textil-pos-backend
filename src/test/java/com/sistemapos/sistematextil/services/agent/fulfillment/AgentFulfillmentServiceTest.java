package com.sistemapos.sistematextil.services.agent.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappShalomProfile;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.ShalomAgency;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappShalomProfileRepository;
import com.sistemapos.sistematextil.repositories.ShalomAgencyRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.ai.AgentShalomDataField;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore;
import com.sistemapos.sistematextil.services.agent.fulfillment.ShalomAgencySearchService.AgencyView;
import com.sistemapos.sistematextil.services.agent.fulfillment.ShalomAgencySearchService.SearchResult;

class AgentFulfillmentServiceTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-08T19:10:00Z"),ZoneId.of("America/Lima"));
    private CrmWhatsappAgentSessionRepository sessions;
    private CrmWhatsappPaymentRequestRepository payments;
    private CrmWhatsappFulfillmentRepository fulfillments;
    private SucursalRepository branches;
    private ShalomAgencyRepository agencies;
    private CrmWhatsappShalomProfileRepository profiles;
    private ShalomAgencySearchService search;
    private AgentCartStore operations;
    private AgentFulfillmentService service;
    private CrmWhatsappAgentSession session;
    private CrmWhatsappPaymentRequest payment;
    private CrmWhatsappFulfillment fulfillment;

    @BeforeEach
    void setUp(){
        sessions=mock(CrmWhatsappAgentSessionRepository.class);payments=mock(CrmWhatsappPaymentRequestRepository.class);
        fulfillments=mock(CrmWhatsappFulfillmentRepository.class);branches=mock(SucursalRepository.class);
        agencies=mock(ShalomAgencyRepository.class);profiles=mock(CrmWhatsappShalomProfileRepository.class);
        search=mock(ShalomAgencySearchService.class);operations=mock(AgentCartStore.class);
        service=new AgentFulfillmentService(sessions,payments,fulfillments,branches,agencies,profiles,search,operations,
                new CrmWhatsappAgentStateMachine(),new ObjectMapper().findAndRegisterModules(),clock);
        Empresa company=new Empresa();company.setIdEmpresa(3);Sucursal branch=new Sucursal();branch.setIdSucursal(1);branch.setEmpresa(company);
        CrmWhatsappConnection connection=new CrmWhatsappConnection();connection.setIdConnection(7L);connection.setEmpresa(company);connection.setSucursal(branch);
        CrmWhatsappConversation conversation=new CrmWhatsappConversation();conversation.setIdConversation(12L);conversation.setConnection(connection);
        Cliente customer=new Cliente();customer.setIdCliente(20);customer.setNombres("Ana Perez");customer.setTelefono("999111222");
        session=new CrmWhatsappAgentSession();session.setIdAgentSession(41L);session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA);session.setConnection(connection);
        session.setConversation(conversation);session.setCliente(customer);
        payment=new CrmWhatsappPaymentRequest();payment.setIdPaymentRequest(31L);payment.setAgentSession(session);
        payment.setStatus(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW);payment.setReservationStatus(CrmWhatsappPaymentReservationStatus.ACTIVE);
        payment.setEvidenceAcceptedAt(LocalDateTime.now(clock).minusMinutes(1));payment.setReviewExpiresAt(LocalDateTime.now(clock).plusHours(2));
        fulfillment=new CrmWhatsappFulfillment();fulfillment.setIdFulfillment(51L);fulfillment.setAgentSession(session);
        fulfillment.setConversation(conversation);fulfillment.setPaymentRequest(payment);fulfillment.setMethod(CrmWhatsappFulfillmentMethod.SHALOM);
        when(sessions.findForUpdate(41L)).thenReturn(Optional.of(session));when(sessions.findById(41L)).thenReturn(Optional.of(session));
        when(payments.findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(any(),any())).thenReturn(Optional.of(payment));
        when(payments.findForUpdate(31L)).thenReturn(Optional.of(payment));when(fulfillments.save(any())).thenAnswer(i->i.getArgument(0));
        when(profiles.findByCliente_IdCliente(20)).thenReturn(Optional.empty());when(operations.findOperation(anyString())).thenReturn(Optional.empty());
        when(operations.claimOperation(any(),anyString(),anyString(),anyString(),any())).thenReturn(true);
    }

    @Test
    void searchesThenConfirmsOnlyAnOfferedAgency(){
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.empty(),Optional.of(fulfillment),Optional.of(fulfillment));
        service.selectShalom(41L,"select");
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        AgencyView view=new AgencyView(9L,7,"AREQUIPA / AV PARRA 379","AQP","AREQUIPA","AREQUIPA",
                "AV PARRA 379","AV. PARRA 379 - Arequipa","01 5007878","LUNES A VIERNES",.98);
        when(search.search("Arequipa Parra")).thenReturn(new SearchResult("MATCH",List.of(view)));
        var proposed=service.saveShalomData(41L,"Ana Perez","12345678","999111222","Arequipa Parra","data");
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
        assertEquals(List.of(view),proposed.agencyCandidates());
        ShalomAgency agency=new ShalomAgency();agency.setIdShalomAgency(9L);agency.setExternalId(7);agency.setName(view.name());
        agency.setAddress(view.address());agency.setDepartment("AREQUIPA");agency.setProvince("AREQUIPA");agency.setActive(true);agency.setDestinationEnabled(true);
        when(agencies.findById(9L)).thenReturn(Optional.of(agency));
        var confirmed=service.confirmShalomAgency(41L,9L,"confirm");
        assertEquals("READY",confirmed.status());assertEquals(CrmWhatsappAgentState.LISTO_PARA_VENTA,session.getState());
        verify(profiles).save(any());
    }

    @Test
    void rejectsShalomPhoneUnlessItHasExactlyNinePeruvianMobileDigits(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.saveShalomData(41L, "Cristhofer Leonardo Quispe Mamani", "75132958",
                        "9338889985", "Agencia Montenegro", "invalid-phone"));

        assertEquals("El celular es incorrecto. Ingresa un numero celular peruano valido de exactamente 9 digitos",
                error.getMessage());
    }

    @Test
    void savesInitialShalomDataOneByOneAndRequestsOnlyWhatIsMissing(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        when(fulfillments.findByAgentSession_IdAgentSession(41L)).thenReturn(Optional.of(fulfillment));
        AgencyView view=new AgencyView(9L,7,"AREQUIPA / AV PARRA 379","AQP","AREQUIPA","AREQUIPA",
                "AV PARRA 379","AV. PARRA 379 - Arequipa","01 5007878","LUNES A VIERNES",.98);
        when(search.search("Arequipa Parra")).thenReturn(new SearchResult("MATCH",List.of(view)));

        var name=service.savePartialShalomData(41L,"Ana Perez","","","");
        assertEquals(List.of("DNI","celular","agencia de Shalom"),name.missingFields());
        assertEquals("Ana Perez",fulfillment.getRecipientName());

        var dni=service.savePartialShalomData(41L,"","12345678","","");
        assertEquals(List.of("celular","agencia de Shalom"),dni.missingFields());
        assertEquals("12345678",fulfillment.getDocumentNumber());

        var phone=service.savePartialShalomData(41L,"","","999111222","");
        assertEquals(List.of("agencia de Shalom"),phone.missingFields());
        assertEquals("999111222",fulfillment.getPhone());

        var agency=service.savePartialShalomData(41L,"","","","Arequipa Parra");
        assertEquals(List.of(),agency.missingFields());
        assertEquals(List.of(view),agency.fulfillment().agencyCandidates());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
    }

    @Test
    void acceptsAllInitialShalomDataInOneMessage(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        when(fulfillments.findByAgentSession_IdAgentSession(41L)).thenReturn(Optional.of(fulfillment));
        AgencyView view=new AgencyView(9L,7,"AREQUIPA / AV PARRA 379","AQP","AREQUIPA","AREQUIPA",
                "AV PARRA 379","AV. PARRA 379 - Arequipa","01 5007878","LUNES A VIERNES",.98);
        when(search.search("Arequipa Parra")).thenReturn(new SearchResult("MATCH",List.of(view)));

        var progress=service.savePartialShalomData(41L,"Ana Perez","12345678","999111222","Arequipa Parra");

        assertEquals(List.of(),progress.missingFields());
        assertEquals(List.of(),progress.validationErrors());
        assertEquals(List.of(view),progress.fulfillment().agencyCandidates());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
    }

    @Test
    void keepsValidShalomFieldsWhenAnotherFieldIsInvalid(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        when(fulfillments.findByAgentSession_IdAgentSession(41L)).thenReturn(Optional.of(fulfillment));
        AgencyView view=new AgencyView(9L,7,"AREQUIPA / AV PARRA 379","AQP","AREQUIPA","AREQUIPA",
                "AV PARRA 379","AV. PARRA 379 - Arequipa","01 5007878","LUNES A VIERNES",.98);
        when(search.search("Arequipa Parra")).thenReturn(new SearchResult("MATCH",List.of(view)));
        when(search.get(9L)).thenReturn(view);

        var invalid=service.savePartialShalomData(41L,"Ana Perez","12345678","9338889985","Arequipa Parra");
        assertEquals(List.of("celular"),invalid.missingFields());
        assertEquals(1,invalid.validationErrors().size());
        assertEquals("Ana Perez",fulfillment.getRecipientName());
        assertEquals("12345678",fulfillment.getDocumentNumber());
        assertEquals("9",fulfillment.getShalomCandidateIds());

        var corrected=service.savePartialShalomData(41L,"","","999111222","");
        assertEquals(List.of(),corrected.missingFields());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
    }

    @Test
    void selectingShalomPrefillsTheSavedProfileAsAnEditableDraft(){
        ShalomAgency agency=agency(9L,"AREQUIPA / AV JESUS","AV. JESUS 1100");
        CrmWhatsappShalomProfile profile=new CrmWhatsappShalomProfile();profile.setCliente(session.getCliente());
        profile.setAgency(agency);profile.setRecipientName("Ana Perez");profile.setDocumentNumber("12345678");
        profile.setPhone("999111222");profile.setConfirmedAt(LocalDateTime.now(clock).minusDays(1));
        when(profiles.findByCliente_IdCliente(20)).thenReturn(Optional.of(profile));
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.empty());

        var result=service.selectShalom(41L,"select-profile");

        assertEquals("Ana Perez",result.recipientName());assertEquals("12345678",result.documentNumber());
        assertEquals("999111222",result.phone());assertEquals(9L,result.shalomAgencyId());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM,session.getState());
    }

    @Test
    void rejectsAgencyThatWasNotOffered(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM);
        fulfillment.setShalomCandidateIds("9");when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        assertThrows(IllegalArgumentException.class,()->service.confirmShalomAgency(41L,10L,"confirm-other"));
    }

    @Test
    void searchesAnotherDistrictWithoutLosingPersonalData(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM);
        fulfillment.setRecipientName("Ana Perez");
        fulfillment.setDocumentNumber("12345678");
        fulfillment.setPhone("999111222");
        fulfillment.setShalomCandidateIds("9,10,11");
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        AgencyView sjl=new AgencyView(44L,410,"LIMA / LIMA / SAN JUAN DE LURIGANCHO / CANTO GRANDE",
                "SJL","LIMA","LIMA","CANTO GRANDE","CALLE SAN MARTIN 189",null,null,.99);
        when(search.search("San Juan de Lurigancho")).thenReturn(new SearchResult("MATCH",List.of(sjl)));

        var result=service.saveShalomData(41L,"","","","San Juan de Lurigancho","refine-sjl");

        assertEquals(List.of(sjl),result.agencyCandidates());
        assertEquals("Ana Perez",fulfillment.getRecipientName());
        assertEquals("12345678",fulfillment.getDocumentNumber());
        assertEquals("999111222",fulfillment.getPhone());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
    }

    @Test
    void changesOnlyAgencyAndKeepsNameDniAndPhone(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        fulfillment.setRecipientName("Ana Perez");fulfillment.setDocumentNumber("12345678");
        fulfillment.setPhone("999111222");
        ShalomAgency oldAgency=agency(9L,"AREQUIPA / AV JESUS","AV. JESUS 1100");
        fulfillment.setShalomAgencyReference(oldAgency);fulfillment.setShalomAgency(oldAgency.getName());
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        AgencyView sjl=new AgencyView(44L,410,"LIMA / LIMA / SAN JUAN DE LURIGANCHO / CANTO GRANDE",
                "SJL","LIMA","LIMA","CANTO GRANDE","CALLE SAN MARTIN 189",null,null,.99);
        when(search.search("San Juan de Lurigancho")).thenReturn(new SearchResult("MATCH",List.of(sjl)));

        var result=service.editShalomData(41L,List.of(AgentShalomDataField.AGENCY),"","","",
                "San Juan de Lurigancho","edit-agency");

        assertEquals("Ana Perez",result.recipientName());assertEquals("12345678",result.documentNumber());
        assertEquals("999111222",result.phone());assertEquals(List.of(sjl),result.agencyCandidates());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
    }

    @Test
    void requestsAndThenReplacesOnlyTheSelectedPersonalField(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        fulfillment.setRecipientName("Ana Perez");fulfillment.setDocumentNumber("12345678");
        fulfillment.setPhone("999111222");
        ShalomAgency agency=agency(9L,"AREQUIPA / AV JESUS","AV. JESUS 1100");
        fulfillment.setShalomAgencyReference(agency);fulfillment.setShalomAgency(agency.getName());
        when(fulfillments.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        AgencyView view=new AgencyView(9L,7,agency.getName(),"AQP","AREQUIPA","AREQUIPA",
                "AV JESUS",agency.getAddress(),null,null,1);
        when(search.get(9L)).thenReturn(view);

        var pending=service.editShalomData(41L,List.of(AgentShalomDataField.NAME),"","","","","clear-name");
        assertEquals(null,pending.recipientName());assertEquals("12345678",pending.documentNumber());
        assertEquals("999111222",pending.phone());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM,session.getState());

        var completed=service.editShalomData(41L,List.of(AgentShalomDataField.NAME),"Maria Lopez","","","","new-name");
        assertEquals("Maria Lopez",completed.recipientName());assertEquals("12345678",completed.documentNumber());
        assertEquals("999111222",completed.phone());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,session.getState());
    }

    private ShalomAgency agency(Long id,String name,String address){
        ShalomAgency agency=new ShalomAgency();agency.setIdShalomAgency(id);agency.setExternalId(id.intValue());
        agency.setName(name);agency.setAddress(address);agency.setDepartment("AREQUIPA");agency.setProvince("AREQUIPA");
        agency.setActive(true);agency.setDestinationEnabled(true);return agency;
    }
}
