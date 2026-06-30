package com.sistemapos.sistematextil.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.config.JwtService;
import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Turno;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.auth.AuthenticationRequest;
import com.sistemapos.sistematextil.util.auth.AuthenticationResponse;
import com.sistemapos.sistematextil.util.auth.ChangePasswordRequest;
import com.sistemapos.sistematextil.util.auth.MeResponse;
import com.sistemapos.sistematextil.util.auth.RegisterRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final int MAX_WIDTH = 800;
    private static final int MAX_HEIGHT = 800;
    private static final float CALIDAD = 0.85f;
    private static final long MAX_SIZE = 3 * 1024 * 1024;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final S3StorageService s3StorageService;
    private final TurnoService turnoService;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;

    static {
        ImageIO.scanForPlugins();
    }

    @Transactional
    public String register(RegisterRequest request) {
        usuarioRepository.findByCorreoAndDeletedAtIsNull(request.email()).ifPresent(u -> {
            throw new RuntimeException("El correo '" + request.email() + "' ya existe");
        });

        usuarioRepository.findByDniAndDeletedAtIsNull(request.dni()).ifPresent(u -> {
            throw new RuntimeException("El DNI '" + request.dni() + "' ya existe");
        });

        usuarioRepository.findByTelefonoAndDeletedAtIsNull(request.telefono()).ifPresent(u -> {
            throw new RuntimeException("El telefono '" + request.telefono() + "' ya existe");
        });

        UsuarioSucursalAccessService.AsignacionSucursal asignacion = usuarioSucursalAccessService.resolverAsignacion(
                request.rol(),
                request.idSucursal(),
                request.idsSucursales());

        Usuario user = Usuario.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .dni(request.dni())
                .correo(request.email())
                .telefono(request.telefono())
                .password(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .puedeAceptarPedidos(puedeAceptarPedidos(request.rol(), request.puedeAceptarPedidos()))
                .sucursal(asignacion.principal())
                .turno(turnoService.resolverTurnoAsignable(request.idTurno()))
                .build();

        Usuario guardado = usuarioRepository.save(user);
        usuarioSucursalAccessService.sincronizarSucursales(guardado, asignacion.permitidas());
        return "Usuario registrado exitosamente";
    }

    public LoginResult authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Usuario user = buscarUsuarioActivoPorCorreo(request.email());
        turnoService.validarAccesoPorTurno(user.getTurno());
        rotarRefreshTokenVersion(user);
        CustomUser customUser = new CustomUser(user);

        String accessToken = jwtService.generateAccessToken(customUser);
        String refreshToken = jwtService.generateRefreshToken(customUser);

        return new LoginResult(toAuthenticationResponse(accessToken, user), refreshToken);
    }

    public RefreshResult refresh(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        Usuario user = buscarUsuarioActivoPorCorreo(email);
        turnoService.validarAccesoPorTurno(user.getTurno());
        CustomUser customUser = new CustomUser(user);

        if (!jwtService.isTokenValid(refreshToken, customUser)) {
            throw new RuntimeException("Refresh token invalido o expirado");
        }
        Integer tokenVersion = jwtService.extractRefreshTokenVersion(refreshToken);
        Integer currentVersion = user.getRefreshTokenVersion() == null ? 0 : user.getRefreshTokenVersion();
        if (tokenVersion == null || !tokenVersion.equals(currentVersion)) {
            throw new RuntimeException("Refresh token invalido o expirado");
        }

        rotarRefreshTokenVersion(user);
        String newAccessToken = jwtService.generateAccessToken(customUser);
        String newRefreshToken = jwtService.generateRefreshToken(customUser);

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    public String changePassword(String email, ChangePasswordRequest request) {
        Usuario user = buscarUsuarioActivoPorCorreo(email);

        if (!passwordEncoder.matches(request.passwordActual(), user.getPassword())) {
            throw new BadCredentialsException("La contrasena actual es incorrecta");
        }

        if (!request.passwordNueva().equals(request.confirmarPassword())) {
            throw new RuntimeException("La confirmacion de contrasena no coincide");
        }

        if (request.passwordActual().equals(request.passwordNueva())) {
            throw new RuntimeException("La nueva contrasena no puede ser igual a la actual");
        }

        user.setPassword(passwordEncoder.encode(request.passwordNueva()));
        incrementarRefreshTokenVersion(user);
        usuarioRepository.save(user);
        return "Contrasena actualizada exitosamente";
    }

    public void logout(String email) {
        Usuario user = buscarUsuarioActivoPorCorreo(email);
        incrementarRefreshTokenVersion(user);
        usuarioRepository.save(user);
    }

    @Transactional
    public MeResponse actualizarFotoPerfil(String email, MultipartFile file) {
        Usuario user = buscarUsuarioActivoPorCorreo(email);
        validarImagen(file);

        String fotoAnteriorUrl = user.getFotoPerfilUrl();
        String nuevaFotoUrl;
        try {
            byte[] optimized = convertirAWebp(file.getBytes());
            String key = construirFotoPerfilKey(user.getIdUsuario());
            nuevaFotoUrl = s3StorageService.upload(optimized, key, "image/webp");
        } catch (IOException e) {
            throw new RuntimeException("No se pudo procesar la imagen enviada");
        }

        user.setFotoPerfilUrl(nuevaFotoUrl);

        try {
            Usuario actualizado = usuarioRepository.save(user);

            if (fotoAnteriorUrl != null
                    && !fotoAnteriorUrl.isBlank()
                    && !fotoAnteriorUrl.equals(nuevaFotoUrl)) {
                try {
                    s3StorageService.deleteByUrl(fotoAnteriorUrl);
                } catch (RuntimeException ignored) {
                    // Best-effort: la foto nueva ya quedo persistida en la base de datos.
                }
            }

            return toMeResponse(actualizado);
        } catch (RuntimeException e) {
            s3StorageService.deleteByUrl(nuevaFotoUrl);
            throw e;
        }
    }

    @Transactional
    public MeResponse eliminarFotoPerfil(String email) {
        Usuario user = buscarUsuarioActivoPorCorreo(email);
        String fotoAnteriorUrl = user.getFotoPerfilUrl();

        if (fotoAnteriorUrl == null || fotoAnteriorUrl.isBlank()) {
            return toMeResponse(user);
        }

        user.setFotoPerfilUrl(null);
        Usuario actualizado = usuarioRepository.save(user);

        try {
            s3StorageService.deleteByUrl(fotoAnteriorUrl);
        } catch (RuntimeException ignored) {
            // Best-effort: se prioriza que la base ya no apunte a un archivo inexistente o inaccesible.
        }

        return toMeResponse(actualizado);
    }

    public MeResponse me(String email) {
        return toMeResponse(buscarUsuarioActivoPorCorreo(email));
    }

    private Usuario buscarUsuarioActivoPorCorreo(String email) {
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private void rotarRefreshTokenVersion(Usuario user) {
        incrementarRefreshTokenVersion(user);
        usuarioRepository.save(user);
    }

    private void incrementarRefreshTokenVersion(Usuario user) {
        int current = user.getRefreshTokenVersion() == null ? 0 : user.getRefreshTokenVersion();
        user.setRefreshTokenVersion(current + 1);
    }

    private AuthenticationResponse toAuthenticationResponse(String accessToken, Usuario user) {
        Integer idSucursal = user.getSucursal() != null ? user.getSucursal().getIdSucursal() : null;
        String nombreSucursal = user.getSucursal() != null ? user.getSucursal().getNombre() : null;
        Turno turno = user.getTurno();
        List<com.sistemapos.sistematextil.util.turno.DiaSemana> diasTurno = turnoService.obtenerDias(turno);
        List<com.sistemapos.sistematextil.util.turno.TurnoDiaHorarioResponse> horariosTurno = turnoService.obtenerHorarios(turno);

        return new AuthenticationResponse(
                accessToken,
                user.getIdUsuario(),
                user.getNombre(),
                user.getApellido(),
                user.getCorreo(),
                user.getDni(),
                user.getTelefono(),
                s3StorageService.resolvePublicUrl(user.getFotoPerfilUrl()),
                user.getRol().name(),
                user.getFechaCreacion(),
                idSucursal,
                nombreSucursal,
                usuarioSucursalAccessService.obtenerSucursalesPermitidasResponse(user),
                turno != null ? turno.getIdTurno() : null,
                turno != null ? turno.getNombre() : null,
                turno != null ? turno.getHoraInicio() : null,
                turno != null ? turno.getHoraFin() : null,
                diasTurno,
                horariosTurno,
                Boolean.TRUE.equals(user.getPuedeAceptarPedidos()));
    }

    private MeResponse toMeResponse(Usuario user) {
        Integer idSucursal = user.getSucursal() != null ? user.getSucursal().getIdSucursal() : null;
        String nombreSucursal = user.getSucursal() != null ? user.getSucursal().getNombre() : null;
        Turno turno = user.getTurno();
        List<com.sistemapos.sistematextil.util.turno.DiaSemana> diasTurno = turnoService.obtenerDias(turno);
        List<com.sistemapos.sistematextil.util.turno.TurnoDiaHorarioResponse> horariosTurno = turnoService.obtenerHorarios(turno);

        return new MeResponse(
                user.getIdUsuario(),
                user.getNombre(),
                user.getApellido(),
                user.getCorreo(),
                user.getDni(),
                user.getTelefono(),
                s3StorageService.resolvePublicUrl(user.getFotoPerfilUrl()),
                user.getRol().name(),
                user.getFechaCreacion(),
                idSucursal,
                nombreSucursal,
                usuarioSucursalAccessService.obtenerSucursalesPermitidasResponse(user),
                turno != null ? turno.getIdTurno() : null,
                turno != null ? turno.getNombre() : null,
                turno != null ? turno.getHoraInicio() : null,
                turno != null ? turno.getHoraFin() : null,
                diasTurno,
                horariosTurno,
                Boolean.TRUE.equals(user.getPuedeAceptarPedidos()));
    }

    private boolean puedeAceptarPedidos(Rol rol, Boolean value) {
        return Boolean.TRUE.equals(value) && (rol == Rol.VENTAS || rol == Rol.VENTAS_ALMACEN);
    }

    private String construirFotoPerfilKey(Integer idUsuario) {
        return "usuarios/usuario-" + idUsuario + "/perfil-" + UUID.randomUUID() + ".webp";
    }

    private byte[] convertirAWebp(byte[] input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Thumbnails.of(new ByteArrayInputStream(input))
                .size(MAX_WIDTH, MAX_HEIGHT)
                .outputFormat("webp")
                .outputQuality(CALIDAD)
                .toOutputStream(out);

        return out.toByteArray();
    }

    private void validarImagen(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Debe enviar una imagen");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new RuntimeException("La foto de perfil no debe superar 3MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Formato de imagen no permitido");
        }
    }

    public record LoginResult(AuthenticationResponse response, String refreshToken) {}
    public record RefreshResult(String accessToken, String refreshToken) {}
}
