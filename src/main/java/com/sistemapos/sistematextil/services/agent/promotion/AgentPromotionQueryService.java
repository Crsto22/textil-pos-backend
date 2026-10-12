package com.sistemapos.sistematextil.services.agent.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.EcommercePromocionComboItem;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.repositories.AgentCatalogReadRepository;
import com.sistemapos.sistematextil.repositories.EcommercePromocionComboRepository;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver.CatalogScope;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Cart;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.CartItem;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource;

@Service
public class AgentPromotionQueryService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Pattern EXPLICIT_COMBO = Pattern.compile("(?:combo|promocion|promo)\\s*(?:numero|nro)?\\s*#?\\s*(\\d+)");
    private static final Set<String> STOP_WORDS = Set.of(
            "promocion", "promociones", "promo", "promos", "combo", "combos", "oferta", "ofertas",
            "descuento", "descuentos", "quiero", "deseo", "busco", "ver", "muestrame", "mostrar",
            "tienes", "tiene", "hay", "llevo", "llevar", "compro", "comprar", "si", "por", "favor",
            "que", "cual", "cuales", "el", "la", "los", "las", "un", "una", "de", "del", "en",
            "para", "me", "tu", "su", "dos", "2", "mismo", "misma");

    private final AgentCatalogScopeResolver scopeResolver;
    private final AgentCatalogReadRepository catalogRepository;
    private final EcommercePromocionComboRepository promotionRepository;
    private final AgentContextDataSource contextDataSource;

    public AgentPromotionQueryService(
            AgentCatalogScopeResolver scopeResolver,
            AgentCatalogReadRepository catalogRepository,
            EcommercePromocionComboRepository promotionRepository,
            AgentContextDataSource contextDataSource) {
        this.scopeResolver = scopeResolver;
        this.catalogRepository = catalogRepository;
        this.promotionRepository = promotionRepository;
        this.contextDataSource = contextDataSource;
    }

    @Transactional(readOnly = true)
    public PromotionSearchResult searchPromotions(Long sessionId, String query, Integer requestedLimit) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        Map<Integer, ProductInventory> inventory = loadInventory(scope.branchId());
        String normalizedQuery = normalize(query);
        List<String> tokens = meaningfulTokens(normalizedQuery);
        Integer explicitNumber = explicitComboNumber(normalizedQuery);
        boolean asksTwoSameProduct = (containsWord(normalizedQuery, "dos") || containsWord(normalizedQuery, "2"))
                && !tokens.isEmpty();
        boolean browseAll = normalizedQuery.isBlank() || (tokens.isEmpty() && explicitNumber == null);

        List<ScoredPromotion> ranked = availablePromotions(inventory).stream()
                .map(promotion -> new ScoredPromotion(promotion,
                        score(promotion, normalizedQuery, tokens, explicitNumber, asksTwoSameProduct, browseAll)))
                .filter(candidate -> browseAll || candidate.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPromotion::score).reversed()
                        .thenComparing(candidate -> candidate.promotion().getPrecioCombo())
                        .thenComparing(candidate -> candidate.promotion().getIdEcommercePromocionCombo()))
                .toList();
        List<PromotionSummary> promotions = ranked.stream().limit(limit)
                .map(candidate -> toSummary(candidate.promotion(), inventory)).toList();
        return new PromotionSearchResult(query == null ? "" : query.trim(), scope.branchId(), promotions,
                ranked.size() > promotions.size());
    }

    @Transactional(readOnly = true)
    public PromotionDetail getPromotion(Long sessionId, Integer promotionId) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        Map<Integer, ProductInventory> inventory = loadInventory(scope.branchId());
        EcommercePromocionCombo promotion = requireAvailablePromotion(promotionId, inventory);
        PromotionSummary summary = toSummary(promotion, inventory);
        return new PromotionDetail(summary.promotionId(), summary.name(), summary.type(), summary.comboPrice(),
                summary.regularPriceFrom(), summary.savingsFrom(), summary.startsAt(), summary.endsAt(),
                summary.untilStockRunsOut(), summary.items(), scope.branchId(), true);
    }

    @Transactional(readOnly = true)
    public CartPromotionValidation validateCart(Long sessionId, Integer requestedPromotionId) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        Cart cart = contextDataSource.findActiveCart(sessionId).orElse(null);
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            return new CartPromotionValidation("EMPTY_CART", scope.branchId(), null, ZERO, List.of(),
                    List.of(), "El carrito activo no tiene productos");
        }

        List<Integer> variantIds = cart.items().stream().map(CartItem::variantId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Integer, SucursalStock> variantStock = variantIds.isEmpty() ? Map.of()
                : catalogRepository.findVariantsByBranch(scope.branchId(), variantIds).stream()
                        .collect(Collectors.toMap(row -> row.getProductoVariante().getIdProductoVariante(),
                                row -> row, (first, ignored) -> first));
        CartSnapshot snapshot = cartSnapshot(cart, variantStock);
        if (!snapshot.unknownVariants().isEmpty()) {
            return new CartPromotionValidation("INVALID_CART", scope.branchId(), cart.id(), cart.total(), List.of(),
                    List.of(), "El carrito contiene variantes que no pertenecen a la sucursal de la conversacion");
        }
        if (!snapshot.stockSufficient()) {
            return new CartPromotionValidation("STOCK_INSUFFICIENT", scope.branchId(), cart.id(), cart.total(),
                    List.of(), List.of(), "El stock actual no alcanza para una o mas variantes del carrito");
        }

        Map<Integer, ProductInventory> inventory = loadInventory(scope.branchId());
        List<EcommercePromocionCombo> candidates;
        if (requestedPromotionId != null) {
            EcommercePromocionCombo requested = findActivePromotion(requestedPromotionId);
            if (requested == null || !isAvailable(requested, inventory)) {
                return new CartPromotionValidation("PROMOTION_NOT_AVAILABLE", scope.branchId(), cart.id(),
                        cart.total(), List.of(), List.of(), "La promocion solicitada no esta vigente o disponible");
            }
            candidates = List.of(requested);
        } else {
            candidates = availablePromotions(inventory);
        }

        List<PromotionApplication> eligible = new ArrayList<>();
        List<PromotionMissingItems> incomplete = new ArrayList<>();
        for (EcommercePromocionCombo promotion : candidates) {
            Map<Integer, Integer> required = requiredProducts(promotion);
            int applications = maximumApplications(snapshot.productQuantities(), required);
            if (applications > 0) {
                BigDecimal regular = regularPriceForOneApplication(snapshot, required);
                BigDecimal savings = regular.subtract(promotion.getPrecioCombo()).setScale(2, RoundingMode.HALF_UP);
                if (savings.compareTo(BigDecimal.ZERO) > 0) {
                    eligible.add(new PromotionApplication(promotion.getIdEcommercePromocionCombo(), promotion.getNombre(),
                            applications, promotion.getPrecioCombo(), regular, savings));
                }
            } else if (requestedPromotionId != null) {
                incomplete.add(new PromotionMissingItems(promotion.getIdEcommercePromocionCombo(),
                        promotion.getNombre(), missingItems(required, snapshot.productQuantities(), inventory)));
            }
        }
        String status = !eligible.isEmpty() ? "ELIGIBLE" : "INCOMPLETE";
        String message = !eligible.isEmpty()
                ? "El carrito cumple las condiciones de la promocion"
                : "El carrito aun no cumple las cantidades requeridas";
        return new CartPromotionValidation(status, scope.branchId(), cart.id(), cart.total(), eligible, incomplete,
                message);
    }

    private Map<Integer, ProductInventory> loadInventory(Integer branchId) {
        Map<Integer, List<SucursalStock>> grouped = catalogRepository.findCatalogByBranch(branchId).stream()
                .collect(Collectors.groupingBy(row -> row.getProductoVariante().getProducto().getIdProducto(),
                        LinkedHashMap::new, Collectors.toList()));
        Map<Integer, ProductInventory> result = new LinkedHashMap<>();
        grouped.forEach((productId, rows) -> {
            Producto product = rows.getFirst().getProductoVariante().getProducto();
            int stock = rows.stream().mapToInt(this::safeStock).sum();
            BigDecimal minimumPrice = rows.stream().filter(row -> safeStock(row) > 0)
                    .map(SucursalStock::getProductoVariante)
                    .map(ProductoVariante::getPrecio).filter(Objects::nonNull).map(BigDecimal::valueOf)
                    .min(BigDecimal::compareTo).orElse(ZERO).setScale(2, RoundingMode.HALF_UP);
            result.put(productId, new ProductInventory(productId, product.getNombre(), stock, minimumPrice));
        });
        return result;
    }

    private List<EcommercePromocionCombo> availablePromotions(Map<Integer, ProductInventory> inventory) {
        return promotionRepository.listarActivas(LocalDateTime.now()).stream()
                .filter(promotion -> isAvailable(promotion, inventory)).toList();
    }

    private EcommercePromocionCombo requireAvailablePromotion(
            Integer promotionId, Map<Integer, ProductInventory> inventory) {
        if (promotionId == null) throw new IllegalArgumentException("La promocion es obligatoria");
        EcommercePromocionCombo promotion = findActivePromotion(promotionId);
        if (promotion == null || !isAvailable(promotion, inventory)) {
            throw new IllegalArgumentException("La promocion no esta vigente o disponible en esta sucursal");
        }
        return promotion;
    }

    private EcommercePromocionCombo findActivePromotion(Integer promotionId) {
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.listarActivas(now).stream()
                .filter(promotion -> Objects.equals(promotion.getIdEcommercePromocionCombo(), promotionId))
                .findFirst().orElse(null);
    }

    private boolean isAvailable(EcommercePromocionCombo promotion, Map<Integer, ProductInventory> inventory) {
        if (promotion == null || promotion.getItems() == null || promotion.getItems().isEmpty()) return false;
        Map<Integer, Integer> required = requiredProducts(promotion);
        if (required.values().stream().mapToInt(Integer::intValue).sum() != 2) return false;
        return required.entrySet().stream().allMatch(entry -> {
            ProductInventory product = inventory.get(entry.getKey());
            return product != null && product.stock() >= entry.getValue();
        });
    }

    private PromotionSummary toSummary(
            EcommercePromocionCombo promotion, Map<Integer, ProductInventory> inventory) {
        List<PromotionItem> items = promotion.getItems().stream()
                .map(item -> new PromotionItem(item.getProducto().getIdProducto(), item.getProducto().getNombre(),
                        item.getCantidadRequerida(), inventory.get(item.getProducto().getIdProducto()).stock()))
                .sorted(Comparator.comparing(PromotionItem::productName, String.CASE_INSENSITIVE_ORDER)).toList();
        BigDecimal regular = promotion.getItems().stream()
                .map(item -> inventory.get(item.getProducto().getIdProducto()).minimumPrice()
                        .multiply(BigDecimal.valueOf(item.getCantidadRequerida())))
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal savings = regular.subtract(promotion.getPrecioCombo()).max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new PromotionSummary(promotion.getIdEcommercePromocionCombo(), promotion.getNombre(),
                promotionType(promotion), promotion.getPrecioCombo(), regular, savings,
                promotion.getFechaInicio(), promotion.getFechaFin(),
                Boolean.TRUE.equals(promotion.getHastaAgotarStock()), items);
    }

    private int score(EcommercePromocionCombo promotion, String query, List<String> tokens,
            Integer explicitNumber, boolean asksTwoSameProduct, boolean browseAll) {
        if (browseAll) return 1;
        String name = normalize(promotion.getNombre());
        String products = promotion.getItems().stream().map(item -> normalize(item.getProducto().getNombre()))
                .collect(Collectors.joining(" "));
        String searchable = name + " " + products;
        int score = 0;
        if (explicitNumber != null) {
            if (Objects.equals(promotion.getIdEcommercePromocionCombo(), explicitNumber)) score += 1200;
            if (name.equals("combo " + explicitNumber) || name.equals("promocion " + explicitNumber)) score += 1400;
        }
        if (name.equals(query)) score += 1000;
        else if (name.contains(query) || query.contains(name)) score += 500;
        for (String token : tokens) {
            if (name.contains(token)) score += 100;
            if (products.contains(token)) score += 180;
            else if (minimumTokenDistance(token, products) <= 1) score += 30;
        }
        if (asksTwoSameProduct && "TWO_SAME_PRODUCT".equals(promotionType(promotion))
                && tokens.stream().anyMatch(products::contains)) {
            score += 700;
        }
        return score;
    }

    private CartSnapshot cartSnapshot(Cart cart, Map<Integer, SucursalStock> variantStock) {
        Map<Integer, Integer> quantities = new HashMap<>();
        Map<Integer, List<CartUnit>> units = new HashMap<>();
        List<Integer> unknown = new ArrayList<>();
        boolean stockSufficient = true;
        for (CartItem item : cart.items()) {
            SucursalStock stock = variantStock.get(item.variantId());
            if (stock == null) {
                unknown.add(item.variantId());
                continue;
            }
            int quantity = Math.max(0, item.quantity());
            if (safeStock(stock) < quantity) stockSufficient = false;
            Integer productId = stock.getProductoVariante().getProducto().getIdProducto();
            quantities.merge(productId, quantity, Integer::sum);
            BigDecimal unitPrice = item.unitPrice() == null ? ZERO : item.unitPrice();
            List<CartUnit> productUnits = units.computeIfAbsent(productId, ignored -> new ArrayList<>());
            for (int index = 0; index < quantity; index++) productUnits.add(new CartUnit(productId, unitPrice));
        }
        units.values().forEach(list -> list.sort(Comparator.comparing(CartUnit::price)));
        return new CartSnapshot(quantities, units, unknown, stockSufficient);
    }

    private BigDecimal regularPriceForOneApplication(CartSnapshot cart, Map<Integer, Integer> required) {
        BigDecimal result = ZERO;
        for (Map.Entry<Integer, Integer> entry : required.entrySet()) {
            List<CartUnit> units = cart.unitsByProduct().getOrDefault(entry.getKey(), List.of());
            for (int index = 0; index < entry.getValue() && index < units.size(); index++) {
                result = result.add(units.get(index).price());
            }
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private int maximumApplications(Map<Integer, Integer> available, Map<Integer, Integer> required) {
        return required.entrySet().stream()
                .mapToInt(entry -> available.getOrDefault(entry.getKey(), 0) / entry.getValue())
                .min().orElse(0);
    }

    private List<MissingItem> missingItems(Map<Integer, Integer> required, Map<Integer, Integer> available,
            Map<Integer, ProductInventory> inventory) {
        return required.entrySet().stream()
                .map(entry -> new MissingItem(entry.getKey(), inventory.get(entry.getKey()).name(),
                        entry.getValue(), available.getOrDefault(entry.getKey(), 0),
                        Math.max(0, entry.getValue() - available.getOrDefault(entry.getKey(), 0))))
                .filter(item -> item.missingQuantity() > 0).toList();
    }

    private Map<Integer, Integer> requiredProducts(EcommercePromocionCombo promotion) {
        return promotion.getItems().stream().collect(Collectors.toMap(
                item -> item.getProducto().getIdProducto(),
                item -> Math.max(0, item.getCantidadRequerida()), Integer::sum, LinkedHashMap::new));
    }

    private String promotionType(EcommercePromocionCombo promotion) {
        Map<Integer, Integer> required = requiredProducts(promotion);
        return required.size() == 1 && required.values().stream().findFirst().orElse(0) == 2
                ? "TWO_SAME_PRODUCT" : "MIXED_PRODUCTS";
    }

    private Integer explicitComboNumber(String query) {
        Matcher matcher = EXPLICIT_COMBO.matcher(query);
        if (!matcher.find()) return null;
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> meaningfulTokens(String value) {
        if (value.isBlank()) return List.of();
        return List.of(value.split("\\s+")).stream()
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token)).distinct().toList();
    }

    private int minimumTokenDistance(String token, String words) {
        return List.of(words.split("\\s+")).stream().filter(word -> !word.isBlank())
                .mapToInt(word -> levenshtein(token, word)).min().orElse(Integer.MAX_VALUE);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) previous[column] = column;
        for (int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1), substitution);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private boolean containsWord(String value, String word) {
        return List.of(value.split("\\s+")).contains(word);
    }

    private int safeStock(SucursalStock row) {
        return row.getCantidad() == null ? 0 : Math.max(0, row.getCantidad());
    }

    private record ProductInventory(Integer id, String name, int stock, BigDecimal minimumPrice) {
    }

    private record ScoredPromotion(EcommercePromocionCombo promotion, int score) {
    }

    private record CartUnit(Integer productId, BigDecimal price) {
    }

    private record CartSnapshot(Map<Integer, Integer> productQuantities, Map<Integer, List<CartUnit>> unitsByProduct,
            List<Integer> unknownVariants, boolean stockSufficient) {
    }

    public record PromotionSearchResult(String query, Integer branchId, List<PromotionSummary> promotions,
            boolean hasMore) {
    }

    public record PromotionSummary(Integer promotionId, String name, String type, BigDecimal comboPrice,
            BigDecimal regularPriceFrom, BigDecimal savingsFrom, LocalDateTime startsAt, LocalDateTime endsAt,
            boolean untilStockRunsOut, List<PromotionItem> items) {
    }

    public record PromotionDetail(Integer promotionId, String name, String type, BigDecimal comboPrice,
            BigDecimal regularPriceFrom, BigDecimal savingsFrom, LocalDateTime startsAt, LocalDateTime endsAt,
            boolean untilStockRunsOut, List<PromotionItem> items, Integer branchId, boolean available) {
    }

    public record PromotionItem(Integer productId, String productName, int requiredQuantity, int branchStock) {
    }

    public record CartPromotionValidation(String status, Integer branchId, Long cartId, BigDecimal currentCartTotal,
            List<PromotionApplication> eligiblePromotions, List<PromotionMissingItems> incompletePromotions,
            String message) {
    }

    public record PromotionApplication(Integer promotionId, String name, int maximumApplications,
            BigDecimal comboPrice, BigDecimal regularPrice, BigDecimal savingsPerApplication) {
    }

    public record PromotionMissingItems(Integer promotionId, String name, List<MissingItem> items) {
    }

    public record MissingItem(Integer productId, String productName, int requiredQuantity, int cartQuantity,
            int missingQuantity) {
    }
}
