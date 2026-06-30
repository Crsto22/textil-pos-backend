package com.sistemapos.sistematextil.util.auth;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.sistemapos.sistematextil.util.turno.DiaSemana;
import com.sistemapos.sistematextil.util.turno.TurnoDiaHorarioResponse;
import com.sistemapos.sistematextil.util.usuario.SucursalPermitidaResponse;

public record MeResponse(
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
        List<TurnoDiaHorarioResponse> horariosTurno,
        Boolean puedeAceptarPedidos
) {
}

