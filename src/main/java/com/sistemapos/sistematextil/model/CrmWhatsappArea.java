package com.sistemapos.sistematextil.model;

import java.util.Locale;

public enum CrmWhatsappArea {
    SALES("sales", "kiments-sales"),
    SHIPPING("shipping", "kiments-shipping");

    private final String path;
    private final String clientId;

    CrmWhatsappArea(String path, String clientId) {
        this.path = path;
        this.clientId = clientId;
    }

    public String path() { return path; }
    public String clientId() { return clientId; }

    public static CrmWhatsappArea from(String value) {
        if (value == null) throw new IllegalArgumentException("Area de WhatsApp requerida");
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
