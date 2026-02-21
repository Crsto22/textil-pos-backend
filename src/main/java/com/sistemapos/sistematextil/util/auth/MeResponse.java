package com.sistemapos.sistematextil.util.auth;

import java.time.LocalDateTime;

public record MeResponse(
        Integer idUsuario,
        String nombre,
        String apellido,
        String correo,
        String dni,
        String telefono,
        String rol,
        LocalDateTime fechaCreacion,
        Integer idSucursal,
        String nombreSucursal
) {
}

