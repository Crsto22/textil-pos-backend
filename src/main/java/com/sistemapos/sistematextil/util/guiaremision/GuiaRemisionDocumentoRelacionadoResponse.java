package com.sistemapos.sistematextil.util.guiaremision;

public record GuiaRemisionDocumentoRelacionadoResponse(
        Integer idGuiaDocumentoRelacionado,
        String tipoDocumento,
        String serie,
        String numero,
        String numeroDocumento) {
}
