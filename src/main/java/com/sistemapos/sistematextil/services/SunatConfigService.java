package com.sistemapos.sistematextil.services;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;
import com.sistemapos.sistematextil.util.sunatconfig.SunatConfigConnectionTestResponse;
import com.sistemapos.sistematextil.util.sunatconfig.SunatConfigResponse;
import com.sistemapos.sistematextil.util.sunatconfig.SunatConfigUpsertRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatConfigService {

    private final SunatConfigRepository sunatConfigRepository;
    private final EmpresaRepository empresaRepository;
    private final SunatSecretCryptoService sunatSecretCryptoService;
    private final SunatCertificateStorageService sunatCertificateStorageService;
    private final SunatProperties sunatProperties;

    public SunatConfigResponse obtener() {
        return toResponse(obtenerConfigActual());
    }

    @Transactional
    public SunatConfigResponse guardar(SunatConfigUpsertRequest request) {
        Empresa empresa = obtenerEmpresaPrincipal();
        Optional<SunatConfig> configExistente = obtenerConfigActualOpcional(empresa);
        SunatConfig config = configExistente.orElseGet(SunatConfig::new);
        boolean esNuevo = config.getIdSunatConfig() == null;
        String ambienteAnterior = config.getAmbiente();

        config.setEmpresa(empresa);
        String ambiente = normalizarAmbiente(request.ambiente());
        config.setAmbiente(ambiente);
        config.setUsuarioSol(normalizarUsuarioSol(request.usuarioSol()));
        config.setUrlBillService(resolverUrlBillService(
                request.urlBillService(),
                ambiente,
                ambienteAnterior,
                config.getUrlBillService()));
        config.setActivo(normalizarActivo(request.activo()));
        config.setDeletedAt(null);

        String claveSol = normalizarTextoOpcional(request.claveSol());
        if (claveSol != null) {
            config.setClaveSol(sunatSecretCryptoService.encrypt(claveSol));
        } else if (esNuevo || config.getClaveSol() == null || config.getClaveSol().isBlank()) {
            throw new RuntimeException("Ingrese claveSol");
        }

        String certificadoPassword = normalizarTextoOpcional(request.certificadoPassword());
        if (certificadoPassword != null) {
            config.setCertificadoPassword(sunatSecretCryptoService.encrypt(certificadoPassword));
        }

        config.setClientId(normalizarTextoOpcional(request.clientId()));

        String clientSecret = normalizarTextoOpcional(request.clientSecret());
        if (clientSecret != null) {
            config.setClientSecret(sunatSecretCryptoService.encrypt(clientSecret));
        } else if (esNuevo) {
            config.setClientSecret(null);
        }

        SunatConfig saved = sunatConfigRepository.save(config);
        return toResponse(saved);
    }

    @Transactional
    public SunatConfigResponse subirCertificado(MultipartFile file, String certificadoPassword) {
        SunatConfig config = obtenerConfigActual();
        String certificadoAnterior = config.getCertificadoUrl();
        SunatCertificateStorageService.StoredCertificate stored = sunatCertificateStorageService.store(config.getEmpresa(),
                file);

        config.setCertificadoUrl(stored.absolutePath());
        String passwordNormalizado = normalizarTextoOpcional(certificadoPassword);
        if (passwordNormalizado != null) {
            config.setCertificadoPassword(sunatSecretCryptoService.encrypt(passwordNormalizado));
        }

        try {
            SunatConfig saved = sunatConfigRepository.save(config);
            if (certificadoAnterior != null && !certificadoAnterior.equals(stored.absolutePath())) {
                sunatCertificateStorageService.deleteIfManaged(certificadoAnterior);
            }
            return toResponse(saved);
        } catch (RuntimeException e) {
            sunatCertificateStorageService.deleteIfManaged(stored.absolutePath());
            throw e;
        }
    }

    public SunatConfigConnectionTestResponse probarConexion() {
        SunatConfig config = obtenerConfigActual();
        validarConfigActiva(config);

        String claveSol = sunatSecretCryptoService.decrypt(config.getClaveSol());
        if (claveSol == null || claveSol.isBlank()) {
            throw new RuntimeException("La configuracion SUNAT no tiene claveSol");
        }

        Path certificadoPath = sunatCertificateStorageService.resolveStoredPath(config.getCertificadoUrl());
        String certificadoPassword = sunatSecretCryptoService.decrypt(config.getCertificadoPassword());
        if (certificadoPassword == null || certificadoPassword.isBlank()) {
            throw new RuntimeException("La configuracion SUNAT no tiene certificadoPassword");
        }

        try (var input = java.nio.file.Files.newInputStream(certificadoPath)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, certificadoPassword.toCharArray());

            String alias = Collections.list(keyStore.aliases()).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("El certificado digital no contiene alias validos"));

            Certificate certificate = keyStore.getCertificate(alias);
            LocalDateTime vigenteDesde = null;
            LocalDateTime vigenteHasta = null;
            if (certificate instanceof X509Certificate x509Certificate) {
                vigenteDesde = LocalDateTime.ofInstant(
                        x509Certificate.getNotBefore().toInstant(),
                        ZoneId.systemDefault());
                vigenteHasta = LocalDateTime.ofInstant(
                        x509Certificate.getNotAfter().toInstant(),
                        ZoneId.systemDefault());
            }

            return new SunatConfigConnectionTestResponse(
                    true,
                    "Configuracion SUNAT validada correctamente. No se realizo envio real a SUNAT.",
                    config.getAmbiente(),
                    config.getUsuarioSol(),
                    config.getUrlBillService(),
                    obtenerNombreArchivo(config.getCertificadoUrl()),
                    true,
                    true,
                    true,
                    alias,
                    vigenteDesde,
                    vigenteHasta,
                    sunatProperties.normalizedMode());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo validar el certificado digital con la clave configurada");
        }
    }

    private SunatConfigResponse toResponse(SunatConfig config) {
        Empresa empresa = config.getEmpresa();
        return new SunatConfigResponse(
                config.getIdSunatConfig(),
                empresa != null ? empresa.getIdEmpresa() : null,
                empresa != null ? empresa.getNombre() : null,
                empresa != null ? empresa.getRuc() : null,
                config.getAmbiente(),
                config.getUsuarioSol(),
                config.getUrlBillService(),
                obtenerNombreArchivo(config.getCertificadoUrl()),
                tieneValor(config.getClaveSol()),
                tieneValor(config.getCertificadoUrl()),
                tieneValor(config.getCertificadoPassword()),
                tieneValor(config.getClientId()),
                tieneValor(config.getClientSecret()),
                config.getActivo(),
                sunatProperties.normalizedMode(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }

    private SunatConfig obtenerConfigActual() {
        Empresa empresa = obtenerEmpresaPrincipal();
        return obtenerConfigActualOpcional(empresa)
                .orElseThrow(() -> new RuntimeException("No hay configuracion SUNAT registrada"));
    }

    private Optional<SunatConfig> obtenerConfigActualOpcional(Empresa empresa) {
        if (empresa == null || empresa.getIdEmpresa() == null) {
            throw new RuntimeException("No hay empresa registrada");
        }

        List<SunatConfig> configs = sunatConfigRepository
                .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSunatConfigAsc(empresa.getIdEmpresa());

        if (configs.size() > 1) {
            throw new RuntimeException("Existe mas de una configuracion SUNAT activa. Depure la tabla sunat_config");
        }
        return configs.stream().findFirst();
    }

    private Empresa obtenerEmpresaPrincipal() {
        return empresaRepository.findTopByOrderByIdEmpresaAsc()
                .orElseThrow(() -> new RuntimeException("No hay empresa registrada"));
    }

    private void validarConfigActiva(SunatConfig config) {
        if (config == null) {
            throw new RuntimeException("No hay configuracion SUNAT registrada");
        }
        if (!"ACTIVO".equalsIgnoreCase(config.getActivo())) {
            throw new RuntimeException("La configuracion SUNAT esta inactiva");
        }
    }

    private String normalizarAmbiente(String ambiente) {
        if (ambiente == null || ambiente.isBlank()) {
            return "BETA";
        }
        String normalized = ambiente.trim().toUpperCase(Locale.ROOT);
        if (!"BETA".equals(normalized) && !"PRODUCCION".equals(normalized)) {
            throw new RuntimeException("ambiente permitido: BETA o PRODUCCION");
        }
        return normalized;
    }

    private String normalizarUsuarioSol(String usuarioSol) {
        if (usuarioSol == null || usuarioSol.isBlank()) {
            throw new RuntimeException("Ingrese usuarioSol");
        }
        String normalized = usuarioSol.trim();
        if (normalized.length() > 50) {
            throw new RuntimeException("usuarioSol no debe superar 50 caracteres");
        }
        return normalized;
    }

    private String normalizarActivo(String activo) {
        if (activo == null || activo.isBlank()) {
            return "ACTIVO";
        }
        String normalized = activo.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVO".equals(normalized) && !"INACTIVO".equals(normalized)) {
            throw new RuntimeException("activo permitido: ACTIVO o INACTIVO");
        }
        return normalized;
    }

    private String resolverUrlBillService(
            String urlRequest,
            String ambienteNuevo,
            String ambienteAnterior,
            String urlActual) {
        String normalized = normalizarTextoOpcional(urlRequest);
        if (normalized != null) {
            return validarUrl(normalized);
        }
        if (urlActual != null && !urlActual.isBlank()
                && ambienteAnterior != null
                && ambienteAnterior.equalsIgnoreCase(ambienteNuevo)) {
            return urlActual;
        }
        return defaultUrlBillService(ambienteNuevo);
    }

    private String validarUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new RuntimeException("urlBillService debe ser una URL absoluta");
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("urlBillService debe ser una URL valida");
        }
    }

    private String defaultUrlBillService(String ambiente) {
        if ("PRODUCCION".equalsIgnoreCase(ambiente)) {
            return "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService?wsdl";
        }
        return "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService?wsdl";
    }

    private String normalizarTextoOpcional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean tieneValor(String value) {
        return value != null && !value.isBlank();
    }

    private String obtenerNombreArchivo(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            return Path.of(storedPath).getFileName().toString();
        } catch (Exception e) {
            return storedPath;
        }
    }
}
