package com.sistemapos.sistematextil.util.cliente;

import java.time.LocalDateTime;

public record ClienteListItemResponse(
        Integer idCliente,
        String tipoDocumento,
        String nroDocumento,
        String nombres,
        String telefono,
        String correo,
        String direccion,
        String estado,
        LocalDateTime fechaCreacion,
        Integer idSucursal,
        String nombreSucursal,
        Integer idUsuarioCreacion,
        String nombreUsuarioCreacion
) {
}

