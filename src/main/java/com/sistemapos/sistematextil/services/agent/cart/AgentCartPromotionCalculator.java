package com.sistemapos.sistematextil.services.agent.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.model.EcommercePromocionCombo;

@Component
public class AgentCartPromotionCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int MAX_EXACT_STATES = 200_000;

    public Calculation calculate(
            List<PricedLine> lines,
            List<EcommercePromocionCombo> activePromotions,
            Map<Integer, Integer> branchStockByProduct) {
        BigDecimal subtotal = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        List<Unit> units = expand(lines);
        List<Candidate> selected = selectBest(units, activePromotions, branchStockByProduct);
        BigDecimal discount = selected.stream().map(Candidate::discount).reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        List<AppliedPromotion> applications = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            applications.add(new AppliedPromotion(candidate.promotion().getIdEcommercePromocionCombo(),
                    candidate.promotion().getNombre(), index + 1, candidate.promotion().getPrecioCombo(),
                    candidate.regularPrice(), candidate.discount(),
                    List.of(candidate.first().index(), candidate.second().index())));
        }
        return new Calculation(subtotal, discount, subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP),
                List.copyOf(applications));
    }

    private List<Unit> expand(List<PricedLine> lines) {
        List<Unit> units = new ArrayList<>();
        for (PricedLine line : lines) {
            for (int count = 0; count < line.quantity(); count++) {
                units.add(new Unit(units.size(), line.productId(), line.variantId(), line.unitPrice()));
            }
        }
        return units;
    }

    private List<PairPromotion> promotionPairs(List<EcommercePromocionCombo> promotions,
            Map<Integer, Integer> branchStockByProduct) {
        Map<ProductPair, PairPromotion> bestByPair = new HashMap<>();
        for (EcommercePromocionCombo promotion : promotions) {
            Map<Integer, Integer> required = requiredProducts(promotion);
            if (required.values().stream().mapToInt(Integer::intValue).sum() != 2) continue;
            boolean branchAvailable = required.entrySet().stream()
                    .allMatch(entry -> branchStockByProduct.getOrDefault(entry.getKey(), 0) >= entry.getValue());
            if (!branchAvailable || promotion.getPrecioCombo() == null) continue;
            List<Integer> products = required.entrySet().stream()
                    .flatMap(entry -> java.util.stream.IntStream.range(0, entry.getValue())
                            .mapToObj(ignored -> entry.getKey()))
                    .sorted()
                    .toList();
            if (products.size() != 2) continue;
            ProductPair pair = new ProductPair(products.get(0), products.get(1));
            PairPromotion candidate = new PairPromotion(pair, promotion);
            bestByPair.merge(pair, candidate, (current, replacement) ->
                    comparePromotions(replacement.promotion(), current.promotion()) < 0 ? replacement : current);
        }
        return bestByPair.values().stream()
                .sorted(Comparator.comparing((PairPromotion pair) -> pair.pair().firstProductId())
                        .thenComparing(pair -> pair.pair().secondProductId())
                        .thenComparing(pair -> pair.promotion().getIdEcommercePromocionCombo()))
                .toList();
    }

    private int comparePromotions(EcommercePromocionCombo first, EcommercePromocionCombo second) {
        int price = first.getPrecioCombo().compareTo(second.getPrecioCombo());
        if (price != 0) return price;
        return Comparator.nullsLast(Integer::compareTo)
                .compare(first.getIdEcommercePromocionCombo(), second.getIdEcommercePromocionCombo());
    }

    private Map<Integer, Integer> requiredProducts(EcommercePromocionCombo promotion) {
        if (promotion == null || promotion.getItems() == null) return Map.of();
        Map<Integer, Integer> required = new HashMap<>();
        promotion.getItems().forEach(item -> {
            if (item.getProducto() != null && item.getProducto().getIdProducto() != null
                    && item.getCantidadRequerida() != null && item.getCantidadRequerida() > 0) {
                required.merge(item.getProducto().getIdProducto(), item.getCantidadRequerida(), Integer::sum);
            }
        });
        return required;
    }

    private List<Candidate> selectBest(List<Unit> units, List<EcommercePromocionCombo> promotions,
            Map<Integer, Integer> branchStockByProduct) {
        List<PairPromotion> pairs = promotionPairs(promotions, branchStockByProduct);
        if (units.size() < 2 || pairs.isEmpty()) return List.of();

        Map<Integer, List<Unit>> grouped = units.stream().collect(java.util.stream.Collectors.groupingBy(
                Unit::productId, LinkedHashMap::new, java.util.stream.Collectors.toCollection(ArrayList::new)));
        grouped.values().forEach(group -> group.sort(Comparator.comparing(Unit::price).reversed()
                .thenComparing(Unit::index)));
        List<Integer> productIds = grouped.keySet().stream().sorted().toList();
        Map<Integer, Integer> productPositions = new HashMap<>();
        for (int index = 0; index < productIds.size(); index++) productPositions.put(productIds.get(index), index);
        List<List<Unit>> groups = productIds.stream().map(grouped::get).toList();
        Map<Integer, List<PairPromotion>> pairsByProduct = new HashMap<>();
        for (PairPromotion pair : pairs) {
            pairsByProduct.computeIfAbsent(pair.pair().firstProductId(), ignored -> new ArrayList<>()).add(pair);
            if (!pair.pair().firstProductId().equals(pair.pair().secondProductId())) {
                pairsByProduct.computeIfAbsent(pair.pair().secondProductId(), ignored -> new ArrayList<>()).add(pair);
            }
        }

        SearchBudget budget = new SearchBudget(MAX_EXACT_STATES);
        try {
            return solve(groups, productIds, productPositions, pairsByProduct, new int[groups.size()],
                    new HashMap<>(), budget).candidates();
        } catch (SearchLimitReached ignored) {
            return selectGreedy(units, pairs);
        }
    }

    private Selection solve(List<List<Unit>> groups, List<Integer> productIds,
            Map<Integer, Integer> productPositions, Map<Integer, List<PairPromotion>> pairsByProduct,
            int[] positions, Map<PositionKey, Selection> memo, SearchBudget budget) {
        PositionKey key = new PositionKey(positions);
        Selection cached = memo.get(key);
        if (cached != null) return cached;
        budget.visit();
        int firstGroup = -1;
        for (int index = 0; index < groups.size(); index++) {
            if (positions[index] < groups.get(index).size()) {
                firstGroup = index;
                break;
            }
        }
        if (firstGroup < 0) return Selection.EMPTY;

        int[] skipped = positions.clone();
        skipped[firstGroup]++;
        Selection best = solve(groups, productIds, productPositions, pairsByProduct, skipped, memo, budget);
        Integer productId = productIds.get(firstGroup);
        Unit first = groups.get(firstGroup).get(positions[firstGroup]);
        for (PairPromotion pair : pairsByProduct.getOrDefault(productId, List.of())) {
            Integer otherProduct = pair.pair().other(productId);
            Integer secondGroup = productPositions.get(otherProduct);
            if (secondGroup == null) continue;
            int[] consumed = positions.clone();
            Unit second;
            if (firstGroup == secondGroup) {
                if (positions[firstGroup] + 1 >= groups.get(firstGroup).size()) continue;
                second = groups.get(firstGroup).get(positions[firstGroup] + 1);
                consumed[firstGroup] += 2;
            } else {
                if (positions[secondGroup] >= groups.get(secondGroup).size()) continue;
                second = groups.get(secondGroup).get(positions[secondGroup]);
                consumed[firstGroup]++;
                consumed[secondGroup]++;
            }
            Candidate candidate = candidate(pair.promotion(), first, second);
            if (candidate == null) continue;
            Selection tail = solve(groups, productIds, productPositions, pairsByProduct, consumed, memo, budget);
            Selection selected = tail.prepend(candidate);
            if (selected.betterThan(best)) best = selected;
        }
        memo.put(key, best);
        return best;
    }

    private List<Candidate> selectGreedy(List<Unit> units, List<PairPromotion> pairs) {
        List<Candidate> candidates = new ArrayList<>();
        for (PairPromotion pair : pairs) {
            for (int first = 0; first < units.size(); first++) {
                for (int second = first + 1; second < units.size(); second++) {
                    Unit a = units.get(first);
                    Unit b = units.get(second);
                    if (!pair.pair().matches(a.productId(), b.productId())) continue;
                    Candidate candidate = candidate(pair.promotion(), a, b);
                    if (candidate != null) candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator.comparing(Candidate::discount).reversed()
                .thenComparing(candidate -> candidate.promotion().getIdEcommercePromocionCombo())
                .thenComparing(candidate -> candidate.first().index())
                .thenComparing(candidate -> candidate.second().index()));
        boolean[] used = new boolean[units.size()];
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (used[candidate.first().index()] || used[candidate.second().index()]) continue;
            used[candidate.first().index()] = true;
            used[candidate.second().index()] = true;
            selected.add(candidate);
        }
        return selected;
    }

    private Candidate candidate(EcommercePromocionCombo promotion, Unit first, Unit second) {
        BigDecimal regular = first.price().add(second.price()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = regular.subtract(promotion.getPrecioCombo()).setScale(2, RoundingMode.HALF_UP);
        return discount.compareTo(BigDecimal.ZERO) > 0
                ? new Candidate(promotion, first, second, regular, discount) : null;
    }

    public record PricedLine(Integer variantId, Integer productId, int quantity, BigDecimal unitPrice) {
    }

    public record AppliedPromotion(Integer promotionId, String name, int applicationOrder, BigDecimal comboPrice,
            BigDecimal regularPrice, BigDecimal discount, List<Integer> unitIndexes) {
    }

    public record Calculation(BigDecimal subtotal, BigDecimal discount, BigDecimal total,
            List<AppliedPromotion> promotions) {
    }

    private record Unit(int index, Integer productId, Integer variantId, BigDecimal price) {
    }

    private record ProductPair(Integer firstProductId, Integer secondProductId) {
        Integer other(Integer productId) {
            return firstProductId.equals(productId) ? secondProductId : firstProductId;
        }

        boolean matches(Integer first, Integer second) {
            return (firstProductId.equals(first) && secondProductId.equals(second))
                    || (firstProductId.equals(second) && secondProductId.equals(first));
        }
    }

    private record PairPromotion(ProductPair pair, EcommercePromocionCombo promotion) {
    }

    private record PositionKey(List<Integer> positions) {
        PositionKey(int[] positions) {
            this(Arrays.stream(positions).boxed().toList());
        }
    }

    private record Selection(BigDecimal discount, List<Candidate> candidates) {
        private static final Selection EMPTY = new Selection(ZERO, List.of());

        Selection prepend(Candidate candidate) {
            List<Candidate> result = new ArrayList<>(candidates.size() + 1);
            result.add(candidate);
            result.addAll(candidates);
            return new Selection(discount.add(candidate.discount()), List.copyOf(result));
        }

        boolean betterThan(Selection other) {
            return discount.compareTo(other.discount) > 0;
        }
    }

    private static final class SearchBudget {
        private final int maximum;
        private int visited;

        private SearchBudget(int maximum) {
            this.maximum = maximum;
        }

        private void visit() {
            if (++visited > maximum) throw new SearchLimitReached();
        }
    }

    private static final class SearchLimitReached extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record Candidate(EcommercePromocionCombo promotion, Unit first, Unit second,
            BigDecimal regularPrice, BigDecimal discount) {
    }
}
