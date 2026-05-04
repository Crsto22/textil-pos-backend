package com.sistemapos.sistematextil.util.guiaremision;

public record GuiaRemisionCatalogoVehiculoResponse(
        Integer idCatalogoVehiculo,
        Integer idEmpresa,
        String nombreEmpresa,
        String placa,
        Boolean esPrincipal) {
}
