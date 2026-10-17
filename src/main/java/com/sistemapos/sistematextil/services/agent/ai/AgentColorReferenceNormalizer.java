package com.sistemapos.sistematextil.services.agent.ai;

import java.text.Normalizer;
import java.util.Locale;

/** Normaliza regionalismos solo después de que la IA los clasificó como color. */
final class AgentColorReferenceNormalizer {

    private AgentColorReferenceNormalizer() {
    }

    static String canonicalize(String colorReference) {
        if (colorReference == null) return "";
        String clean = colorReference.trim();
        if (normalize(clean).equals("morocho")) return "NEGRO";
        return clean;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
