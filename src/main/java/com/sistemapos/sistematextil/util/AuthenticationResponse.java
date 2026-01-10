package com.sistemapos.sistematextil.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticationResponse(

    @JsonProperty ("access_token") String accessToken,
    @JsonProperty ("refresh_token") String refreshToken,
    Integer idUsuario,
    String nombre,
    String apellido,
    String rol,

    Integer idSucursal

) {

}
