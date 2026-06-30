package com.sistemapos.sistematextil.util.ecommerce;

public record EcommercePortadaResponse(
        Integer idEcommercePortada,
        String desktopUrl,
        String desktopThumbUrl,
        String mobileUrl,
        String mobileThumbUrl,
        Integer orden,
        String estado
) {
}
