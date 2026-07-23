package com.sistemapos.sistematextil.util.turno;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record TurnoListItemResponse(
        Integer idTurno,
        String nombre,
        LocalTime horaInicio,
        LocalTime horaFin,
        Integer toleranciaMinutos,
        List<DiaSemana> dias,
        List<TurnoDiaHorarioResponse> horariosDias,
        String estado,
        LocalDateTime fechaCreacion
) {
}
