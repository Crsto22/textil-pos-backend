package com.sistemapos.sistematextil.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.JwtService;
import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.AuthenticationRequest;
import com.sistemapos.sistematextil.util.AuthenticationResponse;
import com.sistemapos.sistematextil.util.ChangePasswordRequest;
import com.sistemapos.sistematextil.util.RegisterRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public String register(RegisterRequest request) {
        if (request.idSucursal() == null) {
            throw new RuntimeException("La sucursal es obligatoria");
        }

        usuarioRepository.findByCorreoAndDeletedAtIsNull(request.email()).ifPresent(u -> {
            throw new RuntimeException("El correo '" + request.email() + "' ya existe");
        });

        usuarioRepository.findByDniAndDeletedAtIsNull(request.dni()).ifPresent(u -> {
            throw new RuntimeException("El DNI '" + request.dni() + "' ya existe");
        });

        usuarioRepository.findByTelefonoAndDeletedAtIsNull(request.telefono()).ifPresent(u -> {
            throw new RuntimeException("El telefono '" + request.telefono() + "' ya existe");
        });

        Sucursal sucursal = sucursalRepository.findById(request.idSucursal())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        Usuario user = Usuario.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .dni(request.dni())
                .correo(request.email())
                .telefono(request.telefono())
                .password(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .sucursal(sucursal)
                .build();

        usuarioRepository.save(user);
        return "Usuario registrado exitosamente";
    }

    public LoginResult authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Usuario user = usuarioRepository.findByCorreoAndDeletedAtIsNull(request.email()).orElseThrow();
        CustomUser customUser = new CustomUser(user);

        String accessToken = jwtService.generateAccessToken(customUser);
        String refreshToken = jwtService.generateRefreshToken(customUser);

        Integer idSucursal = user.getSucursal() != null ? user.getSucursal().getIdSucursal() : null;

        AuthenticationResponse body = new AuthenticationResponse(
                accessToken,
                user.getIdUsuario(),
                user.getNombre(),
                user.getApellido(),
                user.getRol().name(),
                idSucursal);

        return new LoginResult(body, refreshToken);
    }

    public RefreshResult refresh(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        Usuario user = usuarioRepository.findByCorreoAndDeletedAtIsNull(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        CustomUser customUser = new CustomUser(user);

        if (!jwtService.isTokenValid(refreshToken, customUser)) {
            throw new RuntimeException("Refresh token invalido o expirado");
        }

        String newAccessToken = jwtService.generateAccessToken(customUser);
        String newRefreshToken = jwtService.generateRefreshToken(customUser);

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    public String changePassword(String email, ChangePasswordRequest request) {
        Usuario user = usuarioRepository.findByCorreoAndDeletedAtIsNull(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

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
        usuarioRepository.save(user);
        return "Contrasena actualizada exitosamente";
    }

    public record LoginResult(AuthenticationResponse response, String refreshToken) {}
    public record RefreshResult(String accessToken, String refreshToken) {}
}
