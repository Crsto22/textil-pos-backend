package com.sistemapos.sistematextil.util.guiaremision;

public record GuiaRemisionCatalogoConductorResponse(
        Integer idCatalogoConductor,
        Integer idEmpresa,
        String nombreEmpresa,
        String tipoDocumento,
        String nroDocumento,
        String nombres,
        String apellidos,
        String licencia,
        Boolean esPrincipal) {
}
