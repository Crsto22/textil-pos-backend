package com.sistemapos.sistematextil.util.cpe;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConsultaCpeResponse(
        String rucEmisor,
        String tipoComprobante,
        String serie,
        Integer correlativo,
        LocalDate fechaEmision,
        BigDecimal importeTotal,
        String estado,
        String sunatEstado,
        boolean pdfDisponible,
        boolean xmlDisponible,
        boolean cdrDisponible,
        String pdfUrl,
        String xmlUrl,
        String cdrUrl) {
}
