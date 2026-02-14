package com.sistemapos.sistematextil.util;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticationResponse(
    @JsonProperty("access_token") String accessToken,
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
