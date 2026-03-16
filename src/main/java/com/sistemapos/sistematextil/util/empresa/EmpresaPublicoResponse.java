package com.sistemapos.sistematextil.util.empresa;

import com.sistemapos.sistematextil.model.Empresa;

public record EmpresaPublicoResponse(
        String nombre,
        String nombreComercial,
        String logoUrl,
        Boolean generaFacturacionElectronica
) {
    public static EmpresaPublicoResponse fromEntity(Empresa empresa) {
        String nombreComercial = empresa.getNombreComercial();
        String nombreMostrar = nombreComercial == null || nombreComercial.isBlank()
                ? empresa.getNombre()
                : nombreComercial;
        return new EmpresaPublicoResponse(
                nombreMostrar,
                nombreComercial,
                empresa.getLogoUrl(),
                empresa.getGeneraFacturacionElectronica());
    }
}
