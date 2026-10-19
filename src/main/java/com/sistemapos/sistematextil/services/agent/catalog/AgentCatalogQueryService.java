package com.sistemapos.sistematextil.services.agent.catalog;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.ProductoVarianteOfertaSucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.repositories.AgentCatalogReadRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteOfertaSucursalRepository;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver.CatalogScope;
import com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability;

@Service
public class AgentCatalogQueryService {
    private static final String PRODUCT_PAGE_BASE_URL = "https://kiments.com.pe/productos/";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final int MIN_PRODUCT_TOKEN_LENGTH = 3;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");
    private static final Set<String> STOP_WORDS = Set.of(
            "quiero", "deseo", "busco", "ver", "muestrame", "mostrar", "tienes", "tiene", "hay",
            "producto", "productos", "modelo", "modelos", "conjunto", "prenda", "ropa", "por", "favor",
            "que", "cual", "cuales", "el", "la", "los", "las", "un", "una", "de", "del", "en", "para",
            "me", "tu", "su");

    private final AgentCatalogScopeResolver scopeResolver;
    private final AgentCatalogReadRepository catalogRepository;
    private final ProductoColorImagenRepository imageRepository;
    private final ProductoVarianteOfertaSucursalRepository branchOfferRepository;

    public AgentCatalogQueryService(
            AgentCatalogScopeResolver scopeResolver,
            AgentCatalogReadRepository catalogRepository,
            ProductoColorImagenRepository imageRepository,
            ProductoVarianteOfertaSucursalRepository branchOfferRepository) {
        this.scopeResolver = scopeResolver;
        this.catalogRepository = catalogRepository;
        this.imageRepository = imageRepository;
        this.branchOfferRepository = branchOfferRepository;
    }

    @Transactional(readOnly = true)
    public ProductSearchResult searchProducts(Long sessionId, String query, Integer requestedLimit) {
        return searchProducts(sessionId, query, requestedLimit, AgentCatalogAvailability.ALL);
    }

    @Transactional(readOnly = true)
    public ProductSearchResult searchProducts(Long sessionId, String query, Integer requestedLimit,
            AgentCatalogAvailability availability) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        AgentCatalogAvailability safeAvailability = availability == null
                ? AgentCatalogAvailability.ALL : availability;
        String normalizedQuery = normalize(query);
        List<String> tokens = meaningfulTokens(normalizedQuery);
        boolean browseAll = normalizedQuery.isBlank() || tokens.isEmpty();

        Map<Integer, List<SucursalStock>> grouped = groupByProduct(catalogRepository.findCatalogByBranch(scope.branchId()));
        Comparator<ScoredProduct> ranking = browseAll
                ? Comparator.comparing(this::productCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(this::productId, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparingInt(ScoredProduct::score).reversed()
                        .thenComparing(candidate -> candidate.rows().getFirst().getProductoVariante()
                                .getProducto().getNombre(), String.CASE_INSENSITIVE_ORDER);
        List<ScoredProduct> ranked = grouped.values().stream()
                .filter(this::hasAvailableStock)
                .filter(rows -> matchesAvailability(rows, safeAvailability))
                .map(rows -> score(rows, browseAll ? "" : normalizedQuery, tokens))
                .filter(candidate -> browseAll || candidate.score() > 0)
                .sorted(ranking)
                .toList();

        List<ProductSummary> products = ranked.stream().limit(limit)
                .map(candidate -> toSummary(candidate.rows()))
                .toList();
        return new ProductSearchResult(query == null ? "" : query.trim(), scope.branchId(), safeAvailability,
                products, ranked.size() > products.size());
    }

    @Transactional(readOnly = true)
    public ProductDetail getProduct(Long sessionId, Integer productId) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        List<SucursalStock> rows = requireProduct(scope, productId);
        Producto product = rows.getFirst().getProductoVariante().getProducto();
        boolean activePreorder = isActivePreorder(product);
        List<VariantSummary> variants = mapVariants(scope, rows, null, null);
        List<VariantSummary> availableVariants = variants.stream().filter(VariantSummary::available).toList();
        return new ProductDetail(
                product.getIdProducto(), product.getNombre(), product.getDescripcion(), categoryName(product),
                activePreorder, activePreorder ? product.getFechaEnvioPreventa() : null,
                product.getGuiaTallasUrl(), product.getGuiaTallasThumbUrl(),
                availableVariants.stream().map(VariantSummary::color).filter(value -> value != null && !value.isBlank())
                        .distinct().toList(),
                availableVariants.stream().map(VariantSummary::size).filter(value -> value != null && !value.isBlank())
                        .distinct().toList(),
                availableVariants.stream().mapToInt(VariantSummary::stock).sum(), minimumPrice(availableVariants),
                variants.size(), groupAvailability(availableVariants), productPageUrl(product));
    }

