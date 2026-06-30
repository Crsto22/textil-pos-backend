package com.sistemapos.sistematextil.util.usuario;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.sistemapos.sistematextil.util.turno.DiaSemana;
import com.sistemapos.sistematextil.util.turno.TurnoDiaHorarioResponse;

public record UsuarioListItemResponse(
        Integer idUsuario,
        String nombre,
        String apellido,
        String dni,
        String telefono,
        String correo,
        String fotoPerfilUrl,
        String rol,
        String estado,
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
