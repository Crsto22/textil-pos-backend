package com.sistemapos.sistematextil.util.sunat;

public enum SunatBajaEstado {
    NO_APLICA,
    PENDIENTE_ENVIO,
    ENVIANDO,
    PENDIENTE_CDR,
    ERROR_TRANSITORIO,
    ERROR_DEFINITIVO,
    PENDIENTE,
    ACEPTADO,
    OBSERVADO,
    RECHAZADO,
    ERROR;

    public static SunatBajaEstado fromSunatEstado(SunatEstado estado) {
        if (estado == null) {
            return ERROR_DEFINITIVO;
        }
        return switch (estado) {
            case NO_APLICA -> NO_APLICA;
            case PENDIENTE_ENVIO -> PENDIENTE_ENVIO;
            case ENVIANDO -> ENVIANDO;
            case PENDIENTE_CDR -> PENDIENTE_CDR;
            case ERROR_TRANSITORIO -> ERROR_TRANSITORIO;
            case ERROR_DEFINITIVO -> ERROR_DEFINITIVO;
            case PENDIENTE -> PENDIENTE;
            case ACEPTADO -> ACEPTADO;
            case OBSERVADO -> OBSERVADO;
            case RECHAZADO -> RECHAZADO;
            case ERROR -> ERROR;
        };
    }
}
