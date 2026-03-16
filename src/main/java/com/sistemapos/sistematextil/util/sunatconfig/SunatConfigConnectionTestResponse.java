package com.sistemapos.sistematextil.util.sunatconfig;

import java.time.LocalDateTime;

public record SunatConfigConnectionTestResponse(
        boolean ok,
        String message,
        String ambiente,
        String usuarioSol,
        String urlBillService,
        String certificadoNombreArchivo,
        boolean claveSolConfigurada,
        boolean certificadoConfigurado,
        boolean certificadoValido,
        String certificadoAlias,
        LocalDateTime certificadoVigenteDesde,
        LocalDateTime certificadoVigenteHasta,
        String modoIntegracion
) {
}
