package com.sistemapos.sistematextil.util.sunat;

public record SunatCdrResult(
        SunatEstado estado,
        String codigo,
        String mensaje,
        byte[] xmlBytes
) {
}
