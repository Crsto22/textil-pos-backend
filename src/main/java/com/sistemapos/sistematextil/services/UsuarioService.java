package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.PagedResponse;
import com.sistemapos.sistematextil.util.Rol;
import com.sistemapos.sistematextil.util.UsuarioListItemResponse;
import com.sistemapos.sistematextil.util.UsuarioResetPasswordRequest;
import com.sistemapos.sistematextil.util.UsuarioUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public Usuario Buscarporemail(String correo) {
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public PagedResponse<UsuarioListItemResponse> listarPaginado(int page) {
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idUsuario").ascending());
        Page<UsuarioListItemResponse> usuarios = usuarioRepository.findByDeletedAtIsNull(pageable).map(this::toListItemResponse);
        return PagedResponse.fromPage(usuarios);
    }

    public PagedResponse<UsuarioListItemResponse> buscarPaginado(String term, Rol rol, Integer idSucursal, int page) {
        String termNormalizado = (term == null || term.isBlank()) ? null : term.trim();

        if (termNormalizado == null && rol == null && idSucursal == null) {
            return listarPaginado(page);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idUsuario").ascending());
        Page<UsuarioListItemResponse> usuarios = usuarioRepository
                .buscarConFiltros(termNormalizado, rol, idSucursal, pageable)
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

        Sucursal sucursal = resolverSucursalSegunRol(request.rol(), request.idSucursal());

        usuario.setNombre(request.nombre());
        usuario.setApellido(request.apellido());
        usuario.setDni(request.dni());
        usuario.setTelefono(request.telefono());
        usuario.setCorreo(request.correo());
        usuario.setRol(request.rol());
        usuario.setEstado(request.estado().toUpperCase());
        usuario.setSucursal(sucursal);

        Usuario actualizado = usuarioRepository.save(usuario);
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

    private Sucursal resolverSucursalSegunRol(Rol rol, Integer idSucursal) {
        if (rol == Rol.ADMINISTRADOR) {
            return null;
        }
        if (idSucursal == null) {
            throw new RuntimeException("La sucursal es obligatoria para el rol " + rol.name());
        }
        return sucursalRepository.findById(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
    }

    private UsuarioListItemResponse toListItemResponse(Usuario usuario) {
        Integer idSucursal = usuario.getSucursal() != null ? usuario.getSucursal().getIdSucursal() : null;
        String nombreSucursal = usuario.getSucursal() != null ? usuario.getSucursal().getNombre() : null;
        return new UsuarioListItemResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getDni(),
                usuario.getTelefono(),
                usuario.getCorreo(),
                usuario.getRol().name(),
                usuario.getEstado(),
                usuario.getFechaCreacion(),
                idSucursal,
                nombreSucursal);
    }
}
