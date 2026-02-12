package com.sistemapos.sistematextil.util;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.model.Empresa;

public record EmpresaResponse(
        Integer idEmpresa,
        String nombre,
        String ruc,
        String razonSocial,
        String correo,
        LocalDateTime fechaCreacion
) {
    public static EmpresaResponse fromEntity(Empresa empresa) {
        return new EmpresaResponse(
                empresa.getIdEmpresa(),
                empresa.getNombre(),
                empresa.getRuc(),
                empresa.getRazonSocial(),
                empresa.getCorreo(),
                empresa.getFechaCreacion()
        );
    }
}
