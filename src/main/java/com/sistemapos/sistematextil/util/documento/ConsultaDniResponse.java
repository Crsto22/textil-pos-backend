package com.sistemapos.sistematextil.util.documento;

public record ConsultaDniResponse(
        boolean success,
        String dni,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        Integer codVerifica,
        String codVerificaLetra) {
}
