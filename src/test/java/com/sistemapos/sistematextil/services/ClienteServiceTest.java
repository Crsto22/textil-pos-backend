package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.cliente.ClienteCreateRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteListItemResponse;
import com.sistemapos.sistematextil.util.cliente.ClienteRapidoRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteUpdateRequest;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;
import com.sistemapos.sistematextil.util.usuario.Rol;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private VentaRepository ventaRepository;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, empresaRepository, usuarioRepository, ventaRepository);
    }

    @Test
    void insertarFallaSiTelefonoYaExisteEnLaEmpresa() {
        Usuario usuario = crearUsuarioVentas(1);
        Cliente duplicado = crearCliente(40, 1, "999888777", "Cliente Duplicado");

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(clienteRepository.findFirstByTelefonoAndEmpresa_IdEmpresaOrderByIdClienteAsc("999888777", 1))
                .thenReturn(Optional.of(duplicado));

        ClienteCreateRequest request = new ClienteCreateRequest(
                null,
                TipoDocumento.DNI,
                "12345678",
                "Cliente Nuevo",
                "999888777",
                "cliente@test.com",
                "Av. Lima 123");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.insertar(request, "ventas@test.com"));

        assertEquals("El telefono '999888777' ya existe en otro cliente de esta empresa", error.getMessage());
        verifyNoInteractions(empresaRepository, ventaRepository);
    }

    @Test
    void insertarTraduceViolacionDeIndiceUnicoDeMysql() {
        Usuario usuario = crearUsuarioVentas(1);

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(clienteRepository.findFirstByTelefonoAndEmpresa_IdEmpresaOrderByIdClienteAsc("999888777", 1))
                .thenReturn(Optional.empty());
        when(clienteRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Cliente.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        ClienteCreateRequest request = new ClienteCreateRequest(
                null,
                TipoDocumento.DNI,
                "12345678",
                "Cliente Nuevo",
                "999888777",
                "cliente@test.com",
                "Av. Lima 123");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.insertar(request, "ventas@test.com"));

        assertEquals("El telefono '999888777' ya existe en otro cliente de esta empresa", error.getMessage());
    }

    @Test
    void actualizarPermiteMantenerElMismoTelefonoDelCliente() {
        Usuario usuario = crearUsuarioVentas(1);
        Cliente cliente = crearCliente(15, 1, "999888777", "Cliente Actual");
        cliente.setUsuarioCreacion(usuario);
        cliente.setTipoDocumento(TipoDocumento.DNI);
        cliente.setNroDocumento("12345678");
        cliente.setCorreo("actual@test.com");
        cliente.setDireccion("Direccion actual");
        cliente.setEstado("ACTIVO");
        cliente.setFechaCreacion(LocalDateTime.now().minusDays(2));

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(clienteRepository.findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(15, 1))
                .thenReturn(Optional.of(cliente));
        when(clienteRepository.findFirstByTelefonoAndEmpresa_IdEmpresaAndIdClienteNotOrderByIdClienteAsc("999888777", 1, 15))
                .thenReturn(Optional.empty());
        when(clienteRepository.saveAndFlush(cliente)).thenReturn(cliente);

        ClienteUpdateRequest request = new ClienteUpdateRequest(
                1,
                TipoDocumento.DNI,
                "12345678",
                "Cliente Actualizado",
                "999888777",
                "nuevo@test.com",
                "Av. Peru 456",
                "ACTIVO");

        ClienteListItemResponse response = service.actualizar(15, request, "ventas@test.com");

        assertEquals(15, response.idCliente());
        assertEquals("999888777", response.telefono());
        assertEquals("Cliente Actualizado", response.nombres());
        verify(clienteRepository).saveAndFlush(cliente);
    }

    @Test
    void crearRapidoFallaSiElTelefonoYaPerteneceAUnClienteEliminado() {
        Usuario usuario = crearUsuarioVentas(1);
        Cliente eliminado = crearCliente(21, 1, "900111222", "Cliente Eliminado");
        eliminado.setDeletedAt(LocalDateTime.now().minusDays(1));

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(clienteRepository.findFirstByTelefonoAndDeletedAtIsNullAndEmpresa_IdEmpresaOrderByIdClienteAsc("900111222", 1))
                .thenReturn(Optional.empty());
        when(clienteRepository.findFirstByTelefonoAndEmpresa_IdEmpresaOrderByIdClienteAsc("900111222", 1))
                .thenReturn(Optional.of(eliminado));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.crearRapido(new ClienteRapidoRequest("900111222"), "ventas@test.com"));

        assertEquals("El telefono '900111222' ya existe en otro cliente de esta empresa", error.getMessage());
    }

    private Usuario crearUsuarioVentas(Integer idEmpresa) {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(idEmpresa);
        empresa.setNombre("Empresa Test");

        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(10);
        sucursal.setNombre("Sucursal Test");
        sucursal.setEmpresa(empresa);

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(5);
        usuario.setNombre("Ana");
        usuario.setApellido("Ventas");
        usuario.setCorreo("ventas@test.com");
        usuario.setRol(Rol.VENTAS);
        usuario.setSucursal(sucursal);
        return usuario;
    }

    private Cliente crearCliente(Integer idCliente, Integer idEmpresa, String telefono, String nombres) {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(idEmpresa);
        empresa.setNombre("Empresa Test");

        Cliente cliente = new Cliente();
        cliente.setIdCliente(idCliente);
        cliente.setEmpresa(empresa);
        cliente.setTelefono(telefono);
        cliente.setNombres(nombres);
        cliente.setTipoDocumento(TipoDocumento.SIN_DOC);
        cliente.setEstado("ACTIVO");
        cliente.setFechaCreacion(LocalDateTime.now().minusDays(1));
        return cliente;
    }
}
