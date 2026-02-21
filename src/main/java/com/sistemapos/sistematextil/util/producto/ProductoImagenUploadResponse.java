package com.sistemapos.sistematextil.util.producto;

import java.util.List;

public record ProductoImagenUploadResponse(
        Integer colorId,
        List<ProductoImagenUploadItem> imagenes
) {
}
