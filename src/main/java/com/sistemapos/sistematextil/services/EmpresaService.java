package com.sistemapos.sistematextil.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.util.empresa.EmpresaPublicoResponse;

import lombok.AllArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;

@Service
@AllArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final S3StorageService s3StorageService;
    private static final int MAX_WIDTH = 800;
    private static final int MAX_HEIGHT = 800;
    private static final float CALIDAD = 0.85f;
    private static final long MAX_SIZE = 3 * 1024 * 1024; // 3MB

    static {
        ImageIO.scanForPlugins();
    }

    public List<Empresa> listarTodas() {
        return empresaRepository.findAll();
    }

    public EmpresaPublicoResponse obtenerPublica() {
        Empresa empresa = empresaRepository.findTopByOrderByIdEmpresaAsc()
                .orElseThrow(() -> new RuntimeException("No hay empresa registrada"));

        return EmpresaPublicoResponse.fromEntity(empresa);
    }

    public Empresa insertar(Empresa empresa) {
        if (empresa.getFechaCreacion() == null) {
            empresa.setFechaCreacion(LocalDateTime.now());
        }
        return empresaRepository.save(empresa);
    }

    public Empresa obtenerPorId(Integer idEmpresa) {
        return empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> new RuntimeException("La empresa con ID " + idEmpresa + " no existe"));
    }

    public Empresa actualizar(Integer idEmpresa, Empresa empresa) {
        Empresa original = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> new RuntimeException("La empresa no existe"));

        LocalDateTime fechaCreacion = original.getFechaCreacion() != null
                ? original.getFechaCreacion()
                : LocalDateTime.now();

        empresa.setIdEmpresa(idEmpresa);
        empresa.setFechaCreacion(fechaCreacion);

        if (empresa.getLogoUrl() == null) {
            empresa.setLogoUrl(original.getLogoUrl());
        }

        return empresaRepository.save(empresa);
    }

    public void eliminar(Integer idEmpresa) {
        if (!empresaRepository.existsById(idEmpresa)) {
            throw new RuntimeException("La empresa " + idEmpresa + " no existe");
        }
        empresaRepository.deleteById(idEmpresa);
    }

    public Empresa subirLogo(Integer idEmpresa, MultipartFile file) throws IOException {

        Empresa empresa = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        validarImagen(file);

        byte[] optimized = convertirAWebp(file.getBytes());
        String logoAnteriorUrl = empresa.getLogoUrl();

        String key = "empresa/" + idEmpresa + "/logo-" + UUID.randomUUID() + ".webp";

        String url = s3StorageService.upload(
                optimized,
                key,
                "image/webp"
        );

        empresa.setLogoUrl(url);

        try {
            Empresa guardada = empresaRepository.save(empresa);

            if (logoAnteriorUrl != null
                    && !logoAnteriorUrl.isBlank()
                    && !logoAnteriorUrl.equals(url)) {
                s3StorageService.deleteByUrl(logoAnteriorUrl);
            }

            return guardada;
        } catch (RuntimeException e) {
            // Si falla la persistencia, removemos el archivo nuevo para no dejar basura en S3.
            s3StorageService.deleteByUrl(url);
            throw e;
        }
    }

    private byte[] convertirAWebp(byte[] input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Thumbnails.of(new ByteArrayInputStream(input))
                .size(MAX_WIDTH, MAX_HEIGHT)
                .outputFormat("webp")
                .outputQuality(CALIDAD)
                .toOutputStream(out);

        return out.toByteArray();
    }

    private void validarImagen(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Debe enviar una imagen");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new RuntimeException("El logo no debe superar 3MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Formato de imagen no permitido");
        }
    }
}
