package com.sistemapos.sistematextil.util.notacredito;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotaCreditoCreateRequest(
        @NotBlank(message = "El codigoMotivo es obligatorio")
        String codigoMotivo,

        @NotBlank(message = "La descripcionMotivo es obligatoria")
        @Size(min = 5, max = 255, message = "La descripcionMotivo debe tener entre 5 y 255 caracteres")
        String descripcionMotivo,

        List<@Valid NotaCreditoItemRequest> items) {
}
