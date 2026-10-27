package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;

import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappConversationTag;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappTag;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationTagRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappTagRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;

@ExtendWith(MockitoExtension.class)
class CrmShippingChatServiceTest {
    @Mock CrmWhatsappConversationRepository conversationRepository;
    @Mock CrmWhatsappConversationTagRepository conversationTagRepository;
    @Mock CrmWhatsappMessageRepository messageRepository;
    @Mock CrmWhatsappTagRepository tagRepository;
    @Mock EmpresaRepository empresaRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock CrmWhatsappBridgeService bridgeService;
    @Mock CrmWhatsappNumberChangeNoticeService numberChangeNoticeService;
    @Mock CrmWarehouseEventService warehouseEventService;
    @Mock S3StorageService storageService;
    @InjectMocks CrmShippingChatService service;

    @Test
    void salesRoleCannotOpenShippingChat() {
        Usuario actor = new Usuario();
        actor.setIdUsuario(10);
        actor.setRol(Rol.VENTAS);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.conversations(actor, "", null, 0, 10));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void salesConversationCannotBeReadThroughShippingApi() {
        Usuario actor = administrator(7);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setArea(CrmWhatsappArea.SALES);
        connection.setEmpresa(actor.getSucursal().getEmpresa());
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(22L);
        conversation.setConnection(connection);
        when(conversationRepository.findById(22L)).thenReturn(Optional.of(conversation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.messages(22L, null, null, 15, actor));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void shippingConversationPageIsLimitedToFiftyItems() {
        Usuario actor = administrator(7);
        when(conversationRepository.findShippingChatConversations(eq(7), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.conversations(actor, "", null, 0, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(conversationRepository).findShippingChatConversations(eq(7), eq(null), eq(null), pageable.capture());
        assertEquals(50, pageable.getValue().getPageSize());
    }

    @Test
    void warehouseRoleListsShippingConversationsWithMessagesFromItsCompany() {
        Usuario actor = warehouse(7, 12);
        when(conversationRepository.findShippingChatConversations(eq(7), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.conversations(actor, "", null, 0, 10);

        verify(conversationRepository).findShippingChatConversations(eq(7), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void warehouseRoleCanOpenAnyShippingConversationFromItsCompany() {
        Usuario actor = warehouse(7, 12);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setArea(CrmWhatsappArea.SHIPPING);
        connection.setEmpresa(actor.getSucursal().getEmpresa());
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(22L);
        conversation.setConnection(connection);
        when(conversationRepository.findById(22L)).thenReturn(Optional.of(conversation));
        var result = service.conversation(22L, actor);

        assertEquals(22L, result.id());
    }

    @Test
    void administratorCanReadMatchingSalesConversationFromShippingChat() {
        Usuario actor = administrator(7);
        CrmWhatsappConnection shippingConnection = new CrmWhatsappConnection();
        shippingConnection.setArea(CrmWhatsappArea.SHIPPING);
        shippingConnection.setEmpresa(actor.getSucursal().getEmpresa());
        CrmWhatsappConversation shipping = new CrmWhatsappConversation();
        shipping.setIdConversation(22L);
        shipping.setConnection(shippingConnection);
        shipping.setPhone("51932889985@s.whatsapp.net");
        shipping.setPhoneNumber("51932889985");
        CrmWhatsappConversation sales = new CrmWhatsappConversation();
        sales.setIdConversation(11L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(33L);
        message.setConversation(sales);
        message.setMediaStoragePath("crm/whatsapp/sales/receipt.jpg");

        when(conversationRepository.findById(22L)).thenReturn(Optional.of(shipping));
        when(conversationRepository.findSalesConversationForShippingContact(
                eq(7), eq(null), eq("51932889985@s.whatsapp.net"), eq("51932889985"), any(Pageable.class)))
                .thenReturn(List.of(sales));
        when(messageRepository.findMessageHistory(eq(11L), eq(null), any(Pageable.class)))
                .thenReturn(List.of(message));

        var result = service.salesMessages(22L, null, null, 10, actor);

        assertTrue(result.available());
        assertEquals("/api/crm/whatsapp/shipping/conversations/22/sales-messages/33/media",
                result.content().getFirst().mediaUrl());
    }

    @Test
    void warehouseRoleCanReadMediaFromMatchingSalesConversation() {
        Usuario actor = warehouse(7, 12);
        CrmWhatsappConversation shipping = shippingConversation(22L, actor.getSucursal().getEmpresa());
        shipping.setPhone("51932889985@s.whatsapp.net");
        shipping.setPhoneNumber("51932889985");
        CrmWhatsappConversation sales = new CrmWhatsappConversation();
        sales.setIdConversation(11L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(33L);
        message.setConversation(sales);
        message.setMediaStoragePath("crm/whatsapp/sales/receipt.jpg");
        message.setMediaMimeType("image/jpeg");
        message.setMediaFileName("receipt.jpg");

        when(conversationRepository.findById(22L)).thenReturn(Optional.of(shipping));
        when(conversationRepository.findSalesConversationForShippingContact(
                eq(7), eq(null), eq("51932889985@s.whatsapp.net"), eq("51932889985"), any(Pageable.class)))
                .thenReturn(List.of(sales));
        when(messageRepository.findById(33L)).thenReturn(Optional.of(message));
        when(storageService.readBytes("crm/whatsapp/sales/receipt.jpg")).thenReturn(new byte[] { 1, 2, 3 });

        var result = service.salesMedia(22L, 33L, actor);

        assertArrayEquals(new byte[] { 1, 2, 3 }, result.bytes());
        assertEquals("image/jpeg", result.contentType());
        assertEquals("receipt.jpg", result.fileName());
    }

    @Test
    void warehouseRoleCannotReadMediaFromAnotherSalesConversation() {
        Usuario actor = warehouse(7, 12);
        CrmWhatsappConversation shipping = shippingConversation(22L, actor.getSucursal().getEmpresa());
        shipping.setPhone("51932889985@s.whatsapp.net");
        shipping.setPhoneNumber("51932889985");
        CrmWhatsappConversation matchingSales = new CrmWhatsappConversation();
        matchingSales.setIdConversation(11L);
        CrmWhatsappConversation otherSales = new CrmWhatsappConversation();
        otherSales.setIdConversation(12L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(33L);
        message.setConversation(otherSales);
        message.setMediaStoragePath("crm/whatsapp/sales/private.jpg");

        when(conversationRepository.findById(22L)).thenReturn(Optional.of(shipping));
        when(conversationRepository.findSalesConversationForShippingContact(
                eq(7), eq(null), eq("51932889985@s.whatsapp.net"), eq("51932889985"), any(Pageable.class)))
                .thenReturn(List.of(matchingSales));
        when(messageRepository.findById(33L)).thenReturn(Optional.of(message));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.salesMedia(22L, 33L, actor));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void shippingReplyStoresTheAuthenticatedWarehouseUser() {
        Usuario actor = warehouse(7, 12);
        actor.setNombre("Ana");
        actor.setApellido("Envios");
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setArea(CrmWhatsappArea.SHIPPING);
        connection.setEmpresa(actor.getSucursal().getEmpresa());
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(22L);
        conversation.setConnection(connection);
        conversation.setPhone("51932889985@s.whatsapp.net");
        when(conversationRepository.findById(22L)).thenReturn(Optional.of(conversation));
        when(bridgeService.enviarTexto(eq(CrmWhatsappArea.SHIPPING), eq(conversation.getPhone()),
                eq("Pedido enviado"), eq(null), anyString())).thenReturn(ResponseEntity.ok("{}"));
        when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.sendText(22L, new CrmShippingChatService.MessageRequest("Pedido enviado"), actor);

        ArgumentCaptor<com.sistemapos.sistematextil.model.CrmWhatsappMessage> message =
                ArgumentCaptor.forClass(com.sistemapos.sistematextil.model.CrmWhatsappMessage.class);
        verify(messageRepository).save(message.capture());
        assertSame(actor, message.getValue().getActor());
        assertEquals("Ana Envios", message.getValue().getActorName());
    }

    @Test
    void warehouseRoleAssignsCompanyTagToShippingConversationWithoutSalesAssignment() {
        Usuario actor = warehouse(7, 12);
        CrmWhatsappConversation conversation = shippingConversation(22L, actor.getSucursal().getEmpresa());
        CrmWhatsappTag tag = new CrmWhatsappTag();
        tag.setIdTag(5L);
        tag.setNombre("Urgente");
        tag.setColor("#ef4444");
        tag.setEmpresa(actor.getSucursal().getEmpresa());
        when(conversationRepository.findById(22L)).thenReturn(Optional.of(conversation));
        when(tagRepository.findByIdTagAndEmpresa_IdEmpresaAndDeletedAtIsNull(5L, 7)).thenReturn(Optional.of(tag));
        when(conversationTagRepository.findFirstByConversation_IdConversationAndTag_IdTagOrderByIdConversationTagAsc(22L, 5L))
                .thenReturn(Optional.empty());
        when(conversationTagRepository.findActiveByConversationId(22L)).thenAnswer(invocation -> {
            CrmWhatsappConversationTag relation = new CrmWhatsappConversationTag();
            relation.setConversation(conversation);
            relation.setTag(tag);
            return List.of(relation);
        });

        var result = service.assignTag(22L, 5L, actor);

        assertEquals("Urgente", result.getFirst().label());
        verify(conversationTagRepository).save(any(CrmWhatsappConversationTag.class));
    }

    @Test
    void warehouseTagApiRejectsSalesConversation() {
        Usuario actor = warehouse(7, 12);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setArea(CrmWhatsappArea.SALES);
        connection.setEmpresa(actor.getSucursal().getEmpresa());
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(22L);
        conversation.setConnection(connection);
        when(conversationRepository.findById(22L)).thenReturn(Optional.of(conversation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.assignTag(22L, 5L, actor));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void shippingConversationFilterUsesAnActiveCompanyTag() {
        Usuario actor = warehouse(7, 12);
        CrmWhatsappTag tag = new CrmWhatsappTag();
        tag.setIdTag(5L);
        when(tagRepository.findByIdTagAndEmpresa_IdEmpresaAndDeletedAtIsNull(5L, 7)).thenReturn(Optional.of(tag));
        when(conversationRepository.findShippingChatConversations(eq(7), eq(null), eq(5L), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.conversations(actor, "", 5L, 0, 10);

        verify(conversationRepository).findShippingChatConversations(eq(7), eq(null), eq(5L), any(Pageable.class));
    }

    private CrmWhatsappConversation shippingConversation(long id, Empresa company) {
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setArea(CrmWhatsappArea.SHIPPING);
        connection.setEmpresa(company);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(id);
        conversation.setConnection(connection);
        return conversation;
    }

    private Usuario administrator(int companyId) {
        Empresa company = new Empresa();
        company.setIdEmpresa(companyId);
        Sucursal branch = new Sucursal();
        branch.setEmpresa(company);
        Usuario actor = new Usuario();
        actor.setIdUsuario(1);
        actor.setRol(Rol.ADMINISTRADOR);
        actor.setEstado("ACTIVO");
        actor.setSucursal(branch);
        return actor;
    }

    private Usuario warehouse(int companyId, int branchId) {
        Usuario actor = administrator(companyId);
        actor.setRol(Rol.ALMACEN);
        actor.getSucursal().setIdSucursal(branchId);
        return actor;
    }
}
