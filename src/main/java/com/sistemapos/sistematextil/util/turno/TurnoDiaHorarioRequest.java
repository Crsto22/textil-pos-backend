package com.sistemapos.sistematextil.util.turno;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

public record TurnoDiaHorarioRequest(
        @NotNull(message = "Ingrese dia")
        DiaSemana dia,

        @NotNull(message = "Ingrese horaInicio")
        LocalTime horaInicio,

        @NotNull(message = "Ingrese horaFin")
        LocalTime horaFin
) {
}
