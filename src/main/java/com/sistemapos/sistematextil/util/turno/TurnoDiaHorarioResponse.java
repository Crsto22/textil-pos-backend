package com.sistemapos.sistematextil.util.turno;

import java.time.LocalTime;

public record TurnoDiaHorarioResponse(
        DiaSemana dia,
        LocalTime horaInicio,
        LocalTime horaFin
) {
}
