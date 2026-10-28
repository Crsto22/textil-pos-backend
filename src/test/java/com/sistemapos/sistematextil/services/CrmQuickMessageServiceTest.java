package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmQuickMessage;
import com.sistemapos.sistematextil.model.CrmQuickMessageBlock;
import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmQuickMessageBlockRepository;
import com.sistemapos.sistematextil.repositories.CrmQuickMessageRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmQuickMessageService.SendQuickMessageRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

@ExtendWith(MockitoExtension.class)
class CrmQuickMessageServiceTest {

    @Mock private CrmQuickMessageRepository commandRepository;
    @Mock private CrmQuickMessageBlockRepository blockRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private S3StorageService storageService;
    @Mock private CrmWhatsappChatService chatService;
    @Mock private CrmShippingChatService shippingChatService;

    private CrmQuickMessageService service;
    private Usuario actor;
    private CrmQuickMessage command;

    @BeforeEach
    void setUp() {
        service = new CrmQuickMessageService(commandRepository, blockRepository, usuarioRepository,
                sucursalRepository, storageService, chatService, shippingChatService);

        Empresa company = new Empresa();
        company.setIdEmpresa(7);
        Sucursal branch = new Sucursal();
        branch.setEmpresa(company);
        actor = new Usuario();
        actor.setIdUsuario(12);
        actor.setEstado("ACTIVO");
        actor.setRol(Rol.VENTAS);
        actor.setAccesoCrm(true);
        actor.setSucursal(branch);

        command = new CrmQuickMessage();
        command.setIdQuickMessage(31L);
        command.setEmpresa(company);
        command.setCreatedBy(actor);
        command.setKeyword("preventa");
        command.setArea(CrmWhatsappArea.SALES);
        command.setBlocks(new ArrayList<>());

        CrmQuickMessageBlock text = block(1L, "TEXT", 0, "Hola", null);
        CrmQuickMessageBlock image = block(2L, "IMAGE", 1, "Catalogo", "/storage/quick/image.png");
        image.setMediaFileName("image.png");
        image.setMediaMimeType("image/png");
        command.getBlocks().add(text);
        command.getBlocks().add(image);

        when(usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(12)).thenReturn(Optional.of(actor));
        lenient().when(commandRepository.findByIdQuickMessageAndEmpresa_IdEmpresaAndDeletedAtIsNull(31L, 7))
                .thenReturn(Optional.of(command));
    }

    @Test
    void sendsTextAndImageInConfiguredOrder() {
        var result = service.send(31L, new SendQuickMessageRequest(99L), actor);

        assertEquals("COMPLETED", result.status());
        assertEquals(2, result.total());
        assertEquals(2, result.sentCount());
        assertNull(result.error());

        InOrder order = inOrder(chatService);
        order.verify(chatService).enviarMensaje(eq(99L), any(), eq(actor));
        order.verify(chatService).enviarMediaDesdeStorage(99L, "/storage/quick/image.png",
                "image.png", "image/png", "Catalogo", null, actor);
    }

    @Test
    void stopsAfterFirstFailedBlockWithoutRetryingPreviousBlocks() {
        when(chatService.enviarMediaDesdeStorage(99L, "/storage/quick/image.png",
                "image.png", "image/png", "Catalogo", null, actor))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "WhatsApp no disponible"));

        var result = service.send(31L, new SendQuickMessageRequest(99L), actor);

        assertEquals("PARTIAL", result.status());
        assertEquals(1, result.sentCount());
        assertEquals(1, result.failedPosition());
        assertEquals("WhatsApp no disponible", result.error());
        verify(chatService).enviarMensaje(eq(99L), any(), eq(actor));
    }

    @Test
    void deniesWarehouseUsersFromSalesCommandsEvenWhenTheyHaveGeneralCrmAccess() {
        actor.setRol(Rol.ALMACEN);
        actor.setAccesoCrm(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.get(31L, actor));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("No tienes acceso a los comandos de esta area", exception.getReason());
    }

    @Test
    void allowsSalesUsersRegardlessOfGeneralCrmAccessFlag() {
        actor.setAccesoCrm(false);

        var result = service.get(31L, actor);

        assertEquals(31L, result.id());
    }

    @Test
    void keepsCombinedSalesWarehouseRoleAuthorized() {
        actor.setRol(Rol.VENTAS_ALMACEN);
        actor.setAccesoCrm(false);

        var result = service.get(31L, actor);

        assertEquals(31L, result.id());
    }

    @Test
    void warehouseSendsShippingTextAndImageInConfiguredOrder() {
        actor.setRol(Rol.ALMACEN);
        command.setArea(CrmWhatsappArea.SHIPPING);

        var result = service.send(31L, new SendQuickMessageRequest(99L), actor);

        assertEquals("COMPLETED", result.status());
        InOrder order = inOrder(shippingChatService);
        order.verify(shippingChatService).sendText(eq(99L), any(), eq(actor));
        order.verify(shippingChatService).sendMediaFromStorage(99L, "/storage/quick/image.png",
                "image.png", "image/png", "Catalogo", actor);
    }

    private CrmQuickMessageBlock block(Long id, String type, int position, String body, String storagePath) {
        CrmQuickMessageBlock block = new CrmQuickMessageBlock();
        block.setIdBlock(id);
        block.setQuickMessage(command);
        block.setBlockType(type);
        block.setPosition(position);
        block.setBody(body);
        block.setMediaStoragePath(storagePath);
        return block;
    }
}
