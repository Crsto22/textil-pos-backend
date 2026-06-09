package com.sistemapos.sistematextil.util.venta;

import java.time.LocalDateTime;

public record VentaConversionOrigenResponse(
        String tipoComprobante,
        String serie,
        Integer correlativo,
        LocalDateTime convertidoAt,
        Integer idUsuario,
        String usuario
) {
}
