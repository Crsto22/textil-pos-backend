package com.sistemapos.sistematextil.util.ecommerce;

public record EcommerceContactoResponse(
        boolean configurado,
        String whatsappCelular,
        String whatsappNumeroInternacional
) {
    public static EcommerceContactoResponse fromCelular(String whatsappCelular) {
        boolean configurado = whatsappCelular != null && !whatsappCelular.isBlank();
        return new EcommerceContactoResponse(
                configurado,
                configurado ? whatsappCelular : null,
                configurado ? "51" + whatsappCelular : null);
    }
}
