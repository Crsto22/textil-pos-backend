package com.sistemapos.sistematextil.util.producto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductoVarianteStockCreateItem(
        @NotNull(message = "Ingrese idSucursal en stocksSucursales")
        Integer idSucursal,

        @NotNull(message = "Ingrese cantidad en stocksSucursales")
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        Integer cantidad
) {
}
