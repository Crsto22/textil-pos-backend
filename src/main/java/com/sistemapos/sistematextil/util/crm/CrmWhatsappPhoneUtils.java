package com.sistemapos.sistematextil.util.crm;

import java.util.List;
import java.util.Locale;

public final class CrmWhatsappPhoneUtils {

    private static final String WHATSAPP_SUFFIX = "@s.whatsapp.net";
    private static final String LEGACY_WHATSAPP_SUFFIX = "@c.us";

    private CrmWhatsappPhoneUtils() {
    }

    public static String normalizePeruvianMobile(String value) {
        if (value == null) {
            return "";
        }

        String candidate = value.trim().toLowerCase(Locale.ROOT);
        if (candidate.isBlank() || candidate.contains("@lid")) {
            return "";
        }
        if (candidate.contains("@")) {
            if (candidate.endsWith(WHATSAPP_SUFFIX)) {
                candidate = candidate.substring(0, candidate.length() - WHATSAPP_SUFFIX.length());
            } else if (candidate.endsWith(LEGACY_WHATSAPP_SUFFIX)) {
                candidate = candidate.substring(0, candidate.length() - LEGACY_WHATSAPP_SUFFIX.length());
            } else {
                return "";
            }
        }

        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("51")) {
            digits = digits.substring(2);
        }
        return digits.matches("9\\d{8}") ? digits : "";
    }

    public static List<String> variants(String value) {
        String phone = normalizePeruvianMobile(value);
        return phone.isBlank() ? List.of() : List.of(phone, "51" + phone);
    }
}
