package com.sistemapos.sistematextil.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;

class CrmWhatsappPhoneUtilsTest {

    @Test
    void normalizaCelularesPeruanos() {
        assertEquals("932889985", CrmWhatsappPhoneUtils.normalizePeruvianMobile("932889985"));
        assertEquals("932889985", CrmWhatsappPhoneUtils.normalizePeruvianMobile("51932889985"));
        assertEquals("932889985", CrmWhatsappPhoneUtils.normalizePeruvianMobile("51932889985@s.whatsapp.net"));
        assertEquals("932889985", CrmWhatsappPhoneUtils.normalizePeruvianMobile("932889985@c.us"));
    }

    @Test
    void rechazaIdentificadoresTecnicosYFormatosDesconocidos() {
        assertEquals("", CrmWhatsappPhoneUtils.normalizePeruvianMobile("163569568059625@lid"));
        assertEquals("", CrmWhatsappPhoneUtils.normalizePeruvianMobile("163569568059625"));
        assertEquals("", CrmWhatsappPhoneUtils.normalizePeruvianMobile("51932889985@lid"));
        assertEquals("", CrmWhatsappPhoneUtils.normalizePeruvianMobile("932889985@example.com"));
        assertEquals("", CrmWhatsappPhoneUtils.normalizePeruvianMobile("812345678"));
    }

    @Test
    void generaSoloVariantesDefensivasValidas() {
        assertEquals(List.of("932889985", "51932889985"), CrmWhatsappPhoneUtils.variants("932889985"));
        assertEquals(List.of(), CrmWhatsappPhoneUtils.variants("163569568059625@lid"));
    }
}
