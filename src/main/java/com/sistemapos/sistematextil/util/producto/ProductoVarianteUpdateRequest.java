package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record ProductoVarianteUpdateRequest(
        @NotNull(message = "Ingrese colorId")
        Integer colorId,

        @NotNull(message = "Ingrese tallaId")
        Integer tallaId,

        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 100, message = "El SKU no debe superar 100 caracteres")
        String sku,

        @Size(max = 100, message = "El codigo de barras no debe superar 100 caracteres")
        String codigoBarras,

        @NotNull(message = "Ingrese precio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
        Double precio,

        @DecimalMin(value = "0.0", inclusive = false, message = "El precio por mayor debe ser mayor a 0")
        Double precioMayor,

        @DecimalMin(value = "0.0", inclusive = false, message = "El precio de oferta debe ser mayor a 0")
        Double precioOferta,

        LocalDateTime ofertaInicio,

        LocalDateTime ofertaFin,

        @NotEmpty(message = "Ingrese stocksSucursales")
        @Valid
        List<ProductoVarianteStockCreateItem> stocksSucursales
) {
}
