package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWarehouseOrder;
import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.CrmWarehouseOrderRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;

@ExtendWith(MockitoExtension.class)
class CrmWarehouseServiceTest {
    @Mock CrmWarehouseOrderRepository orderRepository;
    @Mock CrmWhatsappConnectionRepository connectionRepository;
    @Mock CrmWhatsappConversationRepository conversationRepository;
    @Mock CrmWhatsappMessageRepository messageRepository;
    @Mock CrmWhatsappFulfillmentRepository fulfillmentRepository;
    @Mock VentaDetalleRepository saleDetailRepository;
    @Mock VentaRepository saleRepository;
    @Mock ProductoColorImagenRepository productImageRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock CrmWhatsappBridgeService bridgeService;
    @Mock CrmWhatsappConnectionStateService connectionStateService;
    @Mock CrmWhatsappNumberChangeNoticeService numberChangeNoticeService;
    @Mock CrmWarehouseEventService warehouseEventService;
    @Mock S3StorageService storageService;
    @InjectMocks CrmWarehouseService service;

    @Test
    void administratorCanStartShippingChatWithoutSalesConversation() {
        Empresa company = new Empresa();
        company.setIdEmpresa(7);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        branch.setEmpresa(company);
        Usuario administrator = new Usuario();
        administrator.setIdUsuario(1);
        administrator.setRol(Rol.ADMINISTRADOR);
        administrator.setSucursal(branch);

        Cliente customer = new Cliente();
        customer.setNombres("Cliente nuevo");
        customer.setTelefono("932 889 985");
        Venta sale = new Venta();
        sale.setIdVenta(90);
        sale.setSucursal(branch);
        sale.setCliente(customer);

        CrmWhatsappConnection shipping = new CrmWhatsappConnection();
        shipping.setIdConnection(20L);
        shipping.setArea(CrmWhatsappArea.SHIPPING);
        shipping.setEmpresa(company);

        when(orderRepository.findByVenta_IdVenta(90)).thenReturn(Optional.empty());
        when(saleRepository.findById(90)).thenReturn(Optional.of(sale));
        when(orderRepository.save(any(CrmWarehouseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionRepository.findByEmpresa_IdEmpresaAndArea(7, CrmWhatsappArea.SHIPPING))
                .thenReturn(Optional.of(shipping));
        when(conversationRepository.findByConnection_IdConnectionAndPhone(20L, "51932889985@s.whatsapp.net"))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(CrmWhatsappConversation.class))).thenAnswer(invocation -> {
            CrmWhatsappConversation conversation = invocation.getArgument(0);
            conversation.setIdConversation(44L);
            return conversation;
        });

        var result = service.openShippingConversation(90, administrator);

        assertEquals(44L, result.conversationId());
    }

    @Test
    void reusesShippingChatWithSameNormalizedPhoneEvenWhenWhatsappIdentityIsLid() {
        Empresa company = new Empresa();
        company.setIdEmpresa(7);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        branch.setEmpresa(company);
        Usuario administrator = new Usuario();
        administrator.setIdUsuario(1);
        administrator.setRol(Rol.ADMINISTRADOR);
        administrator.setSucursal(branch);

        Cliente customer = new Cliente();
        customer.setNombres("Cliente existente");
        customer.setTelefono("924 127 067");
        Venta sale = new Venta();
        sale.setIdVenta(91);
        sale.setSucursal(branch);
        sale.setCliente(customer);

        CrmWhatsappConnection shipping = new CrmWhatsappConnection();
        shipping.setIdConnection(20L);
        shipping.setArea(CrmWhatsappArea.SHIPPING);
        shipping.setEmpresa(company);

        CrmWhatsappConversation lidConversation = new CrmWhatsappConversation();
        lidConversation.setIdConversation(55L);
        lidConversation.setConnection(shipping);
        lidConversation.setPhone("163569568059625@lid");
        lidConversation.setPhoneNumber("924127067");

        when(orderRepository.findByVenta_IdVenta(91)).thenReturn(Optional.empty());
        when(saleRepository.findById(91)).thenReturn(Optional.of(sale));
        when(orderRepository.save(any(CrmWarehouseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionRepository.findByEmpresa_IdEmpresaAndArea(7, CrmWhatsappArea.SHIPPING))
                .thenReturn(Optional.of(shipping));
        when(conversationRepository.findByConnection_IdConnectionAndPhone(20L, "51924127067@s.whatsapp.net"))
                .thenReturn(Optional.empty());
        when(conversationRepository.findShippingIdentityCandidatesForUpdate(
                eq(20L), eq("924127067"), eq("51924127067@s.whatsapp.net"), any()))
                .thenReturn(List.of(lidConversation));

        var result = service.openShippingConversation(91, administrator);

        assertEquals(55L, result.conversationId());
        verify(conversationRepository, never()).save(any(CrmWhatsappConversation.class));
    }
}
