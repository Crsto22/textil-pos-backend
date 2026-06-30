package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Turno;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.turno.DiaSemana;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.usuario.UsuarioListItemResponse;
import com.sistemapos.sistematextil.util.usuario.UsuarioResetPasswordRequest;
import com.sistemapos.sistematextil.util.usuario.UsuarioUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final PasswordEncoder passwordEncoder;
    private final TurnoService turnoService;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;
    private final S3StorageService s3StorageService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public Usuario Buscarporemail(String correo) {
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public PagedResponse<UsuarioListItemResponse> listarPaginado(
            int page,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        Integer idSucursalFiltro = resolverIdSucursalFiltroListado(idSucursal);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idUsuario").ascending());
        Page<UsuarioListItemResponse> usuarios = idSucursalFiltro == null
                ? usuarioRepository.findByDeletedAtIsNullAndRolNot(Rol.SISTEMA, pageable).map(this::toListItemResponse)
                : usuarioRepository.buscarConFiltros(null, null, Rol.SISTEMA, idSucursalFiltro, pageable).map(this::toListItemResponse);
        return PagedResponse.fromPage(usuarios);
    }

    public PagedResponse<UsuarioListItemResponse> buscarPaginado(String term, Rol rol, Integer idSucursal, int page) {
        String termNormalizado = (term == null || term.isBlank()) ? null : term.trim();

        if (termNormalizado == null && rol == null && idSucursal == null) {
            PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idUsuario").ascending());
            Page<UsuarioListItemResponse> usuarios = usuarioRepository.findByDeletedAtIsNullAndRolNot(Rol.SISTEMA, pageable)
                    .map(this::toListItemResponse);
            return PagedResponse.fromPage(usuarios);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idUsuario").ascending());
        Page<UsuarioListItemResponse> usuarios = usuarioRepository
                .buscarConFiltros(termNormalizado, rol, Rol.SISTEMA, idSucursal, pageable)
                .map(this::toListItemResponse);

        return PagedResponse.fromPage(usuarios);
    }

    @Transactional
    public UsuarioListItemResponse actualizar(Integer idUsuario, UsuarioUpdateRequest request) {
        Usuario usuario = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario con ID " + idUsuario + " no encontrado"));

        usuarioRepository.findByCorreoAndDeletedAtIsNull(request.correo()).ifPresent(u -> {
            if (!u.getIdUsuario().equals(idUsuario)) {
                throw new RuntimeException("El correo ya pertenece a otro usuario");
            }
        });

        usuarioRepository.findByDniAndDeletedAtIsNull(request.dni()).ifPresent(u -> {
            if (!u.getIdUsuario().equals(idUsuario)) {
                throw new RuntimeException("El DNI ya pertenece a otro usuario");
            }
        });

        usuarioRepository.findByTelefonoAndDeletedAtIsNull(request.telefono()).ifPresent(u -> {
            if (!u.getIdUsuario().equals(idUsuario)) {
                throw new RuntimeException("El telefono ya pertenece a otro usuario");
            }
        });

        UsuarioSucursalAccessService.AsignacionSucursal asignacion = usuarioSucursalAccessService.resolverAsignacion(
                request.rol(),
                request.idSucursal(),
                request.idsSucursales());
        Turno turno = turnoService.resolverTurnoAsignable(request.idTurno());

        usuario.setNombre(request.nombre());
        usuario.setApellido(request.apellido());
        usuario.setDni(request.dni());
        usuario.setTelefono(request.telefono());
        usuario.setCorreo(request.correo());
        usuario.setRol(request.rol());
        usuario.setEstado(request.estado().toUpperCase());
        usuario.setPuedeAceptarPedidos(puedeAceptarPedidos(request.rol(), request.puedeAceptarPedidos()));
        usuario.setSucursal(asignacion.principal());
        usuario.setTurno(turno);

        Usuario actualizado = usuarioRepository.save(usuario);
        usuarioSucursalAccessService.sincronizarSucursales(actualizado, asignacion.permitidas());
        return toListItemResponse(actualizado);
    }

    @Transactional
    public void eliminarLogico(Integer idUsuario) {
        Usuario usuario = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario con ID " + idUsuario + " no encontrado o ya eliminado"));

        usuario.setEstado("INACTIVO");
        usuario.setDeletedAt(LocalDateTime.now());
        liberarCamposUnicos(usuario);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public String resetearPassword(Integer idUsuario, UsuarioResetPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario con ID " + idUsuario + " no encontrado"));

        if (!request.passwordNueva().equals(request.confirmarPassword())) {
            throw new RuntimeException("La confirmacion de contrasena no coincide");
        }

        if (passwordEncoder.matches(request.passwordNueva(), usuario.getPassword())) {
            throw new RuntimeException("La nueva contrasena no puede ser igual a la actual");
        }

        usuario.setPassword(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);

        return "Contrasena reseteada exitosamente";
    }

    private void liberarCamposUnicos(Usuario usuario) {
        int id = usuario.getIdUsuario();
        usuario.setCorreo("deleted_" + id + "@invalid.local");
        usuario.setDni(String.format("%08d", 10_000_000 + (id % 90_000_000)));
        usuario.setTelefono(String.format("%09d", 100_000_000 + (id % 900_000_000)));
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer resolverIdSucursalFiltroListado(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            return null;
        }
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalRequest)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"))
                .getIdSucursal();
    }

    private UsuarioListItemResponse toListItemResponse(Usuario usuario) {
        Integer idSucursal = usuario.getSucursal() != null ? usuario.getSucursal().getIdSucursal() : null;
        String nombreSucursal = usuario.getSucursal() != null ? usuario.getSucursal().getNombre() : null;
        Integer idTurno = usuario.getTurno() != null ? usuario.getTurno().getIdTurno() : null;
        String nombreTurno = usuario.getTurno() != null ? usuario.getTurno().getNombre() : null;
        List<DiaSemana> diasTurno = turnoService.obtenerDias(usuario.getTurno());
        return new UsuarioListItemResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getDni(),
                usuario.getTelefono(),
                usuario.getCorreo(),
                s3StorageService.resolvePublicUrl(usuario.getFotoPerfilUrl()),
                usuario.getRol().name(),
                usuario.getEstado(),
                usuario.getFechaCreacion(),
                idSucursal,
                nombreSucursal,
                usuarioSucursalAccessService.obtenerSucursalesPermitidasResponse(usuario),
                idTurno,
                nombreTurno,
                usuario.getTurno() != null ? usuario.getTurno().getHoraInicio() : null,
                usuario.getTurno() != null ? usuario.getTurno().getHoraFin() : null,
                diasTurno,
                turnoService.obtenerHorarios(usuario.getTurno()),
                Boolean.TRUE.equals(usuario.getPuedeAceptarPedidos()));
    }

    private boolean puedeAceptarPedidos(Rol rol, Boolean value) {
        return Boolean.TRUE.equals(value) && (rol == Rol.VENTAS || rol == Rol.VENTAS_ALMACEN);
    }
}
