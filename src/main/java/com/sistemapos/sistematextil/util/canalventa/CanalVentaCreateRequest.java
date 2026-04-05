package com.sistemapos.sistematextil.util.canalventa;

import com.sistemapos.sistematextil.model.CanalVenta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CanalVentaCreateRequest(
        @NotNull(message = "Ingrese idSucursal")
        Integer idSucursal,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no debe superar 100 caracteres")
        String nombre,

        @NotNull(message = "Ingrese plataforma")
        CanalVenta.Plataforma plataforma,

        @Size(max = 255, message = "La descripcion no debe superar 255 caracteres")
        String descripcion,

        Boolean activo) {
}
