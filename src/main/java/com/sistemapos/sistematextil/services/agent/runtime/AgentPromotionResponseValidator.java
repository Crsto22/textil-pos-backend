package com.sistemapos.sistematextil.services.agent.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Comprueba que una respuesta sobre promociones use al menos una promocion real
 * obtenida por Java. No interpreta el mensaje del cliente ni redacta plantillas.
 */
final class AgentPromotionResponseValidator {
    private static final Pattern CURRENCY_BEFORE = Pattern.compile(
            "(?iu)(?:S\\s*/\\s*\\.?|PEN\\s*)\\s*(\\d+(?:[.,]\\d{1,2})?)");
    private static final Pattern CURRENCY_AFTER = Pattern.compile(
            "(?iu)(\\d+(?:[.,]\\d{1,2})?)\\s*(?:PEN|sol(?:es)?)\\b");

    private final JsonMapper json = JsonMapper.builder().build();

    Validation validate(String evidenceJson, String customerMessage) {
        if (evidenceJson == null || evidenceJson.isBlank()) return Validation.success();
        try {
            JsonNode promotionsEvidence = json.readTree(evidenceJson).path("promotions");
            if (!promotionsEvidence.isObject()) return Validation.success();
            JsonNode promotions = promotionsEvidence.path("promotions");
            if (!promotions.isArray() || promotions.isEmpty()) return Validation.success();

            String normalizedMessage = normalize(customerMessage);
            List<BigDecimal> statedPrices = extractPrices(customerMessage);
            List<PromotionFact> facts = new ArrayList<>();
            promotions.forEach(node -> {
                String name = node.path("name").asText("").trim();
                JsonNode price = node.get("comboPrice");
                if (!name.isBlank() && price != null && price.isNumber()) {
                    facts.add(new PromotionFact(name, normalize(name),
                            price.decimalValue().setScale(2, RoundingMode.HALF_UP)));
                }
            });
            if (facts.isEmpty()) return Validation.success();

            boolean grounded = facts.stream().anyMatch(fact ->
                    containsPhrase(normalizedMessage, fact.normalizedName())
                            && statedPrices.stream().anyMatch(fact.price()::equals));
            if (grounded) return Validation.success();

            String options = facts.stream().limit(10)
                    .map(fact -> fact.name() + " a S/" + fact.price())
                    .reduce((left, right) -> left + "; " + right).orElse("");
            return new Validation(false, "La base de datos devolvio promociones vigentes. "
                    + "No puedes afirmar que no existen ni sustituirlas por productos. "
                    + "Menciona promociones verificadas con su precio, por ejemplo: " + options + ".");
        } catch (Exception malformedEvidence) {
            throw new IllegalStateException("No se pudo validar la evidencia de promociones", malformedEvidence);
        }
    }

    private List<BigDecimal> extractPrices(String message) {
        if (message == null || message.isBlank()) return List.of();
        List<BigDecimal> result = new ArrayList<>();
        addMatches(result, CURRENCY_BEFORE.matcher(message));
        addMatches(result, CURRENCY_AFTER.matcher(message));
        return result;
    }

    private void addMatches(List<BigDecimal> target, Matcher matcher) {
        while (matcher.find()) {
            try {
                target.add(new BigDecimal(matcher.group(1).replace(',', '.'))
                        .setScale(2, RoundingMode.HALF_UP));
            } catch (NumberFormatException ignored) {
                // La segunda redaccion corregira un importe ausente o invalido.
            }
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private boolean containsPhrase(String text, String phrase) {
        return !phrase.isBlank() && (" " + text + " ").contains(" " + phrase + " ");
    }

    record Validation(boolean valid, String correctionInstruction) {
        static Validation success() {
            return new Validation(true, "");
        }
    }

    private record PromotionFact(String name, String normalizedName, BigDecimal price) {
    }
}
