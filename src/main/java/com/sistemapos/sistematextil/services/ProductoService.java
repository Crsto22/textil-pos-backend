package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoCreateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoCompletoResponse;
import com.sistemapos.sistematextil.util.producto.ProductoColorImagenResumen;
import com.sistemapos.sistematextil.util.producto.ProductoColorResumen;
import com.sistemapos.sistematextil.util.producto.ProductoCreateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.producto.ProductoImagenCreateItem;
import com.sistemapos.sistematextil.util.producto.ProductoListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoTallaResumen;
import com.sistemapos.sistematextil.util.producto.ProductoUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteCreateItem;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final ColorService colorService;
    private final TallaService tallaService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<ProductoListItemResponse> listarPaginado(int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Page<Producto> productos = esAdministrador(usuarioAutenticado)
                ? productoRepository.findAllByOrderByIdProductoAsc(pageable)
                : productoRepository.findBySucursal_IdSucursalOrderByIdProductoAsc(
                        obtenerIdSucursalUsuario(usuarioAutenticado),
                        pageable);

        return PagedResponse.fromPage(productos.map(this::toListItemResponse));
    }

    public PagedResponse<ProductoListItemResponse> buscarPaginado(String q, int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page, correoUsuarioAutenticado);
        }

        Integer idSucursalFiltro = esAdministrador(usuarioAutenticado) ? null : obtenerIdSucursalUsuario(usuarioAutenticado);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Page<Producto> productos = productoRepository.buscarConFiltros(term, idSucursalFiltro, pageable);

        return PagedResponse.fromPage(productos.map(this::toListItemResponse));
    }

    public PagedResponse<ProductoListadoResumenResponse> listarResumenPaginado(
            int page,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Page<Producto> productos = esAdministrador(usuarioAutenticado)
                ? productoRepository.findAllByOrderByIdProductoAsc(pageable)
                : productoRepository.findBySucursal_IdSucursalOrderByIdProductoAsc(
                        obtenerIdSucursalUsuario(usuarioAutenticado),
                        pageable);

        List<Integer> productoIds = productos.getContent().stream()
                .map(Producto::getIdProducto)
                .toList();

        Map<Integer, List<ProductoColorResumen>> coloresPorProducto;
        Map<Integer, PrecioRange> preciosPorProducto;
        if (productoIds.isEmpty()) {
            coloresPorProducto = Map.of();
            preciosPorProducto = Map.of();
        } else {
            List<ProductoVarianteResumenRow> variantesResumen = productoVarianteRepository.obtenerResumenPorProductos(productoIds);
            Map<Integer, Map<Integer, List<ProductoTallaResumen>>> tallasPorProductoColor =
                    construirTallasPorProductoColor(variantesResumen);
            preciosPorProducto = construirPreciosPorProducto(variantesResumen);
            coloresPorProducto = construirColoresPorProducto(productoIds, tallasPorProductoColor);
        }

        List<ProductoListadoResumenResponse> content = productos.getContent().stream()
                .map(producto -> {
                    PrecioRange precioRange = preciosPorProducto.get(producto.getIdProducto());
                    return toResumenResponse(
                            producto,
                            precioRange,
                            coloresPorProducto.getOrDefault(producto.getIdProducto(), List.of()));
                })
                .toList();

        Page<ProductoListadoResumenResponse> mapped = new PageImpl<>(
                content,
                productos.getPageable(),
                productos.getTotalElements());

        return PagedResponse.fromPage(mapped);
    }

    @Transactional
    public ProductoListItemResponse insertar(ProductoCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Sucursal sucursal = resolverSucursalParaEscritura(request.idSucursal(), usuarioAutenticado);
        Categoria categoria = obtenerCategoriaActivaEnSucursal(request.idCategoria(), sucursal.getIdSucursal());

        String sku = normalizarRequerido(request.sku(), "El SKU es obligatorio");
        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());
        String codigoExterno = normalizar(request.codigoExterno());

        validarSkuUnicoPorSucursal(sku, sucursal.getIdSucursal(), null);
        validarCodigoExternoUnico(codigoExterno, null);

        Producto producto = new Producto();
        producto.setSucursal(sucursal);
        producto.setCategoria(categoria);
        producto.setSku(sku);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setCodigoExterno(codigoExterno);
        producto.setFechaCreacion(LocalDateTime.now());
        producto.setEstado("ACTIVO");

        try {
            Producto creado = productoRepository.save(producto);
            return toListItemResponse(creado);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se pudo guardar el producto por restriccion de datos unicos");
        }
    }

    @Transactional
    public ProductoCompletoResponse insertarCompleto(
            ProductoCompletoCreateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Sucursal sucursal = resolverSucursalParaEscritura(request.idSucursal(), usuarioAutenticado);
        Categoria categoria = obtenerCategoriaActivaEnSucursal(request.idCategoria(), sucursal.getIdSucursal());

        String sku = normalizarRequerido(request.sku(), "El SKU es obligatorio");
        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());
        String codigoExterno = normalizar(request.codigoExterno());

        validarSkuUnicoPorSucursal(sku, sucursal.getIdSucursal(), null);
        validarCodigoExternoUnico(codigoExterno, null);

        Producto producto = new Producto();
        producto.setSucursal(sucursal);
        producto.setCategoria(categoria);
        producto.setSku(sku);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setCodigoExterno(codigoExterno);
        producto.setFechaCreacion(LocalDateTime.now());
        producto.setEstado("ACTIVO");

        Producto creado = productoRepository.save(producto);

        List<ProductoVariante> variantes = construirVariantes(creado, sucursal, request.variantes());
        if (!variantes.isEmpty()) {
            productoVarianteRepository.saveAll(variantes);
        }

        List<ProductoColorImagen> imagenes = construirImagenes(creado, request.imagenes());
        if (!imagenes.isEmpty()) {
            productoColorImagenRepository.saveAll(imagenes);
        }

        return new ProductoCompletoResponse(
                toListItemResponse(creado),
                variantes.size(),
                imagenes.size());
    }

    @Transactional
    public ProductoListItemResponse actualizar(
            Integer idProducto,
            ProductoUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);
        Sucursal sucursalDestino = resolverSucursalParaActualizacion(producto, request.idSucursal(), usuarioAutenticado);
        Categoria categoriaDestino = obtenerCategoriaActivaEnSucursal(request.idCategoria(), sucursalDestino.getIdSucursal());

        String sku = normalizarRequerido(request.sku(), "El SKU es obligatorio");
        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());
        String codigoExterno = normalizar(request.codigoExterno());

        validarSkuUnicoPorSucursal(sku, sucursalDestino.getIdSucursal(), idProducto);
        validarCodigoExternoUnico(codigoExterno, idProducto);

        producto.setSucursal(sucursalDestino);
        producto.setCategoria(categoriaDestino);
        producto.setSku(sku);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setCodigoExterno(codigoExterno);

        try {
            Producto actualizado = productoRepository.save(producto);
            return toListItemResponse(actualizado);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se pudo actualizar el producto por restriccion de datos unicos");
        }
    }

    @Transactional
    public void eliminar(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);
        if (productoRepository.estaEnUso(producto.getIdProducto())) {
            throw new RuntimeException("No se puede eliminar el producto '" + producto.getNombre()
                    + "' porque esta asociado a variantes. Te sugiero archivarlo.");
        }
        productoRepository.deleteById(producto.getIdProducto());
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepository.findByIdProducto(id)
                .orElseThrow(() -> new RuntimeException("Producto con ID " + id + " no encontrado"));
    }

    private Producto obtenerProductoConAlcance(Integer idProducto, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return productoRepository.findByIdProducto(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto con ID " + idProducto + " no encontrado"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return productoRepository.findByIdProductoAndSucursal_IdSucursal(idProducto, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Producto con ID " + idProducto + " no encontrado"));
    }

    private Sucursal resolverSucursalParaEscritura(Integer idSucursalRequest, Usuario usuarioAutenticado) {
        Integer idSucursalDestino = esAdministrador(usuarioAutenticado)
                ? idSucursalRequeridaParaAdmin(idSucursalRequest)
                : obtenerIdSucursalUsuario(usuarioAutenticado);
        return obtenerSucursalActiva(idSucursalDestino);
    }

    private Sucursal resolverSucursalParaActualizacion(
            Producto productoOriginal,
            Integer idSucursalRequest,
            Usuario usuarioAutenticado) {
        Integer idSucursalDestino;
        if (esAdministrador(usuarioAutenticado)) {
            idSucursalDestino = idSucursalRequest != null
                    ? idSucursalRequest
                    : productoOriginal.getSucursal().getIdSucursal();
        } else {
            idSucursalDestino = obtenerIdSucursalUsuario(usuarioAutenticado);
        }
        return obtenerSucursalActiva(idSucursalDestino);
    }

    private Categoria obtenerCategoriaActivaEnSucursal(Integer idCategoria, Integer idSucursal) {
        Categoria categoria = categoriaRepository.findByIdCategoriaAndSucursal_IdSucursal(idCategoria, idSucursal)
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada en la sucursal seleccionada"));
        if (!"ACTIVO".equalsIgnoreCase(categoria.getEstado())) {
            throw new RuntimeException("No se puede usar una categoria INACTIVA");
        }
        return categoria;
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        if (!"ACTIVO".equalsIgnoreCase(sucursal.getEstado())) {
            throw new RuntimeException("No se pueden gestionar productos en una sucursal INACTIVA");
        }
        return sucursal;
    }

    private void validarSkuUnicoPorSucursal(String sku, Integer idSucursal, Integer idProducto) {
        boolean existe = idProducto == null
                ? productoRepository.existsBySkuAndSucursalIdSucursal(sku, idSucursal)
                : productoRepository.existsBySkuAndSucursalIdSucursalAndIdProductoNot(sku, idSucursal, idProducto);
        if (existe) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }
    }

    private void validarCodigoExternoUnico(String codigoExterno, Integer idProducto) {
        if (codigoExterno == null) {
            return;
        }
        boolean existe = idProducto == null
                ? productoRepository.existsByCodigoExterno(codigoExterno)
                : productoRepository.existsByCodigoExternoAndIdProductoNot(codigoExterno, idProducto);
        if (existe) {
            throw new RuntimeException("El codigo externo '" + codigoExterno + "' ya pertenece a otro producto");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private Integer idSucursalRequeridaParaAdmin(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            throw new RuntimeException("Ingrese idSucursal");
        }
        return idSucursalRequest;
    }

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para gestionar productos");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private String normalizarRequerido(String value, String message) {
        String normalizado = normalizar(value);
        if (normalizado == null) {
            throw new RuntimeException(message);
        }
        return normalizado;
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProductoListItemResponse toListItemResponse(Producto producto) {
        Integer idCategoria = producto.getCategoria() != null ? producto.getCategoria().getIdCategoria() : null;
        String nombreCategoria = producto.getCategoria() != null ? producto.getCategoria().getNombreCategoria() : null;
        Integer idSucursal = producto.getSucursal() != null ? producto.getSucursal().getIdSucursal() : null;
        String nombreSucursal = producto.getSucursal() != null ? producto.getSucursal().getNombre() : null;

        return new ProductoListItemResponse(
                producto.getIdProducto(),
                producto.getSku(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
                producto.getCodigoExterno(),
                idCategoria,
                nombreCategoria,
                idSucursal,
                nombreSucursal);
    }

    private ProductoListadoResumenResponse toResumenResponse(
            Producto producto,
            PrecioRange precioRange,
            List<ProductoColorResumen> colores) {
        Integer idCategoria = producto.getCategoria() != null ? producto.getCategoria().getIdCategoria() : null;
        String nombreCategoria = producto.getCategoria() != null ? producto.getCategoria().getNombreCategoria() : null;
        Integer idSucursal = producto.getSucursal() != null ? producto.getSucursal().getIdSucursal() : null;
        String nombreSucursal = producto.getSucursal() != null ? producto.getSucursal().getNombre() : null;
        Double precioMin = precioRange != null ? precioRange.min : null;
        Double precioMax = precioRange != null ? precioRange.max : null;

        return new ProductoListadoResumenResponse(
                producto.getIdProducto(),
                producto.getSku(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
                producto.getCodigoExterno(),
                precioMin,
                precioMax,
                idCategoria,
                nombreCategoria,
                idSucursal,
                nombreSucursal,
                colores);
    }

    private List<ProductoVariante> construirVariantes(
            Producto producto,
            Sucursal sucursal,
            List<ProductoVarianteCreateItem> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            throw new RuntimeException("Ingrese variantes del producto");
        }

        Set<String> combinaciones = new HashSet<>();
        Map<Integer, Color> coloresCache = new HashMap<>();
        Map<Integer, Talla> tallasCache = new HashMap<>();

        List<ProductoVariante> result = variantes.stream()
                .map(item -> {
                    if (item.colorId() == null || item.tallaId() == null) {
                        throw new RuntimeException("Cada variante debe incluir colorId y tallaId");
                    }
                    String key = item.colorId() + "-" + item.tallaId();
                    if (!combinaciones.add(key)) {
                        throw new RuntimeException("No puede repetir la misma combinacion de color y talla");
                    }

                    Color color = coloresCache.computeIfAbsent(item.colorId(), id -> colorService.obtenerPorId(id));
                    if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
                        throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
                    }
                    Talla talla = tallasCache.computeIfAbsent(item.tallaId(), id -> tallaService.obtenerPorId(id));
                    if (!"ACTIVO".equalsIgnoreCase(talla.getEstado())) {
                        throw new RuntimeException("No se puede usar la talla '" + talla.getNombre() + "' porque esta INACTIVA");
                    }

                    ProductoVariante variante = new ProductoVariante();
                    variante.setProducto(producto);
                    variante.setSucursal(sucursal);
                    variante.setColor(color);
                    variante.setTalla(talla);
                    variante.setPrecio(item.precio());
                    variante.setStock(item.stock());
                    variante.setEstado("ACTIVO");
                    return variante;
                })
                .toList();

        return result;
    }

    private Map<Integer, List<ProductoColorResumen>> construirColoresPorProducto(
            List<Integer> productoIds,
            Map<Integer, Map<Integer, List<ProductoTallaResumen>>> tallasPorProductoColor) {
        List<ProductoImagenColorRow> rows = productoColorImagenRepository.obtenerResumenPorProductos(productoIds);
        Map<Integer, Map<Integer, ColorImagenPick>> porProducto = new HashMap<>();

        for (ProductoImagenColorRow row : rows) {
            if (row.productoId() == null || row.colorId() == null) {
                continue;
            }
            Map<Integer, ColorImagenPick> porColor = porProducto.computeIfAbsent(
                    row.productoId(),
                    key -> new HashMap<>());

            ColorImagenPick existing = porColor.get(row.colorId());
            boolean rowPrincipal = Boolean.TRUE.equals(row.esPrincipal());
            int rowOrden = row.orden() == null ? Integer.MAX_VALUE : row.orden();

            if (existing == null || debeReemplazar(existing, rowPrincipal, rowOrden)) {
                ProductoColorImagenResumen imagen = new ProductoColorImagenResumen(
                        row.url(),
                        row.urlThumb(),
                        row.orden(),
                        row.esPrincipal());
                ColorImagenPick pick = new ColorImagenPick(
                        row.colorId(),
                        row.colorNombre(),
                        row.colorHex(),
                        imagen,
                        rowOrden,
                        rowPrincipal);
                porColor.put(row.colorId(), pick);
            }
        }

        Map<Integer, List<ProductoColorResumen>> result = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, ColorImagenPick>> entry : porProducto.entrySet()) {
            List<ProductoColorResumen> colores = entry.getValue().values().stream()
                    .sorted(Comparator.comparingInt(ColorImagenPick::colorId))
                    .map(pick -> new ProductoColorResumen(
                            pick.colorId(),
                            pick.nombre(),
                            pick.hex(),
                            pick.imagen(),
                            tallasPorProductoColor
                                    .getOrDefault(entry.getKey(), Map.of())
                                    .getOrDefault(pick.colorId(), List.of())))
                    .toList();
            result.put(entry.getKey(), colores);
        }

        return result;
    }

    private boolean debeReemplazar(ColorImagenPick existing, boolean rowPrincipal, int rowOrden) {
        if (rowPrincipal && !existing.esPrincipal()) {
            return true;
        }
        if (!rowPrincipal && existing.esPrincipal()) {
            return false;
        }
        return rowOrden < existing.orden();
    }

    private static class ColorImagenPick {
        private final Integer colorId;
        private final String nombre;
        private final String hex;
        private final ProductoColorImagenResumen imagen;
        private final int orden;
        private final boolean esPrincipal;

        private ColorImagenPick(
                Integer colorId,
                String nombre,
                String hex,
                ProductoColorImagenResumen imagen,
                int orden,
                boolean esPrincipal) {
            this.colorId = colorId;
            this.nombre = nombre;
            this.hex = hex;
            this.imagen = imagen;
            this.orden = orden;
            this.esPrincipal = esPrincipal;
        }

        private Integer colorId() {
            return colorId;
        }

        private String nombre() {
            return nombre;
        }

        private String hex() {
            return hex;
        }

        private ProductoColorImagenResumen imagen() {
            return imagen;
        }

        private int orden() {
            return orden;
        }

        private boolean esPrincipal() {
            return esPrincipal;
        }
    }

    private Map<Integer, Map<Integer, List<ProductoTallaResumen>>> construirTallasPorProductoColor(
            List<ProductoVarianteResumenRow> variantesResumen) {
        Map<Integer, Map<Integer, Map<Integer, ProductoTallaResumen>>> acumulado = new HashMap<>();

        for (ProductoVarianteResumenRow row : variantesResumen) {
            if (row.productoId() == null || row.colorId() == null || row.tallaId() == null) {
                continue;
            }
            Map<Integer, Map<Integer, ProductoTallaResumen>> porColor = acumulado.computeIfAbsent(
                    row.productoId(),
                    key -> new HashMap<>());
            Map<Integer, ProductoTallaResumen> tallas = porColor.computeIfAbsent(
                    row.colorId(),
                    key -> new HashMap<>());
            tallas.putIfAbsent(
                    row.tallaId(),
                    new ProductoTallaResumen(row.tallaId(), row.tallaNombre()));
        }

        Map<Integer, Map<Integer, List<ProductoTallaResumen>>> result = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Map<Integer, ProductoTallaResumen>>> productoEntry : acumulado.entrySet()) {
            Map<Integer, List<ProductoTallaResumen>> porColor = new HashMap<>();
            for (Map.Entry<Integer, Map<Integer, ProductoTallaResumen>> colorEntry : productoEntry.getValue().entrySet()) {
                List<ProductoTallaResumen> list = colorEntry.getValue().values().stream()
                        .sorted(Comparator.comparingInt(ProductoTallaResumen::tallaId))
                        .toList();
                porColor.put(colorEntry.getKey(), list);
            }
            result.put(productoEntry.getKey(), porColor);
        }

        return result;
    }

    private Map<Integer, PrecioRange> construirPreciosPorProducto(
            List<ProductoVarianteResumenRow> variantesResumen) {
        Map<Integer, PrecioRange> precios = new HashMap<>();

        for (ProductoVarianteResumenRow row : variantesResumen) {
            if (row.productoId() == null || row.precio() == null) {
                continue;
            }
            PrecioRange range = precios.computeIfAbsent(row.productoId(), key -> new PrecioRange());
            range.acumular(row.precio());
        }

        return precios;
    }

    private static class PrecioRange {
        private Double min;
        private Double max;

        private void acumular(Double precio) {
            if (precio == null) {
                return;
            }
            if (min == null || precio < min) {
                min = precio;
            }
            if (max == null || precio > max) {
                max = precio;
            }
        }
    }

    private List<ProductoColorImagen> construirImagenes(
            Producto producto,
            List<ProductoImagenCreateItem> imagenes) {
        if (imagenes == null || imagenes.isEmpty()) {
            throw new RuntimeException("Ingrese imagenes del producto");
        }

        Map<Integer, List<ProductoImagenCreateItem>> porColor = new HashMap<>();
        for (ProductoImagenCreateItem item : imagenes) {
            if (item.colorId() == null) {
                throw new RuntimeException("Cada imagen debe incluir colorId");
            }
            porColor.computeIfAbsent(item.colorId(), key -> new ArrayList<>()).add(item);
        }

        Map<Integer, Color> coloresCache = new HashMap<>();
        List<ProductoColorImagen> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<Integer, List<ProductoImagenCreateItem>> entry : porColor.entrySet()) {
            Integer colorId = entry.getKey();
            List<ProductoImagenCreateItem> items = entry.getValue();

            if (items.size() > 5) {
                throw new RuntimeException("Maximo 5 imagenes por color");
            }

            long principales = items.stream()
                    .filter(item -> Boolean.TRUE.equals(item.esPrincipal()))
                    .count();
            if (principales > 1) {
                throw new RuntimeException("Solo una imagen principal por color");
            }

            Set<Integer> ordenes = new HashSet<>();
            for (ProductoImagenCreateItem item : items) {
                if (item.orden() == null || item.orden() < 1 || item.orden() > 5) {
                    throw new RuntimeException("El orden de imagen debe estar entre 1 y 5");
                }
                if (!ordenes.add(item.orden())) {
                    throw new RuntimeException("No se puede repetir el orden de imagen por color");
                }
            }

            Integer ordenPrincipalAuto = null;
            if (principales == 0) {
                ordenPrincipalAuto = items.stream()
                        .min(Comparator.comparingInt(ProductoImagenCreateItem::orden))
                        .map(ProductoImagenCreateItem::orden)
                        .orElse(null);
            }

            Color color = coloresCache.computeIfAbsent(colorId, id -> colorService.obtenerPorId(id));
            if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
                throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
            }

            for (ProductoImagenCreateItem item : items) {
                ProductoColorImagen imagen = new ProductoColorImagen();
                imagen.setProducto(producto);
                imagen.setColor(color);
                imagen.setUrl(normalizarRequerido(item.url(), "La url de la imagen es obligatoria"));
                imagen.setUrlThumb(normalizarRequerido(item.urlThumb(), "La url del thumbnail es obligatoria"));
                imagen.setOrden(item.orden());
                boolean esPrincipal = Boolean.TRUE.equals(item.esPrincipal())
                        || (ordenPrincipalAuto != null && ordenPrincipalAuto.equals(item.orden()));
                imagen.setEsPrincipal(esPrincipal);
                imagen.setEstado("ACTIVO");
                imagen.setCreatedAt(now);
                imagen.setUpdatedAt(now);
                result.add(imagen);
            }
        }

        return result;
    }
}
