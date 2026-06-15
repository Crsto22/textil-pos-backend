package com.sistemapos.sistematextil.util.metodopago;

public record SucursalMetodoPagoConfigResponse(
        Integer idMetodoPago,
        String nombre,
        Boolean activo,
        Boolean requiereCodigoOperacion,
        Boolean requiereFechaPago,
        Boolean requiereHoraPago
) {
}
