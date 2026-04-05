package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoColorImagenResumen;
import com.sistemapos.sistematextil.util.producto.ProductoColorResumen;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoCreateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoResponse;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoCreateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.producto.ProductoImagenCreateItem;
import com.sistemapos.sistematextil.util.producto.ProductoImagenDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoTallaResumen;
import com.sistemapos.sistematextil.util.producto.ProductoUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteCreateItem;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteStockCreateItem;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteStockDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteStockSucursalRow;
import com.sistemapos.sistematextil.util.producto.StockSucursalVentaResumen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductoService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";
    private static final String ESTADO_ARCHIVADO = "ARCHIVADO";

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final SucursalStockRepository sucursalStockRepository;
    private final ColorService colorService;
    private final TallaService tallaService;
    private final S3StorageService s3StorageService;
    private final StockMovimientoService stockMovimientoService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<ProductoListItemResponse> listarPaginado(
            int page,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuario);

        Integer idSucursalFiltro = resolverSucursalCatalogo(idSucursal);
        Sucursal sucursalContexto = obtenerSucursalContextoCatalogo(idSucursalFiltro);
        Page<Producto> productos = buscarProductos(null, idCategoria, idColor, conOferta, idSucursalFiltro, false, page);
        Map<Integer, VarianteCatalogo> catalogo = obtenerCatalogoPorProductos(productos.getContent(), idSucursalFiltro, false);

        return PagedResponse.fromPage(productos.map(producto -> toListItemResponse(
                producto,
                catalogo.get(producto.getIdProducto()),
                sucursalContexto)));
    }

    public PagedResponse<ProductoListadoResumenResponse> buscarPaginado(
            String q,
            int page,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Boolean soloDisponibles,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuario);

        String term = normalizar(q);
        if (term == null) {
            return listarResumenPaginado(
                    page,
                    idCategoria,
                    idColor,
                    conOferta,
                    soloDisponibles,
                    idSucursal,
                    correoUsuarioAutenticado);
        }

        Integer idSucursalFiltro = resolverSucursalCatalogo(idSucursal);
        Sucursal sucursalContexto = obtenerSucursalContextoCatalogo(idSucursalFiltro);
        Page<Producto> productos = buscarProductos(
                term,
                idCategoria,
                idColor,
                conOferta,
                idSucursalFiltro,
                Boolean.TRUE.equals(soloDisponibles),
                page);
        Map<Integer, VarianteCatalogo> catalogo = obtenerCatalogoPorProductos(
                productos.getContent(),
                idSucursalFiltro,
                true,
                Boolean.TRUE.equals(soloDisponibles));

        List<ProductoListadoResumenResponse> content = productos.getContent().stream()
                .map(producto -> toResumenResponse(
                        producto,
                        catalogo.get(producto.getIdProducto()),
                        sucursalContexto))
                .toList();

        return PagedResponse.fromPage(new PageImpl<>(content, productos.getPageable(), productos.getTotalElements()));
    }

    public PagedResponse<ProductoListadoResumenResponse> listarResumenPaginado(
            int page,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Boolean soloDisponibles,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuario);

        Integer idSucursalFiltro = resolverSucursalCatalogo(idSucursal);
        Sucursal sucursalContexto = obtenerSucursalContextoCatalogo(idSucursalFiltro);
        Page<Producto> productos = buscarProductos(
                null,
                idCategoria,
                idColor,
                conOferta,
                idSucursalFiltro,
                Boolean.TRUE.equals(soloDisponibles),
                page);
        Map<Integer, VarianteCatalogo> catalogo = obtenerCatalogoPorProductos(
                productos.getContent(),
                idSucursalFiltro,
                true,
                Boolean.TRUE.equals(soloDisponibles));

        List<ProductoListadoResumenResponse> content = productos.getContent().stream()
                .map(producto -> toResumenResponse(
                        producto,
                        catalogo.get(producto.getIdProducto()),
                        sucursalContexto))
                .toList();

        return PagedResponse.fromPage(new PageImpl<>(content, productos.getPageable(), productos.getTotalElements()));
    }

    @Transactional
    public ProductoListItemResponse insertar(ProductoCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicion(usuario);

        Producto producto = new Producto();
        producto.setCategoria(obtenerCategoriaActiva(request.idCategoria()));
        producto.setNombre(normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio"));
        producto.setDescripcion(normalizar(request.descripcion()));
        producto.setEstado(ESTADO_ACTIVO);
        producto.setActivo(ESTADO_ACTIVO);
        producto.setDeletedAt(null);

        try {
            Producto guardado = productoRepository.save(producto);
            return toListItemResponse(guardado, null);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se pudo guardar el producto por restriccion de datos");
        }
    }

    @Transactional
    public ProductoCompletoResponse insertarCompleto(
            ProductoCompletoCreateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicion(usuario);

        Producto producto = new Producto();
        producto.setCategoria(obtenerCategoriaActiva(request.idCategoria()));
        producto.setNombre(normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio"));
        producto.setDescripcion(normalizar(request.descripcion()));
        producto.setEstado(ESTADO_ACTIVO);
        producto.setActivo(ESTADO_ACTIVO);
        producto.setDeletedAt(null);

        Producto guardado = productoRepository.save(producto);
        List<ProductoVariante> variantes = construirVariantes(guardado, request.variantes(), null);
        if (!variantes.isEmpty()) {
            productoVarianteRepository.saveAllAndFlush(variantes);
        }
        sincronizarStocks(usuario, variantes, request.variantes(), "STOCK INICIAL POR CREACION DE PRODUCTO - ");

        List<ProductoColorImagen> imagenes = construirImagenes(guardado, request.imagenes());
        if (!imagenes.isEmpty()) {
            productoColorImagenRepository.saveAll(imagenes);
        }

        VarianteCatalogo catalogo = obtenerCatalogoPorProductos(List.of(guardado), null, false).get(guardado.getIdProducto());
        return new ProductoCompletoResponse(toListItemResponse(guardado, catalogo), variantes.size(), imagenes.size());
    }

    @Transactional
    public ProductoCompletoResponse actualizarCompleto(
            Integer idProducto,
            ProductoCompletoUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicion(usuario);

        Producto producto = obtenerPorId(idProducto);
        producto.setCategoria(obtenerCategoriaActiva(request.idCategoria()));
        producto.setNombre(normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio"));
        producto.setDescripcion(normalizar(request.descripcion()));
        producto.setEstado(ESTADO_ACTIVO);
        producto.setActivo(ESTADO_ACTIVO);
        producto.setDeletedAt(null);

        Producto actualizado = productoRepository.save(producto);
        List<ProductoVariante> variantes = sincronizarVariantes(
                actualizado,
                productoVarianteRepository.findByProductoIdProducto(idProducto),
                request.variantes());
        productoVarianteRepository.saveAllAndFlush(variantes);
        for (ProductoVariante variante : variantesActivas(variantes)) {
            if (variante.getColor() != null && variante.getColor().getIdColor() != null) {
                limpiarImagenesColorSiNoHayVariantesActivas(idProducto, variante.getColor().getIdColor());
            }
        }
        sincronizarStocks(usuario, variantesActivas(variantes), request.variantes(), "SINCRONIZACION DE STOCK POR ACTUALIZACION DE PRODUCTO - ");

        List<ProductoColorImagen> imagenesActuales = productoColorImagenRepository.findByProductoIdProductoAndDeletedAtIsNull(idProducto);
        Set<String> urlsActuales = extraerUrls(imagenesActuales);
        productoColorImagenRepository.deleteByProductoIdProducto(idProducto);
        List<ProductoColorImagen> imagenesNuevas = construirImagenes(actualizado, request.imagenes());
        if (!imagenesNuevas.isEmpty()) {
            productoColorImagenRepository.saveAll(imagenesNuevas);
        }
        eliminarImagenesObsoletas(urlsActuales, extraerUrls(imagenesNuevas));

        VarianteCatalogo catalogo = obtenerCatalogoPorProductos(List.of(actualizado), null, false).get(actualizado.getIdProducto());
        return new ProductoCompletoResponse(
                toListItemResponse(actualizado, catalogo),
                variantesActivas(variantes).size(),
                imagenesNuevas.size());
    }

    @Transactional
    public ProductoListItemResponse actualizar(
            Integer idProducto,
            ProductoUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicion(usuario);

        Producto producto = obtenerPorId(idProducto);
        producto.setCategoria(obtenerCategoriaActiva(request.idCategoria()));
        producto.setNombre(normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio"));
        producto.setDescripcion(normalizar(request.descripcion()));
        producto.setEstado(ESTADO_ACTIVO);
        producto.setActivo(ESTADO_ACTIVO);
        producto.setDeletedAt(null);

        Producto actualizado = productoRepository.save(producto);
        VarianteCatalogo catalogo = obtenerCatalogoPorProductos(List.of(actualizado), null, false).get(actualizado.getIdProducto());
        return toListItemResponse(actualizado, catalogo);
    }

    @Transactional
    public void eliminar(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicion(usuario);

        Producto producto = obtenerPorId(idProducto);
        producto.setEstado(ESTADO_ARCHIVADO);
        producto.setActivo(ESTADO_INACTIVO);
        producto.setDeletedAt(LocalDateTime.now());
        productoRepository.save(producto);

        List<ProductoVariante> variantes = productoVarianteRepository.findByProductoIdProducto(idProducto);
        LocalDateTime now = LocalDateTime.now();
        for (ProductoVariante variante : variantes) {
            variante.setEstado(ESTADO_INACTIVO);
            variante.setActivo(ESTADO_INACTIVO);
            variante.setDeletedAt(now);
        }
        if (!variantes.isEmpty()) {
            productoVarianteRepository.saveAll(variantes);
        }
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepository.findByIdProductoAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Producto con ID " + id + " no encontrado"));
    }

    public Producto obtenerPorIdConAlcance(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuario);
        return obtenerPorId(idProducto);
    }

    public ProductoDetalleResponse obtenerDetalle(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuario);

        Producto producto = obtenerPorId(idProducto);
        List<ProductoVariante> variantes = productoVarianteRepository.findByProductoIdProductoAndDeletedAtIsNull(idProducto);
        List<Integer> idsVariante = variantes.stream().map(ProductoVariante::getIdProductoVariante).filter(Objects::nonNull).toList();
        List<Sucursal> sucursalesActivas = sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc(ESTADO_ACTIVO);
        List<SucursalStock> stocks = idsVariante.isEmpty() ? List.of() : sucursalStockRepository.listarPorVariantes(idsVariante);

        Map<Integer, Map<Integer, Integer>> stockPorVariante = new HashMap<>();
        for (SucursalStock stock : stocks) {
            Integer varianteId = stock.getProductoVariante() != null ? stock.getProductoVariante().getIdProductoVariante() : null;
            Integer sucursalId = stock.getSucursal() != null ? stock.getSucursal().getIdSucursal() : null;
            if (varianteId == null || sucursalId == null) {
                continue;
            }
            stockPorVariante.computeIfAbsent(varianteId, unused -> new HashMap<>())
                    .put(sucursalId, stock.getCantidad() == null ? 0 : stock.getCantidad());
        }

        List<ProductoVarianteDetalleResponse> detalleVariantes = variantes.stream()
                .map(variante -> toDetalleVariante(
                        variante,
                        sucursalesActivas,
                        stockPorVariante.getOrDefault(variante.getIdProductoVariante(), Map.of())))
                .toList();

        List<ProductoImagenDetalleResponse> imagenes = productoColorImagenRepository.findByProductoIdProductoAndDeletedAtIsNull(idProducto)
                .stream()
                .map(this::toImagenDetalleResponse)
                .sorted(Comparator.comparing(ProductoImagenDetalleResponse::colorId, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductoImagenDetalleResponse::orden, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        VarianteCatalogo catalogo = obtenerCatalogoPorProductos(List.of(producto), null, false).get(producto.getIdProducto());
        return new ProductoDetalleResponse(toListItemResponse(producto, catalogo), detalleVariantes, imagenes);
    }

    public void limpiarImagenesColorSiNoHayVariantesActivas(Integer idProducto, Integer idColor) {
        if (idProducto == null || idColor == null) {
            return;
        }
        boolean existen = productoVarianteRepository.existsVarianteActivaPorProductoYColor(idProducto, idColor);
        if (existen) {
            return;
        }

        List<ProductoColorImagen> imagenes = productoColorImagenRepository
                .findByProductoIdProductoAndColorIdColorAndDeletedAtIsNull(idProducto, idColor);
        if (imagenes.isEmpty()) {
            return;
        }

        Set<String> urls = extraerUrls(imagenes);
        productoColorImagenRepository.deleteAll(imagenes);
        eliminarImagenesObsoletas(urls, Set.of());
    }

    private Page<Producto> buscarProductos(String term, Integer idCategoria, Integer idColor, Boolean conOferta, int page) {
        return buscarProductos(term, idCategoria, idColor, conOferta, null, false, page);
    }

    private Page<Producto> buscarProductos(
            String term,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Integer idSucursalFiltro,
            boolean soloDisponibles,
            int page) {
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        return productoRepository.buscarConFiltros(
                term,
                idSucursalFiltro,
                idCategoria,
                idColor,
                conOferta,
                resolverTipoSucursalCatalogo(idSucursalFiltro),
                soloDisponibles,
                pageable);
    }

    private Map<Integer, VarianteCatalogo> obtenerCatalogoPorProductos(
            List<Producto> productos,
            Integer idSucursalFiltro,
            boolean incluirColores) {
        return obtenerCatalogoPorProductos(productos, idSucursalFiltro, incluirColores, false);
    }

    private Map<Integer, VarianteCatalogo> obtenerCatalogoPorProductos(
            List<Producto> productos,
            Integer idSucursalFiltro,
            boolean incluirColores,
            boolean soloDisponibles) {
        if (productos == null || productos.isEmpty()) {
            return Map.of();
        }

        List<Integer> productoIds = productos.stream()
                .map(Producto::getIdProducto)
                .filter(Objects::nonNull)
                .toList();
        if (productoIds.isEmpty()) {
            return Map.of();
        }

        List<ProductoVarianteResumenRow> resumenes = productoVarianteRepository.obtenerResumenCatalogoPorProductos(
                productoIds,
                idSucursalFiltro,
                resolverTipoSucursalCatalogo(idSucursalFiltro),
                soloDisponibles);
        List<Integer> varianteIds = resumenes.stream()
                .map(ProductoVarianteResumenRow::varianteId)
                .filter(Objects::nonNull)
                .toList();
        List<ProductoVarianteStockSucursalRow> stocks = varianteIds.isEmpty()
                ? List.of()
                : productoVarianteRepository.obtenerStocksCatalogoPorVariantes(
                        varianteIds,
                        idSucursalFiltro,
                        resolverTipoSucursalCatalogo(idSucursalFiltro));

        Map<Integer, List<StockSucursalVentaResumen>> stocksPorVariante = agruparStocksVenta(stocks);
        Map<ProductoColorKey, ProductoColorImagenResumen> imagenesPrincipales = resolverImagenesPrincipales(productoIds);
        Map<Integer, VarianteCatalogoBuilder> builders = new HashMap<>();

        for (ProductoVarianteResumenRow row : resumenes) {
            if (row.productoId() == null) {
                continue;
            }
            VarianteCatalogoBuilder builder = builders.computeIfAbsent(row.productoId(), unused -> new VarianteCatalogoBuilder());
            builder.acumularReferencia(row);
            builder.acumularPrecio(resolverPrecioVigente(row.precio(), row.precioOferta(), row.ofertaInicio(), row.ofertaFin()));
            if (incluirColores) {
                builder.acumularColor(row, stocksPorVariante.getOrDefault(row.varianteId(), List.of()), imagenesPrincipales);
            }
        }

        Map<Integer, VarianteCatalogo> result = new HashMap<>();
        for (Map.Entry<Integer, VarianteCatalogoBuilder> entry : builders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().build());
        }
        return result;
    }

    private List<ProductoVariante> construirVariantes(
            Producto producto,
            List<ProductoVarianteCreateItem> items,
            Integer idProductoExcluir) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<ProductoVariante> result = new ArrayList<>();
        Set<String> skus = new HashSet<>();
        Set<String> codigos = new HashSet<>();

        for (ProductoVarianteCreateItem item : items) {
            validarStocksSolicitados(item.stocksSucursales());
            String sku = normalizarRequerido(item.sku(), "El SKU es obligatorio");
            if (!skus.add(sku.toLowerCase())) {
                throw new RuntimeException("No puede repetir SKU dentro del mismo producto");
            }
            if (productoVarianteRepository.existsSkuParaOtroProducto(sku, idProductoExcluir)) {
                throw new RuntimeException("El SKU '" + sku + "' ya existe");
            }

            String codigoBarras = normalizar(item.codigoBarras());
            if (codigoBarras != null) {
                if (!codigos.add(codigoBarras.toLowerCase())) {
                    throw new RuntimeException("No puede repetir codigo de barras dentro del mismo producto");
                }
                if (productoVarianteRepository.existsCodigoBarrasParaOtroProducto(codigoBarras, idProductoExcluir)) {
                    throw new RuntimeException("El codigo de barras '" + codigoBarras + "' ya existe");
                }
            }

            if (producto.getIdProducto() != null && productoVarianteRepository.existsByProductoIdProductoAndTallaIdTallaAndColorIdColor(
                    producto.getIdProducto(),
                    item.tallaId(),
                    item.colorId())) {
                throw new RuntimeException("Ya existe esta variante (talla/color) para el producto");
            }

            Color color = colorService.obtenerPorId(item.colorId());
            Talla talla = tallaService.obtenerPorId(item.tallaId());
            validarCatalogoActivo(color, talla);

            Double precioMayor = normalizarPrecioMayor(item.precioMayor());
            Double precioOferta = normalizarPrecioOferta(item.precioOferta());
            LocalDateTime ofertaInicio = item.ofertaInicio();
            LocalDateTime ofertaFin = item.ofertaFin();
            validarPrecioMayor(item.precio(), precioMayor);
            validarPrecioOferta(item.precio(), precioOferta, ofertaInicio, ofertaFin);
            if (precioOferta == null) {
                ofertaInicio = null;
                ofertaFin = null;
            }

            ProductoVariante variante = new ProductoVariante();
            variante.setProducto(producto);
            variante.setColor(color);
            variante.setTalla(talla);
            variante.setSku(sku);
            variante.setCodigoBarras(codigoBarras);
            variante.setPrecio(item.precio());
            variante.setPrecioMayor(precioMayor);
            variante.setPrecioOferta(precioOferta);
            variante.setOfertaInicio(ofertaInicio);
            variante.setOfertaFin(ofertaFin);
            variante.setEstado(ESTADO_ACTIVO);
            variante.setActivo(ESTADO_ACTIVO);
            variante.setDeletedAt(null);
            result.add(variante);
        }

        return result;
    }

    private List<ProductoVariante> sincronizarVariantes(
            Producto producto,
            List<ProductoVariante> existentes,
            List<ProductoVarianteCreateItem> items) {
        Map<String, ProductoVariante> porClave = new HashMap<>();
        for (ProductoVariante existente : existentes) {
            if (existente.getColor() == null || existente.getTalla() == null) {
                continue;
            }
            porClave.put(claveVariante(existente.getColor().getIdColor(), existente.getTalla().getIdTalla()), existente);
        }

        Set<String> clavesSolicitadas = new HashSet<>();
        List<ProductoVariante> resultado = new ArrayList<>();
        Set<String> skus = new HashSet<>();
        Set<String> codigos = new HashSet<>();

        for (ProductoVarianteCreateItem item : items) {
            validarStocksSolicitados(item.stocksSucursales());
            String clave = claveVariante(item.colorId(), item.tallaId());
            if (!clavesSolicitadas.add(clave)) {
                throw new RuntimeException("No puede repetir la combinacion color/talla dentro del mismo producto");
            }

            ProductoVariante existente = porClave.get(clave);
            String sku = normalizarRequerido(item.sku(), "El SKU es obligatorio");
            if (!skus.add(sku.toLowerCase())) {
                throw new RuntimeException("No puede repetir SKU dentro del mismo producto");
            }
            if (productoVarianteRepository.existsSkuParaOtroProducto(sku, producto.getIdProducto())
                    && (existente == null || !sku.equalsIgnoreCase(existente.getSku()))) {
                throw new RuntimeException("El SKU '" + sku + "' ya existe");
            }

            String codigoBarras = normalizar(item.codigoBarras());
            if (codigoBarras != null) {
                if (!codigos.add(codigoBarras.toLowerCase())) {
                    throw new RuntimeException("No puede repetir codigo de barras dentro del mismo producto");
                }
                if (productoVarianteRepository.existsCodigoBarrasParaOtroProducto(codigoBarras, producto.getIdProducto())
                        && (existente == null || !codigoBarras.equalsIgnoreCase(existente.getCodigoBarras()))) {
                    throw new RuntimeException("El codigo de barras '" + codigoBarras + "' ya existe");
                }
            }

            Color color = colorService.obtenerPorId(item.colorId());
            Talla talla = tallaService.obtenerPorId(item.tallaId());
            validarCatalogoActivo(color, talla);

            Double precioMayor = normalizarPrecioMayor(item.precioMayor());
            Double precioOferta = normalizarPrecioOferta(item.precioOferta());
            LocalDateTime ofertaInicio = item.ofertaInicio();
            LocalDateTime ofertaFin = item.ofertaFin();
            validarPrecioMayor(item.precio(), precioMayor);
            validarPrecioOferta(item.precio(), precioOferta, ofertaInicio, ofertaFin);
            if (precioOferta == null) {
                ofertaInicio = null;
                ofertaFin = null;
            }

            ProductoVariante destino = existente == null ? new ProductoVariante() : existente;
            destino.setProducto(producto);
            destino.setColor(color);
            destino.setTalla(talla);
            destino.setSku(sku);
            destino.setCodigoBarras(codigoBarras);
            destino.setPrecio(item.precio());
            destino.setPrecioMayor(precioMayor);
            destino.setPrecioOferta(precioOferta);
            destino.setOfertaInicio(ofertaInicio);
            destino.setOfertaFin(ofertaFin);
            destino.setEstado(ESTADO_ACTIVO);
            destino.setActivo(ESTADO_ACTIVO);
            destino.setDeletedAt(null);
            resultado.add(destino);
        }

        LocalDateTime now = LocalDateTime.now();
        for (ProductoVariante existente : existentes) {
            String clave = existente.getColor() == null || existente.getTalla() == null
                    ? null
                    : claveVariante(existente.getColor().getIdColor(), existente.getTalla().getIdTalla());
            if (clave != null && clavesSolicitadas.contains(clave)) {
                continue;
            }
            existente.setEstado(ESTADO_INACTIVO);
            existente.setActivo(ESTADO_INACTIVO);
            existente.setDeletedAt(now);
            resultado.add(existente);
        }

        return resultado;
    }

    private void sincronizarStocks(
            Usuario usuario,
            List<ProductoVariante> variantesPersistidas,
            List<ProductoVarianteCreateItem> items,
            String prefijoMotivo) {
        if (variantesPersistidas == null || variantesPersistidas.isEmpty()) {
            return;
        }

        Map<String, ProductoVarianteCreateItem> itemPorClave = new HashMap<>();
        for (ProductoVarianteCreateItem item : items) {
            itemPorClave.put(claveVariante(item.colorId(), item.tallaId()), item);
        }

        List<Sucursal> sucursalesActivas = sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc(ESTADO_ACTIVO);
        Map<Integer, Sucursal> sucursalPorId = new HashMap<>();
        for (Sucursal sucursal : sucursalesActivas) {
            sucursalPorId.put(sucursal.getIdSucursal(), sucursal);
        }

        for (ProductoVariante variante : variantesPersistidas) {
            if (variante.getDeletedAt() != null || variante.getIdProductoVariante() == null) {
                continue;
            }
            String clave = claveVariante(
                    variante.getColor() != null ? variante.getColor().getIdColor() : null,
                    variante.getTalla() != null ? variante.getTalla().getIdTalla() : null);
            ProductoVarianteCreateItem item = itemPorClave.get(clave);
            if (item == null) {
                continue;
            }

            Map<Integer, Integer> cantidades = new HashMap<>();
            for (ProductoVarianteStockCreateItem stockItem : item.stocksSucursales()) {
                if (!sucursalPorId.containsKey(stockItem.idSucursal())) {
                    throw new RuntimeException("La sucursal con ID " + stockItem.idSucursal() + " no esta activa");
                }
                cantidades.put(stockItem.idSucursal(), stockItem.cantidad());
            }

            for (Sucursal sucursal : sucursalesActivas) {
                Integer objetivo = cantidades.getOrDefault(sucursal.getIdSucursal(), 0);
                SucursalStock actual = sucursalStockRepository.findBySucursalIdSucursalAndProductoVarianteIdProductoVariante(
                        sucursal.getIdSucursal(),
                        variante.getIdProductoVariante()).orElse(null);
                Integer actualCantidad = actual == null || actual.getCantidad() == null ? 0 : actual.getCantidad();
                if (!Objects.equals(actualCantidad, objetivo)) {
                    stockMovimientoService.ajustar(
                            sucursal.getIdSucursal(),
                            variante.getIdProductoVariante(),
                            objetivo,
                            prefijoMotivo + variante.getSku(),
                            usuario);
                }
            }
        }
    }

    private List<ProductoVariante> variantesActivas(List<ProductoVariante> variantes) {
        return variantes.stream().filter(variante -> variante.getDeletedAt() == null).toList();
    }

    private Categoria obtenerCategoriaActiva(Integer idCategoria) {
        return categoriaRepository.findByIdCategoriaAndDeletedAtIsNullAndEstado(idCategoria, ESTADO_ACTIVO)
                .orElseThrow(() -> new RuntimeException("Categoria con ID " + idCategoria + " no encontrada"));
    }

    private Integer resolverSucursalCatalogo(Integer idSucursal) {
        if (idSucursal == null) {
            return null;
        }
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        if (!ESTADO_ACTIVO.equalsIgnoreCase(sucursal.getEstado())) {
            throw new RuntimeException("Sucursal no encontrada");
        }
        return sucursal.getIdSucursal();
    }

    private SucursalTipo resolverTipoSucursalCatalogo(Integer idSucursal) {
        return idSucursal == null ? SucursalTipo.VENTA : null;
    }

    private ProductoListItemResponse toListItemResponse(Producto producto, VarianteCatalogo catalogo) {
        return toListItemResponse(producto, catalogo, null);
    }

    private ProductoListItemResponse toListItemResponse(Producto producto, VarianteCatalogo catalogo, Sucursal sucursalContexto) {
        return new ProductoListItemResponse(
                producto.getIdProducto(),
                catalogo != null ? catalogo.sku() : null,
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
                producto.getCategoria() != null ? producto.getCategoria().getIdCategoria() : null,
                producto.getCategoria() != null ? producto.getCategoria().getNombreCategoria() : null,
                sucursalContexto != null ? sucursalContexto.getIdSucursal() : null,
                sucursalContexto != null ? sucursalContexto.getNombre() : null);
    }

    private ProductoListadoResumenResponse toResumenResponse(Producto producto, VarianteCatalogo catalogo) {
        return toResumenResponse(producto, catalogo, null);
    }

    private ProductoListadoResumenResponse toResumenResponse(
            Producto producto,
            VarianteCatalogo catalogo,
            Sucursal sucursalContexto) {
        return new ProductoListadoResumenResponse(
                producto.getIdProducto(),
                catalogo != null ? catalogo.sku() : null,
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
                catalogo != null ? catalogo.precioMin() : null,
                catalogo != null ? catalogo.precioMax() : null,
                producto.getCategoria() != null ? producto.getCategoria().getIdCategoria() : null,
                producto.getCategoria() != null ? producto.getCategoria().getNombreCategoria() : null,
                sucursalContexto != null ? sucursalContexto.getIdSucursal() : null,
                sucursalContexto != null ? sucursalContexto.getNombre() : null,
                catalogo != null ? catalogo.colores() : List.of());
    }

    private Sucursal obtenerSucursalContextoCatalogo(Integer idSucursalVenta) {
        if (idSucursalVenta == null) {
            return null;
        }
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalVenta).orElse(null);
    }

    private ProductoVarianteDetalleResponse toDetalleVariante(
            ProductoVariante variante,
            List<Sucursal> sucursalesActivas,
            Map<Integer, Integer> cantidadesPorSucursal) {
        List<ProductoVarianteStockDetalleResponse> stocks = sucursalesActivas.stream()
                .map(sucursal -> new ProductoVarianteStockDetalleResponse(
                        sucursal.getIdSucursal(),
                        sucursal.getNombre(),
                        sucursal.getTipo() != null ? sucursal.getTipo().name() : null,
                        cantidadesPorSucursal.getOrDefault(sucursal.getIdSucursal(), 0)))
                .toList();
        int stockTotal = stocks.stream().mapToInt(item -> item.cantidad() == null ? 0 : item.cantidad()).sum();

        return new ProductoVarianteDetalleResponse(
                variante.getIdProductoVariante(),
                variante.getSku(),
                variante.getCodigoBarras(),
                variante.getColor() != null ? variante.getColor().getIdColor() : null,
                variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante.getColor() != null ? variante.getColor().getCodigo() : null,
                variante.getTalla() != null ? variante.getTalla().getIdTalla() : null,
                variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                variante.getPrecio(),
                variante.getPrecioMayor(),
                variante.getPrecioOferta(),
                variante.getOfertaInicio(),
                variante.getOfertaFin(),
                stockTotal,
                stocks,
                variante.getEstado());
    }

    private ProductoImagenDetalleResponse toImagenDetalleResponse(ProductoColorImagen imagen) {
        return new ProductoImagenDetalleResponse(
                imagen.getIdColorImagen(),
                imagen.getColor() != null ? imagen.getColor().getIdColor() : null,
                imagen.getColor() != null ? imagen.getColor().getNombre() : null,
                imagen.getColor() != null ? imagen.getColor().getCodigo() : null,
                imagen.getUrl(),
                imagen.getUrlThumb(),
                imagen.getOrden(),
                imagen.getEsPrincipal(),
                imagen.getEstado());
    }

    private Map<ProductoColorKey, ProductoColorImagenResumen> resolverImagenesPrincipales(List<Integer> productoIds) {
        Map<ProductoColorKey, ProductoColorImagenResumen> result = new HashMap<>();
        Map<ProductoColorKey, Integer> ordenActual = new HashMap<>();
        for (ProductoImagenColorRow row : productoColorImagenRepository.obtenerResumenPorProductos(productoIds)) {
            if (row.productoId() == null || row.colorId() == null) {
                continue;
            }
            ProductoColorKey key = new ProductoColorKey(row.productoId(), row.colorId());
            int orden = row.orden() == null ? Integer.MAX_VALUE : row.orden();
            Integer ordenExistente = ordenActual.get(key);
            boolean principal = Boolean.TRUE.equals(row.esPrincipal());
            boolean reemplazar = ordenExistente == null || principal || orden < ordenExistente;
            if (reemplazar) {
                ordenActual.put(key, orden);
                result.put(key, new ProductoColorImagenResumen(row.url(), row.urlThumb(), row.orden(), row.esPrincipal()));
            }
        }
        return result;
    }

    private Map<Integer, List<StockSucursalVentaResumen>> agruparStocksVenta(List<ProductoVarianteStockSucursalRow> rows) {
        Map<Integer, List<StockSucursalVentaResumen>> result = new HashMap<>();
        for (ProductoVarianteStockSucursalRow row : rows) {
            if (row.varianteId() == null) {
                continue;
            }
            result.computeIfAbsent(row.varianteId(), unused -> new ArrayList<>())
                    .add(new StockSucursalVentaResumen(row.idSucursal(), row.nombreSucursal(), row.stock()));
        }
        return result;
    }

    private void validarRolLectura(Usuario usuario) {
        if (!usuario.getRol().permiteVentas() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar productos");
        }
    }

    private void validarRolEdicion(Usuario usuario) {
        if (!usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para editar productos");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarCatalogoActivo(Color color, Talla talla) {
        if (!ESTADO_ACTIVO.equalsIgnoreCase(color.getEstado())) {
            throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
        }
        if (!ESTADO_ACTIVO.equalsIgnoreCase(talla.getEstado())) {
            throw new RuntimeException("No se puede usar la talla '" + talla.getNombre() + "' porque esta INACTIVA");
        }
    }

    private void validarStocksSolicitados(List<ProductoVarianteStockCreateItem> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            throw new RuntimeException("Ingrese stocksSucursales");
        }
        Set<Integer> ids = new HashSet<>();
        for (ProductoVarianteStockCreateItem stock : stocks) {
            if (!ids.add(stock.idSucursal())) {
                throw new RuntimeException("No puede repetir sucursal dentro de stocksSucursales");
            }
        }
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizarRequerido(String value, String message) {
        String normalizado = normalizar(value);
        if (normalizado == null) {
            throw new RuntimeException(message);
        }
        return normalizado;
    }

    private Double normalizarPrecioMayor(Double precioMayor) {
        if (precioMayor == null) {
            return null;
        }
        if (precioMayor <= 0) {
            throw new RuntimeException("El precio por mayor debe ser mayor a 0");
        }
        return precioMayor;
    }

    private Double normalizarPrecioOferta(Double precioOferta) {
        if (precioOferta == null) {
            return null;
        }
        if (precioOferta <= 0) {
            throw new RuntimeException("El precio de oferta debe ser mayor a 0");
        }
        return precioOferta;
    }

    private void validarPrecioMayor(Double precio, Double precioMayor) {
        if (precioMayor == null) {
            return;
        }
        if (precio == null || precioMayor >= precio) {
            throw new RuntimeException("El precio por mayor debe ser menor al precio regular");
        }
    }

    private void validarPrecioOferta(Double precio, Double precioOferta, LocalDateTime ofertaInicio, LocalDateTime ofertaFin) {
        if (precioOferta == null) {
            if (ofertaInicio != null || ofertaFin != null) {
                throw new RuntimeException("No puede registrar ofertaInicio/ofertaFin sin precioOferta");
            }
            return;
        }
        if (precio == null || precioOferta >= precio) {
            throw new RuntimeException("El precio de oferta debe ser menor al precio regular");
        }
        if ((ofertaInicio == null) != (ofertaFin == null)) {
            throw new RuntimeException("Debe enviar ofertaInicio y ofertaFin juntas");
        }
        if (ofertaInicio != null && ofertaFin != null && !ofertaFin.isAfter(ofertaInicio)) {
            throw new RuntimeException("ofertaFin debe ser mayor a ofertaInicio");
        }
    }

    private Double resolverPrecioVigente(Double precio, Double precioOferta, LocalDateTime ofertaInicio, LocalDateTime ofertaFin) {
        if (precioOferta == null || precio == null || precioOferta >= precio) {
            return precio;
        }
        if (ofertaInicio == null && ofertaFin == null) {
            return precioOferta;
        }
        if (ofertaInicio == null || ofertaFin == null) {
            return precio;
        }
        LocalDateTime ahora = LocalDateTime.now();
        return ahora.isBefore(ofertaInicio) || ahora.isAfter(ofertaFin) ? precio : precioOferta;
    }

    private List<ProductoColorImagen> construirImagenes(Producto producto, List<ProductoImagenCreateItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<ProductoImagenCreateItem>> porColor = new HashMap<>();
        for (ProductoImagenCreateItem item : items) {
            porColor.computeIfAbsent(item.colorId(), unused -> new ArrayList<>()).add(item);
        }

        List<ProductoColorImagen> result = new ArrayList<>();
        for (Map.Entry<Integer, List<ProductoImagenCreateItem>> entry : porColor.entrySet()) {
            Color color = colorService.obtenerPorId(entry.getKey());
            if (!ESTADO_ACTIVO.equalsIgnoreCase(color.getEstado())) {
                throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
            }
            boolean tienePrincipal = entry.getValue().stream().anyMatch(item -> Boolean.TRUE.equals(item.esPrincipal()));
            Integer ordenPrincipal = tienePrincipal
                    ? null
                    : entry.getValue().stream().map(ProductoImagenCreateItem::orden).filter(Objects::nonNull).min(Integer::compareTo).orElse(null);
            for (ProductoImagenCreateItem item : entry.getValue()) {
                ProductoColorImagen imagen = new ProductoColorImagen();
                imagen.setProducto(producto);
                imagen.setColor(color);
                imagen.setUrl(normalizarRequerido(item.url(), "La url de la imagen es obligatoria"));
                imagen.setUrlThumb(normalizarRequerido(item.urlThumb(), "La url del thumbnail es obligatoria"));
                imagen.setOrden(item.orden());
                imagen.setEsPrincipal(Boolean.TRUE.equals(item.esPrincipal()) || Objects.equals(ordenPrincipal, item.orden()));
                imagen.setEstado(ESTADO_ACTIVO);
                result.add(imagen);
            }
        }
        return result;
    }

    private Set<String> extraerUrls(List<ProductoColorImagen> imagenes) {
        Set<String> urls = new HashSet<>();
        if (imagenes == null) {
            return urls;
        }
        for (ProductoColorImagen imagen : imagenes) {
            agregarUrl(urls, imagen.getUrl());
            agregarUrl(urls, imagen.getUrlThumb());
        }
        return urls;
    }

    private void agregarUrl(Set<String> urls, String url) {
        if (url != null && !url.isBlank()) {
            urls.add(url.trim());
        }
    }

    private void eliminarImagenesObsoletas(Set<String> actuales, Set<String> vigentes) {
        for (String url : actuales) {
            if (vigentes.contains(url)) {
                continue;
            }
            try {
                s3StorageService.deleteByUrl(url);
            } catch (RuntimeException e) {
                log.warn("No se pudo eliminar imagen obsoleta en S3: {}", url, e);
            }
        }
    }

    private String claveVariante(Integer colorId, Integer tallaId) {
        return colorId + ":" + tallaId;
    }

    private record VarianteCatalogo(String sku, Double precioMin, Double precioMax, List<ProductoColorResumen> colores) {
    }

    private static class VarianteCatalogoBuilder {
        private String sku;
        private Double precioMin;
        private Double precioMax;
        private final Map<Integer, ColorBuilder> colores = new LinkedHashMap<>();

        private void acumularReferencia(ProductoVarianteResumenRow row) {
            if (sku == null && row.sku() != null) {
                sku = row.sku();
            }
        }

        private void acumularPrecio(Double precio) {
            if (precio == null) {
                return;
            }
            if (precioMin == null || precio < precioMin) {
                precioMin = precio;
            }
            if (precioMax == null || precio > precioMax) {
                precioMax = precio;
            }
        }

        private void acumularColor(
                ProductoVarianteResumenRow row,
                List<StockSucursalVentaResumen> stocksSucursales,
                Map<ProductoColorKey, ProductoColorImagenResumen> imagenesPrincipales) {
            if (row.colorId() == null) {
                return;
            }
            ProductoColorKey key = row.productoId() != null
                    ? new ProductoColorKey(row.productoId(), row.colorId())
                    : null;
            ColorBuilder color = colores.computeIfAbsent(
                    row.colorId(),
                    unused -> new ColorBuilder(
                            row.colorId(),
                            row.colorNombre(),
                            row.colorHex(),
                            key != null ? imagenesPrincipales.get(key) : null));
            color.acumularTalla(row, stocksSucursales);
        }

        private VarianteCatalogo build() {
            List<ProductoColorResumen> coloresOrdenados = colores.values().stream()
                    .map(ColorBuilder::build)
                    .sorted(Comparator.comparing(ProductoColorResumen::colorId, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            return new VarianteCatalogo(sku, precioMin, precioMax, coloresOrdenados);
        }
    }

    private static class ColorBuilder {
        private final Integer colorId;
        private final String nombre;
        private final String hex;
        private final ProductoColorImagenResumen imagenPrincipal;
        private final List<ProductoTallaResumen> tallas = new ArrayList<>();

        private ColorBuilder(Integer colorId, String nombre, String hex, ProductoColorImagenResumen imagenPrincipal) {
            this.colorId = colorId;
            this.nombre = nombre;
            this.hex = hex;
            this.imagenPrincipal = imagenPrincipal;
        }

        private void acumularTalla(ProductoVarianteResumenRow row, List<StockSucursalVentaResumen> stocksSucursales) {
            tallas.add(new ProductoTallaResumen(
                    row.varianteId(),
                    row.tallaId(),
                    row.tallaNombre(),
                    row.sku(),
                    row.codigoBarras(),
                    row.precio(),
                    row.precioMayor(),
                    row.precioOferta(),
                    row.ofertaInicio(),
                    row.ofertaFin(),
                    row.stock() == null ? 0 : row.stock().intValue(),
                    stocksSucursales,
                    row.estado()));
        }

        private ProductoColorResumen build() {
            List<ProductoTallaResumen> ordenadas = tallas.stream()
                    .sorted(Comparator.comparing(ProductoTallaResumen::tallaId, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            return new ProductoColorResumen(colorId, nombre, hex, imagenPrincipal, ordenadas);
        }
    }

    private record ProductoColorKey(Integer productoId, Integer colorId) {
    }
}
