package com.sistemapos.sistematextil.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatConfigValidationService {

    private static final String MENSAJE_CERTIFICADO_VENTA =
            "Debe configurar el certificado SUNAT para emitir boletas y facturas.";
    private static final String MENSAJE_CERTIFICADO_NOTA_CREDITO =
            "Debe configurar el certificado SUNAT para emitir notas de credito.";
    private static final String MENSAJE_CERTIFICADO_OPERACION_SUNAT =
            "Debe configurar el certificado SUNAT antes de realizar operaciones relacionadas con SUNAT.";

    private final SunatConfigRepository sunatConfigRepository;
    private final SunatCertificateStorageService sunatCertificateStorageService;

    public void validarCertificadoParaVentaElectronica(Integer idEmpresa) {
        validarCertificadoConfigurado(idEmpresa, MENSAJE_CERTIFICADO_VENTA);
    }

    public void validarCertificadoParaNotaCredito(Integer idEmpresa) {
        validarCertificadoConfigurado(idEmpresa, MENSAJE_CERTIFICADO_NOTA_CREDITO);
    }

    public void validarCertificadoParaOperacionSunat(Integer idEmpresa) {
        validarCertificadoConfigurado(idEmpresa, MENSAJE_CERTIFICADO_OPERACION_SUNAT);
    }

    public SunatConfig obtenerConfiguracionActiva(Integer idEmpresa) {
        if (idEmpresa == null) {
            throw new RuntimeException("No se pudo determinar la empresa para validar la configuracion SUNAT");
        }

        List<SunatConfig> configs = sunatConfigRepository
                .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSunatConfigAsc(idEmpresa);

        if (configs.isEmpty()) {
            throw new RuntimeException("No hay configuracion SUNAT registrada");
        }
        if (configs.size() > 1) {
            throw new RuntimeException("Existe mas de una configuracion SUNAT activa. Depure la tabla sunat_config");
        }

        SunatConfig config = configs.get(0);
        if (!"ACTIVO".equalsIgnoreCase(config.getActivo())) {
            throw new RuntimeException("La configuracion SUNAT esta inactiva");
        }
        return config;
    }

    private void validarCertificadoConfigurado(Integer idEmpresa, String mensajeSiFaltaCertificado) {
        SunatConfig config;
        try {
            config = obtenerConfiguracionActiva(idEmpresa);
        } catch (RuntimeException e) {
            if ("No hay configuracion SUNAT registrada".equals(e.getMessage())) {
                throw new RuntimeException(mensajeSiFaltaCertificado);
            }
            throw e;
        }
        if (config.getCertificadoUrl() == null || config.getCertificadoUrl().isBlank()) {
            throw new RuntimeException(mensajeSiFaltaCertificado);
        }
        Path certificadoPath = sunatCertificateStorageService.resolveStoredPath(config.getCertificadoUrl());
        if (!Files.isRegularFile(certificadoPath) || !Files.isReadable(certificadoPath)) {
            throw new RuntimeException("El certificado SUNAT configurado no existe o no se puede leer.");
        }
    }
}
