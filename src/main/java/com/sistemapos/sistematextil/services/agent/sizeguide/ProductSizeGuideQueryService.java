package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;

@Service
public class ProductSizeGuideQueryService {
    private final JdbcTemplate jdbc;
    private final JdbcProductSizeGuideStore store;
    private final AgentCatalogQueryService catalog;

    public ProductSizeGuideQueryService(JdbcTemplate jdbc, JdbcProductSizeGuideStore store,
            AgentCatalogQueryService catalog) {
        this.jdbc = jdbc;
        this.store = store;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public SizeGuideResult getGuide(Long sessionId, Integer productId) {
        catalog.getProduct(sessionId, productId);
        return getAdminGuide(productId);
    }

    @Transactional(readOnly = true)
    public SizeGuideResult getAdminGuide(Integer productId) {
        var status = requireStatus(productId);
        if (!"AVAILABLE".equals(status.analysisStatus())) {
            return new SizeGuideResult(productId, productName(productId), status.analysisStatus(),
                    status.measurementType(), null, status.analyzedAt(), status.confidence(), List.of());
        }
        List<MeasurementRow> rows = rows(productId);
        Map<Long, MutableComponent> components = new LinkedHashMap<>();
        for (MeasurementRow row : rows) {
            MutableComponent component = components.computeIfAbsent(row.componentId(),
                    ignored -> new MutableComponent(row.componentName(), row.componentDescription(), new ArrayList<>()));
            component.measurements().add(new GuideMeasurement(row.sizeLabel(), row.measurementCode(),
                    row.measurementName(), row.referenceCm(), row.minimumCm(), row.maximumCm()));
        }
        return new SizeGuideResult(productId, productName(productId), status.analysisStatus(),
                status.measurementType(), guideDescription(productId), status.analyzedAt(), status.confidence(),
                components.values().stream().map(item -> new GuideComponent(item.name(), item.description(),
                        List.copyOf(item.measurements()))).toList());
    }

    @Transactional(readOnly = true)
    public SizeRecommendation recommend(Long sessionId, Integer productId, String requestedComponent,
            List<BodyMeasurement> measurements, String fitPreference, String requestedColor) {
        var detail = catalog.getProduct(sessionId, productId);
        var status = requireStatus(productId);
        if (!"AVAILABLE".equals(status.analysisStatus())) {
            return new SizeRecommendation(productId, detail.name(), status.analysisStatus(),
                    status.measurementType(), true, false, clean(fitPreference), List.of(), List.of(),
                    "", "La guia aun no tiene medidas estructuradas disponibles", List.of(), false);
        }
        Map<String, Integer> availableStock = new LinkedHashMap<>();
        String color = clean(requestedColor);
        catalog.getVariants(sessionId, productId, color.isBlank() ? null : color, null).variants().forEach(variant -> {
            if (variant.available() && variant.size() != null) {
                availableStock.merge(normalizeSize(variant.size()), variant.stock(), Integer::sum);
            }
        });
        return calculate(productId, detail.name(), status.measurementType(), requestedComponent,
                measurements, fitPreference, rows(productId), availableStock);
    }

    public SizeRecommendation recommend(Long sessionId, Integer productId, String requestedComponent,
            List<BodyMeasurement> measurements, String fitPreference) {
        return recommend(sessionId, productId, requestedComponent, measurements, fitPreference, null);
    }

    SizeRecommendation calculate(Integer productId, String productName, String measurementType,
            String requestedComponent, List<BodyMeasurement> measurements, String fitPreference,
            List<MeasurementRow> guideRows, Map<String, Integer> availableStock) {
        List<NormalizedMeasurement> requested;
        try {
            requested = normalizeMeasurements(measurements);
        } catch (IllegalArgumentException error) {
            return new SizeRecommendation(productId, productName, "AVAILABLE",
                    measurementType, true, false, clean(fitPreference), List.of(), List.of(),
                    "", error.getMessage(), List.of(), false);
        }
        if (requested.isEmpty()) {
            return new SizeRecommendation(productId, productName, "AVAILABLE",
                    measurementType, true, false, clean(fitPreference), List.of(), List.of(),
                    "", "Falta una medida corporal para orientar la talla", List.of(), false);
        }
        List<MeasurementRow> allRows = guideRows == null ? List.of() : guideRows;
        String componentFilter = normalize(requestedComponent);
        if (!componentFilter.isBlank()) {
            List<MeasurementRow> componentRows = allRows.stream()
                    .filter(row -> matches(row.componentName(), componentFilter)).toList();
            // Algunos modelos repiten el nombre del producto en el campo de pieza.
            // Eso significa "todo el conjunto", no un componente inexistente.
            if (!componentRows.isEmpty()) {
                allRows = componentRows;
            } else if (!matches(productName, componentFilter)) {
                return new SizeRecommendation(productId, productName, "AVAILABLE",
                        measurementType, true, false, clean(fitPreference), List.of(), List.of(),
                        "", "El componente indicado no aparece en la guia del producto", List.of(), false);
            }
        }
        Map<Long, List<MeasurementRow>> byComponent = allRows.stream()
                .collect(java.util.stream.Collectors.groupingBy(MeasurementRow::componentId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        List<SizeCandidateAssessment> candidates = candidateAssessments(
                allRows, requested, clean(fitPreference));
        if (byComponent.size() > 1) {
            List<ComponentRecommendation> setRecommendation = recommendWholeSet(
                    byComponent, requested, clean(fitPreference), availableStock);
            if (!setRecommendation.isEmpty()) {
                return new SizeRecommendation(productId, productName, "AVAILABLE",
                        measurementType, true, false, clean(fitPreference),
                        List.copyOf(setRecommendation), List.of(), "", "", candidates,
                        isBetweenSizes(setRecommendation.getFirst().recommendedSize(), candidates));
            }
        }
        List<ComponentRecommendation> recommendations = new ArrayList<>();
        Set<String> recommendedSizes = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();
        for (List<MeasurementRow> componentRows : byComponent.values()) {
            ComponentRecommendation recommendation = recommendComponent(componentRows, requested,
                    clean(fitPreference), availableStock);
            if (recommendation != null) {
                recommendations.add(recommendation);
                recommendedSizes.add(normalizeSize(recommendation.recommendedSize()));
                missing.addAll(recommendation.missingMeasurements());
            }
        }
        if (recommendations.isEmpty()) {
            return new SizeRecommendation(productId, productName, "AVAILABLE",
                    measurementType, true, false, clean(fitPreference), List.of(),
                    requested.stream().map(NormalizedMeasurement::code).toList(),
                    nextUsefulMeasure(allRows, requested),
                    "Las medidas indicadas no aparecen en la guia de este producto", List.of(), false);
        }
        boolean conflict = recommendedSizes.size() > 1;
        String next = conflict ? nextUsefulMeasure(allRows, requested) : "";
        return new SizeRecommendation(productId, productName, "AVAILABLE",
                measurementType, "INDETERMINADO".equals(measurementType), conflict,
                clean(fitPreference), List.copyOf(recommendations), List.copyOf(missing), next, "",
                candidates, isBetweenSizes(recommendations.getFirst().recommendedSize(), candidates));
    }

    private List<SizeCandidateAssessment> candidateAssessments(List<MeasurementRow> rows,
            List<NormalizedMeasurement> requested, String fitPreference) {
        Set<String> sizes = rows.stream().map(MeasurementRow::sizeLabel).map(this::normalizeSize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SizeCandidateAssessment> result = new ArrayList<>();
        for (String size : sizes) {
            List<MeasurementMatch> matches = matchesForSize(rows, size, requested);
            boolean coversEveryInput = requested.stream().allMatch(input -> rows.stream()
                    .anyMatch(row -> normalizeSize(row.sizeLabel()).equals(size)
                            && measureMatches(row.measurementCode(), input.code())));
            if (!coversEveryInput || matches.isEmpty()) continue;
            SizeScore score = score(size, matches, fitPreference);
            result.add(new SizeCandidateAssessment(size, score.undersized(), score.rawDifference(),
                    score.matches()));
        }
        result.sort(Comparator.comparingInt(item -> sizeOrder(item.size())));
        return List.copyOf(result);
    }

    private boolean isBetweenSizes(String selectedSize, List<SizeCandidateAssessment> candidates) {
        if (candidates == null || candidates.size() < 2) return false;
        SizeCandidateAssessment closest = candidates.stream()
                .min(Comparator.comparing(SizeCandidateAssessment::totalDifference)
                        .thenComparing(Comparator.comparingInt(
                                (SizeCandidateAssessment item) -> sizeOrder(item.size())).reversed()))
                .orElse(null);
        return closest != null && closest.undersized()
                && !normalizeSize(closest.size()).equals(normalizeSize(selectedSize));
    }

    /**
     * Los conjuntos se comercializan en una sola talla. Se puntua cada talla
     * comun usando conjuntamente las medidas de todas sus piezas; nunca se
     * devuelve una talla distinta para blazer/chaleco y pantalon/falda.
     */
    private List<ComponentRecommendation> recommendWholeSet(
            Map<Long, List<MeasurementRow>> byComponent,
            List<NormalizedMeasurement> requested, String fitPreference,
            Map<String, Integer> availableStock) {
        Set<String> commonSizes = null;
        for (List<MeasurementRow> componentRows : byComponent.values()) {
            Set<String> componentSizes = componentRows.stream()
                    .map(MeasurementRow::sizeLabel).map(this::normalizeSize)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (commonSizes == null) commonSizes = new LinkedHashSet<>(componentSizes);
            else commonSizes.retainAll(componentSizes);
        }
        if (commonSizes == null || commonSizes.isEmpty()) return List.of();

        List<MeasurementRow> allRows = byComponent.values().stream().flatMap(List::stream).toList();
        List<SizeScore> scores = new ArrayList<>();
        for (String normalizedSize : commonSizes) {
            List<MeasurementMatch> matches = matchesForSize(allRows, normalizedSize, requested);
            boolean coversEveryInput = requested.stream().allMatch(input -> allRows.stream()
                    .anyMatch(row -> normalizeSize(row.sizeLabel()).equals(normalizedSize)
                            && measureMatches(row.measurementCode(), input.code())));
            if (coversEveryInput && !matches.isEmpty()) {
                String label = allRows.stream()
                        .filter(row -> normalizeSize(row.sizeLabel()).equals(normalizedSize))
                        .map(MeasurementRow::sizeLabel).findFirst().orElse(normalizedSize);
                scores.add(score(label, matches, fitPreference));
            }
        }
        scores.sort(Comparator.comparing(SizeScore::score)
                .thenComparing(Comparator.comparingInt(
                        (SizeScore item) -> sizeOrder(item.size())).reversed()));
        if (scores.isEmpty()) return List.of();

        SizeScore selected = scores.getFirst();
        String selectedSize = normalizeSize(selected.size());
        List<String> availableCommonSizes = scores.stream().map(SizeScore::size)
                .filter(size -> availableStock.getOrDefault(normalizeSize(size), 0) > 0).toList();
        List<ComponentRecommendation> result = new ArrayList<>();
        for (List<MeasurementRow> componentRows : byComponent.values()) {
            List<MeasurementMatch> evidence = matchesForSize(componentRows, selectedSize, requested);
            List<String> missing = requested.stream()
                    .filter(input -> componentRows.stream().noneMatch(row ->
                            normalizeSize(row.sizeLabel()).equals(selectedSize)
                                    && measureMatches(row.measurementCode(), input.code())))
                    .map(NormalizedMeasurement::code).toList();
            int stock = availableStock.getOrDefault(selectedSize, 0);
            result.add(new ComponentRecommendation(componentRows.getFirst().componentName(),
                    selected.size(), stock > 0, stock, evidence, missing, availableCommonSizes));
        }
        return result;
    }

    private List<MeasurementMatch> matchesForSize(List<MeasurementRow> rows, String normalizedSize,
            List<NormalizedMeasurement> requested) {
        List<MeasurementMatch> matches = new ArrayList<>();
        for (NormalizedMeasurement input : requested) {
            for (MeasurementRow row : rows) {
                if (!normalizeSize(row.sizeLabel()).equals(normalizedSize)
                        || !measureMatches(row.measurementCode(), input.code())) continue;
                BigDecimal guideValue = comparableValue(row);
                if (guideValue != null) {
                    matches.add(new MeasurementMatch(row.measurementName(), input.valueCm(), guideValue,
                            guideValue.subtract(input.valueCm()).abs()));
                }
            }
        }
        return List.copyOf(matches);
    }

    private ComponentRecommendation recommendComponent(List<MeasurementRow> rows,
            List<NormalizedMeasurement> requested, String fitPreference, Map<String, Integer> availableStock) {
        Map<String, List<MeasurementMatch>> bySize = new LinkedHashMap<>();
        Set<String> matchedInputs = new LinkedHashSet<>();
        for (NormalizedMeasurement input : requested) {
            List<MeasurementRow> compatible = rows.stream()
                    .filter(row -> measureMatches(row.measurementCode(), input.code())).toList();
            if (compatible.isEmpty()) continue;
            matchedInputs.add(input.code());
            for (MeasurementRow row : compatible) {
                BigDecimal guideValue = comparableValue(row);
                if (guideValue == null) continue;
                bySize.computeIfAbsent(row.sizeLabel(), ignored -> new ArrayList<>())
                        .add(new MeasurementMatch(row.measurementName(), input.valueCm(), guideValue,
                                guideValue.subtract(input.valueCm()).abs()));
            }
        }
        if (bySize.isEmpty()) return null;
        int requiredMatches = matchedInputs.size();
        List<SizeScore> scores = bySize.entrySet().stream()
                .filter(entry -> entry.getValue().size() == requiredMatches)
                .map(entry -> score(entry.getKey(), entry.getValue(), fitPreference))
                .sorted(Comparator.comparing(SizeScore::score)
                        .thenComparing(Comparator.comparingInt(
                                (SizeScore item) -> sizeOrder(item.size())).reversed()))
                .toList();
        if (scores.isEmpty()) return null;
        SizeScore selected = scores.getFirst();
        List<String> missing = requested.stream().map(NormalizedMeasurement::code)
                .filter(code -> !matchedInputs.contains(code)).toList();
        return new ComponentRecommendation(rows.getFirst().componentName(), selected.size(),
                availableStock.getOrDefault(normalizeSize(selected.size()), 0) > 0,
                availableStock.getOrDefault(normalizeSize(selected.size()), 0), selected.matches(), missing,
                scores.stream().map(SizeScore::size)
                        .filter(size -> availableStock.getOrDefault(normalizeSize(size), 0) > 0).toList());
    }

    private SizeScore score(String size, List<MeasurementMatch> matches, String fitPreference) {
        BigDecimal rawDifference = matches.stream().map(MeasurementMatch::difference)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean preferNotSmaller = !"AJUSTADO".equals(normalize(fitPreference));
        boolean undersized = matches.stream()
                .anyMatch(item -> item.guideValue().compareTo(item.customerValue()) < 0);
        BigDecimal score = rawDifference;
        if (preferNotSmaller && undersized) {
            score = score.add(new BigDecimal("1000"));
        }
        return new SizeScore(size, score, rawDifference, undersized, List.copyOf(matches));
    }

    private List<NormalizedMeasurement> normalizeMeasurements(List<BodyMeasurement> values) {
        if (values == null) return List.of();
        List<NormalizedMeasurement> result = new ArrayList<>();
        for (BodyMeasurement value : values) {
            if (value == null || value.value() == null) continue;
            String code = ProductSizeGuideValidator.normalizedCode(value.name());
            BigDecimal centimeters = "IN".equalsIgnoreCase(clean(value.unit()))
                    ? value.value().multiply(new BigDecimal("2.54")) : value.value();
            centimeters = centimeters.setScale(2, RoundingMode.HALF_UP);
            if (centimeters.compareTo(new BigDecimal("20")) < 0
                    || centimeters.compareTo(new BigDecimal("250")) > 0) {
                throw new IllegalArgumentException("La medida " + value.name() + " esta fuera de un rango valido");
            }
            result.add(new NormalizedMeasurement(code, centimeters));
        }
        return result;
    }

    private List<MeasurementRow> rows(Integer productId) {
        return jdbc.query("""
                SELECT c.id_componente,c.nombre,c.descripcion,m.talla_etiqueta,t.codigo,t.nombre,
                       m.valor_referencia_cm,m.valor_min_cm,m.valor_max_cm
                FROM producto_guia_talla g
                JOIN producto_guia_talla_componente c ON c.id_guia=g.id_guia
                JOIN producto_guia_talla_medida m ON m.id_componente=c.id_componente
                JOIN guia_talla_tipo_medida t ON t.id_tipo_medida=m.id_tipo_medida
                WHERE g.producto_id=? AND g.estado='ACTIVE'
                ORDER BY c.orden,m.talla_etiqueta,t.codigo
                """, (rs, row) -> new MeasurementRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getBigDecimal(7),
                        rs.getBigDecimal(8), rs.getBigDecimal(9)), productId);
    }

    private JdbcProductSizeGuideStore.GuideStatus requireStatus(Integer productId) {
        var status = store.status(productId);
        if (status == null) throw new IllegalArgumentException("El producto no existe");
        return status;
    }

    private String productName(Integer productId) {
        return jdbc.queryForObject("SELECT nombre FROM producto WHERE producto_id=?", String.class, productId);
    }

    private String guideDescription(Integer productId) {
        List<String> values = jdbc.query("""
                SELECT descripcion FROM producto_guia_talla
                WHERE producto_id=? AND estado='ACTIVE' LIMIT 1
                """, (rs, row) -> rs.getString(1), productId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private BigDecimal comparableValue(MeasurementRow row) {
        if (row.referenceCm() != null) return row.referenceCm();
        if (row.minimumCm() != null && row.maximumCm() != null) {
            return row.minimumCm().add(row.maximumCm()).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        }
        return row.maximumCm() != null ? row.maximumCm() : row.minimumCm();
    }

    private String nextUsefulMeasure(List<MeasurementRow> rows, List<NormalizedMeasurement> provided) {
        Set<String> present = provided.stream().map(NormalizedMeasurement::code).collect(java.util.stream.Collectors.toSet());
        for (String code : List.of("CONTORNO_DE_BUSTO", "BUSTO", "CONTORNO_DE_CADERA", "CADERA", "CONTORNO_DE_CINTURA", "CINTURA")) {
            if (!present.contains(code) && rows.stream().anyMatch(row -> measureMatches(row.measurementCode(), code))) {
                return rows.stream().filter(row -> measureMatches(row.measurementCode(), code))
                        .map(MeasurementRow::measurementName).findFirst().orElse("");
            }
        }
        return "";
    }

    private boolean measureMatches(String guide, String requested) {
        return guide.equals(requested) || guide.endsWith("_" + requested) || requested.endsWith("_" + guide);
    }

    private boolean matches(String value, String normalizedFilter) {
        String candidate = normalize(value);
        return candidate.contains(normalizedFilter) || normalizedFilter.contains(candidate);
    }

    private int sizeOrder(String value) {
        return switch (normalizeSize(value)) {
            case "XXS" -> 0; case "XS" -> 1; case "S" -> 2; case "M" -> 3;
            case "L" -> 4; case "XL" -> 5; case "XXL" -> 6; case "XXXL" -> 7;
            default -> 100;
        };
    }

    private String normalizeSize(String value) { return clean(value).toUpperCase(Locale.ROOT); }
    private String normalize(String value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }

    private record MutableComponent(String name, String description, List<GuideMeasurement> measurements) {}
    record MeasurementRow(Long componentId, String componentName, String componentDescription,
            String sizeLabel, String measurementCode, String measurementName, BigDecimal referenceCm,
            BigDecimal minimumCm, BigDecimal maximumCm) {}
    private record NormalizedMeasurement(String code, BigDecimal valueCm) {}
    private record SizeScore(String size, BigDecimal score, BigDecimal rawDifference,
            boolean undersized, List<MeasurementMatch> matches) {}

    public record BodyMeasurement(String name, BigDecimal value, String unit) {}
    public record GuideMeasurement(String size, String measurementCode, String measurementName,
            BigDecimal referenceCm, BigDecimal minimumCm, BigDecimal maximumCm) {}
    public record GuideComponent(String name, String description, List<GuideMeasurement> measurements) {}
    public record SizeGuideResult(Integer productId, String productName, String status, String measurementType,
            String description, LocalDateTime analyzedAt, Integer confidence, List<GuideComponent> components) {}
    public record MeasurementMatch(String measurement, BigDecimal customerValue, BigDecimal guideValue,
            BigDecimal difference) {}
    public record ComponentRecommendation(String component, String recommendedSize, boolean available,
            int availableStock, List<MeasurementMatch> evidence, List<String> missingMeasurements,
            List<String> availableGuideSizes) {}
    public record SizeCandidateAssessment(String size, boolean undersized, BigDecimal totalDifference,
            List<MeasurementMatch> evidence) {}
    public record SizeRecommendation(Integer productId, String productName, String status, String measurementType,
            boolean orientationOnly, boolean componentConflict, String fitPreference,
            List<ComponentRecommendation> components, List<String> missingMeasurements, String nextUsefulMeasurement,
            String message, List<SizeCandidateAssessment> candidates, boolean betweenSizes) {}
}
