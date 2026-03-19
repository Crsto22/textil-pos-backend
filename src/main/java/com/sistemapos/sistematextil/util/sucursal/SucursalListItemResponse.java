package com.sistemapos.sistematextil.util.sucursal;

import java.time.LocalDateTime;
import java.util.List;

public record SucursalListItemResponse(
        Integer idSucursal,
        String nombre,
        String descripcion,
        String direccion,
        String telefono,
        String correo,
        String ubigeo,
        String departamento,
        String provincia,
        String distrito,
        String codigoEstablecimientoSunat,
        String estado,
        LocalDateTime fechaCreacion,
        Integer idEmpresa,
        String nombreEmpresa,
        List<String> usuarios,
        List<SucursalUsuarioResumenResponse> usuariosDetalle,
        Long usuariosTotal,
        Long usuariosFaltantes
) {
}
