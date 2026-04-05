package com.sistemapos.sistematextil.util.canalventa;

import java.time.LocalDateTime;

public record CanalVentaResponse(
        Integer idCanalVenta,
        Integer idSucursal,
        String nombreSucursal,
        String tipoSucursal,
        String nombre,
        String plataforma,
        String descripcion,
        Boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
