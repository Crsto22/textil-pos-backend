package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.cliente.ClienteCompraResumenResponse;
import com.sistemapos.sistematextil.util.cliente.ClienteCreateRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteDetalleResponse;
import com.sistemapos.sistematextil.util.cliente.ClienteListItemResponse;
import com.sistemapos.sistematextil.util.cliente.ClienteRapidoRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteUpdateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private static final String PREFIJO_CLIENTE_RAPIDO = "CLIENTE ";

    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<ClienteListItemResponse> listarPaginado(
            int page,
            String tipoDocumento,
            String correoUsuarioAutenticado) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }

        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCliente").ascending());
        TipoDocumento tipoDocumentoFiltro = normalizarTipoDocumentoParaFiltro(tipoDocumento);
        Integer idEmpresaFiltro = resolverEmpresaContexto(usuarioAutenticado).getIdEmpresa();

        Page<Cliente> clientesPage = clienteRepository.buscarConFiltros(
                null,
                idEmpresaFiltro,
                tipoDocumentoFiltro,
                pageable);
        Page<ClienteListItemResponse> clientes = clientesPage.map(this::toListItemResponse);
        return PagedResponse.fromPage(clientes);
    }

    public PagedResponse<ClienteListItemResponse> buscarPaginado(
            String term,
            int page,
            String tipoDocumento,
            String correoUsuarioAutenticado) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }

        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        String termNormalizado = (term == null || term.isBlank()) ? null : term.trim();
        Integer idEmpresaFiltro = resolverEmpresaContexto(usuarioAutenticado).getIdEmpresa();
        TipoDocumento tipoDocumentoFiltro = normalizarTipoDocumentoParaFiltro(tipoDocumento);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCliente").ascending());
        Page<ClienteListItemResponse> clientes = clienteRepository
                .buscarConFiltros(termNormalizado, idEmpresaFiltro, tipoDocumentoFiltro, pageable)
                .map(this::toListItemResponse);

        return PagedResponse.fromPage(clientes);
    }

    public ClienteDetalleResponse obtenerDetalle(Integer idCliente, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Cliente cliente = obtenerClienteConAcceso(idCliente, usuarioAutenticado);
        long comprasTotales = ventaRepository.countByClienteIdClienteAndDeletedAtIsNullAndEstado(idCliente, "EMITIDA");
        BigDecimal montoTotalCompras = valorMonetarioSeguro(
                ventaRepository.sumarTotalPorClienteYEstado(idCliente, "EMITIDA"));
        List<ClienteCompraResumenResponse> ultimasCompras = ventaRepository
                .findTop3ByClienteIdClienteAndDeletedAtIsNullAndEstadoOrderByFechaDesc(idCliente, "EMITIDA")
                .stream()
                .map(this::toCompraResumenResponse)
                .toList();

        return toDetalleResponse(cliente, comprasTotales, montoTotalCompras, ultimasCompras);
    }

    @Transactional
    public ClienteListItemResponse crearRapido(ClienteRapidoRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        Empresa empresa = resolverEmpresaContexto(usuarioAutenticado);
        String telefono = normalizarTelefono(request.telefono());

        Cliente clienteExistente = clienteRepository
                .findFirstByTelefonoAndDeletedAtIsNullAndEmpresa_IdEmpresaOrderByIdClienteAsc(
                        telefono,
                        empresa.getIdEmpresa())
                .orElse(null);
        if (clienteExistente != null) {
            return toListItemResponse(clienteExistente);
        }
        validarTelefonoUnicoEnEmpresa(telefono, empresa.getIdEmpresa(), null);

        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresa);
        cliente.setUsuarioCreacion(usuarioAutenticado);
        cliente.setTipoDocumento(TipoDocumento.SIN_DOC);
        cliente.setNroDocumento(null);
        cliente.setNombres(PREFIJO_CLIENTE_RAPIDO + telefono);
        cliente.setTelefono(telefono);
        cliente.setCorreo(null);
        cliente.setDireccion(null);
        cliente.setEstado("ACTIVO");
        cliente.setFechaCreacion(LocalDateTime.now());
        cliente.setDeletedAt(null);

        try {
            Cliente creado = clienteRepository.saveAndFlush(cliente);
            return toListItemResponse(creado);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException(mensajeTelefonoDuplicado(telefono));
        }
    }

    @Transactional
    public ClienteListItemResponse insertar(ClienteCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        Empresa empresa = resolverEmpresaParaCrear(request.idEmpresa(), usuarioAutenticado);

        String nroDocumento = normalizarYValidarDocumento(request.tipoDocumento(), request.nroDocumento());
        String telefono = normalizarTelefonoOpcional(request.telefono());
        validarTelefonoUnicoEnEmpresa(telefono, empresa.getIdEmpresa(), null);

        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresa);
        cliente.setUsuarioCreacion(usuarioAutenticado);
        cliente.setTipoDocumento(request.tipoDocumento());
        cliente.setNroDocumento(nroDocumento);
        cliente.setNombres(request.nombres().trim());
        cliente.setTelefono(telefono);
        cliente.setCorreo(normalizarNullable(request.correo()));
        cliente.setDireccion(normalizarNullable(request.direccion()));
        cliente.setEstado("ACTIVO");
        cliente.setFechaCreacion(LocalDateTime.now());
        cliente.setDeletedAt(null);

        try {
            Cliente creado = clienteRepository.saveAndFlush(cliente);
            return toListItemResponse(creado);
        } catch (DataIntegrityViolationException e) {
            if (telefono != null) {
                throw new RuntimeException(mensajeTelefonoDuplicado(telefono));
            }
            throw e;
        }
    }

    @Transactional
    public ClienteListItemResponse actualizar(Integer idCliente, ClienteUpdateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        Cliente cliente = obtenerClienteConAcceso(idCliente, usuarioAutenticado);
        validarEmpresaInmutable(cliente, request.idEmpresa(), usuarioAutenticado);

        String nroDocumento = normalizarYValidarDocumento(request.tipoDocumento(), request.nroDocumento());
        String telefono = normalizarTelefonoOpcional(request.telefono());
        Integer idEmpresaCliente = cliente.getEmpresa() != null ? cliente.getEmpresa().getIdEmpresa() : null;
        validarTelefonoUnicoEnEmpresa(telefono, idEmpresaCliente, idCliente);

        cliente.setTipoDocumento(request.tipoDocumento());
        cliente.setNroDocumento(nroDocumento);
        cliente.setNombres(request.nombres().trim());
        cliente.setTelefono(telefono);
        cliente.setCorreo(normalizarNullable(request.correo()));
        cliente.setDireccion(normalizarNullable(request.direccion()));
        cliente.setEstado(request.estado().toUpperCase());

        try {
            Cliente actualizado = clienteRepository.saveAndFlush(cliente);
            return toListItemResponse(actualizado);
        } catch (DataIntegrityViolationException e) {
            if (telefono != null) {
                throw new RuntimeException(mensajeTelefonoDuplicado(telefono));
            }
            throw e;
        }
    }

    @Transactional
    public void eliminarLogico(Integer idCliente, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Cliente cliente = obtenerClienteConAcceso(idCliente, usuarioAutenticado);

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

    private Empresa resolverEmpresaContexto(Usuario usuario) {
        if (usuario.getSucursal() != null
                && usuario.getSucursal().getEmpresa() != null
                && usuario.getSucursal().getEmpresa().getIdEmpresa() != null) {
            return usuario.getSucursal().getEmpresa();
        }
        return empresaRepository.findTopByOrderByIdEmpresaAsc()
                .orElseThrow(() -> new RuntimeException("No hay empresa registrada"));
    }

    private Empresa resolverEmpresaParaCrear(Integer idEmpresaRequest, Usuario usuarioAutenticado) {
        Empresa empresaContexto = resolverEmpresaContexto(usuarioAutenticado);
        if (esVentas(usuarioAutenticado)) {
            if (idEmpresaRequest != null && !empresaContexto.getIdEmpresa().equals(idEmpresaRequest)) {
                throw new RuntimeException("No tiene permisos para registrar clientes en otra empresa");
            }
            return empresaContexto;
        }
        if (idEmpresaRequest == null) {
            return empresaContexto;
        }
        return empresaRepository.findById(idEmpresaRequest)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));
    }

    private void validarEmpresaInmutable(Cliente cliente, Integer idEmpresaRequest, Usuario usuarioAutenticado) {
        Integer idEmpresaActual = cliente.getEmpresa() != null ? cliente.getEmpresa().getIdEmpresa() : null;
        if (idEmpresaActual == null) {
            throw new RuntimeException("El cliente no tiene empresa asociada");
        }

        Integer idEmpresaDestino = idEmpresaRequest != null
                ? idEmpresaRequest
                : idEmpresaActual;
        if (!idEmpresaActual.equals(idEmpresaDestino)) {
            throw new RuntimeException("No se permite mover clientes a otra empresa");
        }

        if (esVentas(usuarioAutenticado)) {
            Integer idEmpresaUsuario = resolverEmpresaContexto(usuarioAutenticado).getIdEmpresa();
            if (!idEmpresaUsuario.equals(idEmpresaActual)) {
                throw new RuntimeException("No tiene permisos para actualizar clientes de otra empresa");
            }
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private boolean esVentas(Usuario usuario) {
        return usuario.getRol() == Rol.VENTAS;
    }

    private Cliente obtenerClienteConAcceso(Integer idCliente, Usuario usuarioAutenticado) {
        Integer idEmpresaUsuario = resolverEmpresaContexto(usuarioAutenticado).getIdEmpresa();
        return clienteRepository.findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(idCliente, idEmpresaUsuario)
                .orElseThrow(() -> new RuntimeException("Cliente con ID " + idCliente + " no encontrado"));
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

    private String normalizarTelefono(String telefono) {
        String normalizado = normalizarNullable(telefono);
        if (normalizado == null) {
            throw new RuntimeException("Ingrese telefono");
        }
        return validarRegex(normalizado, "\\d{7,20}", "El telefono debe tener entre 7 y 20 digitos");
    }

    private String normalizarTelefonoOpcional(String telefono) {
        String normalizado = normalizarNullable(telefono);
        if (normalizado == null) {
            return null;
        }
        return validarRegex(normalizado, "\\d{7,20}", "El telefono debe tener entre 7 y 20 digitos");
    }

    private void validarTelefonoUnicoEnEmpresa(String telefono, Integer idEmpresa, Integer idClienteActual) {
        if (telefono == null || idEmpresa == null) {
            return;
        }

        Cliente duplicado = idClienteActual == null
                ? clienteRepository.findFirstByTelefonoAndEmpresa_IdEmpresaOrderByIdClienteAsc(telefono, idEmpresa)
                        .orElse(null)
                : clienteRepository.findFirstByTelefonoAndEmpresa_IdEmpresaAndIdClienteNotOrderByIdClienteAsc(
                        telefono,
                        idEmpresa,
                        idClienteActual).orElse(null);
        if (duplicado != null) {
            throw new RuntimeException(mensajeTelefonoDuplicado(telefono));
        }
    }

    private String mensajeTelefonoDuplicado(String telefono) {
        return "El telefono '" + telefono + "' ya existe en otro cliente de esta empresa";
    }

    private String normalizarNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TipoDocumento normalizarTipoDocumentoParaFiltro(String tipoDocumento) {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            return null;
        }

        String normalizado = tipoDocumento.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        try {
            return TipoDocumento.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("tipoDocumento permitido: DNI, RUC, CE o SIN_DOC");
        }
    }

    private ClienteListItemResponse toListItemResponse(Cliente cliente) {
        Integer idEmpresa = cliente.getEmpresa() != null ? cliente.getEmpresa().getIdEmpresa() : null;
        String nombreEmpresa = cliente.getEmpresa() != null ? cliente.getEmpresa().getNombre() : null;

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
                idEmpresa,
                nombreEmpresa,
                idUsuarioCreacion,
                nombreUsuarioCreacion);
    }

    private ClienteDetalleResponse toDetalleResponse(
            Cliente cliente,
            long comprasTotales,
            BigDecimal montoTotalCompras,
            List<ClienteCompraResumenResponse> ultimasCompras) {
        Integer idEmpresa = cliente.getEmpresa() != null ? cliente.getEmpresa().getIdEmpresa() : null;
        String nombreEmpresa = cliente.getEmpresa() != null ? cliente.getEmpresa().getNombre() : null;

        Integer idUsuarioCreacion = cliente.getUsuarioCreacion() != null ? cliente.getUsuarioCreacion().getIdUsuario() : null;
        String nombreUsuarioCreacion = null;
        if (cliente.getUsuarioCreacion() != null) {
            nombreUsuarioCreacion = cliente.getUsuarioCreacion().getNombre() + " "
                    + cliente.getUsuarioCreacion().getApellido();
        }

        return new ClienteDetalleResponse(
                cliente.getIdCliente(),
                cliente.getTipoDocumento() != null ? cliente.getTipoDocumento().name() : null,
                cliente.getNroDocumento(),
                cliente.getNombres(),
                cliente.getTelefono(),
                cliente.getCorreo(),
                cliente.getDireccion(),
                cliente.getEstado(),
                cliente.getFechaCreacion(),
                idEmpresa,
                nombreEmpresa,
                idUsuarioCreacion,
                nombreUsuarioCreacion,
                comprasTotales,
                valorMonetarioSeguro(montoTotalCompras),
                ultimasCompras);
    }

    private ClienteCompraResumenResponse toCompraResumenResponse(Venta venta) {
        return new ClienteCompraResumenResponse(
                venta.getIdVenta(),
                venta.getFecha(),
                venta.getTipoComprobante(),
                venta.getSerie(),
                venta.getCorrelativo(),
                venta.getMoneda(),
                valorMonetarioSeguro(venta.getTotal()),
                venta.getEstado());
    }

    private BigDecimal valorMonetarioSeguro(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
