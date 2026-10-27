package com.sistemapos.sistematextil.services.agent.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.repositories.AgentCatalogReadRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.EcommercePromocionComboRepository;
import com.sistemapos.sistematextil.services.PrecioOfertaService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartPromotionCalculator.AppliedPromotion;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartPromotionCalculator.Calculation;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartPromotionCalculator.PricedLine;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartItemRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.OperationRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.PendingPromotionRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.PricedCartItem;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.PromotionApplicationRow;

@Service
public class AgentCartService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    public static final Duration CONFIRMED_CART_WINDOW = Duration.ofHours(1);

    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentCatalogReadRepository catalogRepository;
    private final EcommercePromocionComboRepository promotionRepository;
    private final PrecioOfertaService priceService;
    private final AgentCartPromotionCalculator promotionCalculator;
    private final AgentCartStore store;
    private final ObjectMapper objectMapper;

    @Autowired
    public AgentCartService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentCatalogReadRepository catalogRepository,
            EcommercePromocionComboRepository promotionRepository,
            PrecioOfertaService priceService,
            AgentCartPromotionCalculator promotionCalculator,
            AgentCartStore store) {
        this(sessionRepository, stateMachine, catalogRepository, promotionRepository, priceService,
                promotionCalculator, store, new ObjectMapper().findAndRegisterModules());
    }

    AgentCartService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentCatalogReadRepository catalogRepository,
            EcommercePromocionComboRepository promotionRepository,
            PrecioOfertaService priceService,
            AgentCartPromotionCalculator promotionCalculator,
            AgentCartStore store,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.stateMachine = stateMachine;
        this.catalogRepository = catalogRepository;
        this.promotionRepository = promotionRepository;
        this.priceService = priceService;
        this.promotionCalculator = promotionCalculator;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CartResult addProduct(Long sessionId, Integer variantId, Integer quantity, String operationId) {
        int safeQuantity = requirePositiveQuantity(quantity);
        return execute(sessionId, operationId, "agregar_producto_carrito",
                variantId + ":" + safeQuantity,
                () -> {
                    LockedSession scope = lockEditableSession(sessionId);
                    CartRow cart = requireOrCreateDraftCart(sessionId);
                    SucursalStock stock = requireVariant(scope.branchId(), variantId);
                    int current = store.findItems(cart.id()).stream()
                            .filter(item -> Objects.equals(item.variantId(), variantId))
                            .mapToInt(CartItemRow::quantity).findFirst().orElse(0);
                    int target = Math.addExact(current, safeQuantity);
                    validateStock(stock, target);
                    store.upsertItem(cart.id(), price(stock, target, scope.branchId()), LocalDateTime.now());
                    store.deletePendingPromotion(sessionId);
                    markCartUpdated(scope.session());
                    return recalculateLocked(scope, cart, "DRAFT");
                });
    }

    @Transactional
    public CartResult addCombo(Long sessionId, Integer promotionId, List<ComboSelection> selections,
            String operationId) {
        List<ComboSelection> normalizedSelections = selections == null ? List.of() : selections.stream()
                .filter(Objects::nonNull)
                .map(selection -> new ComboSelection(selection.variantId(), requirePositiveQuantity(selection.quantity())))
                .toList();
        Map<Integer, Integer> quantitiesByVariant = new LinkedHashMap<>();
        normalizedSelections.forEach(selection -> {
            if (selection.variantId() == null) throw new IllegalArgumentException("La variante es obligatoria");
            quantitiesByVariant.merge(selection.variantId(), selection.quantity(), Math::addExact);
        });
        List<ComboSelection> safeSelections = quantitiesByVariant.entrySet().stream()
                .map(entry -> new ComboSelection(entry.getKey(), entry.getValue())).toList();
        return execute(sessionId, operationId, "agregar_combo_carrito",
                promotionId + ":" + safeSelections,
                () -> {
                    if (promotionId == null) throw new IllegalArgumentException("La promocion es obligatoria");
                    if (safeSelections.isEmpty()) {
                        throw new IllegalArgumentException("Debes seleccionar color y talla para cada producto del combo");
                    }
                    LockedSession scope = lockEditableSession(sessionId);
                    CartRow cart = requireOrCreateDraftCart(sessionId);
                    Map<Integer, List<SucursalStock>> catalog = catalogByProduct(scope.branchId());
                    EcommercePromocionCombo promotion = promotionRepository.listarActivas(LocalDateTime.now()).stream()
                            .filter(candidate -> Objects.equals(candidate.getIdEcommercePromocionCombo(), promotionId))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("La promocion no esta vigente"));
                    Map<Integer, Integer> required = promotion.getItems().stream().collect(Collectors.toMap(
                            item -> item.getProducto().getIdProducto(), item -> item.getCantidadRequerida(),
                            Integer::sum, LinkedHashMap::new));

                    Map<Integer, SucursalStock> selectedVariants = new LinkedHashMap<>();
                    Map<Integer, Integer> selectedProducts = new LinkedHashMap<>();
                    for (ComboSelection selection : safeSelections) {
                        SucursalStock stock = requireVariant(scope.branchId(), selection.variantId());
                        selectedVariants.put(selection.variantId(), stock);
                        selectedProducts.merge(stock.getProductoVariante().getProducto().getIdProducto(),
                                selection.quantity(), Integer::sum);
                    }
                    if (!required.equals(selectedProducts)) {
                        throw new IllegalArgumentException("Las variantes elegidas no cumplen los productos y cantidades del combo");
                    }
                    boolean promotionAvailable = required.entrySet().stream().allMatch(entry ->
                            catalog.getOrDefault(entry.getKey(), List.of()).stream().mapToInt(this::safeStock).sum()
                                    >= entry.getValue());
                    if (!promotionAvailable) {
                        throw new IllegalArgumentException("La promocion no tiene stock suficiente en esta sucursal");
                    }

                    CartResult before = recalculateLocked(scope, cart, "DRAFT");
                    int previousApplications = (int) before.promotions().stream()
                            .filter(item -> Objects.equals(item.promotionId(), promotionId))
                            .count();
                    Map<Integer, Integer> current = store.findItems(cart.id()).stream()
                            .collect(Collectors.toMap(CartItemRow::variantId, CartItemRow::quantity));
                    Map<Integer, Integer> allocatedByProduct = allocatedPromotionQuantities(
                            before.promotions(), promotionRepository.listarActivas(LocalDateTime.now()));
                    for (ComboSelection selection : safeSelections) {
                        SucursalStock stock = selectedVariants.get(selection.variantId());
                        Integer productId = stock.getProductoVariante().getProducto().getIdProducto();
                        int currentQuantity = current.getOrDefault(selection.variantId(), 0);
                        int allocated = allocatedByProduct.getOrDefault(productId, 0);
                        int allocatedFromVariant = Math.min(currentQuantity, allocated);
                        allocatedByProduct.put(productId, allocated - allocatedFromVariant);
                        int reusable = Math.min(selection.quantity(), currentQuantity - allocatedFromVariant);
                        int quantityToAdd = selection.quantity() - reusable;
                        if (quantityToAdd == 0) continue;
                        int target = Math.addExact(currentQuantity, quantityToAdd);
                        validateStock(stock, target);
                        store.upsertItem(cart.id(), price(stock, target, scope.branchId()), LocalDateTime.now());
                    }
                    markCartUpdated(scope.session());
                    CartResult result = recalculateLocked(scope, cart, "DRAFT");
                    long resultingApplications = result.promotions().stream()
                            .filter(item -> Objects.equals(item.promotionId(), promotionId))
                            .count();
                    if (resultingApplications <= previousApplications) {
                        throw new IllegalStateException(
                                "La promocion seleccionada no pudo aplicarse al carrito");
                    }
                    store.deletePendingPromotion(sessionId);
                    return result;
                });
    }

    @Transactional(readOnly = true)
    public Optional<PendingPromotionDraft> pendingPromotion(Long sessionId) {
        return store.findPendingPromotion(sessionId).map(this::readPendingPromotion);
    }

    @Transactional
    public void savePendingPromotion(Long sessionId, Integer promotionId, String promotionName,
            List<PendingPromotionSelection> selections, Long sourceJobId) {
        if (sessionId == null || promotionId == null) {
            throw new IllegalArgumentException("La sesion y la promocion pendiente son obligatorias");
        }
        store.upsertPendingPromotion(sessionId, promotionId,
                promotionName == null ? "" : promotionName,
                writePendingSelections(selections), sourceJobId, LocalDateTime.now());
    }

    @Transactional
    public void clearPendingPromotion(Long sessionId) {
        if (sessionId != null) store.deletePendingPromotion(sessionId);
    }

    @Transactional
    public CartResult replaceCart(Long sessionId, List<ComboSelection> selections,
            Integer expectedPromotionId, String operationId) {
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        if (selections != null) {
            for (ComboSelection selection : selections) {
                if (selection == null || selection.variantId() == null) {
                    throw new IllegalArgumentException("Todas las variantes del pedido son obligatorias");
                }
                quantities.merge(selection.variantId(), requirePositiveQuantity(selection.quantity()), Math::addExact);
            }
        }
        if (quantities.isEmpty()) throw new IllegalArgumentException("El pedido corregido no puede quedar vacio");
        return execute(sessionId, operationId, "reemplazar_carrito",
                expectedPromotionId + ":" + quantities, () -> {
                    LockedSession scope = lockEditableSession(sessionId);
                    CartRow cart = requireOrCreateDraftCart(sessionId);
                    Map<Integer, SucursalStock> validated = new LinkedHashMap<>();
                    for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
                        SucursalStock stock = requireVariant(scope.branchId(), entry.getKey());
                        validateStock(stock, entry.getValue());
                        validated.put(entry.getKey(), stock);
                    }
                    // All lines are validated before the existing draft is touched.
                    store.clearCart(cart.id());
                    LocalDateTime now = LocalDateTime.now();
                    for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
                        store.upsertItem(cart.id(),
                                price(validated.get(entry.getKey()), entry.getValue(), scope.branchId()), now);
                    }
                    CartResult result = recalculateLocked(scope, cart, "DRAFT");
                    if (expectedPromotionId != null && result.promotions().stream()
                            .noneMatch(item -> Objects.equals(item.promotionId(), expectedPromotionId))) {
                        throw new IllegalArgumentException("Los productos elegidos no completan la promocion indicada");
                    }
                    store.deletePendingPromotion(sessionId);
                    markCartUpdated(scope.session());
                    return result;
                });
    }

    private Map<Integer, Integer> allocatedPromotionQuantities(List<CartPromotionView> applied,
            List<EcommercePromocionCombo> activePromotions) {
        Map<Integer, EcommercePromocionCombo> byId = activePromotions.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(EcommercePromocionCombo::getIdEcommercePromocionCombo,
                        promotion -> promotion, (first, ignored) -> first));
        Map<Integer, Integer> allocated = new LinkedHashMap<>();
        for (CartPromotionView application : applied) {
            EcommercePromocionCombo promotion = byId.get(application.promotionId());
            if (promotion == null || promotion.getItems() == null) continue;
            promotion.getItems().forEach(item -> {
                if (item.getProducto() != null && item.getProducto().getIdProducto() != null
                        && item.getCantidadRequerida() != null && item.getCantidadRequerida() > 0) {
                    allocated.merge(item.getProducto().getIdProducto(), item.getCantidadRequerida(), Integer::sum);
                }
            });
        }
        return allocated;
    }

    @Transactional
    public CartResult changeQuantity(Long sessionId, Integer variantId, Integer quantity, String operationId) {
        int safeQuantity = requirePositiveQuantity(quantity);
        return execute(sessionId, operationId, "cambiar_cantidad_carrito", variantId + ":" + safeQuantity,
                () -> {
                    LockedSession scope = lockEditableSession(sessionId);
                    CartRow cart = requireExistingDraftCart(sessionId);
                    boolean exists = store.findItems(cart.id()).stream()
                            .anyMatch(item -> Objects.equals(item.variantId(), variantId));
                    if (!exists) throw new IllegalArgumentException("La variante no existe en el carrito");
                    SucursalStock stock = requireVariant(scope.branchId(), variantId);
                    validateStock(stock, safeQuantity);
                    store.upsertItem(cart.id(), price(stock, safeQuantity, scope.branchId()), LocalDateTime.now());
                    store.deletePendingPromotion(sessionId);
                    markCartUpdated(scope.session());
                    return recalculateLocked(scope, cart, "DRAFT");
                });
    }

    @Transactional
    public CartResult changeVariant(Long sessionId, Integer currentVariantId, Integer newVariantId,
            String operationId) {
        if (currentVariantId == null || newVariantId == null) {
            throw new IllegalArgumentException("Las variantes actual y nueva son obligatorias");
        }
        if (Objects.equals(currentVariantId, newVariantId)) {
            throw new IllegalArgumentException("La nueva talla o color debe ser diferente");
        }
        return execute(sessionId, operationId, "cambiar_variante_carrito",
                currentVariantId + ":" + newVariantId,
                () -> {
                    LockedSession scope = lockEditableSession(sessionId);
                    CartRow cart = requireExistingDraftCart(sessionId);
                    List<CartItemRow> items = store.findItems(cart.id());
                    CartItemRow current = items.stream()
                            .filter(item -> Objects.equals(item.variantId(), currentVariantId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("La variante actual no existe en el carrito"));
                    SucursalStock currentStock = requireVariant(scope.branchId(), currentVariantId);
                    SucursalStock replacementStock = requireVariant(scope.branchId(), newVariantId);
                    Integer currentProductId = currentStock.getProductoVariante().getProducto().getIdProducto();
                    Integer replacementProductId = replacementStock.getProductoVariante().getProducto().getIdProducto();
                    if (!Objects.equals(currentProductId, replacementProductId)) {
                        throw new IllegalArgumentException("La talla o color nuevo debe pertenecer al mismo producto");
                    }
                    int existingReplacementQuantity = items.stream()
                            .filter(item -> Objects.equals(item.variantId(), newVariantId))
                            .mapToInt(CartItemRow::quantity)
                            .findFirst()
                            .orElse(0);
                    int targetQuantity = Math.addExact(existingReplacementQuantity, current.quantity());
                    validateStock(replacementStock, targetQuantity);
                    if (store.deleteItem(cart.id(), currentVariantId) != 1) {
                        throw new IllegalStateException("No se pudo reemplazar la variante actual");
                    }
                    store.upsertItem(cart.id(), price(replacementStock, targetQuantity, scope.branchId()),
                            LocalDateTime.now());
                    store.deletePendingPromotion(sessionId);
                    markCartUpdated(scope.session());
                    return recalculateLocked(scope, cart, "DRAFT");
                });
    }

    @Transactional
    public CartResult replaceProduct(Long sessionId, Integer currentVariantId, Integer newVariantId,
            String operationId) {
        if (currentVariantId == null || newVariantId == null) {
            throw new IllegalArgumentException("Las variantes actual y nueva son obligatorias");
        }
        if (Objects.equals(currentVariantId, newVariantId)) {
            throw new IllegalArgumentException("El nuevo producto debe ser diferente");
        }
        return execute(sessionId, operationId, "reemplazar_producto_carrito",
                currentVariantId + ":" + newVariantId,
                () -> {
                    LockedSession scope = lockEditableSession(sessionId);
                    CartRow cart = requireExistingDraftCart(sessionId);
                    List<CartItemRow> items = store.findItems(cart.id());
                    CartItemRow current = items.stream()
                            .filter(item -> Objects.equals(item.variantId(), currentVariantId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("El producto actual no existe en el carrito"));
                    SucursalStock currentStock = requireVariant(scope.branchId(), currentVariantId);
                    SucursalStock replacementStock = requireVariant(scope.branchId(), newVariantId);
                    Integer currentProductId = currentStock.getProductoVariante().getProducto().getIdProducto();
                    Integer replacementProductId = replacementStock.getProductoVariante().getProducto().getIdProducto();
                    if (Objects.equals(currentProductId, replacementProductId)) {
                        throw new IllegalArgumentException("Para cambiar solo color o talla usa cambiar_variante_carrito");
                    }
                    int existingReplacementQuantity = items.stream()
                            .filter(item -> Objects.equals(item.variantId(), newVariantId))
                            .mapToInt(CartItemRow::quantity)
                            .findFirst()
                            .orElse(0);
                    int targetQuantity = Math.addExact(existingReplacementQuantity, current.quantity());
                    validateStock(replacementStock, targetQuantity);
                    if (store.deleteItem(cart.id(), currentVariantId) != 1) {
                        throw new IllegalStateException("No se pudo reemplazar el producto actual");
                    }
                    store.upsertItem(cart.id(), price(replacementStock, targetQuantity, scope.branchId()),
                            LocalDateTime.now());
                    store.deletePendingPromotion(sessionId);
                    markCartUpdated(scope.session());
                    return recalculateLocked(scope, cart, "DRAFT");
                });
    }

    @Transactional
    public CartResult removeProduct(Long sessionId, Integer variantId, String operationId) {
        return execute(sessionId, operationId, "eliminar_producto_carrito", String.valueOf(variantId), () -> {
            LockedSession scope = lockEditableSession(sessionId);
            CartRow cart = requireExistingDraftCart(sessionId);
            if (variantId == null || store.deleteItem(cart.id(), variantId) != 1) {
                throw new IllegalArgumentException("La variante no existe en el carrito");
            }
            store.deletePendingPromotion(sessionId);
            CartResult result = recalculateLocked(scope, cart, "DRAFT");
            if (result.items().isEmpty()) markCartEmpty(scope.session());
            else markCartUpdated(scope.session());
            return result;
        });
    }

    @Transactional
    public CartResult keepOnlyProduct(Long sessionId, Integer variantId, Integer quantity, String operationId) {
        if (variantId == null) throw new IllegalArgumentException("La variante que se conservara es obligatoria");
        return execute(sessionId, operationId, "conservar_solo_producto_carrito",
                variantId + ":" + String.valueOf(quantity), () -> {
                    LockedSession scope = lockEditableSession(sessionId);
                    CartRow cart = requireExistingDraftCart(sessionId);
                    List<CartItemRow> items = store.findItems(cart.id());
                    CartItemRow selected = items.stream()
                            .filter(item -> Objects.equals(item.variantId(), variantId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("El producto no existe en el carrito"));
                    for (CartItemRow item : items) {
                        if (!Objects.equals(item.variantId(), variantId)) {
                            store.deleteItem(cart.id(), item.variantId());
                        }
                    }
                    int targetQuantity = quantity == null || quantity < 1 ? selected.quantity() : quantity;
                    SucursalStock selectedStock = requireVariant(scope.branchId(), variantId);
                    validateStock(selectedStock, targetQuantity);
                    store.upsertItem(cart.id(), price(selectedStock, targetQuantity, scope.branchId()),
                            LocalDateTime.now());
                    store.deletePendingPromotion(sessionId);
                    markCartUpdated(scope.session());
                    return recalculateLocked(scope, cart, "DRAFT");
                });
    }

    @Transactional
    public CartResult emptyCart(Long sessionId, String operationId) {
        return execute(sessionId, operationId, "vaciar_carrito", "empty", () -> {
            LockedSession scope = lockEditableSession(sessionId);
            Optional<CartRow> cart = store.lockActiveCart(sessionId);
            if (cart.isEmpty()) {
                store.deletePendingPromotion(sessionId);
                return emptyResult();
            }
            store.clearCart(cart.get().id());
            store.deletePendingPromotion(sessionId);
            store.updateTotals(cart.get().id(), ZERO, ZERO, ZERO, LocalDateTime.now());
            markCartEmpty(scope.session());
            return new CartResult(cart.get().id(), "DRAFT", "PEN", ZERO, ZERO, ZERO, List.of(), List.of());
        });
    }

    @Transactional
    public CartResult recalculate(Long sessionId, String operationId) {
        return execute(sessionId, operationId, "recalcular_carrito", "recalculate", () -> {
            LockedSession scope = lockEditableSession(sessionId);
            Optional<CartRow> cart = store.lockActiveCart(sessionId);
            return cart.isEmpty() ? emptyResult() : recalculateLocked(scope, cart.get(), "DRAFT");
        });
    }

    @Transactional(readOnly = true)
    public CartTotals currentTotals(Long sessionId) {
        return store.findActiveCart(sessionId)
                .map(cart -> new CartTotals(cart.status(), cart.currency(), cart.subtotal(),
                        cart.discountTotal(), cart.total()))
                .orElseGet(CartTotals::emptyTotals);
    }

    @Transactional
    public CartResult confirm(Long sessionId, String operationId) {
        return execute(sessionId, operationId, "confirmar_carrito", "confirm", () -> {
            LockedSession scope = lockEditableSession(sessionId);
            CartRow cart = requireExistingDraftCart(sessionId);
            CartResult recalculated = recalculateLocked(scope, cart, "DRAFT");
            if (recalculated.items().isEmpty()) throw new IllegalStateException("No se puede confirmar un carrito vacio");
            if (scope.session().getState() != CrmWhatsappAgentState.ARMANDO_CARRITO) {
                throw new IllegalStateException("La sesion no esta lista para confirmar el carrito");
            }
            LocalDateTime now = LocalDateTime.now();
            store.confirm(cart.id(), now);
            store.deletePendingPromotion(sessionId);
            applyEvent(scope.session(), CrmWhatsappAgentEvent.CARRITO_CONFIRMADO, now);
            return new CartResult(recalculated.cartId(), "CONFIRMED", recalculated.currency(),
                    recalculated.subtotal(), recalculated.discountTotal(), recalculated.total(),
                    recalculated.items(), recalculated.promotions());
        });
    }

    /**
     * Clears a confirmed but unpaid cart after one hour. A later customer message
     * must start from a clean shopping context instead of inheriting an abandoned
     * order.
     */
    @Transactional
    public ExpiredCartRefresh refreshExpiredConfirmedCart(Long sessionId) {
        CrmWhatsappAgentSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("La sesion del agente no existe"));
        if (!Boolean.TRUE.equals(session.getActiveSlot())
                || session.getState() != CrmWhatsappAgentState.ESPERANDO_METODO_PAGO) {
            return ExpiredCartRefresh.notRefreshed();
        }
        CartRow cart = store.lockActiveCart(sessionId).orElse(null);
        if (cart == null || !"CONFIRMED".equals(cart.status()) || cart.confirmedAt() == null) {
            return ExpiredCartRefresh.notRefreshed();
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(cart.confirmedAt().plus(CONFIRMED_CART_WINDOW))) {
            return ExpiredCartRefresh.notRefreshed();
        }
        List<CartItemRow> previous = List.copyOf(store.findItems(cart.id()));
        applyEvent(session, CrmWhatsappAgentEvent.CARRITO_REVALIDADO_POR_VENCIMIENTO, now);
        store.reopen(cart.id(), now);
        store.clearCart(cart.id());
        store.replacePromotions(cart.id(), List.of(), now);
        store.deletePendingPromotion(sessionId);
        store.updateTotals(cart.id(), ZERO, ZERO, ZERO, now);
        markCartEmpty(session);
        CartResult cleared = new CartResult(cart.id(), "DRAFT", cart.currency(), ZERO, ZERO, ZERO,
                List.of(), List.of());
        return new ExpiredCartRefresh(true, cleared,
                previous.stream().map(this::cartItemView).toList(), cart.confirmedAt(), now);
    }

    /** Cancels an unpaid draft/confirmed cart. There is no stock reservation to release. */
    @Transactional
    public boolean cancelPendingCart(Long sessionId) {
        CrmWhatsappAgentSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("La sesion del agente no existe"));
        if (session.getState() == CrmWhatsappAgentState.CANCELADO) return false;
        if (session.getState() != CrmWhatsappAgentState.ESPERANDO_METODO_PAGO
                && session.getState() != CrmWhatsappAgentState.ARMANDO_CARRITO) {
            throw new IllegalStateException("No existe un carrito pendiente que pueda cancelarse");
        }
        LocalDateTime now = LocalDateTime.now();
        Optional<CartRow> active = store.lockActiveCart(sessionId);
        if (active.isPresent()) {
            CartRow cart = active.get();
            if ("CONFIRMED".equals(cart.status())) store.reopen(cart.id(), now);
            store.clearCart(cart.id());
            store.replacePromotions(cart.id(), List.of(), now);
            store.deletePendingPromotion(sessionId);
            store.updateTotals(cart.id(), ZERO, ZERO, ZERO, now);
        }
        applyEvent(session, CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, now);
        session.setSelectedPaymentMethod(null);
        session.setSelectedPaymentAccount(null);
        sessionRepository.save(session);
        return true;
    }

    private Integer requireBranchId(CrmWhatsappAgentSession session) {
        if (session.getConnection() == null || session.getConnection().getSucursal() == null
                || session.getConnection().getSucursal().getIdSucursal() == null) {
            throw new IllegalStateException("La conexion WhatsApp no tiene una sucursal configurada");
        }
        return session.getConnection().getSucursal().getIdSucursal();
    }

    private CartItemView cartItemView(CartItemRow item) {
        return new CartItemView(item.id(), item.variantId(), item.productName(), item.color(), item.size(),
                item.quantity(), item.unitPrice(), item.unitDiscount(), item.lineTotal());
    }

    private CartResult recalculateLocked(LockedSession scope, CartRow cart, String status) {
        List<CartItemRow> existing = store.findItems(cart.id());
        if (existing.isEmpty()) {
            store.replacePromotions(cart.id(), List.of(), LocalDateTime.now());
            store.updateTotals(cart.id(), ZERO, ZERO, ZERO, LocalDateTime.now());
            return new CartResult(cart.id(), status, cart.currency(), ZERO, ZERO, ZERO, List.of(), List.of());
        }
        List<Integer> variantIds = existing.stream().map(CartItemRow::variantId).distinct().toList();
        Map<Integer, SucursalStock> stocks = catalogRepository.findVariantsByBranch(scope.branchId(), variantIds)
                .stream().collect(Collectors.toMap(row -> row.getProductoVariante().getIdProductoVariante(),
                        row -> row, (first, ignored) -> first));
        List<PricedLine> pricedLines = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (CartItemRow line : existing) {
            SucursalStock stock = stocks.get(line.variantId());
            // A draft cart may survive for days while real stock keeps moving. A
            // stale line must not prevent a new, valid selection from being added.
            // The customer will see the recalculated cart without that unavailable
            // line and can choose a replacement normally.
            if (stock == null || safeStock(stock) < line.quantity()) {
                store.deleteItem(cart.id(), line.variantId());
                continue;
            }
            PricedCartItem priced = price(stock, line.quantity(), scope.branchId());
            store.upsertItem(cart.id(), priced, now);
            pricedLines.add(new PricedLine(priced.variantId(),
                    stock.getProductoVariante().getProducto().getIdProducto(), priced.quantity(), priced.unitPrice()));
        }

        if (pricedLines.isEmpty()) {
            store.replacePromotions(cart.id(), List.of(), now);
            store.updateTotals(cart.id(), ZERO, ZERO, ZERO, now);
            return new CartResult(cart.id(), status, cart.currency(), ZERO, ZERO, ZERO, List.of(), List.of());
        }

        Map<Integer, Integer> stockByProduct = catalogRepository.findCatalogByBranch(scope.branchId()).stream()
                .collect(Collectors.groupingBy(row -> row.getProductoVariante().getProducto().getIdProducto(),
                        Collectors.summingInt(this::safeStock)));
        Calculation calculation = promotionCalculator.calculate(pricedLines,
                promotionRepository.listarActivas(now), stockByProduct);
        List<PromotionApplicationRow> promotionRows = calculation.promotions().stream()
                .map(application -> promotionRow(cart.id(), application)).toList();
        store.replacePromotions(cart.id(), promotionRows, now);
        store.updateTotals(cart.id(), calculation.subtotal(), calculation.discount(), calculation.total(), now);

        Map<Integer, CartItemRow> persisted = store.findItems(cart.id()).stream()
                .collect(Collectors.toMap(CartItemRow::variantId, item -> item));
        List<CartItemView> items = pricedLines.stream().map(line -> {
            CartItemRow item = persisted.get(line.variantId());
            return new CartItemView(item.id(), item.variantId(), item.productName(), item.color(), item.size(),
                    item.quantity(), item.unitPrice(), item.unitDiscount(), item.lineTotal());
        }).toList();
        List<CartPromotionView> promotions = calculation.promotions().stream()
                .map(application -> new CartPromotionView(application.promotionId(), application.name(),
                        application.applicationOrder(), application.regularPrice(), application.comboPrice(),
                        application.discount())).toList();
        return new CartResult(cart.id(), status, cart.currency(), calculation.subtotal(), calculation.discount(),
                calculation.total(), items, promotions);
    }

    private PromotionApplicationRow promotionRow(Long cartId, AppliedPromotion application) {
        String source = cartId + ":" + application.promotionId() + ":" + application.applicationOrder()
                + ":" + application.unitIndexes();
        return new PromotionApplicationRow(application.promotionId(), application.applicationOrder(),
                application.name(), application.comboPrice(), application.discount(), sha256(source));
    }

    private PricedCartItem price(SucursalStock stock, int quantity, Integer branchId) {
        ProductoVariante variant = stock.getProductoVariante();
        Double resolved = priceService.resolverPrecioVigente(variant, branchId);
        if (resolved == null || resolved < 0) throw new IllegalStateException("La variante no tiene un precio valido");
        BigDecimal unitPrice = BigDecimal.valueOf(resolved).setScale(2, RoundingMode.HALF_UP);
        BigDecimal regular = variant.getPrecio() == null ? unitPrice
                : BigDecimal.valueOf(variant.getPrecio()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitDiscount = regular.subtract(unitPrice).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        return new PricedCartItem(variant.getIdProductoVariante(), quantity, variant.getProducto().getNombre(),
                variant.getColor() == null ? "" : variant.getColor().getNombre(),
                variant.getTalla() == null ? "" : variant.getTalla().getNombre(), unitPrice, unitDiscount,
                unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
    }

    private LockedSession lockEditableSession(Long sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("La sesion del agente es obligatoria");
        CrmWhatsappAgentSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("La sesion del agente no existe"));
        if (!Boolean.TRUE.equals(session.getActiveSlot())) throw new IllegalStateException("La sesion ya no esta activa");
        if (session.getConnection() == null || session.getConnection().getSucursal() == null
                || session.getConnection().getSucursal().getIdSucursal() == null) {
            throw new IllegalStateException("La conexion WhatsApp no tiene una sucursal configurada");
        }
        CrmWhatsappAgentState state = session.getState();
        if (state != CrmWhatsappAgentState.IDLE && state != CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS
                && state != CrmWhatsappAgentState.ARMANDO_CARRITO) {
            throw new IllegalStateException("El carrito no puede modificarse en el estado " + state);
        }
        return new LockedSession(session, session.getConnection().getSucursal().getIdSucursal());
    }

    private CartRow requireOrCreateDraftCart(Long sessionId) {
        Optional<CartRow> active = store.lockActiveCart(sessionId);
        if (active.isPresent()) return requireDraft(active.get());
        Optional<CartRow> latest = store.findLatestCart(sessionId);
        if (latest.isPresent()) throw new IllegalStateException("La sesion ya tiene un carrito cerrado");
        return store.createCart(sessionId, "agent-cart-session-" + sessionId, LocalDateTime.now());
    }

    private CartRow requireExistingDraftCart(Long sessionId) {
        return requireDraft(store.lockActiveCart(sessionId)
                .orElseThrow(() -> new IllegalStateException("No existe un carrito activo")));
    }

    private CartRow requireDraft(CartRow cart) {
        if (!"DRAFT".equals(cart.status())) throw new IllegalStateException("El carrito ya no se puede modificar");
        return cart;
    }

    private SucursalStock requireVariant(Integer branchId, Integer variantId) {
        if (variantId == null) throw new IllegalArgumentException("La variante es obligatoria");
        return catalogRepository.findVariantByBranch(branchId, variantId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "La variante no pertenece al catalogo de la sucursal de esta conversacion"));
    }

    private Map<Integer, List<SucursalStock>> catalogByProduct(Integer branchId) {
        return catalogRepository.findCatalogByBranch(branchId).stream()
                .collect(Collectors.groupingBy(row -> row.getProductoVariante().getProducto().getIdProducto()));
    }

    private void validateStock(SucursalStock stock, int quantity) {
        if (safeStock(stock) < quantity) {
            throw new IllegalArgumentException("Stock insuficiente; disponible: " + safeStock(stock));
        }
    }

    private int safeStock(SucursalStock stock) {
        return stock.getCantidad() == null ? 0 : Math.max(0, stock.getCantidad());
    }

    private int requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        return quantity;
    }

    private void markCartUpdated(CrmWhatsappAgentSession session) {
        LocalDateTime now = LocalDateTime.now();
        if (session.getState() == CrmWhatsappAgentState.IDLE) {
            applyEvent(session, CrmWhatsappAgentEvent.CONVERSACION_INICIADA, now);
        }
        applyEvent(session, CrmWhatsappAgentEvent.CARRITO_ACTUALIZADO, now);
    }

    private void markCartEmpty(CrmWhatsappAgentSession session) {
        if (session.getState() == CrmWhatsappAgentState.ARMANDO_CARRITO) {
            applyEvent(session, CrmWhatsappAgentEvent.CARRITO_VACIADO, LocalDateTime.now());
        }
    }

    private void applyEvent(CrmWhatsappAgentSession session, CrmWhatsappAgentEvent event, LocalDateTime now) {
        session.setState(stateMachine.transition(session.getState(), event));
        session.setLastEvent(event);
        session.setStateChangedAt(now);
        session.setLastActivityAt(now);
        sessionRepository.save(session);
    }

    private CartResult execute(Long sessionId, String operationId, String toolName, String request,
            Supplier<CartResult> action) {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");
        }
        String key = sha256("cart:" + sessionId + ":" + operationId.trim());
        String fingerprint = sha256(toolName + ":" + request);
        Optional<OperationRow> existing = store.findOperation(key);
        if (existing.isPresent()) return replay(existing.get(), toolName, fingerprint);
        LocalDateTime now = LocalDateTime.now();
        if (!store.claimOperation(sessionId, key, toolName, fingerprint, now)) {
            OperationRow concurrent = store.findOperation(key)
                    .orElseThrow(() -> new IllegalStateException("La operacion del carrito esta en curso"));
            return replay(concurrent, toolName, fingerprint);
        }
        CartResult result = action.get();
        store.completeOperation(key, write(result), LocalDateTime.now());
        return result;
    }

    private CartResult replay(OperationRow operation, String toolName, String fingerprint) {
        if (!toolName.equals(operation.toolName()) || !fingerprint.equals(operation.requestFingerprint())) {
            throw new IllegalStateException("El identificador de operacion ya fue usado con otra solicitud");
        }
        if (!"COMPLETED".equals(operation.status()) || operation.responseJson() == null) {
            throw new IllegalStateException("La operacion del carrito aun esta en curso");
        }
        try {
            return objectMapper.readValue(operation.responseJson(), CartResult.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo recuperar el resultado idempotente del carrito", error);
        }
    }

    private String write(CartResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo guardar el resultado del carrito", error);
        }
    }

    private PendingPromotionDraft readPendingPromotion(PendingPromotionRow row) {
        try {
            List<PendingPromotionSelection> selections = objectMapper.readValue(row.selectionsJson(),
                    new TypeReference<List<PendingPromotionSelection>>() { });
            return new PendingPromotionDraft(row.promotionId(), row.promotionName(), selections, row.sourceJobId());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo leer la seleccion provisional de la promocion", error);
        }
    }

    private String writePendingSelections(List<PendingPromotionSelection> selections) {
        try {
            return objectMapper.writeValueAsString(selections == null ? List.of() : selections);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo guardar la seleccion provisional de la promocion", error);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no esta disponible", error);
        }
    }

    private CartResult emptyResult() {
        return new CartResult(null, "EMPTY", "PEN", ZERO, ZERO, ZERO, List.of(), List.of());
    }

    private record LockedSession(CrmWhatsappAgentSession session, Integer branchId) {
    }

    public record ComboSelection(Integer variantId, Integer quantity) {
    }

    public record PendingPromotionDraft(Integer promotionId, String promotionName,
            List<PendingPromotionSelection> selections, Long sourceJobId) {
        public PendingPromotionDraft {
            selections = selections == null ? List.of() : List.copyOf(selections);
        }
    }

    public record PendingPromotionSelection(Integer productId, String productName,
            String colorReference, String sizeReference, Integer quantity, Integer variantId) {
        public PendingPromotionSelection {
            productName = productName == null ? "" : productName;
            colorReference = colorReference == null ? "" : colorReference;
            sizeReference = sizeReference == null ? "" : sizeReference;
            quantity = quantity == null || quantity < 1 ? 1 : quantity;
        }
    }

    public record CartResult(Long cartId, String status, String currency, BigDecimal subtotal,
            BigDecimal discountTotal, BigDecimal total, List<CartItemView> items,
            List<CartPromotionView> promotions) {
    }

    public record CartTotals(String status, String currency, BigDecimal subtotal,
            BigDecimal discountTotal, BigDecimal total) {
        static CartTotals emptyTotals() {
            return new CartTotals("EMPTY", "PEN", ZERO, ZERO, ZERO);
        }

        public boolean empty() {
            return total == null || total.signum() <= 0;
        }
    }

    public record CartItemView(Long cartItemId, Integer variantId, String product, String color, String size,
            int quantity, BigDecimal unitPrice, BigDecimal unitDiscount, BigDecimal lineTotal) {
    }

    public record CartPromotionView(Integer promotionId, String name, int applicationOrder,
            BigDecimal regularPrice, BigDecimal comboPrice, BigDecimal discount) {
    }

    public record ExpiredCartRefresh(boolean refreshed, CartResult cart, List<CartItemView> removedItems,
            LocalDateTime previousConfirmedAt, LocalDateTime refreshedAt) {
        public static ExpiredCartRefresh notRefreshed() {
            return new ExpiredCartRefresh(false, null, List.of(), null, null);
        }
    }
}
