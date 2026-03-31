package com.sistemapos.sistematextil.util.sunat;

import java.time.LocalDateTime;

public record SunatEmissionResult(
        SunatEstado estado,
        String codigo,
        String mensaje,
        String hash,
        String ticket,
        String xmlNombre,
        String xmlKey,
        String zipNombre,
        String cdrNombre,
        String cdrKey,
        LocalDateTime fechaEnvio,
        LocalDateTime fechaRespuesta
) {
}
