package com.sistemapos.sistematextil.util.venta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VentaDetalleCreateItem(
        @NotNull(message = "Ingrese idProductoVariante")
        Integer idProductoVariante,

        @NotNull(message = "Ingrese cantidad")
        @Min(value = 1, message = "La cantidad debe ser mayor o igual a 1")
        Integer cantidad,

        @DecimalMin(value = "0.01", message = "El precioUnitario debe ser mayor a 0")
        Double precioUnitario,

        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
        Double descuento
) {
}
