package com.sistemapos.sistematextil.util.ecommerce;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record EcommercePedidoCreateRequest(
        @Valid @NotNull Cliente cliente,
        @Valid @NotNull Envio envio,
        @NotNull String metodoPago,
        @Valid @NotEmpty List<Item> items,
        List<Integer> promocionesEsperadas,
        String turnstileToken) {

    public record Cliente(
            String dni,
            String nombres,
            String apellidos,
            String correo,
            String telefono,
            Boolean deseaFactura,
            String ruc) {
    }

    public record Envio(
            String tipo,
            String direccion,
            String referencia,
            String departamento,
            String provincia,
            String distrito,
            String tarifa) {
    }

    public record Item(
            @NotNull Integer idProductoVariante,
            @NotNull
            @Min(value = 1, message = "La cantidad debe ser mayor o igual a 1")
            @Max(value = 5, message = "La cantidad maxima por variante es 5")
            Integer cantidad) {
    }
}
