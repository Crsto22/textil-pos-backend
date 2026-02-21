package com.sistemapos.sistematextil.util.usuario;

import java.time.LocalDateTime;

public record UsuarioListItemResponse(
        Integer idUsuario,
        String nombre,
        String apellido,
        String dni,
        String telefono,
        String correo,
        String rol,
        String estado,
        LocalDateTime fechaCreacion,
        Integer idSucursal,
        String nombreSucursal
) {
}
