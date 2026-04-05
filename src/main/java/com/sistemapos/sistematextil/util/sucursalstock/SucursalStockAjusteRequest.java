package com.sistemapos.sistematextil.util.sucursalstock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SucursalStockAjusteRequest(
        @NotNull(message = "Ingrese idSucursal")
        Integer idSucursal,

        @NotNull(message = "Ingrese idProductoVariante")
        Integer idProductoVariante,

        @NotNull(message = "Ingrese cantidad")
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        Integer cantidad,

        @Size(max = 150, message = "El motivo no debe superar 150 caracteres")
        String motivo) {
}
