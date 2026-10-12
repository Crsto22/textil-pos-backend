package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.Extraction;

@Component
public class ProductSizeGuideValidator {
    private final int minimumConfidence;

    public ProductSizeGuideValidator(
            @Value("${crm.whatsapp.agent.size-guide.minimum-confidence:85}") int minimumConfidence) {
        this.minimumConfidence = Math.max(50, Math.min(100, minimumConfidence));
    }

    public ValidatedGuide validate(Extraction extraction) {
        if (extraction == null || !extraction.sizeGuide()) throw new IllegalArgumentException("La imagen no contiene una guia de tallas");
        if (extraction.confidence() < minimumConfidence) {
            throw new IllegalArgumentException("Confianza insuficiente al leer la guia: " + extraction.confidence());
        }
        String sourceUnit = normalizeUnit(extraction.unit());
        String basis = normalizeBasis(extraction.measurementType());
        if (extraction.components() == null || extraction.components().isEmpty()) {
            throw new IllegalArgumentException("No se detectaron componentes en la guia");
        }
        Set<String> unique = new HashSet<>();
        int measurements = 0;
        for (var component : extraction.components()) {
            if (clean(component.name()).isBlank()) throw new IllegalArgumentException("Se detecto un componente sin nombre");
            if (component.measurements() == null || component.measurements().isEmpty()) {
                throw new IllegalArgumentException("El componente " + component.name() + " no tiene medidas");
            }
            for (var row : component.measurements()) {
                String size = clean(row.sizeLabel()).toUpperCase(Locale.ROOT);
                String measure = normalizedCode(row.measurementName());
                if (size.isBlank() || measure.isBlank()) throw new IllegalArgumentException("Hay una medida sin talla o nombre");
                if (row.referenceValue() == null && row.minimumValue() == null && row.maximumValue() == null) {
                    throw new IllegalArgumentException("La medida " + row.measurementName() + " no tiene valor numerico");
                }
                validateNumber(row.referenceValue());
                validateNumber(row.minimumValue());
                validateNumber(row.maximumValue());
                validateNumber(row.toleranceValue());
                if (row.minimumValue() != null && row.maximumValue() != null
                        && row.minimumValue().compareTo(row.maximumValue()) > 0) {
                    throw new IllegalArgumentException("El rango de " + row.measurementName() + " es invalido");
                }
                String key = normalizedCode(component.name()) + "|" + size + "|" + measure;
                if (!unique.add(key)) throw new IllegalArgumentException("La guia contiene medidas duplicadas");
                measurements++;
            }
        }
        if (measurements < 2) throw new IllegalArgumentException("La guia no contiene suficientes medidas verificables");
        return new ValidatedGuide(basis, sourceUnit, extraction.description(), extraction.confidence(), extraction);
    }

    private void validateNumber(BigDecimal value) {
        if (value == null) return;
        BigDecimal centimeters = value;
        if (centimeters.compareTo(new BigDecimal("5")) < 0 || centimeters.compareTo(new BigDecimal("350")) > 0) {
            throw new IllegalArgumentException("La guia contiene una medida fuera de rango");
        }
    }

    private String normalizeUnit(String value) {
        String unit = clean(value).toUpperCase(Locale.ROOT);
        return "IN".equals(unit) ? "IN" : "CM";
    }

    private String normalizeBasis(String value) {
        String basis = clean(value).toUpperCase(Locale.ROOT);
        return Set.of("CUERPO", "PRENDA").contains(basis) ? basis : "INDETERMINADO";
    }

    public static String normalizedCode(String value) {
        String normalized = Normalizer.normalize(clean(value), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record ValidatedGuide(String measurementType, String sourceUnit, String description,
            int confidence, Extraction extraction) {}
}
