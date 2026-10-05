package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sistemapos.sistematextil.config.JwtService;
import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.auth.AuthenticationRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceSessionTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private S3StorageService s3StorageService;
    @Mock private TurnoService turnoService;
    @Mock private UsuarioSucursalAccessService usuarioSucursalAccessService;

    private AuthenticationService service;
    private Usuario user;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                usuarioRepository,
                passwordEncoder,
                jwtService,
                authenticationManager,
                s3StorageService,
                turnoService,
                usuarioSucursalAccessService);
        user = Usuario.builder()
                .idUsuario(1)
                .nombre("Admin")
                .apellido("Kiments")
                .correo("admin@kiments.tech")
                .dni("12345678")
                .telefono("999999999")
                .password("hash")
                .rol(Rol.ADMINISTRADOR)
                .estado("ACTIVO")
                .refreshTokenVersion(4)
                .build();
        when(usuarioRepository.findByCorreoAndDeletedAtIsNull(user.getCorreo()))
                .thenReturn(Optional.of(user));
    }

    @Test
    void iniciarSesionNoInvalidaLasDemasAplicaciones() {
        stubGeneratedTokens();
        when(turnoService.obtenerDias(null)).thenReturn(List.of());
        when(turnoService.obtenerHorarios(null)).thenReturn(List.of());
        when(usuarioSucursalAccessService.obtenerSucursalesPermitidasResponse(user)).thenReturn(List.of());

        service.authenticate(new AuthenticationRequest(user.getCorreo(), "password"));

        verify(usuarioRepository, never()).save(user);
    }

    @Test
    void renovarSesionNoInvalidaRenovacionesParalelas() {
        stubGeneratedTokens();
        when(jwtService.extractUsername("refresh")).thenReturn(user.getCorreo());
        when(jwtService.isTokenValid(any(String.class), any(CustomUser.class))).thenReturn(true);
        when(jwtService.extractRefreshTokenVersion("refresh")).thenReturn(4);

        service.refresh("refresh");
        service.refresh("refresh");

        verify(usuarioRepository, never()).save(user);
    }

    @Test
    void cerrarSesionConLaCookieRevocaLaSesionCompartida() {
        when(jwtService.extractUsername("refresh")).thenReturn(user.getCorreo());
        when(jwtService.isTokenValid(any(String.class), any(CustomUser.class))).thenReturn(true);
        when(jwtService.extractRefreshTokenVersion("refresh")).thenReturn(4);

        service.logoutByRefreshToken("refresh");

        assertEquals(5, user.getRefreshTokenVersion());
        verify(usuarioRepository).save(user);
    }

    private void stubGeneratedTokens() {
        when(jwtService.generateAccessToken(any(CustomUser.class))).thenReturn("access");
        when(jwtService.generateRefreshToken(any(CustomUser.class))).thenReturn("refresh");
    }
}
