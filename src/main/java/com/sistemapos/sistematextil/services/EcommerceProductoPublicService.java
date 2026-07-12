package com.sistemapos.sistematextil.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.ProductoVarianteOfertaSucursal;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoColorGroupProjection;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioImagenProductoResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoColorListItemResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoColorStockResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoDetalleSlugResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoListadoResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteStockSucursalRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcommerceProductoPublicService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private static final String ACTIVO = "ACTIVO";
    private static final String TIENDA_NO_CONFIGURADA = "TIENDA_NO_CONFIGURADA";

    private final SucursalRepository sucursalRepository;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final PrecioOfertaService precioOfertaService;
    private final EcommercePromocionComboService ecommercePromocionComboService;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final EcommercePortadaService ecommercePortadaService;

    public EcommerceProductoListadoResponse listarProductos(
            String q,
            int page,
            int size,
            Integer idCategoria,
            Integer idColor,
            List<String> tallas,
            Double precioMax,
            Boolean soloDisponibles) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), normalizarSize(size));
        Sucursal sucursal = obtenerSucursalEcommerce();
        if (sucursal == null) {
            return new EcommerceProductoListadoResponse(
                    false,
                    "Tienda ecommerce no configurada",
                    List.of(),
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    0,
                    0,
                    0,
                    true,
                    true,
                    true);
        }

        String term = normalizarTermino(q);
        List<String> tokens = tokensBusqueda(term);
        Page<EcommerceProductoColorGroupProjection> grupos = productoVarianteRepository.listarGruposEcommerce(
                term,
                token(tokens, 0),
                token(tokens, 1),
                token(tokens, 2),
                token(tokens, 3),
                token(tokens, 4),
                token(tokens, 5),
                sucursal.getIdSucursal(),
                idCategoria,
                idColor,
                normalizarTallasCsv(tallas),
                precioMax != null && precioMax >= 0 ? precioMax : null,
                Boolean.TRUE.equals(soloDisponibles),
                pageable);

        List<EcommerceProductoColorListItemResponse> content = construirItems(grupos.getContent(), sucursal);
        return new EcommerceProductoListadoResponse(
                true,
                null,
                content,
                grupos.getNumber(),
                grupos.getSize(),
                grupos.getTotalPages(),
                grupos.getTotalElements(),
                content.size(),
                grupos.isFirst(),
                grupos.isLast(),
                grupos.isEmpty());
    }

    public EcommerceInicioResponse obtenerInicio() {
        Sucursal sucursal = obtenerSucursalEcommerce();
        if (sucursal == null) {
            return new EcommerceInicioResponse(false, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        Integer idSucursal = sucursal.getIdSucursal();

        List<EcommerceProductoColorGroupProjection> gruposAleatorios = productoVarianteRepository
                .listarAleatoriosEcommerce(idSucursal, 4);
        List<EcommerceProductoColorListItemResponse> aleatorios = construirItems(gruposAleatorios, sucursal);

        List<Object[]> topVentas = ventaDetalleRepository.obtenerTopProductosColorEcommerce(12);
        List<EcommerceProductoColorListItemResponse> masVendidos;

        if (topVentas.isEmpty()) {
            masVendidos = List.of();
        } else {
            List<Integer> productoIds = new ArrayList<>();
            List<Integer> colorIds = new ArrayList<>();
            Map<ProductColorKey, Long> ventasPorKey = new LinkedHashMap<>();

            for (Object[] row : topVentas) {
                Integer productoId = ((Number) row[0]).intValue();
                Integer colorId = ((Number) row[1]).intValue();
                Long totalVendido = ((Number) row[2]).longValue();
                productoIds.add(productoId);
                colorIds.add(colorId);
                ventasPorKey.put(new ProductColorKey(productoId, colorId), totalVendido);
            }

            List<ProductoVariante> variantes = productoVarianteRepository
                    .listarVariantesEcommercePorProductosYColores(productoIds, colorIds);

            Set<Integer> varianteIds = variantes.stream()
                    .map(ProductoVariante::getIdProductoVariante)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Integer, Integer> stocks = obtenerStocks(varianteIds, idSucursal);
            Map<Integer, ProductoVarianteOfertaSucursal> ofertas = precioOfertaService
                    .obtenerOfertasSucursalPorVariantes(varianteIds, idSucursal);
            Map<ProductColorKey, List<ProductoColorImagen>> imagenes = obtenerImagenes(productoIds);

            Map<ProductColorKey, List<ProductoVariante>> variantesPorKey = variantes.stream()
                    .collect(Collectors.groupingBy(this::key, LinkedHashMap::new, Collectors.toList()));

            masVendidos = new ArrayList<>();
            for (Map.Entry<ProductColorKey, Long> entry : ventasPorKey.entrySet()) {
                ProductColorKey key = entry.getKey();
                List<ProductoVariante> variantesGrupo = variantesPorKey.getOrDefault(key, List.of());
                if (variantesGrupo.isEmpty()) {
                    continue;
                }
                EcommerceProductoColorListItemResponse item = construirItemDesdeVariantes(
                        variantesGrupo, imagenes, stocks, ofertas);
                if (item.stockTotalColor() != null && item.stockTotalColor() > 0) {
                    masVendidos.add(item);
                    if (masVendidos.size() >= 4) {
                        break;
                    }
                }
            }
        }

        return new EcommerceInicioResponse(
                true,
                ecommercePortadaService.listarPublicas(),
                obtenerImagenesProductosInicio(),
                aleatorios,
                masVendidos,
                ecommercePromocionComboService.listarInicioAleatorias(3));
    }

    public EcommerceProductoDetalleSlugResponse obtenerDetallePorSlug(String slug) {
        Sucursal sucursal = obtenerSucursalEcommerce();
        if (sucursal == null) {
            throw new RuntimeException(TIENDA_NO_CONFIGURADA);
        }
        String slugNormalizado = ProductoService.normalizarSlug(slug);
        if (slugNormalizado == null) {
            throw new RuntimeException("Producto no encontrado");
        }
        Producto producto = productoRepository.findBySlugAndDeletedAtIsNull(slugNormalizado)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        if (!Boolean.TRUE.equals(producto.getPublicarEcommerce())) {
            throw new RuntimeException("Producto no encontrado");
        }
        Integer idProducto = producto.getIdProducto();

        List<ProductoVariante> variantesProducto = productoVarianteRepository
                .listarVariantesEcommercePorProducto(idProducto);
        if (variantesProducto.isEmpty()) {
            throw new RuntimeException("Producto no encontrado");
        }

        Map<Integer, List<ProductoVariante>> variantesPorColor = variantesProducto.stream()
                .filter(v -> v.getColor() != null && v.getColor().getIdColor() != null)
                .collect(Collectors.groupingBy(v -> v.getColor().getIdColor(), LinkedHashMap::new, Collectors.toList()));

        Set<Integer> varianteIds = variantesProducto.stream()
                .map(ProductoVariante::getIdProductoVariante)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Integer> stocks = obtenerStocks(varianteIds, sucursal.getIdSucursal());
        Map<Integer, ProductoVarianteOfertaSucursal> ofertas = precioOfertaService
                .obtenerOfertasSucursalPorVariantes(varianteIds, sucursal.getIdSucursal());
        Map<ProductColorKey, List<ProductoColorImagen>> imagenes = obtenerImagenes(List.of(idProducto));

        List<EcommerceProductoDetalleSlugResponse.ColorCompletoItem> colores = new ArrayList<>();
        for (Map.Entry<Integer, List<ProductoVariante>> entry : variantesPorColor.entrySet()) {
            Integer idColor = entry.getKey();
            List<ProductoVariante> variantesColor = entry.getValue();

            EcommerceProductoColorListItemResponse item = construirItemDesdeVariantes(
                    variantesColor,
                    imagenes,
                    stocks,
                    ofertas);

            ProductColorKey key = new ProductColorKey(idProducto, idColor);
            List<EcommerceProductoColorListItemResponse.ImagenItem> imagenesColor = toImagenItems(
                    imagenes.getOrDefault(key, List.of()));

            colores.add(new EcommerceProductoDetalleSlugResponse.ColorCompletoItem(
                    item.color(),
                    item.imagenPrincipal(),
                    imagenesColor,
                    item.precioMinimo(),
                    item.precioMaximo(),
                    item.estadoStock(),
                    item.stockTotalColor(),
                    item.variantes()));
        }

        return new EcommerceProductoDetalleSlugResponse(
                true,
                toProductoItem(producto),
                promocionesCombo(producto),
                colores,
                construirItems(
                        productoVarianteRepository.listarRecomendadosEcommerce(
                                sucursal.getIdSucursal(),
                                idProducto,
                                5),
                        sucursal));
    }

    public EcommerceProductoColorStockResponse obtenerStockColorPorSlug(String slug, Integer idColor) {
        Sucursal sucursal = obtenerSucursalEcommerce();
        if (sucursal == null) {
            throw new RuntimeException(TIENDA_NO_CONFIGURADA);
        }
        String slugNormalizado = ProductoService.normalizarSlug(slug);
        if (slugNormalizado == null || idColor == null) {
            throw new RuntimeException("Producto no encontrado");
        }
        Producto producto = productoRepository.findBySlugAndDeletedAtIsNull(slugNormalizado)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        if (!Boolean.TRUE.equals(producto.getPublicarEcommerce())) {
            throw new RuntimeException("Producto no encontrado");
        }

        List<ProductoVariante> variantes = productoVarianteRepository
                .listarVariantesEcommercePorProductoYColor(producto.getIdProducto(), idColor);
        if (variantes.isEmpty()) {
            throw new RuntimeException("Producto no encontrado");
        }

        Set<Integer> varianteIds = variantes.stream()
                .map(ProductoVariante::getIdProductoVariante)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Integer> stocks = obtenerStocks(varianteIds, sucursal.getIdSucursal());
        Map<Integer, ProductoVarianteOfertaSucursal> ofertas = precioOfertaService
                .obtenerOfertasSucursalPorVariantes(varianteIds, sucursal.getIdSucursal());
        EcommerceProductoColorListItemResponse item = construirItemDesdeVariantes(
                variantes,
                Map.of(),
                stocks,
                ofertas);

        return new EcommerceProductoColorStockResponse(
                producto.getIdProducto(),
                producto.getSlug(),
                item.color(),
                item.estadoStock(),
                item.stockTotalColor(),
                item.variantes());
    }

    public EcommerceProductoColorListItemResponse.VarianteItem obtenerStockVariantePorSlug(
            String slug,
            Integer idProductoVariante) {
        Sucursal sucursal = obtenerSucursalEcommerce();
        if (sucursal == null) {
            throw new RuntimeException(TIENDA_NO_CONFIGURADA);
        }
        String slugNormalizado = ProductoService.normalizarSlug(slug);
        if (slugNormalizado == null || idProductoVariante == null) {
            throw new RuntimeException("Producto no encontrado");
        }
        Producto producto = productoRepository.findBySlugAndDeletedAtIsNull(slugNormalizado)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        if (!Boolean.TRUE.equals(producto.getPublicarEcommerce())) {
            throw new RuntimeException("Producto no encontrado");
        }
        ProductoVariante variante = productoVarianteRepository
                .buscarVarianteEcommercePorProducto(producto.getIdProducto(), idProductoVariante)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        Map<Integer, Integer> stocks = obtenerStocks(Set.of(variante.getIdProductoVariante()), sucursal.getIdSucursal());
        Map<Integer, ProductoVarianteOfertaSucursal> ofertas = precioOfertaService
                .obtenerOfertasSucursalPorVariantes(Set.of(variante.getIdProductoVariante()), sucursal.getIdSucursal());
        return toVarianteItem(
                variante,
                stocks,
                ofertas);
    }

    private List<EcommerceProductoColorListItemResponse> construirItems(
            List<EcommerceProductoColorGroupProjection> grupos,
            Sucursal sucursal) {
        if (grupos == null || grupos.isEmpty()) {
            return List.of();
        }

        List<Integer> productoIds = grupos.stream()
                .map(EcommerceProductoColorGroupProjection::getProductoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Integer> colorIds = grupos.stream()
                .map(EcommerceProductoColorGroupProjection::getColorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<ProductColorKey> keys = grupos.stream()
                .map(g -> new ProductColorKey(g.getProductoId(), g.getColorId()))
                .collect(Collectors.toSet());

        List<ProductoVariante> variantes = productoVarianteRepository
                .listarVariantesEcommercePorProductosYColores(productoIds, colorIds)
                .stream()
                .filter(v -> keys.contains(key(v)))
                .toList();
        Set<Integer> varianteIds = variantes.stream()
                .map(ProductoVariante::getIdProductoVariante)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Integer> stocks = obtenerStocks(varianteIds, sucursal.getIdSucursal());
        Map<Integer, ProductoVarianteOfertaSucursal> ofertas = precioOfertaService
                .obtenerOfertasSucursalPorVariantes(varianteIds, sucursal.getIdSucursal());
        Map<ProductColorKey, List<ProductoColorImagen>> imagenes = obtenerImagenes(productoIds);
        Map<ProductColorKey, List<ProductoVariante>> variantesPorKey = variantes.stream()
                .collect(Collectors.groupingBy(this::key, LinkedHashMap::new, Collectors.toList()));
        Map<ProductColorKey, EcommerceProductoColorGroupProjection> gruposPorKey = grupos.stream()
                .collect(Collectors.toMap(
                        g -> new ProductColorKey(g.getProductoId(), g.getColorId()),
                        Function.identity(),
                        (actual, ignorado) -> actual,
                        LinkedHashMap::new));

        List<EcommerceProductoColorListItemResponse> items = new ArrayList<>();
        for (Map.Entry<ProductColorKey, EcommerceProductoColorGroupProjection> entry : gruposPorKey.entrySet()) {
            ProductColorKey key = entry.getKey();
            List<ProductoVariante> variantesGrupo = variantesPorKey.getOrDefault(key, List.of());
            if (variantesGrupo.isEmpty()) {
                continue;
            }
            items.add(construirItemDesdeVariantes(
                    variantesGrupo,
                    imagenes,
                    stocks,
                    ofertas));
        }
        return items;
    }

    private EcommerceProductoColorListItemResponse construirItemDesdeVariantes(
            List<ProductoVariante> variantes,
            Map<ProductColorKey, List<ProductoColorImagen>> imagenes,
            Map<Integer, Integer> stocks,
            Map<Integer, ProductoVarianteOfertaSucursal> ofertas) {
        ProductoVariante referencia = variantes.get(0);
        Producto producto = referencia.getProducto();
        ProductColorKey key = key(referencia);
        List<EcommerceProductoColorListItemResponse.VarianteItem> varianteItems = variantes.stream()
                .map(variante -> toVarianteItem(variante, stocks, ofertas))
                .toList();
        int stockTotal = varianteItems.stream()
                .map(EcommerceProductoColorListItemResponse.VarianteItem::stock)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        Double precioMinimo = varianteItems.stream()
                .map(EcommerceProductoColorListItemResponse.VarianteItem::precioVigente)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);
        Double precioMaximo = varianteItems.stream()
                .map(EcommerceProductoColorListItemResponse.VarianteItem::precioVigente)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);

        return new EcommerceProductoColorListItemResponse(
                toProductoItem(producto),
                toColorItem(referencia),
                resolverImagenPrincipal(producto, imagenes.getOrDefault(key, List.of())),
                precioMinimo,
                precioMaximo,
                resolverEstadoStock(varianteItems),
                stockTotal,
                promocionesCombo(producto),
                varianteItems);
    }

    private List<EcommerceProductoColorListItemResponse.PromocionComboItem> promocionesCombo(Producto producto) {
        if (producto == null || producto.getIdProducto() == null) {
            return List.of();
        }
        return ecommercePromocionComboService.listarActivasPorProductos(Set.of(producto.getIdProducto()))
                .stream()
                .map(promo -> new EcommerceProductoColorListItemResponse.PromocionComboItem(
                        promo.idPromocionCombo(),
                        promo.nombre(),
                        promo.regla(),
                        promo.precioCombo(),
                        promo.items() != null && promo.items().size() == 1))
                .toList();
    }

    private EcommerceProductoColorListItemResponse.VarianteItem toVarianteItem(
            ProductoVariante variante,
            Map<Integer, Integer> stocks,
            Map<Integer, ProductoVarianteOfertaSucursal> ofertas) {
        Integer varianteId = variante.getIdProductoVariante();
        Integer stock = stocks.getOrDefault(varianteId, 0);
        PrecioOfertaService.ResultadoPrecioOferta precio = precioOfertaService.resolver(
                variante,
                ofertas.get(varianteId));
        return new EcommerceProductoColorListItemResponse.VarianteItem(
                varianteId,
                variante.getSku(),
                variante.getCodigoBarras(),
                new EcommerceProductoColorListItemResponse.TallaItem(
                        variante.getTalla().getIdTalla(),
                        variante.getTalla().getNombre()),
                precio.precioRegular(),
                variante.getPrecioMayor(),
                precio.precioOfertaAplicada(),
                precio.precioVigente(),
                precio.tipoOfertaAplicada(),
                precio.sucursalOfertaId(),
                precio.ofertaInicioAplicada(),
                precio.ofertaFinAplicada(),
                stock,
                stock > 0,
                variante.getEstado());
    }

    private EcommerceProductoColorListItemResponse.ProductoItem toProductoItem(Producto producto) {
        return new EcommerceProductoColorListItemResponse.ProductoItem(
                producto.getIdProducto(),
                producto.getNombre(),
                producto.getSlug(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
                new EcommerceProductoColorListItemResponse.CategoriaItem(
                        producto.getCategoria().getIdCategoria(),
                        producto.getCategoria().getNombreCategoria()),
                producto.getImagenGlobalUrl(),
                producto.getImagenGlobalThumbUrl(),
                producto.getGuiaTallasUrl(),
                producto.getGuiaTallasThumbUrl(),
                preventaActiva(producto),
                preventaActiva(producto) ? producto.getFechaEnvioPreventa() : null);
    }

    private boolean preventaActiva(Producto producto) {
        return producto != null
                && Boolean.TRUE.equals(producto.getPreventa())
                && producto.getFechaEnvioPreventa() != null
                && producto.getFechaEnvioPreventa().isAfter(java.time.LocalDate.now());
    }

    private List<EcommerceInicioImagenProductoResponse> obtenerImagenesProductosInicio() {
        return productoRepository.listarImagenesInicioEcommerce(PageRequest.of(0, 12))
                .stream()
                .map(producto -> new EcommerceInicioImagenProductoResponse(
                        producto.getIdProducto(),
                        producto.getNombre(),
                        producto.getSlug(),
                        producto.getImagenGlobalUrl(),
                        producto.getImagenGlobalThumbUrl(),
                        preventaActiva(producto)))
                .toList();
    }

    private EcommerceProductoColorListItemResponse.ColorItem toColorItem(ProductoVariante variante) {
        return new EcommerceProductoColorListItemResponse.ColorItem(
                variante.getColor().getIdColor(),
                variante.getColor().getNombre(),
                variante.getColor().getCodigo());
    }

    private EcommerceProductoColorListItemResponse.ImagenItem resolverImagenPrincipal(
            Producto producto,
            List<ProductoColorImagen> imagenesColor) {
        List<EcommerceProductoColorListItemResponse.ImagenItem> imagenes = toImagenItems(imagenesColor);
        if (!imagenes.isEmpty()) {
            return imagenes.get(0);
        }
        if (producto != null && (tieneTexto(producto.getImagenGlobalUrl()) || tieneTexto(producto.getImagenGlobalThumbUrl()))) {
            return new EcommerceProductoColorListItemResponse.ImagenItem(
                    null,
                    producto.getImagenGlobalUrl(),
                    producto.getImagenGlobalThumbUrl(),
                    null,
                    true,
                    ACTIVO,
                    "GLOBAL");
        }
        return null;
    }

    private List<EcommerceProductoColorListItemResponse.ImagenItem> toImagenItems(List<ProductoColorImagen> imagenesColor) {
        if (imagenesColor == null || imagenesColor.isEmpty()) {
            return List.of();
        }
        return imagenesColor.stream()
                .sorted(Comparator
                        .comparing((ProductoColorImagen img) -> !Boolean.TRUE.equals(img.getEsPrincipal()))
                        .thenComparing(img -> img.getOrden() == null ? Integer.MAX_VALUE : img.getOrden())
                        .thenComparing(img -> img.getIdColorImagen() == null ? Integer.MAX_VALUE : img.getIdColorImagen()))
                .map(img -> new EcommerceProductoColorListItemResponse.ImagenItem(
                        img.getIdColorImagen(),
                        img.getUrl(),
                        img.getUrlThumb(),
                        img.getOrden(),
                        img.getEsPrincipal(),
                        img.getEstado(),
                        "COLOR"))
                .toList();
    }

    private String resolverEstadoStock(List<EcommerceProductoColorListItemResponse.VarianteItem> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            return "AGOTADO";
        }
        long conStock = variantes.stream()
                .filter(v -> Boolean.TRUE.equals(v.disponible()))
                .count();
        if (conStock == 0) {
            return "AGOTADO";
        }
        if (conStock == variantes.size()) {
            return "DISPONIBLE";
        }
        return "PARCIAL";
    }

    private Map<ProductColorKey, List<ProductoColorImagen>> obtenerImagenes(List<Integer> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return Map.of();
        }
        return productoColorImagenRepository.findByProductoIdProductoInAndDeletedAtIsNull(productoIds)
                .stream()
                .filter(img -> ACTIVO.equals(img.getEstado()))
                .filter(img -> img.getProducto() != null && img.getProducto().getIdProducto() != null)
                .filter(img -> img.getColor() != null && img.getColor().getIdColor() != null)
                .collect(Collectors.groupingBy(
                        img -> new ProductColorKey(
                                img.getProducto().getIdProducto(),
                                img.getColor().getIdColor()),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private Map<Integer, Integer> obtenerStocks(Set<Integer> varianteIds, Integer idSucursal) {
        if (varianteIds == null || varianteIds.isEmpty()) {
            return Map.of();
        }
        return productoVarianteRepository.obtenerStocksCatalogoPorVariantes(
                        new ArrayList<>(varianteIds),
                        idSucursal,
                        SucursalTipo.VENTA)
                .stream()
                .collect(Collectors.toMap(
                        ProductoVarianteStockSucursalRow::varianteId,
                        ProductoVarianteStockSucursalRow::stock,
                        Integer::sum,
                        HashMap::new));
    }

    private Sucursal obtenerSucursalEcommerce() {
        return sucursalRepository
                .findFirstByPublicarEcommerceTrueAndDeletedAtIsNullAndEstadoAndTipoOrderByIdSucursalAsc(
                        ACTIVO,
                        SucursalTipo.VENTA)
                .orElse(null);
    }


    private ProductColorKey key(ProductoVariante variante) {
        return new ProductColorKey(
                variante.getProducto().getIdProducto(),
                variante.getColor().getIdColor());
    }

    private String normalizarTermino(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        return term.trim();
    }

    private List<String> tokensBusqueda(String term) {
        if (term == null) {
            return List.of();
        }
        return java.util.Arrays.stream(term.split("\\s+"))
                .filter(token -> !token.isBlank())
                .limit(6)
                .toList();
    }

    private String token(List<String> tokens, int index) {
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private String normalizarTallasCsv(List<String> tallas) {
        if (tallas == null || tallas.isEmpty()) {
            return null;
        }
        String value = tallas.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(talla -> !talla.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.joining(","));
        return value.isBlank() ? null : value;
    }

    private int normalizarSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private boolean tieneTexto(String value) {
        return value != null && !value.isBlank();
    }

    private record ProductColorKey(Integer productoId, Integer colorId) {
    }
}
