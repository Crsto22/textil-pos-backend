package com.sistemapos.sistematextil.util.cotizacion;

import com.sistemapos.sistematextil.util.venta.VentaResponse;

public record CotizacionConvertirVentaResponse(
        String message,
        Integer idCotizacion,
        String estadoCotizacion,
        Integer idVenta,
        VentaResponse venta
) {
}
