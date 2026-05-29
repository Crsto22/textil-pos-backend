package com.sistemapos.sistematextil.util.auth;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sistemapos.sistematextil.util.turno.DiaSemana;
import com.sistemapos.sistematextil.util.turno.TurnoDiaHorarioResponse;
import com.sistemapos.sistematextil.util.usuario.SucursalPermitidaResponse;

public record AuthenticationResponse(
    @JsonProperty("access_token") String accessToken,
    Integer idUsuario,
    String nombre,
    String apellido,
    String correo,
    String dni,
    String telefono,
    String fotoPerfilUrl,
    String rol,
    LocalDateTime fechaCreacion,
    Integer idSucursal,
    String nombreSucursal,
    List<SucursalPermitidaResponse> sucursalesPermitidas,
    Integer idTurno,
    String nombreTurno,
    LocalTime horaInicioTurno,
    LocalTime horaFinTurno,
    List<DiaSemana> diasTurno,
    List<TurnoDiaHorarioResponse> horariosTurno
) {
}
