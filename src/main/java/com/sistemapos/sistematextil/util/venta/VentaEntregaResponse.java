package com.sistemapos.sistematextil.util.venta;

import java.math.BigDecimal;

public record VentaEntregaResponse(
        String metodo,
        String destinatario,
        String documento,
        String celular,
        Integer idSucursalRecojo,
        String sucursalRecojo,
        Long idAgenciaShalom,
        String agenciaShalom,
        String direccionAgenciaShalom,
        String destinoShalom,
        String estadoFilm,
        BigDecimal montoFilm,
        boolean filmPagado
) {
}
