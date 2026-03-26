package com.sistemapos.sistematextil.util.cliente;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ClienteDetalleResponse(
        Integer idCliente,
        String tipoDocumento,
        String nroDocumento,
        String nombres,
        String telefono,
        String correo,
        String direccion,
        String estado,
        LocalDateTime fechaCreacion,
        Integer idEmpresa,
        String nombreEmpresa,
        Integer idUsuarioCreacion,
        String nombreUsuarioCreacion,
        long comprasTotales,
        BigDecimal montoTotalCompras,
        List<ClienteCompraResumenResponse> ultimasCompras
) {
}
