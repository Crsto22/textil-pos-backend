package com.sistemapos.sistematextil.util.cpe;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ConsultaCpeRequest(
        @NotBlank(message = "El RUC emisor es obligatorio")
        @Pattern(regexp = "\\d{11}", message = "El RUC emisor debe tener 11 digitos")
        String rucEmisor,

        @NotBlank(message = "El tipo de comprobante es obligatorio")
        String tipoComprobante,

        @NotBlank(message = "La serie es obligatoria")
        String serie,

        @NotNull(message = "El correlativo es obligatorio")
        @Positive(message = "El correlativo debe ser positivo")
        Integer correlativo,

        @NotNull(message = "La fecha de emision es obligatoria")
        LocalDate fechaEmision,

        @NotNull(message = "El importe total es obligatorio")
        @DecimalMin(value = "0.00", message = "El importe total no puede ser negativo")
        BigDecimal importeTotal) {
}
