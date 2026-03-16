package com.sistemapos.sistematextil.util.sunatconfig;

import java.time.LocalDateTime;

public record SunatConfigResponse(
        Integer idSunatConfig,
        Integer idEmpresa,
        String nombreEmpresa,
        String rucEmpresa,
        String ambiente,
        String usuarioSol,
        String urlBillService,
        String certificadoNombreArchivo,
        Boolean tieneClaveSol,
        Boolean tieneCertificado,
        Boolean tieneCertificadoPassword,
        Boolean tieneClientId,
        Boolean tieneClientSecret,
        String activo,
        String modoIntegracion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
