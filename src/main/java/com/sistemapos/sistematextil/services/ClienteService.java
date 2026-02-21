package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.cliente.ClienteCreateRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteListItemResponse;
import com.sistemapos.sistematextil.util.cliente.ClienteUpdateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<ClienteListItemResponse> listarPaginado(int page, String correoUsuarioAutenticado) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }

        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCliente").ascending());

        Page<Cliente> clientesPage;
        if (esAdministrador(usuarioAutenticado)) {
            clientesPage = clienteRepository.findByDeletedAtIsNull(pageable);
        } else if (esVentas(usuarioAutenticado)) {
            Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
            clientesPage = clienteRepository.findByDeletedAtIsNullAndSucursal_IdSucursal(pageable, idSucursalUsuario);
        } else {
            throw new RuntimeException("No tiene permisos para listar clientes");
        }

        Page<ClienteListItemResponse> clientes = clientesPage.map(this::toListItemResponse);
        return PagedResponse.fromPage(clientes);
    }

    public PagedResponse<ClienteListItemResponse> buscarPaginado(String term, int page, String correoUsuarioAutenticado) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }

        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        String termNormalizado = (term == null || term.isBlank()) ? null : term.trim();
        Integer idSucursalFiltro = esVentas(usuarioAutenticado) ? obtenerIdSucursalUsuario(usuarioAutenticado) : null;

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCliente").ascending());
        Page<ClienteListItemResponse> clientes = clienteRepository
                .buscarPorNombreODni(termNormalizado, idSucursalFiltro, TipoDocumento.DNI, pageable)
                .map(this::toListItemResponse);

        return PagedResponse.fromPage(clientes);
    }

    @Transactional
    public ClienteListItemResponse insertar(ClienteCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        if (esVentas(usuarioAutenticado)) {
            Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
            if (!idSucursalUsuario.equals(request.idSucursal())) {
                throw new RuntimeException("No tiene permisos para registrar clientes en otra sucursal");
            }
        }

        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(request.idSucursal())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        String nroDocumento = normalizarYValidarDocumento(request.tipoDocumento(), request.nroDocumento());

        Cliente cliente = new Cliente();
        cliente.setSucursal(sucursal);
        cliente.setUsuarioCreacion(usuarioAutenticado);
        cliente.setTipoDocumento(request.tipoDocumento());
        cliente.setNroDocumento(nroDocumento);
        cliente.setNombres(request.nombres().trim());
        cliente.setTelefono(normalizarNullable(request.telefono()));
        cliente.setCorreo(normalizarNullable(request.correo()));
        cliente.setDireccion(normalizarNullable(request.direccion()));
        cliente.setEstado("ACTIVO");
        cliente.setFechaCreacion(LocalDateTime.now());
        cliente.setDeletedAt(null);

        Cliente creado = clienteRepository.save(cliente);
        return toListItemResponse(creado);
    }

    @Transactional
    public ClienteListItemResponse actualizar(Integer idCliente, ClienteUpdateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Cliente cliente;
        Integer idSucursalUsuario = null;
        if (esAdministrador(usuarioAutenticado)) {
            cliente = clienteRepository.findByIdClienteAndDeletedAtIsNull(idCliente)
                    .orElseThrow(() -> new RuntimeException("Cliente con ID " + idCliente + " no encontrado"));
        } else {
            idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
            cliente = clienteRepository.findByIdClienteAndDeletedAtIsNullAndSucursal_IdSucursal(idCliente, idSucursalUsuario)
                    .orElseThrow(() -> new RuntimeException("Cliente con ID " + idCliente + " no encontrado"));
        }

        if (esVentas(usuarioAutenticado) && !idSucursalUsuario.equals(request.idSucursal())) {
            throw new RuntimeException("No tiene permisos para mover clientes a otra sucursal");
        }

        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(request.idSucursal())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        String nroDocumento = normalizarYValidarDocumento(request.tipoDocumento(), request.nroDocumento());

        cliente.setSucursal(sucursal);
        cliente.setTipoDocumento(request.tipoDocumento());
        cliente.setNroDocumento(nroDocumento);
        cliente.setNombres(request.nombres().trim());
        cliente.setTelefono(normalizarNullable(request.telefono()));
        cliente.setCorreo(normalizarNullable(request.correo()));
        cliente.setDireccion(normalizarNullable(request.direccion()));
        cliente.setEstado(request.estado().toUpperCase());

        Cliente actualizado = clienteRepository.save(cliente);
        return toListItemResponse(actualizado);
    }

    @Transactional
    public void eliminarLogico(Integer idCliente, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Cliente cliente;
        if (esAdministrador(usuarioAutenticado)) {
            cliente = clienteRepository.findByIdClienteAndDeletedAtIsNull(idCliente)
                    .orElseThrow(() -> new RuntimeException("Cliente con ID " + idCliente + " no encontrado o ya eliminado"));
        } else {
            Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
            cliente = clienteRepository.findByIdClienteAndDeletedAtIsNullAndSucursal_IdSucursal(idCliente, idSucursalUsuario)
                    .orElseThrow(() -> new RuntimeException("Cliente con ID " + idCliente + " no encontrado o ya eliminado"));
        }

        cliente.setEstado("INACTIVO");
        cliente.setDeletedAt(LocalDateTime.now());
        clienteRepository.save(cliente);
    }

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado debe tener rol ADMINISTRADOR o VENTAS");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private boolean esVentas(Usuario usuario) {
        return usuario.getRol() == Rol.VENTAS;
    }

    private String normalizarYValidarDocumento(TipoDocumento tipoDocumento, String nroDocumento) {
        if (tipoDocumento == TipoDocumento.SIN_DOC) {
            return null;
        }

        String normalizado = normalizarNullable(nroDocumento);
        if (normalizado == null) {
            throw new RuntimeException("El nro_documento es obligatorio para el tipo " + tipoDocumento.name());
        }

        return switch (tipoDocumento) {
            case DNI -> validarRegex(normalizado, "\\d{8}", "Para DNI el nro_documento debe tener 8 digitos");
            case RUC -> validarRegex(normalizado, "\\d{11}", "Para RUC el nro_documento debe tener 11 digitos");
            case CE -> validarRegex(normalizado.toUpperCase(), "[A-Z0-9]{6,20}",
                    "Para CE el nro_documento debe ser alfanumerico de 6 a 20 caracteres");
            case SIN_DOC -> null;
        };
    }

    private String validarRegex(String value, String regex, String message) {
        if (!value.matches(regex)) {
            throw new RuntimeException(message);
        }
        return value;
    }

    private String normalizarNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ClienteListItemResponse toListItemResponse(Cliente cliente) {
        Integer idSucursal = cliente.getSucursal() != null ? cliente.getSucursal().getIdSucursal() : null;
        String nombreSucursal = cliente.getSucursal() != null ? cliente.getSucursal().getNombre() : null;

        Integer idUsuarioCreacion = cliente.getUsuarioCreacion() != null ? cliente.getUsuarioCreacion().getIdUsuario() : null;
        String nombreUsuarioCreacion = null;
        if (cliente.getUsuarioCreacion() != null) {
            nombreUsuarioCreacion = cliente.getUsuarioCreacion().getNombre() + " "
                    + cliente.getUsuarioCreacion().getApellido();
        }

        return new ClienteListItemResponse(
                cliente.getIdCliente(),
                cliente.getTipoDocumento() != null ? cliente.getTipoDocumento().name() : null,
                cliente.getNroDocumento(),
                cliente.getNombres(),
                cliente.getTelefono(),
                cliente.getCorreo(),
                cliente.getDireccion(),
                cliente.getEstado(),
                cliente.getFechaCreacion(),
                idSucursal,
                nombreSucursal,
                idUsuarioCreacion,
                nombreUsuarioCreacion);
    }
}
