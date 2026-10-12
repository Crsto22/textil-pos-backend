package com.sistemapos.sistematextil.services.agent.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Verifica la redaccion libre de Gemini contra los precios de catalogo que Java
 * ya obtuvo de MySQL. No redacta respuestas ni clasifica intenciones.
 */
final class AgentProductPriceResponseValidator {
    private static final Pattern CURRENCY_BEFORE = Pattern.compile(
            "(?iu)(?:S\\s*/\\s*\\.?|PEN\\s*)\\s*(\\d+(?:[.,]\\d{1,2})?)");
    private static final Pattern CURRENCY_AFTER = Pattern.compile(
            "(?iu)(\\d+(?:[.,]\\d{1,2})?)\\s*(?:PEN|sol(?:es)?)\\b");

    private final JsonMapper json = JsonMapper.builder().build();

    Validation validate(String evidenceJson, String customerMessage) {
        if (evidenceJson == null || evidenceJson.isBlank()) return Validation.success();

        try {
            JsonNode evidence = json.readTree(evidenceJson);
            Map<String, ProductPrice> products = new LinkedHashMap<>();
            addProduct(products, evidence.path("selectedProduct"), true);
            addSearchProducts(products, evidence.path("productSearch"));
            addSearchProducts(products, evidence.path("availableProductsWhenNoMatch"));
            if (products.isEmpty()) return Validation.success();

            String normalizedMessage = normalize(customerMessage);
            List<BigDecimal> statedPrices = extractPrices(customerMessage);
            List<String> issues = new ArrayList<>();

            for (ProductPrice product : products.values()) {
                boolean mentioned = normalizedMessage.contains(product.normalizedName());
                if (product.required() && !mentioned) {
                    issues.add("Debes mencionar el producto " + product.name() + ".");
                    mentioned = true;
                }
                if (mentioned && statedPrices.stream().noneMatch(product.price()::equals)) {
                    issues.add("Debes mostrar el precio real de " + product.name()
                            + ": S/" + product.price().setScale(2, RoundingMode.HALF_UP) + ".");
                }
            }
            return issues.isEmpty() ? Validation.success() : new Validation(false, List.copyOf(issues));
        } catch (Exception malformedEvidence) {
            throw new IllegalStateException("No se pudo validar los precios de la evidencia comercial",
                    malformedEvidence);
        }
    }

    private void addSearchProducts(Map<String, ProductPrice> target, JsonNode search) {
        JsonNode products = search.path("products");
        if (!products.isArray()) return;
        products.forEach(product -> addProduct(target, product, false));
    }

    private void addProduct(Map<String, ProductPrice> target, JsonNode product, boolean required) {
        if (!product.isObject()) return;
        String name = product.path("name").asText("").trim();
        JsonNode priceNode = product.get("priceFrom");
        if (name.isBlank() || priceNode == null || !priceNode.isNumber()) return;
        BigDecimal price = priceNode.decimalValue().setScale(2, RoundingMode.HALF_UP);
        if (price.signum() < 0) return;
        String key = normalize(name);
        ProductPrice previous = target.get(key);
        target.put(key, new ProductPrice(name, key, price, required || previous != null && previous.required()));
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
                // La redaccion se corregira si no queda ningun monto valido para el producto.
            }
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    record Validation(boolean valid, List<String> issues) {
        static Validation success() {
            return new Validation(true, List.of());
        }

        String correctionInstruction() {
            return String.join("\n", issues.stream().map(issue -> "- " + issue).toList());
        }
    }

    private record ProductPrice(String name, String normalizedName, BigDecimal price, boolean required) {
    }
}
