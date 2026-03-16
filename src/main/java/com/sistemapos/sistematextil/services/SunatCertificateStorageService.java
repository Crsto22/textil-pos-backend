package com.sistemapos.sistematextil.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.Empresa;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatCertificateStorageService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Set<String> EXT_PERMITIDAS = Set.of("pfx", "p12");

    private final SunatProperties sunatProperties;

    public StoredCertificate store(Empresa empresa, MultipartFile file) {
        validarArchivo(file);
        try {
            String extension = obtenerExtension(file.getOriginalFilename());
            String ruc = empresa != null && empresa.getRuc() != null && !empresa.getRuc().isBlank()
                    ? empresa.getRuc().trim()
                    : "empresa";

            Path empresaDir = resolveBasePath().resolve(ruc).normalize();
            Files.createDirectories(empresaDir);

            String fileName = "certificado-" + LocalDateTime.now().format(FILE_TS) + "." + extension;
            Path destino = empresaDir.resolve(fileName).normalize();
            if (!destino.startsWith(empresaDir)) {
                throw new RuntimeException("Ruta de certificado invalida");
            }

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destino, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredCertificate(destino.toAbsolutePath().toString(), fileName);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el certificado digital");
        }
    }

    public void deleteIfManaged(String storedPath) {
        Path managedPath = resolveManagedPath(storedPath);
        if (managedPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(managedPath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo reemplazar el certificado anterior");
        }
    }

    public Path resolveStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new RuntimeException("No hay certificado digital configurado");
        }
        Path path = Paths.get(storedPath.trim()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new RuntimeException("El certificado digital configurado no existe en disco");
        }
        return path;
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Debe enviar el certificado digital");
        }

        long maxBytes = Math.max(1, sunatProperties.getCertMaxFileSizeMb()) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new RuntimeException("El certificado digital no debe superar "
                    + sunatProperties.getCertMaxFileSizeMb() + "MB");
        }

        String extension = obtenerExtension(file.getOriginalFilename());
        if (!EXT_PERMITIDAS.contains(extension)) {
            throw new RuntimeException("Formato de certificado permitido: .pfx o .p12");
        }
    }

    private String obtenerExtension(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            throw new RuntimeException("El certificado digital debe tener extension .pfx o .p12");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
        if (extension.isBlank()) {
            throw new RuntimeException("El certificado digital debe tener extension .pfx o .p12");
        }
        return extension;
    }

    private Path resolveBasePath() {
        String configured = sunatProperties.getCertBasePath();
        String value = configured == null || configured.isBlank() ? "storage/sunat/certificados" : configured.trim();
        return Paths.get(value).toAbsolutePath().normalize();
    }

    private Path resolveManagedPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        Path base = resolveBasePath();
        Path path = Paths.get(storedPath.trim()).toAbsolutePath().normalize();
        return path.startsWith(base) ? path : null;
    }

    public record StoredCertificate(
            String absolutePath,
            String fileName) {
    }
}
