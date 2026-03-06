package com.sistemapos.sistematextil.util.empresa;

import com.sistemapos.sistematextil.model.Empresa;

public record EmpresaPublicoResponse(
        String nombre,
        String logoUrl
) {
    public static EmpresaPublicoResponse fromEntity(Empresa empresa) {
        return new EmpresaPublicoResponse(
                empresa.getNombre(),
                empresa.getLogoUrl()
        );
    }
}
