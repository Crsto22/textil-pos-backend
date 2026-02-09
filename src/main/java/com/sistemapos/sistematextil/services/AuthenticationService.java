package com.sistemapos.sistematextil.services;

import org.springframework.security.authentication.AuthenticationManager;
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
import com.sistemapos.sistematextil.util.RegisterRequest;
import com.sistemapos.sistematextil.util.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // ── REGISTRO ──
    public String register(RegisterRequest request) {
        Sucursal sucursal = null;

        if (request.idSucursal() != null) {
            sucursal = sucursalRepository.findById(request.idSucursal())
                    .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        }

        var user = Usuario.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .dni(request.dni())
                .correo(request.email())
                .telefono(request.telefono())
                .password(passwordEncoder.encode(request.password()))
                .rol(Rol.ADMINISTRADOR)
                .sucursal(sucursal)
                .build();
        usuarioRepository.save(user);

        return "Usuario registrado exitosamente";
    }

    // ── LOGIN → retorna AuthenticationResponse + refreshToken por separado ──
    public LoginResult authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var user = usuarioRepository.findByCorreo(request.email()).orElseThrow();
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

    // ── REFRESH → valida refresh token y genera nuevo access + refresh ──
    public RefreshResult refresh(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        var user = usuarioRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        CustomUser customUser = new CustomUser(user);

        if (!jwtService.isTokenValid(refreshToken, customUser)) {
            throw new RuntimeException("Refresh token inválido o expirado");
        }

        // Rotación: generar nuevos tokens
        String newAccessToken = jwtService.generateAccessToken(customUser);
        String newRefreshToken = jwtService.generateRefreshToken(customUser);

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    // ── Records internos para agrupar resultados ──
    public record LoginResult(AuthenticationResponse response, String refreshToken) {}
    public record RefreshResult(String accessToken, String refreshToken) {}
}
