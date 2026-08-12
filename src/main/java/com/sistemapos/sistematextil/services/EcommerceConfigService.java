package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.EcommerceConfig;
import com.sistemapos.sistematextil.repositories.EcommerceConfigRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceContactoResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcommerceConfigService {

    private static final int CONFIG_ID = 1;

    private final EcommerceConfigRepository ecommerceConfigRepository;
    private final EcommerceCacheInvalidationService ecommerceCacheInvalidationService;

    public EcommerceContactoResponse obtenerContacto() {
        return EcommerceContactoResponse.fromCelular(
                ecommerceConfigRepository.findById(CONFIG_ID)
                        .map(EcommerceConfig::getWhatsappCelular)
                        .orElse(null));
    }

    @Transactional
    public EcommerceContactoResponse guardarContacto(String whatsappCelular) {
        EcommerceConfig config = ecommerceConfigRepository.findById(CONFIG_ID).orElseGet(() -> {
            EcommerceConfig nuevo = new EcommerceConfig();
            nuevo.setIdEcommerceConfig(CONFIG_ID);
            return nuevo;
        });
        config.setWhatsappCelular(normalizarWhatsapp(whatsappCelular));
        EcommerceConfig guardado = ecommerceConfigRepository.save(config);
        ecommerceCacheInvalidationService.invalidate();
        return EcommerceContactoResponse.fromCelular(guardado.getWhatsappCelular());
    }

    private String normalizarWhatsapp(String whatsappCelular) {
        if (whatsappCelular == null || whatsappCelular.isBlank()) {
            return null;
        }
        String normalizado = whatsappCelular.trim();
        if (!normalizado.matches("\\d{9}")) {
            throw new RuntimeException("El celular WhatsApp debe tener exactamente 9 digitos");
        }
        return normalizado;
    }
}
