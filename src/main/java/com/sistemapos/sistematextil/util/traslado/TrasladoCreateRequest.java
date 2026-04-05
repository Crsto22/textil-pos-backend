package com.sistemapos.sistematextil.util.traslado;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TrasladoCreateRequest(
        @NotNull(message = "Ingrese idSucursalOrigen")
        Integer idSucursalOrigen,

        @NotNull(message = "Ingrese idSucursalDestino")
        Integer idSucursalDestino,

        @NotNull(message = "Ingrese al menos un producto para trasladar")
        @Size(min = 1, message = "Ingrese al menos un producto para trasladar")
        List<@Valid TrasladoItemCreateRequest> items,

        @JsonProperty("idProductoVariante")
        Integer idProductoVarianteCompat,

        @JsonProperty("cantidad")
        Integer cantidadCompat,

        @Size(max = 255, message = "El motivo no debe superar 255 caracteres")
        String motivo) {

    public TrasladoCreateRequest(
            Integer idSucursalOrigen,
            Integer idSucursalDestino,
            List<TrasladoItemCreateRequest> items,
            String motivo) {
        this(idSucursalOrigen, idSucursalDestino, items, null, null, motivo);
    }

    public TrasladoCreateRequest {
        if ((items == null || items.isEmpty())
                && idProductoVarianteCompat != null
                && cantidadCompat != null) {
            items = List.of(new TrasladoItemCreateRequest(idProductoVarianteCompat, cantidadCompat));
        } else if (items == null) {
            items = List.of();
        } else {
            items = List.copyOf(items);
        }
    }
}
