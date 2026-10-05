package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.ProductoVarianteOfertaSucursal;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalMetodoPagoConfig;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappBusinessHoursRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository.EcommerceProductNameView;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioComboResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiCommercialQueryService {

    private static final int MAX_PRODUCTS = 10;
    private static final int MAX_VARIANTS = 20;

    private final SucursalStockRepository stockRepository;
    private final ProductoColorImagenRepository imageRepository;
    private final SucursalMetodoPagoConfigRepository paymentRepository;
    private final VentaRepository ventaRepository;
    private final CrmWhatsappBusinessHoursRepository businessHoursRepository;
    private final PrecioOfertaService precioOfertaService;
    private final EcommercePromocionComboService promotionService;
    private final S3StorageService storageService;

    @Value("${crm.whatsapp.ai.ecommerce-base-url:https://kiments.com.pe}")
    private String ecommerceBaseUrl;

    @Transactional(readOnly = true)
    public CatalogResult searchProducts(CrmWhatsappConversation conversation, String query) {
        return searchProducts(conversation, query, 0, "");
    }

    @Transactional(readOnly = true)
    public CatalogResult searchProducts(CrmWhatsappConversation conversation, String query, int page) {
        return searchProducts(conversation, query, page, "");
    }

    @Transactional(readOnly = true)
    public CatalogResult searchProducts(
            CrmWhatsappConversation conversation, String query, int page, String fallbackProduct) {
        CommercialContext context = requireContext(conversation);
        String term = limit(query, 80);
        int safePage = Math.max(0, Math.min(page, 10));
        ProductResolution resolution = ProductResolution.none();
        List<Integer> productIds;
        boolean hasMore = false;
        if (term.isBlank()) {
            productIds = stockRepository.listarIdsProductosEcommerceDisponiblesParaIa(
                    context.branch().getIdSucursal(), PageRequest.of(safePage, MAX_PRODUCTS + 1));
            hasMore = productIds.size() > MAX_PRODUCTS;
            productIds = productIds.stream().limit(MAX_PRODUCTS).toList();
        } else {
            List<EcommerceProductNameView> names = stockRepository
                    .listarNombresProductosEcommerceDisponiblesParaIa(
                            context.branch().getIdSucursal(), PageRequest.of(0, 500));
            resolution = resolveProductName(term, names);
            if ("NONE".equals(resolution.status()) && !clean(fallbackProduct).isBlank()) {
                resolution = resolveProductName(limit(fallbackProduct, 80), names);
            }
            if (resolution.unique()) {
                productIds = List.of(resolution.matches().getFirst().productId());
            } else if (resolution.ambiguous()) {
                productIds = List.of();
            } else {
                productIds = stockRepository.buscarIdsProductosEcommerceDisponiblesParaIa(
                        context.branch().getIdSucursal(), term, PageRequest.of(safePage, MAX_PRODUCTS + 1));
                hasMore = productIds.size() > MAX_PRODUCTS;
                productIds = productIds.stream().limit(MAX_PRODUCTS).toList();
            }
        }
        String interpretedProduct = "";
        boolean corrected = false;
        if (resolution.unique()) {
            ProductNameMatch match = resolution.matches().getFirst();
            interpretedProduct = match.productName();
            corrected = resolution.corrected();
        }
        List<SucursalStock> stocks = productIds.isEmpty()
                ? List.of()
                : stockRepository.listarVariantesEcommerceDisponiblesParaIa(
                        context.branch().getIdSucursal(), productIds);
        Map<Integer, AvailabilitySummary> availability = summarizeAvailability(stocks);

        // A product detail query needs every color/size combination. The compact
        // 20-variant limit is retained only for multi-product catalog listings.
        List<SucursalStock> selected = productIds.size() == 1 ? stocks : selectLimits(stocks);
        List<Integer> variantIds = selected.stream()
                .map(item -> item.getProductoVariante().getIdProductoVariante())
                .distinct()
                .toList();
        Map<Integer, ProductoVarianteOfertaSucursal> offers = precioOfertaService
                .obtenerOfertasSucursalPorVariantes(variantIds, context.branch().getIdSucursal());
        Map<String, ProductImage> images = resolveImages(selected);

        Map<Integer, ProductBuilder> grouped = new LinkedHashMap<>();
        for (SucursalStock stock : selected) {
            ProductoVariante variant = stock.getProductoVariante();
            Producto product = variant.getProducto();
            ProductBuilder builder = grouped.computeIfAbsent(product.getIdProducto(), ignored -> new ProductBuilder(
                    product.getIdProducto(), clean(product.getNombre()),
                    product.getCategoria() == null ? "" : clean(product.getCategoria().getNombreCategoria()),
                    clean(product.getSlug()), ecommerceUrl(product.getSlug()),
                    preventaActiva(product), preventaActiva(product) ? product.getFechaEnvioPreventa() : null,
                    publicUrl(product.getImagenGlobalUrl()), publicUrl(product.getImagenGlobalThumbUrl()),
                    publicUrl(product.getGuiaTallasUrl()), publicUrl(product.getGuiaTallasThumbUrl()),
                    availability.getOrDefault(product.getIdProducto(), AvailabilitySummary.empty()).colors(),
                    availability.getOrDefault(product.getIdProducto(), AvailabilitySummary.empty()).sizes()));
            PrecioOfertaService.ResultadoPrecioOferta price = precioOfertaService.resolver(
                    variant, offers.get(variant.getIdProductoVariante()));
            ProductImage image = images.get(imageKey(product.getIdProducto(),
                    variant.getColor() == null ? null : variant.getColor().getIdColor()));
            builder.variants().add(new VariantResult(
                    variant.getIdProductoVariante(), clean(variant.getSku()), clean(variant.getCodigoBarras()),
                    variant.getColor() == null ? "" : clean(variant.getColor().getNombre()),
                    variant.getTalla() == null ? "" : clean(variant.getTalla().getNombre()),
                    Math.max(0, stock.getCantidad() == null ? 0 : stock.getCantidad()),
                    stock.getCantidad() != null && stock.getCantidad() > 0,
                    money(price.precioRegular()), money(price.precioVigente()),
                    money(price.precioOfertaAplicada()), money(variant.getPrecioMayor()),
                    variant.getPrecioMayor() == null
                            ? null : "Precio referencial; las condiciones mayoristas deben confirmarse con un asesor",
                    image == null || clean(image.url()).isBlank() ? builder.globalImageUrl() : image.url(),
                    image == null || clean(image.thumbnailUrl()).isBlank()
                            ? builder.globalThumbUrl() : image.thumbnailUrl(),
                    image != null && !clean(image.url()).isBlank()));
        }
        List<ProductResult> products = grouped.values().stream()
                .map(ProductBuilder::build)
                .toList();
        return new CatalogResult(context.branch().getIdSucursal(), context.branch().getNombre(), term,
                interpretedProduct, corrected, resolution.status(),
                resolution.matches().stream()
                        .map(match -> new ProductCandidate(match.productId(), match.productName()))
                        .toList(), hasMore, products);
    }

    @Transactional(readOnly = true)
    public CategoryResult listCategories(CrmWhatsappConversation conversation) {
        CommercialContext context = requireContext(conversation);
        List<CategoryItem> categories = stockRepository
                .listarCategoriasDisponiblesParaIa(context.branch().getIdSucursal(), PageRequest.of(0, 30))
                .stream()
                .map(category -> new CategoryItem(category.getIdCategoria(), clean(category.getNombreCategoria())))
                .toList();
        return new CategoryResult(context.branch().getIdSucursal(), context.branch().getNombre(), categories);
    }

    @Transactional(readOnly = true)
    public BranchResult branchDetails(CrmWhatsappConversation conversation) {
        Sucursal branch = requireContext(conversation).branch();
        var hours = businessHoursRepository.findByConnection_IdConnectionOrderByIdBusinessHoursAsc(
                conversation.getConnection().getIdConnection()).stream()
                .map(item -> new BusinessHoursItem(item.getDayOfWeek(), Boolean.TRUE.equals(item.getClosed()),
                        item.getOpensAt() == null ? "" : item.getOpensAt().toString(),
                        item.getClosesAt() == null ? "" : item.getClosesAt().toString()))
                .toList();
        return new BranchResult(branch.getIdSucursal(), clean(branch.getNombre()), clean(branch.getDireccion()),
                clean(branch.getCiudad()), clean(branch.getTelefono()), hours.isEmpty(),
                hours.isEmpty() ? "El horario comercial debe confirmarse con un asesor" : "Horario comercial configurado",
                hours);
    }

    @Transactional(readOnly = true)
    public PaymentResult paymentMethods(CrmWhatsappConversation conversation) {
        CommercialContext context = requireContext(conversation);
        List<PaymentMethodItem> methods = paymentRepository.findActivosBySucursal(context.branch().getIdSucursal())
                .stream()
                .filter(this::activePayment)
                .map(config -> new PaymentMethodItem(
                        config.getMetodoPago().getIdMetodoPago(), clean(config.getMetodoPago().getNombre()),
                        clean(config.getMetodoPago().getDescripcion()),
                        Boolean.TRUE.equals(config.getRequiereCodigoOperacion()),
                        Boolean.TRUE.equals(config.getRequiereFechaPago()),
                        Boolean.TRUE.equals(config.getRequiereHoraPago()),
                        config.getMetodoPago().getCuentas() == null ? List.of()
                                : config.getMetodoPago().getCuentas().stream()
                                        .filter(account -> !Boolean.FALSE.equals(account.getActivo()))
                                        .map(MetodoPagoCuenta::getNumeroCuenta)
                                        .map(this::clean)
                                        .filter(value -> !value.isBlank())
                                        .limit(5)
                                        .toList()))
                .toList();
        return new PaymentResult(context.branch().getIdSucursal(), context.branch().getNombre(), methods,
                "Una captura o codigo de operacion no confirma el pago; requiere revision humana");
    }

    @Transactional(readOnly = true)
    public PromotionCatalogResult promotions(CrmWhatsappConversation conversation, String productQuery, int page) {
        CommercialContext context = requireContext(conversation);
        Set<Integer> requestedProducts = Set.of();
        String query = limit(productQuery, 80);
        if (!query.isBlank()) {
            requestedProducts = searchProducts(conversation, query).products().stream()
                    .map(ProductResult::productId).collect(java.util.stream.Collectors.toSet());
        }
        Set<Integer> availableProducts = new LinkedHashSet<>(
                stockRepository.listarIdsProductosEcommerceDisponiblesParaIa(
                        context.branch().getIdSucursal(), PageRequest.of(0, 1000)));
        final Set<Integer> productFilter = requestedProducts;
        List<PromotionResult> promotions = promotionService.listarPublicas(Math.max(0, page), 24).content().stream()
                .filter(combo -> combo.items().stream().allMatch(item -> availableProducts.contains(item.idProducto())))
                .filter(combo -> productFilter.isEmpty()
                        || combo.items().stream().anyMatch(item -> productFilter.contains(item.idProducto())))
                .map(this::promotionResult)
                .toList();
        return new PromotionCatalogResult(context.branch().getIdSucursal(), query, promotions);
    }

    @Transactional(readOnly = true)
    public OfferCatalogResult offers(CrmWhatsappConversation conversation, String productQuery, int page) {
        CatalogResult catalog = searchProducts(conversation, productQuery, page);
        List<OfferProductResult> products = catalog.products().stream()
                .map(product -> new OfferProductResult(product.productId(), product.name(),
                        product.variants().stream()
                                .filter(variant -> variant.available() && variant.offerPrice() != null
                                        && variant.currentPrice().compareTo(variant.regularPrice()) < 0)
                                .map(variant -> new OfferVariantResult(variant.variantId(), variant.color(), variant.size(),
                                        variant.regularPrice(), variant.currentPrice(), variant.imageUrl()))
                                .toList()))
                .filter(product -> !product.variants().isEmpty())
                .toList();
        return new OfferCatalogResult(catalog.branchId(), catalog.query(), products);
    }

    private PromotionResult promotionResult(EcommerceInicioComboResponse combo) {
        return new PromotionResult(combo.idPromocionCombo(), combo.nombre(), combo.regla(), combo.precioCombo(),
                combo.precioRegularMinimo(), combo.ahorroMinimo(), combo.items().stream()
                        .map(item -> new PromotionProductResult(item.idProducto(), item.nombre(),
                                item.cantidadRequerida(), publicUrl(item.imagenGlobalUrl()),
                                publicUrl(item.imagenGlobalThumbUrl())))
                        .toList());
    }

    @Transactional(readOnly = true)
    public ClientResult currentClient(CrmWhatsappConversation conversation) {
        CommercialContext context = requireContext(conversation);
        Cliente client = context.client();
        return client == null
                ? new ClientResult(false, null, "No existe un cliente vinculado; requiere intervencion humana")
                : new ClientResult(true, clean(client.getNombres()), "Cliente vinculado a esta conversacion");
    }

    @Transactional(readOnly = true)
    public SalesResult clientSales(CrmWhatsappConversation conversation, String receipt) {
        CommercialContext context = requireContext(conversation);
        if (context.client() == null) {
            return new SalesResult(context.branch().getIdSucursal(), false, List.of(),
                    "No existe un cliente vinculado; requiere intervencion humana");
        }
        String term = limit(receipt, 40);
        List<SaleItem> sales = ventaRepository.buscarConFiltros(
                term.isBlank() ? null : term,
                context.branch().getIdSucursal(), null, context.client().getIdCliente(),
                null, null, null,
                PageRequest.of(0, 5, Sort.by("fecha").descending()))
                .getContent().stream()
                .map(this::saleItem)
                .toList();
        return new SalesResult(context.branch().getIdSucursal(), true, sales,
                sales.isEmpty() ? "No se encontraron ventas para el criterio indicado" : "Resumen seguro de ventas");
    }

    public CommercialContext requireContext(CrmWhatsappConversation conversation) {
        if (conversation == null || conversation.getConnection() == null
                || conversation.getConnection().getSucursal() == null
                || conversation.getConnection().getEmpresa() == null) {
            throw new IllegalStateException("La conversacion no tiene una sucursal vinculada");
        }
        Sucursal branch = conversation.getConnection().getSucursal();
        if (branch.getDeletedAt() != null || !"ACTIVO".equalsIgnoreCase(clean(branch.getEstado()))) {
            throw new IllegalStateException("La sucursal vinculada no esta activa");
        }
        if (branch.getEmpresa() == null || branch.getEmpresa().getIdEmpresa() == null
                || !branch.getEmpresa().getIdEmpresa().equals(
                        conversation.getConnection().getEmpresa().getIdEmpresa())) {
            throw new IllegalStateException("La sucursal no pertenece a la empresa de la conexion");
        }
        Cliente client = conversation.getCliente();
        if (client != null && (client.getDeletedAt() != null || client.getEmpresa() == null
                || !branch.getEmpresa().getIdEmpresa().equals(client.getEmpresa().getIdEmpresa()))) {
            throw new IllegalStateException("El cliente vinculado no pertenece a la empresa de la conversacion");
        }
        return new CommercialContext(branch, client);
    }

    private List<SucursalStock> selectLimits(List<SucursalStock> stocks) {
        Map<Integer, List<SucursalStock>> grouped = new LinkedHashMap<>();
        for (SucursalStock stock : stocks) {
            if (stock == null || stock.getProductoVariante() == null
                    || stock.getProductoVariante().getProducto() == null) continue;
            Integer productId = stock.getProductoVariante().getProducto().getIdProducto();
            grouped.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(stock);
        }
        List<SucursalStock> selected = new ArrayList<>();
        int variantIndex = 0;
        boolean added;
        do {
            added = false;
            for (List<SucursalStock> variants : grouped.values()) {
                if (selected.size() >= MAX_VARIANTS) return selected;
                if (variantIndex < variants.size()) {
                    selected.add(variants.get(variantIndex));
                    added = true;
                }
            }
            variantIndex++;
        } while (added);
        return selected;
    }

    private Map<Integer, AvailabilitySummary> summarizeAvailability(List<SucursalStock> stocks) {
        Map<Integer, Set<String>> colors = new LinkedHashMap<>();
        Map<Integer, Set<String>> sizes = new LinkedHashMap<>();
        for (SucursalStock stock : stocks) {
            if (stock == null || stock.getProductoVariante() == null
                    || stock.getProductoVariante().getProducto() == null) continue;
            ProductoVariante variant = stock.getProductoVariante();
            Integer productId = variant.getProducto().getIdProducto();
            String color = variant.getColor() == null ? "" : clean(variant.getColor().getNombre());
            String size = variant.getTalla() == null ? "" : clean(variant.getTalla().getNombre());
            if (!color.isBlank()) colors.computeIfAbsent(productId, ignored -> new LinkedHashSet<>()).add(color);
            if (!size.isBlank()) sizes.computeIfAbsent(productId, ignored -> new LinkedHashSet<>()).add(size);
        }
        Map<Integer, AvailabilitySummary> result = new LinkedHashMap<>();
        for (Integer productId : productIdsInOrder(stocks)) {
            result.put(productId, new AvailabilitySummary(
                    List.copyOf(colors.getOrDefault(productId, Set.of())),
                    List.copyOf(sizes.getOrDefault(productId, Set.of()))));
        }
        return result;
    }

    private List<Integer> productIdsInOrder(List<SucursalStock> stocks) {
        return stocks.stream()
                .filter(stock -> stock != null && stock.getProductoVariante() != null
                        && stock.getProductoVariante().getProducto() != null)
                .map(stock -> stock.getProductoVariante().getProducto().getIdProducto())
                .distinct()
                .toList();
    }

    private Map<String, ProductImage> resolveImages(List<SucursalStock> stocks) {
        List<Integer> productIds = stocks.stream()
                .map(item -> item.getProductoVariante().getProducto().getIdProducto())
                .distinct().toList();
        if (productIds.isEmpty()) return Map.of();
        List<ProductoColorImagen> images = new ArrayList<>(
                imageRepository.findByProductoIdProductoInAndDeletedAtIsNull(productIds));
        images.sort(Comparator
                .comparing((ProductoColorImagen image) -> !Boolean.TRUE.equals(image.getEsPrincipal()))
                .thenComparing(image -> image.getOrden() == null ? Integer.MAX_VALUE : image.getOrden()));
        Map<String, ProductImage> result = new LinkedHashMap<>();
        for (ProductoColorImagen image : images) {
            if (!"ACTIVO".equalsIgnoreCase(clean(image.getEstado())) || image.getProducto() == null
                    || image.getColor() == null) continue;
            result.putIfAbsent(imageKey(image.getProducto().getIdProducto(), image.getColor().getIdColor()),
                    new ProductImage(publicUrl(image.getUrl()), publicUrl(image.getUrlThumb())));
        }
        return result;
    }

    private SaleItem saleItem(Venta sale) {
        String receipt = clean(sale.getSerie());
        if (sale.getCorrelativo() != null) receipt += (receipt.isBlank() ? "" : "-") + sale.getCorrelativo();
        return new SaleItem(sale.getIdVenta(), sale.getFecha(), clean(sale.getTipoComprobante()), receipt,
                clean(sale.getMoneda()), sale.getTotal() == null ? BigDecimal.ZERO : sale.getTotal(),
                clean(sale.getEstado()));
    }

    private boolean activePayment(SucursalMetodoPagoConfig config) {
        return config != null && config.getDeletedAt() == null
                && "ACTIVO".equalsIgnoreCase(clean(config.getEstado()))
                && config.getMetodoPago() != null && config.getMetodoPago().getDeletedAt() == null
                && "ACTIVO".equalsIgnoreCase(clean(config.getMetodoPago().getEstado()));
    }

    private String publicUrl(String value) {
        return clean(storageService.resolvePublicUrl(value));
    }

    private String ecommerceUrl(String value) {
        String slug = clean(value).toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) return "";
        String base = clean(ecommerceBaseUrl);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.matches("https://[a-zA-Z0-9.-]+(?::\\d+)?")) return "";
        return base + "/productos/" + slug;
    }

    private boolean preventaActiva(Producto producto) {
        return producto != null
                && Boolean.TRUE.equals(producto.getPreventa())
                && producto.getFechaEnvioPreventa() != null
                && producto.getFechaEnvioPreventa().isAfter(LocalDate.now());
    }

    private BigDecimal money(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String imageKey(Integer productId, Integer colorId) {
        return productId + ":" + colorId;
    }

    private String limit(String value, int max) {
        String clean = clean(value);
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static Optional<ProductNameMatch> closestProductName(
            String query, List<EcommerceProductNameView> candidates) {
        ProductResolution resolution = resolveProductName(query, candidates);
        return resolution.unique() ? Optional.of(resolution.matches().getFirst()) : Optional.empty();
    }

    static ProductResolution resolveProductName(
            String query, List<EcommerceProductNameView> candidates) {
        String normalizedQuery = normalizeForMatch(query);
        if (normalizedQuery.isBlank() || candidates == null || candidates.isEmpty()) return ProductResolution.none();
        List<ProductNameMatch> exact = new ArrayList<>();
        for (EcommerceProductNameView candidate : candidates) {
            String productName = candidate == null ? "" : cleanStatic(candidate.getProductName());
            String normalizedName = normalizeForMatch(productName);
            if (normalizedName.isBlank()) continue;
            if (containsPhrase(normalizedQuery, normalizedName)) {
                exact.add(new ProductNameMatch(candidate.getProductId(), productName, 0));
            }
        }
        if (!exact.isEmpty()) {
            int longest = exact.stream().mapToInt(match -> normalizeForMatch(match.productName()).length()).max().orElse(0);
            List<ProductNameMatch> mostSpecific = exact.stream()
                    .filter(match -> normalizeForMatch(match.productName()).length() == longest)
                    .toList();
            return mostSpecific.size() == 1
                    ? ProductResolution.unique("EXACT", false, mostSpecific.getFirst())
                    : ProductResolution.ambiguous(mostSpecific);
        }

        String[] queryWords = normalizedQuery.split(" ");
        List<ProductNameMatch> matches = new ArrayList<>();
        for (EcommerceProductNameView candidate : candidates) {
            String productName = candidate == null ? "" : cleanStatic(candidate.getProductName());
            String normalizedName = normalizeForMatch(productName);
            if (normalizedName.length() < 5) continue;
            int distance = closestWindowDistance(queryWords, normalizedName);
            int maxDistance = normalizedName.length() >= 9 ? 2 : 1;
            if (distance <= maxDistance) {
                matches.add(new ProductNameMatch(candidate.getProductId(), productName, distance));
            }
        }
        matches.sort(Comparator.comparingInt(ProductNameMatch::distance)
                .thenComparing(ProductNameMatch::productName));
        if (!matches.isEmpty()) {
            int bestDistance = matches.getFirst().distance();
            List<ProductNameMatch> best = matches.stream()
                    .filter(match -> match.distance() == bestDistance)
                    .toList();
            return best.size() == 1
                    ? ProductResolution.unique("FUZZY", true, best.getFirst())
                    : ProductResolution.ambiguous(best);
        }

        List<ProductNameMatch> tokenMatches = new ArrayList<>();
        for (EcommerceProductNameView candidate : candidates) {
            String productName = candidate == null ? "" : cleanStatic(candidate.getProductName());
            String normalizedName = normalizeForMatch(productName);
            if (normalizedName.isBlank()) continue;
            int distance = Integer.MAX_VALUE;
            for (String productWord : normalizedName.split(" ")) {
                if (productWord.length() < 5) continue;
                distance = Math.min(distance, closestTokenDistance(queryWords, productWord));
            }
            if (distance <= 1) {
                tokenMatches.add(new ProductNameMatch(candidate.getProductId(), productName, distance));
            }
        }
        tokenMatches.sort(Comparator.comparingInt(ProductNameMatch::distance)
                .thenComparing(ProductNameMatch::productName));
        if (tokenMatches.isEmpty()) return ProductResolution.none();
        int bestDistance = tokenMatches.getFirst().distance();
        List<ProductNameMatch> best = tokenMatches.stream()
                .filter(match -> match.distance() == bestDistance)
                .toList();
        return best.size() == 1
                ? ProductResolution.unique("FUZZY", true, best.getFirst())
                : ProductResolution.ambiguous(best);
    }

    private static boolean containsPhrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private static int closestTokenDistance(String[] queryWords, String candidate) {
        int best = Integer.MAX_VALUE;
        for (String queryWord : queryWords) {
            if (queryWord.length() < 5) continue;
            best = Math.min(best, levenshtein(queryWord, candidate));
        }
        return best;
    }

    private static int closestWindowDistance(String[] queryWords, String normalizedName) {
        String[] nameWords = normalizedName.split(" ");
        if (queryWords.length < nameWords.length) return levenshtein(String.join(" ", queryWords), normalizedName);
        int best = Integer.MAX_VALUE;
        for (int start = 0; start <= queryWords.length - nameWords.length; start++) {
            StringBuilder window = new StringBuilder();
            for (int offset = 0; offset < nameWords.length; offset++) {
                if (!window.isEmpty()) window.append(' ');
                window.append(queryWords[start + offset]);
            }
            best = Math.min(best, levenshtein(window.toString(), normalizedName));
        }
        return best;
    }

    private static String normalizeForMatch(String value) {
        return Normalizer.normalize(cleanStatic(value).toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private static String cleanStatic(String value) {
        return value == null ? "" : value.trim();
    }

    public record CommercialContext(Sucursal branch, Cliente client) {}
    public record CatalogResult(Integer branchId, String branch, String query,
            String interpretedProduct, boolean corrected, String resolution,
            List<ProductCandidate> candidates, boolean hasMore, List<ProductResult> products) {
        public CatalogResult(Integer branchId, String branch, String query,
                String interpretedProduct, boolean corrected, List<ProductResult> products) {
            this(branchId, branch, query, interpretedProduct, corrected,
                    products != null && products.size() == 1 ? "EXACT" : "NONE", List.of(), false, products);
        }

        public CatalogResult(Integer branchId, String branch, String query,
                String interpretedProduct, boolean corrected, String resolution,
                List<ProductCandidate> candidates, List<ProductResult> products) {
            this(branchId, branch, query, interpretedProduct, corrected, resolution, candidates, false, products);
        }
    }
    public record ProductCandidate(Integer productId, String name) {}
    record ProductNameMatch(Integer productId, String productName, int distance) {}
    record ProductResolution(String status, boolean corrected, List<ProductNameMatch> matches) {
        static ProductResolution none() { return new ProductResolution("NONE", false, List.of()); }
        static ProductResolution unique(String status, boolean corrected, ProductNameMatch match) {
            return new ProductResolution(status, corrected, List.of(match));
        }
        static ProductResolution ambiguous(List<ProductNameMatch> matches) {
            return new ProductResolution("AMBIGUOUS", false, List.copyOf(matches));
        }
        boolean unique() { return matches.size() == 1 && !ambiguous(); }
        boolean ambiguous() { return "AMBIGUOUS".equals(status); }
    }
    public record ProductResult(Integer productId, String name, String category, String slug, String ecommerceUrl,
            boolean preventa, LocalDate fechaEnvioPreventa, String globalImageUrl, String globalThumbnailUrl,
            String sizeGuideUrl, String sizeGuideThumbnailUrl,
            List<String> availableColors, List<String> availableSizes, List<VariantResult> variants) {
        public ProductResult(Integer productId, String name, String category, String slug, String ecommerceUrl,
                boolean preventa, LocalDate fechaEnvioPreventa,
                String sizeGuideUrl, String sizeGuideThumbnailUrl,
                List<String> availableColors, List<String> availableSizes, List<VariantResult> variants) {
            this(productId, name, category, slug, ecommerceUrl, preventa, fechaEnvioPreventa, "", "",
                    sizeGuideUrl, sizeGuideThumbnailUrl, availableColors, availableSizes, variants);
        }

        public ProductResult(Integer productId, String name, String category,
                String sizeGuideUrl, String sizeGuideThumbnailUrl,
                List<String> availableColors, List<String> availableSizes, List<VariantResult> variants) {
            this(productId, name, category, "", "", false, null, "", "", sizeGuideUrl, sizeGuideThumbnailUrl,
                    availableColors, availableSizes, variants);
        }
    }
    public record VariantResult(Integer variantId, String sku, String barcode, String color, String size,
            Integer stock, boolean available, BigDecimal regularPrice, BigDecimal currentPrice,
            BigDecimal offerPrice, BigDecimal wholesalePrice, String wholesaleNotice,
            String imageUrl, String thumbnailUrl, boolean colorSpecificImage) {
        public VariantResult(Integer variantId, String sku, String barcode, String color, String size,
                Integer stock, boolean available, BigDecimal regularPrice, BigDecimal currentPrice,
                BigDecimal offerPrice, BigDecimal wholesalePrice, String wholesaleNotice,
                String imageUrl, String thumbnailUrl) {
            this(variantId, sku, barcode, color, size, stock, available, regularPrice, currentPrice,
                    offerPrice, wholesalePrice, wholesaleNotice, imageUrl, thumbnailUrl, true);
        }
    }
    public record CategoryResult(Integer branchId, String branch, List<CategoryItem> categories) {}
    public record CategoryItem(Integer categoryId, String name) {}
    public record BranchResult(Integer branchId, String name, String address, String city, String phone,
            boolean scheduleRequiresAdvisor, String scheduleMessage, List<BusinessHoursItem> businessHours) {}
    public record BusinessHoursItem(String day, boolean closed, String opensAt, String closesAt) {}
    public record PaymentResult(Integer branchId, String branch, List<PaymentMethodItem> methods, String warning) {}
    public record PaymentMethodItem(Integer paymentMethodId, String name, String description,
            boolean requiresOperationCode, boolean requiresPaymentDate, boolean requiresPaymentTime,
            List<String> accounts) {}
    public record PromotionCatalogResult(Integer branchId, String query, List<PromotionResult> promotions) {}
    public record PromotionResult(Integer promotionId, String name, String rule, BigDecimal comboPrice,
            BigDecimal regularPrice, BigDecimal savings, List<PromotionProductResult> products) {}
    public record PromotionProductResult(Integer productId, String name, Integer quantity,
            String imageUrl, String thumbnailUrl) {}
    public record OfferCatalogResult(Integer branchId, String query, List<OfferProductResult> products) {}
    public record OfferProductResult(Integer productId, String name, List<OfferVariantResult> variants) {}
    public record OfferVariantResult(Integer variantId, String color, String size, BigDecimal regularPrice,
            BigDecimal offerPrice, String imageUrl) {}
    public record ClientResult(boolean linked, String name, String message) {}
    public record SalesResult(Integer branchId, boolean clientLinked, List<SaleItem> sales, String message) {}
    public record SaleItem(Integer saleId, java.time.LocalDateTime date, String documentType, String receipt,
            String currency, BigDecimal total, String status) {}
    public record ProductImage(String url, String thumbnailUrl) {}

    private record ProductBuilder(Integer productId, String name, String category, String slug, String ecommerceUrl,
            boolean preventa, LocalDate fechaEnvioPreventa,
            String globalImageUrl, String globalThumbUrl, String sizeGuideUrl, String sizeGuideThumbnailUrl,
            List<String> availableColors,
            List<String> availableSizes, List<VariantResult> variants) {
        ProductBuilder(Integer productId, String name, String category, String slug, String ecommerceUrl,
                boolean preventa, LocalDate fechaEnvioPreventa,
                String globalImageUrl, String globalThumbUrl,
                String sizeGuideUrl, String sizeGuideThumbnailUrl,
                List<String> availableColors, List<String> availableSizes) {
            this(productId, name, category, slug, ecommerceUrl, preventa, fechaEnvioPreventa,
                    globalImageUrl, globalThumbUrl, sizeGuideUrl, sizeGuideThumbnailUrl,
                    availableColors, availableSizes, new ArrayList<>());
        }

        ProductResult build() {
            return new ProductResult(productId, name, category, slug, ecommerceUrl, preventa, fechaEnvioPreventa,
                    globalImageUrl, globalThumbUrl, sizeGuideUrl, sizeGuideThumbnailUrl,
                    List.copyOf(availableColors), List.copyOf(availableSizes), List.copyOf(variants));
        }
    }

    private record AvailabilitySummary(List<String> colors, List<String> sizes) {
        static AvailabilitySummary empty() {
            return new AvailabilitySummary(List.of(), List.of());
        }
    }
}
