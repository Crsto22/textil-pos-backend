package com.sistemapos.sistematextil.util.sunatconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SunatConfigUpsertRequest(
        @Pattern(regexp = "^$|BETA|PRODUCCION", message = "ambiente permitido: BETA o PRODUCCION")
        String ambiente,

        @NotBlank(message = "Ingrese usuarioSol")
        @Size(max = 50, message = "usuarioSol no debe superar 50 caracteres")
        String usuarioSol,

        @Size(max = 255, message = "claveSol no debe superar 255 caracteres")
        String claveSol,

        @Size(max = 255, message = "urlBillService no debe superar 255 caracteres")
        String urlBillService,

        @Size(max = 255, message = "certificadoPassword no debe superar 255 caracteres")
        String certificadoPassword,

        @Size(max = 255, message = "clientId no debe superar 255 caracteres")
        String clientId,

        @Size(max = 255, message = "clientSecret no debe superar 255 caracteres")
        String clientSecret,

        @Pattern(regexp = "^$|ACTIVO|INACTIVO", message = "activo permitido: ACTIVO o INACTIVO")
        String activo
) {
}
