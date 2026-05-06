package com.sistemapos.sistematextil.util.sunatconfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SunatConfigResponse(
        Integer idSunatConfig,
        Integer idEmpresa,
        String nombreEmpresa,
        String rucEmpresa,
        String ambiente,
        String usuarioSol,
        String urlBillService,
        String urlConsultaTicket,
        String urlApiToken,
        String urlApiCpe,
        String certificadoNombreArchivo,
        Boolean tieneClaveSol,
        Boolean tieneCertificado,
        Boolean tieneCertificadoPassword,
        Boolean tieneClientId,
        Boolean tieneClientSecret,
        BigDecimal igvPorcentaje,
        String activo,
        String modoIntegracion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
