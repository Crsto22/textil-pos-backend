package com.sistemapos.sistematextil.util.turno;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TurnoCreateRequest(
        @NotBlank(message = "Ingrese nombre de turno")
        @Size(max = 80, message = "El nombre del turno no debe superar 80 caracteres")
        String nombre,

        @NotNull(message = "Ingrese horaInicio")
        LocalTime horaInicio,

        @NotNull(message = "Ingrese horaFin")
        LocalTime horaFin,

        @NotEmpty(message = "Ingrese al menos un dia")
        List<DiaSemana> dias,

        @Valid
        List<TurnoDiaHorarioRequest> horariosDias
) {
}
