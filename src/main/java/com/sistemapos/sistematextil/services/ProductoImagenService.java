package com.sistemapos.sistematextil.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.util.producto.ProductoImagenEditResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenUploadItem;
import com.sistemapos.sistematextil.util.producto.ProductoImagenUploadResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteImagenDeleteResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteImagenUploadResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final ProductoVarianteService productoVarianteService;
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
    public ProductoVarianteImagenUploadResponse subirImagenesDesdeVariante(
            Integer idProductoVariante,
            String correoUsuarioAutenticado,
            List<MultipartFile> files) {
        ProductoVariante variante = productoVarianteService
                .obtenerVarianteEditableConAlcance(idProductoVariante, correoUsuarioAutenticado);
        Integer productoId = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        if (productoId == null) {
            throw new RuntimeException("La variante no tiene producto asociado");
        }
        if (colorId == null) {
            throw new RuntimeException("La variante no tiene color asociado");
        }

        Color color = obtenerColorActivo(colorId);
        List<ProductoColorImagen> existentes = productoColorImagenRepository
                .findByProductoIdProductoAndColorIdColorAndDeletedAtIsNull(productoId, colorId);
        if (existentes.size() + (files == null ? 0 : files.size()) > MAX_IMAGENES_POR_COLOR) {
            throw new RuntimeException("Maximo " + MAX_IMAGENES_POR_COLOR + " imagenes por color");
        }
        validarArchivos(files);

        int siguienteOrden = existentes.stream()
                .map(ProductoColorImagen::getOrden)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        boolean asignarPrincipal = existentes.stream().noneMatch(imagen -> Boolean.TRUE.equals(imagen.getEsPrincipal()));

        List<ProductoColorImagen> nuevas = new ArrayList<>();
        int ordenActual = siguienteOrden;
        for (MultipartFile file : files) {
            UploadPair upload = subirImagen(file, productoId, colorId);
            ProductoColorImagen imagen = new ProductoColorImagen();
            imagen.setProducto(variante.getProducto());
            imagen.setColor(color);
            imagen.setUrl(upload.url());
            imagen.setUrlThumb(upload.urlThumb());
            imagen.setOrden(ordenActual++);
            imagen.setEsPrincipal(asignarPrincipal);
            imagen.setEstado("ACTIVO");
            nuevas.add(imagen);
            if (asignarPrincipal) {
                asignarPrincipal = false;
            }
        }

        List<ProductoColorImagen> guardadas = productoColorImagenRepository.saveAll(nuevas);
        return new ProductoVarianteImagenUploadResponse(
                idProductoVariante,
                productoId,
                colorId,
                construirGrupoImagenKey(productoId, colorId),
                guardadas.stream().map(this::toVarianteUploadItem).toList());
    }

    @Transactional
    public ProductoVarianteImagenDeleteResponse eliminarImagenDesdeVariante(
            Integer idProductoVariante,
            Integer idColorImagen,
            String correoUsuarioAutenticado) {
        if (idColorImagen == null) {
            throw new RuntimeException("Ingrese idColorImagen");
        }

        ProductoVariante variante = productoVarianteService
                .obtenerVarianteEditableConAlcance(idProductoVariante, correoUsuarioAutenticado);
        Integer productoId = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        if (productoId == null) {
            throw new RuntimeException("La variante no tiene producto asociado");
        }
        if (colorId == null) {
            throw new RuntimeException("La variante no tiene color asociado");
        }

        ProductoColorImagen imagen = productoColorImagenRepository.findByIdColorImagenAndDeletedAtIsNull(idColorImagen)
                .orElseThrow(() -> new RuntimeException("Imagen con ID " + idColorImagen + " no encontrada"));
        Integer productoImagenId = imagen.getProducto() != null ? imagen.getProducto().getIdProducto() : null;
        Integer colorImagenId = imagen.getColor() != null ? imagen.getColor().getIdColor() : null;
        if (!productoId.equals(productoImagenId) || !colorId.equals(colorImagenId)) {
            throw new RuntimeException("La imagen no pertenece al producto/color de la variante indicada");
        }

        String oldUrl = imagen.getUrl();
        String oldUrlThumb = imagen.getUrlThumb();
        boolean eraPrincipal = Boolean.TRUE.equals(imagen.getEsPrincipal());
        imagen.setEsPrincipal(false);
        imagen.setEstado("INACTIVO");
        imagen.setDeletedAt(java.time.LocalDateTime.now());
        productoColorImagenRepository.save(imagen);

        List<ProductoColorImagen> restantes = productoColorImagenRepository
                .findByProductoIdProductoAndColorIdColorAndDeletedAtIsNull(productoId, colorId);
        ProductoColorImagen nuevaPrincipal = normalizarImagenPrincipal(productoId, colorId, restantes, eraPrincipal);

        eliminarDesdeStorage(oldUrl);
        eliminarDesdeStorage(oldUrlThumb);

        List<ProductoVarianteImagenDeleteResponse.ImagenItem> imagenesRestantes = restantes.stream()
                .sorted(Comparator
                        .comparing((ProductoColorImagen item) -> !Boolean.TRUE.equals(item.getEsPrincipal()))
                        .thenComparing(item -> item.getOrden() == null ? Integer.MAX_VALUE : item.getOrden())
                        .thenComparing(item -> item.getIdColorImagen() == null ? Integer.MAX_VALUE : item.getIdColorImagen()))
                .map(this::toVarianteDeleteItem)
                .toList();

        return new ProductoVarianteImagenDeleteResponse(
                idProductoVariante,
                productoId,
                colorId,
                construirGrupoImagenKey(productoId, colorId),
                idColorImagen,
                imagenesRestantes,
                nuevaPrincipal != null ? toVarianteDeleteItem(nuevaPrincipal) : null);
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

        ProductoColorImagen imagen = productoColorImagenRepository.findByIdColorImagenAndDeletedAtIsNull(idColorImagen)
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
            try {
                s3StorageService.deleteByUrl(oldUrl);
            } catch (RuntimeException e) {
                log.warn("No se pudo eliminar imagen anterior del almacenamiento administrado: {}", oldUrl, e);
            }
        }
        if (oldUrlThumb != null && !oldUrlThumb.equals(nuevaUrlThumb)) {
            try {
                s3StorageService.deleteByUrl(oldUrlThumb);
            } catch (RuntimeException e) {
                log.warn("No se pudo eliminar thumbnail anterior del almacenamiento administrado: {}", oldUrlThumb, e);
            }
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

    private String construirGrupoImagenKey(Integer productoId, Integer colorId) {
        return productoId + "-" + colorId;
    }

    private UploadPair subirImagen(MultipartFile file, Integer productoId, Integer colorId) {
        validarImagen(file);
        String baseKey = construirBaseKey(productoId, colorId) + UUID.randomUUID();
        try {
            byte[] original = file.getBytes();
            byte[] fullWebp = convertirAWebp(original, MAX_WIDTH, MAX_HEIGHT, CALIDAD_WEBP);
            byte[] thumbWebp = convertirAWebp(original, THUMB_WIDTH, THUMB_HEIGHT, CALIDAD_THUMB);

            String url = s3StorageService.upload(fullWebp, baseKey + ".webp", "image/webp");
            String urlThumb = s3StorageService.upload(thumbWebp, baseKey + "-thumb.webp", "image/webp");
            return new UploadPair(url, urlThumb);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo procesar la imagen enviada");
        }
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

    private void validarArchivos(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new RuntimeException("Debe enviar al menos una imagen");
        }
        for (MultipartFile file : files) {
            validarImagen(file);
        }
    }

    private Color obtenerColorActivo(Integer colorId) {
        Color color = colorService.obtenerPorId(colorId);
        if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
            throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
        }
        return color;
    }

    private void desmarcarImagenesPrincipalesDelMismoColor(Integer productoId, Integer colorId, Integer idActual) {
        List<ProductoColorImagen> imagenes = productoColorImagenRepository
                .findByProductoIdProductoAndColorIdColorAndDeletedAtIsNull(productoId, colorId);
        for (ProductoColorImagen item : imagenes) {
            if (!item.getIdColorImagen().equals(idActual) && Boolean.TRUE.equals(item.getEsPrincipal())) {
                item.setEsPrincipal(false);
            }
        }
        productoColorImagenRepository.saveAll(imagenes);
    }

    private ProductoColorImagen normalizarImagenPrincipal(
            Integer productoId,
            Integer colorId,
            List<ProductoColorImagen> imagenes,
            boolean recalcular) {
        if (imagenes == null || imagenes.isEmpty()) {
            return null;
        }

        ProductoColorImagen principal = imagenes.stream()
                .filter(imagen -> Boolean.TRUE.equals(imagen.getEsPrincipal()))
                .findFirst()
                .orElse(null);

        if (principal == null || recalcular) {
            principal = imagenes.stream()
                    .sorted(Comparator
                            .comparing((ProductoColorImagen item) -> item.getOrden() == null ? Integer.MAX_VALUE : item.getOrden())
                            .thenComparing(item -> item.getIdColorImagen() == null ? Integer.MAX_VALUE : item.getIdColorImagen()))
                    .findFirst()
                    .orElse(null);
        }

        final Integer idPrincipal = principal != null ? principal.getIdColorImagen() : null;
        boolean cambio = false;
        for (ProductoColorImagen item : imagenes) {
            boolean debeSerPrincipal = item.getIdColorImagen() != null && item.getIdColorImagen().equals(idPrincipal);
            if (!java.util.Objects.equals(item.getEsPrincipal(), debeSerPrincipal)) {
                item.setEsPrincipal(debeSerPrincipal);
                cambio = true;
            }
        }
        if (cambio) {
            productoColorImagenRepository.saveAll(imagenes);
        }
        return principal;
    }

    private void eliminarDesdeStorage(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            s3StorageService.deleteByUrl(url);
        } catch (RuntimeException e) {
            log.warn("No se pudo eliminar imagen del almacenamiento administrado: {}", url, e);
        }
    }

    private ProductoVarianteImagenUploadResponse.ImagenItem toVarianteUploadItem(ProductoColorImagen imagen) {
        return new ProductoVarianteImagenUploadResponse.ImagenItem(
                imagen.getIdColorImagen(),
                imagen.getUrl(),
                imagen.getUrlThumb(),
                imagen.getOrden(),
                imagen.getEsPrincipal());
    }

    private ProductoVarianteImagenDeleteResponse.ImagenItem toVarianteDeleteItem(ProductoColorImagen imagen) {
        return new ProductoVarianteImagenDeleteResponse.ImagenItem(
                imagen.getIdColorImagen(),
                imagen.getUrl(),
                imagen.getUrlThumb(),
                imagen.getOrden(),
                imagen.getEsPrincipal());
    }

    private record UploadPair(String url, String urlThumb) {
    }
}
