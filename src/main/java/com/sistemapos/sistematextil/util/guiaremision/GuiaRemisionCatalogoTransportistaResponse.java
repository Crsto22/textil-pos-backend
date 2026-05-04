package com.sistemapos.sistematextil.util.guiaremision;

public record GuiaRemisionCatalogoTransportistaResponse(
        Integer idCatalogoTransportista,
        Integer idEmpresa,
        String nombreEmpresa,
        String transportistaTipoDoc,
        String transportistaNroDoc,
        String transportistaRazonSocial,
        String transportistaRegistroMtc) {
}
