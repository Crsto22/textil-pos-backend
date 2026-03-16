package com.sistemapos.sistematextil.util.venta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VentaDetalleCreateItem(
        @NotNull(message = "Ingrese idProductoVariante")
        Integer idProductoVariante,

        @Size(max = 255, message = "La descripcion no debe superar 255 caracteres")
        String descripcion,

        @NotNull(message = "Ingrese cantidad")
        @Min(value = 1, message = "La cantidad debe ser mayor o igual a 1")
        Integer cantidad,

        @Size(min = 3, max = 3, message = "La unidadMedida debe tener 3 caracteres")
        String unidadMedida,

        @Size(min = 2, max = 2, message = "El codigoTipoAfectacionIgv debe tener 2 caracteres")
        String codigoTipoAfectacionIgv,

        @DecimalMin(value = "0.01", message = "El precioUnitario debe ser mayor a 0")
        Double precioUnitario,

        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
        Double descuento
) {
}
