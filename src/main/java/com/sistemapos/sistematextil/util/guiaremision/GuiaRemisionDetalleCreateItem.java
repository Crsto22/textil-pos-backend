package com.sistemapos.sistematextil.util.guiaremision;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GuiaRemisionDetalleCreateItem(
        @NotNull(message = "El idProductoVariante es obligatorio")
        Integer idProductoVariante,

        @Size(max = 255, message = "La descripcion no debe superar 255 caracteres")
        String descripcion,

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0")
        BigDecimal cantidad,

        @Size(max = 3, message = "La unidadMedida no debe superar 3 caracteres")
        String unidadMedida,

        @Size(max = 30, message = "El codigoProducto no debe superar 30 caracteres")
        String codigoProducto,

        BigDecimal pesoUnitario) {
}