    @Transactional(readOnly = true)
    public VariantResult getVariants(Long sessionId, Integer productId, String color, String size) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        List<SucursalStock> rows = requireProduct(scope, productId);
        List<VariantSummary> variants = mapVariants(scope, rows, color, size);
        Producto product = rows.getFirst().getProductoVariante().getProducto();
        return new VariantResult(productId, product.getNombre(), clean(color), clean(size), variants);
    }

    @Transactional(readOnly = true)
    public StockResult getStock(Long sessionId, Integer variantId, Integer requestedQuantity) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        if (variantId == null) throw new IllegalArgumentException("La variante es obligatoria");
        SucursalStock row = catalogRepository.findVariantByBranch(scope.branchId(), variantId).stream()
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "La variante no pertenece al catalogo de la sucursal de esta conversacion"));
        ProductoVariante variant = row.getProductoVariante();
        int stock = safeStock(row);
        Integer quantity = requestedQuantity == null ? null : Math.max(1, requestedQuantity);
        return new StockResult(variantId, variant.getProducto().getIdProducto(), variant.getProducto().getNombre(),
                colorName(variant), sizeName(variant), scope.branchId(), stock, stock > 0,
                quantity, quantity == null ? null : stock >= quantity);
    }

    @Transactional(readOnly = true)
    public ProductImageResult getProductImage(Long sessionId, Integer productId, String requestedColor) {
        CatalogScope scope = scopeResolver.resolve(sessionId);
        List<SucursalStock> rows = requireProduct(scope, productId);
        Producto product = rows.getFirst().getProductoVariante().getProducto();
        String colorFilter = normalize(requestedColor);
        List<ProductImage> colorImages = imageRepository
                .findByProductoIdProductoAndDeletedAtIsNull(productId).stream()
                .filter(image -> colorFilter.isBlank() || normalize(image.getColor().getNombre()).contains(colorFilter)
                        || colorFilter.contains(normalize(image.getColor().getNombre())))
                .sorted(Comparator.comparing((ProductoColorImagen image) -> !Boolean.TRUE.equals(image.getEsPrincipal()))
                        .thenComparing(ProductoColorImagen::getOrden, Comparator.nullsLast(Integer::compareTo)))
                .map(image -> new ProductImage(image.getColor().getNombre(), image.getUrl(), image.getUrlThumb(),
                        Boolean.TRUE.equals(image.getEsPrincipal())))
                .toList();
        return new ProductImageResult(productId, product.getNombre(), clean(requestedColor),
                product.getImagenGlobalUrl(), product.getImagenGlobalThumbUrl(), colorImages);
    }

    private List<SucursalStock> requireProduct(CatalogScope scope, Integer productId) {
        if (productId == null) throw new IllegalArgumentException("El producto es obligatorio");
        List<SucursalStock> rows = catalogRepository.findProductByBranch(scope.branchId(), productId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("El producto no pertenece al catalogo de la sucursal de esta conversacion");
        }
        return rows;
    }

    private List<VariantSummary> mapVariants(
            CatalogScope scope, List<SucursalStock> rows, String requestedColor, String requestedSize) {
        String colorFilter = normalize(requestedColor);
        String sizeFilter = normalize(requestedSize);
        Map<Integer, ProductoVarianteOfertaSucursal> branchOffers = branchOfferRepository
                .findByProductoVarianteIdProductoVarianteInAndSucursalIdSucursalAndDeletedAtIsNull(
                        rows.stream().map(row -> row.getProductoVariante().getIdProductoVariante()).toList(),
                        scope.branchId()).stream()
                .collect(Collectors.toMap(offer -> offer.getProductoVariante().getIdProductoVariante(),
                        Function.identity(), (first, ignored) -> first));
        boolean exactColorExists = !colorFilter.isBlank() && rows.stream()
                .anyMatch(row -> normalize(colorName(row.getProductoVariante())).equals(colorFilter));
        boolean exactSizeExists = !sizeFilter.isBlank() && rows.stream()
                .anyMatch(row -> normalize(sizeName(row.getProductoVariante())).equals(sizeFilter));
        return rows.stream()
                .filter(row -> matchesPreferred(colorName(row.getProductoVariante()), colorFilter, exactColorExists))
                .filter(row -> matchesPreferred(sizeName(row.getProductoVariante()), sizeFilter, exactSizeExists))
                .map(row -> toVariant(row, branchOffers.get(row.getProductoVariante().getIdProductoVariante())))
                .toList();
    }

    private VariantSummary toVariant(SucursalStock row, ProductoVarianteOfertaSucursal branchOffer) {
        ProductoVariante variant = row.getProductoVariante();
        Double effectivePrice = activeOfferPrice(branchOffer);
        if (effectivePrice == null) effectivePrice = activeOfferPrice(variant);
        if (effectivePrice == null) effectivePrice = variant.getPrecio();
        return new VariantSummary(variant.getIdProductoVariante(), variant.getSku(), colorName(variant),
                sizeName(variant), variant.getPrecio(), effectivePrice, safeStock(row), safeStock(row) > 0);
    }

    private Double activeOfferPrice(ProductoVarianteOfertaSucursal offer) {
        if (offer == null || offer.getPrecioOferta() == null) return null;
        return isWithinOfferWindow(offer.getOfertaInicio(), offer.getOfertaFin()) ? offer.getPrecioOferta() : null;
    }

    private Double activeOfferPrice(ProductoVariante variant) {
        if (variant.getPrecioOferta() == null) return null;
        return isWithinOfferWindow(variant.getOfertaInicio(), variant.getOfertaFin()) ? variant.getPrecioOferta() : null;
    }

    private boolean isWithinOfferWindow(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        return (start == null || !start.isAfter(now)) && (end == null || !end.isBefore(now));
    }

    private Map<Integer, List<SucursalStock>> groupByProduct(List<SucursalStock> rows) {
        return rows.stream().collect(Collectors.groupingBy(
                row -> row.getProductoVariante().getProducto().getIdProducto(), LinkedHashMap::new, Collectors.toList()));
    }

    private boolean hasAvailableStock(List<SucursalStock> rows) {
        return rows.stream().anyMatch(row -> safeStock(row) > 0);
    }

    private boolean matchesAvailability(List<SucursalStock> rows, AgentCatalogAvailability availability) {
        boolean preorder = isActivePreorder(rows.getFirst().getProductoVariante().getProducto());
        return switch (availability) {
            case ALL -> true;
            case PREORDER_ONLY -> preorder;
            case READY_STOCK_ONLY -> !preorder;
        };
    }

    private ScoredProduct score(List<SucursalStock> rows, String query, List<String> tokens) {
        Producto product = rows.getFirst().getProductoVariante().getProducto();
        String name = normalize(product.getNombre());
        String description = normalize(product.getDescripcion());
        String category = normalize(categoryName(product));
        String searchable = name + " " + description + " " + category;
        int score = query.isBlank() ? 1 : 0;
        if (!query.isBlank() && name.equals(query)) score += 1000;
        else if (!query.isBlank() && (name.contains(query) || query.contains(name))) score += 600;
        for (String token : tokens) {
            if (name.equals(token)) score += 250;
            else if (name.contains(token)) score += 120;
            else if (searchable.contains(token)) score += 30;
            else if (minimumTokenDistance(token, name) <= 1) score += 20;
        }
        return new ScoredProduct(rows, score);
    }

    private LocalDateTime productCreatedAt(ScoredProduct candidate) {
        return candidate.rows().getFirst().getProductoVariante().getProducto().getFechaCreacion();
    }

    private Integer productId(ScoredProduct candidate) {
        return candidate.rows().getFirst().getProductoVariante().getProducto().getIdProducto();
    }

    private int minimumTokenDistance(String token, String normalizedName) {
        return List.of(normalizedName.split("\\s+")).stream()
                .mapToInt(nameToken -> levenshtein(token, nameToken)).min().orElse(Integer.MAX_VALUE);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private ProductSummary toSummary(List<SucursalStock> rows) {
        Producto product = rows.getFirst().getProductoVariante().getProducto();
        boolean activePreorder = isActivePreorder(product);
        List<SucursalStock> availableRows = rows.stream().filter(row -> safeStock(row) > 0).toList();
        Set<String> colors = new LinkedHashSet<>();
        Set<String> sizes = new LinkedHashSet<>();
        List<Double> prices = new ArrayList<>();
        int stock = 0;
        for (SucursalStock row : availableRows) {
            ProductoVariante variant = row.getProductoVariante();
            colors.add(colorName(variant));
            sizes.add(sizeName(variant));
            if (variant.getPrecio() != null) prices.add(variant.getPrecio());
            stock += safeStock(row);
        }
        return new ProductSummary(product.getIdProducto(), product.getNombre(), product.getDescripcion(),
                categoryName(product), activePreorder, activePreorder ? product.getFechaEnvioPreventa() : null,
                prices.stream().min(Double::compareTo).orElse(null), stock, List.copyOf(colors), List.copyOf(sizes),
                groupAvailabilityFromStock(availableRows), productPageUrl(product));
    }

    private String productPageUrl(Producto product) {
        if (product == null || product.getSlug() == null || product.getSlug().isBlank()) return "";
        return PRODUCT_PAGE_BASE_URL + product.getSlug().trim();
    }

    /**
     * La marca almacenada conserva la configuracion historica, pero comercialmente
     * la preventa termina al llegar su fecha de despacho en la zona de Kiments.
     */
    private boolean isActivePreorder(Producto product) {
        if (product == null || !Boolean.TRUE.equals(product.getPreventa())) return false;
        LocalDate shippingDate = product.getFechaEnvioPreventa();
        return shippingDate == null || shippingDate.isAfter(LocalDate.now(BUSINESS_ZONE));
    }

    private List<ColorAvailability> groupAvailabilityFromStock(List<SucursalStock> rows) {
        List<VariantSummary> variants = rows.stream()
                .map(row -> new VariantSummary(row.getProductoVariante().getIdProductoVariante(),
                        row.getProductoVariante().getSku(), colorName(row.getProductoVariante()),
                        sizeName(row.getProductoVariante()), row.getProductoVariante().getPrecio(),
                        row.getProductoVariante().getPrecio(), safeStock(row), safeStock(row) > 0))
                .toList();
        return groupAvailability(variants);
    }

    private List<ColorAvailability> groupAvailability(List<VariantSummary> variants) {
        Map<String, List<VariantSummary>> byColor = variants.stream()
                .filter(VariantSummary::available)
                .filter(item -> item.color() != null && !item.color().isBlank())
                .collect(Collectors.groupingBy(VariantSummary::color, LinkedHashMap::new, Collectors.toList()));
        return byColor.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new ColorAvailability(entry.getKey(), entry.getValue().stream()
                        .map(VariantSummary::size)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .sorted(Comparator.comparingInt(this::sizeOrder).thenComparing(String.CASE_INSENSITIVE_ORDER))
                        .toList(), entry.getValue().stream().mapToInt(VariantSummary::stock).sum()))
                .toList();
    }

    private int sizeOrder(String size) {
        return switch (normalize(size).toUpperCase(Locale.ROOT)) {
            case "XS" -> 0;
            case "S" -> 1;
            case "M" -> 2;
            case "L" -> 3;
            case "XL" -> 4;
            default -> 100;
        };
    }

    private Double minimumPrice(List<VariantSummary> variants) {
        return variants.stream().map(VariantSummary::effectivePrice).filter(java.util.Objects::nonNull)
                .min(Double::compareTo).orElse(null);
    }

    private List<String> meaningfulTokens(String value) {
        if (value.isBlank()) return List.of();
        return List.of(value.split("\\s+")).stream()
                .filter(token -> token.length() >= MIN_PRODUCT_TOKEN_LENGTH && !STOP_WORDS.contains(token))
                .distinct().toList();
    }

    private boolean matches(String candidate, String filter) {
        if (filter.isBlank()) return true;
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.contains(filter) || filter.contains(normalizedCandidate);
    }

    private boolean matchesPreferred(String candidate, String filter, boolean exactExists) {
        if (filter.isBlank()) return true;
        String normalizedCandidate = normalize(candidate);
        return exactExists ? normalizedCandidate.equals(filter)
                : normalizedCandidate.contains(filter) || filter.contains(normalizedCandidate);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String colorName(ProductoVariante variant) {
        return variant.getColor() == null ? null : variant.getColor().getNombre();
    }

    private String sizeName(ProductoVariante variant) {
        return variant.getTalla() == null ? null : variant.getTalla().getNombre();
    }

    private String categoryName(Producto product) {
        return product.getCategoria() == null ? null : product.getCategoria().getNombreCategoria();
    }

    private int safeStock(SucursalStock row) {
        return row.getCantidad() == null ? 0 : Math.max(0, row.getCantidad());
    }

    private record ScoredProduct(List<SucursalStock> rows, int score) {
    }

    public record ProductSearchResult(String query, Integer branchId, AgentCatalogAvailability availability,
            List<ProductSummary> products, boolean hasMore) {
        public ProductSearchResult(String query, Integer branchId, List<ProductSummary> products, boolean hasMore) {
            this(query, branchId, AgentCatalogAvailability.ALL, products, hasMore);
        }
    }

    public record ProductSummary(Integer productId, String name, String description, String category,
            boolean preorder, LocalDate preorderShippingDate, Double priceFrom, int totalStock,
            List<String> colors, List<String> sizes, List<ColorAvailability> availabilityByColor,
            String productPageUrl) {
        public ProductSummary(Integer productId, String name, String description, String category,
                boolean preorder, LocalDate preorderShippingDate, Double priceFrom, int totalStock,
                List<String> colors, List<String> sizes) {
            this(productId, name, description, category, preorder, preorderShippingDate, priceFrom, totalStock,
                    colors, sizes, List.of(), "");
        }

        public ProductSummary(Integer productId, String name, String description, String category,
                boolean preorder, LocalDate preorderShippingDate, Double priceFrom, int totalStock,
                List<String> colors, List<String> sizes, List<ColorAvailability> availabilityByColor) {
            this(productId, name, description, category, preorder, preorderShippingDate, priceFrom, totalStock,
                    colors, sizes, availabilityByColor, "");
        }
    }

    public record ProductDetail(Integer productId, String name, String description, String category,
            boolean preorder, LocalDate preorderShippingDate, String sizeGuideUrl, String sizeGuideThumbnailUrl,
            List<String> colors, List<String> sizes, int totalStock, Double priceFrom, int variantCount,
            List<ColorAvailability> availabilityByColor, String productPageUrl) {
        public ProductDetail(Integer productId, String name, String description, String category,
                boolean preorder, LocalDate preorderShippingDate, String sizeGuideUrl, String sizeGuideThumbnailUrl,
                List<String> colors, List<String> sizes, int totalStock, Double priceFrom, int variantCount) {
            this(productId, name, description, category, preorder, preorderShippingDate, sizeGuideUrl,
                    sizeGuideThumbnailUrl, colors, sizes, totalStock, priceFrom, variantCount, List.of(), "");
        }

        public ProductDetail(Integer productId, String name, String description, String category,
                boolean preorder, LocalDate preorderShippingDate, String sizeGuideUrl, String sizeGuideThumbnailUrl,
                List<String> colors, List<String> sizes, int totalStock, Double priceFrom, int variantCount,
                List<ColorAvailability> availabilityByColor) {
            this(productId, name, description, category, preorder, preorderShippingDate, sizeGuideUrl,
                    sizeGuideThumbnailUrl, colors, sizes, totalStock, priceFrom, variantCount,
                    availabilityByColor, "");
        }
    }

    public record ColorAvailability(String color, List<String> sizes, int totalStock) {
    }

    public record VariantResult(Integer productId, String productName, String requestedColor, String requestedSize,
            List<VariantSummary> variants) {
    }

    public record VariantSummary(Integer variantId, String sku, String color, String size, Double regularPrice,
            Double effectivePrice, int stock, boolean available) {
    }

    public record StockResult(Integer variantId, Integer productId, String productName, String color, String size,
            Integer branchId, int availableStock, boolean available, Integer requestedQuantity, Boolean sufficient) {
    }

    public record ProductImageResult(Integer productId, String productName, String requestedColor,
            String globalImageUrl, String globalThumbnailUrl, List<ProductImage> colorImages) {
    }

    public record ProductImage(String color, String url, String thumbnailUrl, boolean primary) {
    }
}
