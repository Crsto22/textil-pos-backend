package com.sistemapos.sistematextil.util.empresa;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.model.Empresa;

public record EmpresaResponse(
        Integer idEmpresa,
        String nombre,
        String nombreComercial,
        String ruc,
        String razonSocial,
        String correo,
        String telefono,
        String logoUrl,
        Boolean generaFacturacionElectronica,
        LocalDateTime fechaCreacion
) {
    public static EmpresaResponse fromEntity(Empresa empresa) {
        return new EmpresaResponse(
                empresa.getIdEmpresa(),
                empresa.getNombre(),
                empresa.getNombreComercial(),
                empresa.getRuc(),
                empresa.getRazonSocial(),
                empresa.getCorreo(),
                empresa.getTelefono(),
                empresa.getLogoUrl(),
                empresa.getGeneraFacturacionElectronica(),
                empresa.getFechaCreacion());
    }
}
