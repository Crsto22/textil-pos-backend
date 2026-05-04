package com.sistemapos.sistematextil.util.turno;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record TurnoListItemResponse(
        Integer idTurno,
        String nombre,
        LocalTime horaInicio,
        LocalTime horaFin,
        List<DiaSemana> dias,
        String estado,
        LocalDateTime fechaCreacion
) {
}
