package com.sistemapos.sistematextil.util.sucursalstock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MovimientoStockRequest(
        @NotNull(message = "Ingrese idSucursal")
        Integer idSucursal,

        @NotNull(message = "Ingrese idProductoVariante")
        Integer idProductoVariante,

        @NotBlank(message = "Ingrese tipoMovimiento")
        @Pattern(regexp = "(?i)ENTRADA|SALIDA", message = "tipoMovimiento permitido: ENTRADA o SALIDA")
        String tipoMovimiento,

        @NotNull(message = "Ingrese cantidad")
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        Integer cantidad,

        @Size(max = 150, message = "El motivo no debe superar 150 caracteres")
        String motivo) {
}
