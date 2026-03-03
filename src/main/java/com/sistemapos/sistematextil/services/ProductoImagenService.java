package com.sistemapos.sistematextil.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.util.producto.ProductoImagenEditResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenUploadItem;
import com.sistemapos.sistematextil.util.producto.ProductoImagenUploadResponse;

import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;

@Service
@RequiredArgsConstructor
public class ProductoImagenService {

    private static final int MAX_IMAGENES_POR_COLOR = 5;
    private static final int MAX_WIDTH = 1600;
    private static final int MAX_HEIGHT = 1600;
    private static final float CALIDAD_WEBP = 0.82f;
    private static final int THUMB_WIDTH = 400;
    private static final int THUMB_HEIGHT = 400;
    private static final float CALIDAD_THUMB = 0.75f;

    private final ColorService colorService;
    private final S3StorageService s3StorageService;
    private final ProductoService productoService;
    private final ProductoColorImagenRepository productoColorImagenRepository;

    static {
        ImageIO.scanForPlugins();
    }

    public ProductoImagenUploadResponse subirImagenes(Integer productoId, Integer colorId, List<MultipartFile> files) {
        if (colorId == null) {
            throw new RuntimeException("Ingrese colorId");
        }
        if (files == null || files.isEmpty()) {
            throw new RuntimeException("Debe enviar al menos una imagen");
        }
        if (files.size() > MAX_IMAGENES_POR_COLOR) {
            throw new RuntimeException("Maximo " + MAX_IMAGENES_POR_COLOR + " imagenes por color");
        }
        if (productoId != null) {
            productoService.obtenerPorId(productoId);
        }

        Color color = colorService.obtenerPorId(colorId);
        if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
            throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
        }

        List<ProductoImagenUploadItem> response = new ArrayList<>();
        int orden = 1;
        for (MultipartFile file : files) {
            validarImagen(file);
            String baseKey = construirBaseKey(productoId, colorId) + UUID.randomUUID();

            try {
                byte[] original = file.getBytes();
                byte[] fullWebp = convertirAWebp(original, MAX_WIDTH, MAX_HEIGHT, CALIDAD_WEBP);
                byte[] thumbWebp = convertirAWebp(original, THUMB_WIDTH, THUMB_HEIGHT, CALIDAD_THUMB);

                String url = s3StorageService.upload(fullWebp, baseKey + ".webp", "image/webp");
                String urlThumb = s3StorageService.upload(thumbWebp, baseKey + "-thumb.webp", "image/webp");

                response.add(new ProductoImagenUploadItem(url, urlThumb, orden));
                orden++;
            } catch (IOException e) {
                throw new RuntimeException("No se pudo procesar la imagen enviada");
            }
        }

        return new ProductoImagenUploadResponse(colorId, response);
    }

    @Transactional
    public ProductoImagenEditResponse reemplazarImagen(
            Integer idColorImagen,
            String correoUsuarioAutenticado,
            MultipartFile file,
            Integer orden,
            Boolean esPrincipal) {
        if (idColorImagen == null) {
            throw new RuntimeException("Ingrese idColorImagen");
        }
        validarImagen(file);
        if (orden != null && (orden < 1 || orden > 5)) {
            throw new RuntimeException("El orden de imagen debe estar entre 1 y 5");
        }

        ProductoColorImagen imagen = productoColorImagenRepository.findByIdColorImagen(idColorImagen)
                .orElseThrow(() -> new RuntimeException("Imagen con ID " + idColorImagen + " no encontrada"));

        Integer productoId = imagen.getProducto() != null ? imagen.getProducto().getIdProducto() : null;
        if (productoId == null) {
            throw new RuntimeException("La imagen no tiene producto asociado");
        }
        productoService.obtenerPorIdConAlcance(productoId, correoUsuarioAutenticado);

        Integer colorId = imagen.getColor() != null ? imagen.getColor().getIdColor() : null;
        if (colorId == null) {
            throw new RuntimeException("La imagen no tiene color asociado");
        }

        Color color = colorService.obtenerPorId(colorId);
        if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
            throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
        }

        String oldUrl = imagen.getUrl();
        String oldUrlThumb = imagen.getUrlThumb();
        String baseKey = construirBaseKey(productoId, colorId) + UUID.randomUUID();

        String nuevaUrl;
        String nuevaUrlThumb;
        try {
            byte[] original = file.getBytes();
            byte[] fullWebp = convertirAWebp(original, MAX_WIDTH, MAX_HEIGHT, CALIDAD_WEBP);
            byte[] thumbWebp = convertirAWebp(original, THUMB_WIDTH, THUMB_HEIGHT, CALIDAD_THUMB);

            nuevaUrl = s3StorageService.upload(fullWebp, baseKey + ".webp", "image/webp");
            nuevaUrlThumb = s3StorageService.upload(thumbWebp, baseKey + "-thumb.webp", "image/webp");
        } catch (IOException e) {
            throw new RuntimeException("No se pudo procesar la imagen enviada");
        }

        imagen.setUrl(nuevaUrl);
        imagen.setUrlThumb(nuevaUrlThumb);
        if (orden != null) {
            imagen.setOrden(orden);
        }
        if (esPrincipal != null) {
            imagen.setEsPrincipal(esPrincipal);
        }

        if (Boolean.TRUE.equals(imagen.getEsPrincipal()) && productoId != null) {
            desmarcarImagenesPrincipalesDelMismoColor(productoId, colorId, idColorImagen);
        }

        ProductoColorImagen actualizada = productoColorImagenRepository.save(imagen);

        if (oldUrl != null && !oldUrl.equals(nuevaUrl)) {
            s3StorageService.deleteByUrl(oldUrl);
        }
        if (oldUrlThumb != null && !oldUrlThumb.equals(nuevaUrlThumb)) {
            s3StorageService.deleteByUrl(oldUrlThumb);
        }

        return new ProductoImagenEditResponse(
                actualizada.getIdColorImagen(),
                productoId,
                colorId,
                actualizada.getUrl(),
                actualizada.getUrlThumb(),
                actualizada.getOrden(),
                actualizada.getEsPrincipal());
    }

    private String construirBaseKey(Integer productoId, Integer colorId) {
        if (productoId != null) {
            return "productos/producto-" + productoId + "/color-" + colorId + "/";
        }
        return "productos/color-" + colorId + "/";
    }

    private byte[] convertirAWebp(byte[] input, int width, int height, float quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(input))
                .size(width, height)
                .outputFormat("webp")
                .outputQuality(quality)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private void validarImagen(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Se encontro una imagen vacia");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Formato de imagen no permitido");
        }
    }

    private void desmarcarImagenesPrincipalesDelMismoColor(Integer productoId, Integer colorId, Integer idActual) {
        List<ProductoColorImagen> imagenes = productoColorImagenRepository
                .findByProductoIdProductoAndColorIdColor(productoId, colorId);
        for (ProductoColorImagen item : imagenes) {
            if (!item.getIdColorImagen().equals(idActual) && Boolean.TRUE.equals(item.getEsPrincipal())) {
                item.setEsPrincipal(false);
            }
        }
        productoColorImagenRepository.saveAll(imagenes);
    }
}
