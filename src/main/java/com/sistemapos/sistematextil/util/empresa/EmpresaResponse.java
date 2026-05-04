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
        String direccion,
        String ubigeo,
        String departamento,
        String provincia,
        String distrito,
        String codigoEstablecimientoSunat,
        String logoUrl,
        Boolean generaFacturacionElectronica,
        Boolean activo,
        LocalDateTime fechaCreacion,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
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
                empresa.getDireccion(),
                empresa.getUbigeo(),
                empresa.getDepartamento(),
                empresa.getProvincia(),
                empresa.getDistrito(),
                empresa.getCodigoEstablecimientoSunat(),
                empresa.getLogoUrl(),
                empresa.getGeneraFacturacionElectronica(),
                empresa.getActivo(),
                empresa.getFechaCreacion(),
                empresa.getUpdatedAt(),
                empresa.getDeletedAt());
    }
}
