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
import com.sistemapos.sistematextil.util.producto.ProductoCompletoUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoColorImagenResumen;
import com.sistemapos.sistematextil.util.producto.ProductoColorResumen;
import com.sistemapos.sistematextil.util.producto.ProductoDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoCreateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.producto.ProductoImagenCreateItem;
import com.sistemapos.sistematextil.util.producto.ProductoImagenDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoTallaResumen;
import com.sistemapos.sistematextil.util.producto.ProductoUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteCreateItem;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private static final String VALOR_ACTIVO = "ACTIVO";
    private static final String VALOR_INACTIVO = "INACTIVO";
    private static final String ESTADO_PRODUCTO_ACTIVO = VALOR_ACTIVO;
    private static final String ESTADO_PRODUCTO_ARCHIVADO = "ARCHIVADO";
    private static final String ESTADO_VARIANTE_ACTIVA = VALOR_ACTIVO;
    private static final String ESTADO_VARIANTE_INACTIVA = "AGOTADO";
    private static final String ESTADO_IMAGEN_INACTIVA = VALOR_INACTIVO;

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final ColorService colorService;
    private final TallaService tallaService;
    private final S3StorageService s3StorageService;

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
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Integer idSucursalFiltro = resolverIdSucursalFiltroListado(usuarioAutenticado, idSucursal);
        Page<Producto> productos = productoRepository.buscarConFiltros(
                null,
                idSucursalFiltro,
                idCategoria,
                idColor,
                conOferta,
                ESTADO_PRODUCTO_ARCHIVADO,
                pageable);

        Map<Integer, VarianteReferencia> referencias = obtenerReferenciasVariantes(productos.getContent());
        return PagedResponse.fromPage(productos.map(producto ->
                toListItemResponse(producto, referencias.get(producto.getIdProducto()))));
    }

    public PagedResponse<ProductoListadoResumenResponse> buscarPaginado(
            String q,
            int page,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        String term = normalizar(q);
        if (term == null) {
            return listarResumenPaginado(page, idCategoria, idColor, conOferta, idSucursal, correoUsuarioAutenticado);
        }

        Integer idSucursalFiltro = resolverIdSucursalFiltroListado(usuarioAutenticado, idSucursal);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Page<Producto> productos = productoRepository.buscarConFiltros(
                term,
                idSucursalFiltro,
                idCategoria,
                idColor,
                conOferta,
                ESTADO_PRODUCTO_ARCHIVADO,
                pageable);

        List<Integer> productoIds = productos.getContent().stream()
                .map(Producto::getIdProducto)
                .toList();

        Map<Integer, List<ProductoColorResumen>> coloresPorProducto;
        Map<Integer, PrecioRange> preciosPorProducto;
        Map<Integer, VarianteReferencia> referenciasPorProducto;
        if (productoIds.isEmpty()) {
            coloresPorProducto = Map.of();
            preciosPorProducto = Map.of();
            referenciasPorProducto = Map.of();
        } else {
            List<ProductoVarianteResumenRow> variantesResumen = productoVarianteRepository.obtenerResumenPorProductos(productoIds);
            Map<Integer, Map<Integer, List<ProductoTallaResumen>>> tallasPorProductoColor =
                    construirTallasPorProductoColor(variantesResumen);
            preciosPorProducto = construirPreciosPorProducto(variantesResumen);
            coloresPorProducto = construirColoresPorProducto(productoIds, variantesResumen, tallasPorProductoColor);
            referenciasPorProducto = construirReferenciasVariantes(variantesResumen);
        }

        List<ProductoListadoResumenResponse> content = productos.getContent().stream()
                .map(producto -> {
                    PrecioRange precioRange = preciosPorProducto.get(producto.getIdProducto());
                    return toResumenResponse(
                            producto,
                            referenciasPorProducto.get(producto.getIdProducto()),
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

    public PagedResponse<ProductoListadoResumenResponse> listarResumenPaginado(
            int page,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Integer idSucursalFiltro = resolverIdSucursalFiltroListado(usuarioAutenticado, idSucursal);
        Page<Producto> productos = productoRepository.buscarConFiltros(
                null,
                idSucursalFiltro,
                idCategoria,
                idColor,
                conOferta,
                ESTADO_PRODUCTO_ARCHIVADO,
                pageable);

        List<Integer> productoIds = productos.getContent().stream()
                .map(Producto::getIdProducto)
                .toList();

        Map<Integer, List<ProductoColorResumen>> coloresPorProducto;
        Map<Integer, PrecioRange> preciosPorProducto;
        Map<Integer, VarianteReferencia> referenciasPorProducto;
        if (productoIds.isEmpty()) {
            coloresPorProducto = Map.of();
            preciosPorProducto = Map.of();
            referenciasPorProducto = Map.of();
        } else {
            List<ProductoVarianteResumenRow> variantesResumen = productoVarianteRepository.obtenerResumenPorProductos(productoIds);
            Map<Integer, Map<Integer, List<ProductoTallaResumen>>> tallasPorProductoColor =
                    construirTallasPorProductoColor(variantesResumen);
            preciosPorProducto = construirPreciosPorProducto(variantesResumen);
            coloresPorProducto = construirColoresPorProducto(productoIds, variantesResumen, tallasPorProductoColor);
            referenciasPorProducto = construirReferenciasVariantes(variantesResumen);
        }

        List<ProductoListadoResumenResponse> content = productos.getContent().stream()
                .map(producto -> {
                    PrecioRange precioRange = preciosPorProducto.get(producto.getIdProducto());
                    return toResumenResponse(
                            producto,
                            referenciasPorProducto.get(producto.getIdProducto()),
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

        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());

        Producto producto = new Producto();
        producto.setSucursal(sucursal);
        producto.setCategoria(categoria);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setFechaCreacion(LocalDateTime.now());
        producto.setEstado(ESTADO_PRODUCTO_ACTIVO);

        try {
            Producto creado = productoRepository.save(producto);
            return toListItemResponse(creado, null);
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

        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());

        Producto producto = new Producto();
        producto.setSucursal(sucursal);
        producto.setCategoria(categoria);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setFechaCreacion(LocalDateTime.now());
        producto.setEstado(ESTADO_PRODUCTO_ACTIVO);

        Producto creado = productoRepository.save(producto);

        List<ProductoVariante> variantes = construirVariantes(creado, sucursal, request.variantes(), null);
        if (!variantes.isEmpty()) {
            productoVarianteRepository.saveAll(variantes);
        }

        List<ProductoColorImagen> imagenes = construirImagenes(creado, request.imagenes());
        if (!imagenes.isEmpty()) {
            productoColorImagenRepository.saveAll(imagenes);
        }

        return new ProductoCompletoResponse(
                toListItemResponse(creado, resolverReferenciaDesdeVariantes(variantes)),
                variantes.size(),
                imagenes.size());
    }

    @Transactional
    public ProductoCompletoResponse actualizarCompleto(
            Integer idProducto,
            ProductoCompletoUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        validarRolEdicionProducto(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);
        Sucursal sucursalDestino = resolverSucursalParaActualizacion(producto, request.idSucursal(), usuarioAutenticado);
        Categoria categoriaDestino = obtenerCategoriaActivaEnSucursal(request.idCategoria(), sucursalDestino.getIdSucursal());

        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());

        producto.setSucursal(sucursalDestino);
        producto.setCategoria(categoriaDestino);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);

        Producto actualizado = productoRepository.save(producto);

        List<ProductoVariante> variantesActualizadas = sincronizarVariantesEnActualizacion(
                actualizado,
                sucursalDestino,
                request.variantes(),
                idProducto);

        List<ProductoColorImagen> imagenesActuales = productoColorImagenRepository.findByProductoIdProducto(idProducto);
        Set<String> urlsActuales = extraerUrlsImagenes(imagenesActuales);

        productoColorImagenRepository.deleteByProductoIdProducto(idProducto);
        productoColorImagenRepository.flush();
        List<ProductoColorImagen> imagenesNuevas = construirImagenes(actualizado, request.imagenes());
        if (!imagenesNuevas.isEmpty()) {
            productoColorImagenRepository.saveAll(imagenesNuevas);
        }

        Set<Integer> coloresImagenes = imagenesNuevas.stream()
                .map(ProductoColorImagen::getColor)
                .filter(color -> color != null && color.getIdColor() != null)
                .map(color -> color.getIdColor())
                .collect(java.util.stream.Collectors.toSet());
        for (Integer colorId : coloresImagenes) {
            limpiarImagenesColorSiNoHayVariantesActivas(idProducto, colorId);
        }

        List<ProductoColorImagen> imagenesVigentes = productoColorImagenRepository.findByProductoIdProducto(idProducto).stream()
                .filter(img -> img != null
                        && "ACTIVO".equalsIgnoreCase(img.getEstado())
                        && img.getDeletedAt() == null)
                .toList();
        Set<String> urlsNuevas = extraerUrlsImagenes(imagenesVigentes);
        eliminarImagenesObsoletasEnS3(urlsActuales, urlsNuevas);

        return new ProductoCompletoResponse(
                toListItemResponse(actualizado, resolverReferenciaDesdeVariantes(variantesActualizadas)),
                variantesActualizadas.size(),
                imagenesNuevas.size());
    }

    @Transactional
    public ProductoListItemResponse actualizar(
            Integer idProducto,
            ProductoUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        validarRolEdicionProducto(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);
        Sucursal sucursalDestino = resolverSucursalParaActualizacion(producto, request.idSucursal(), usuarioAutenticado);
        Categoria categoriaDestino = obtenerCategoriaActivaEnSucursal(request.idCategoria(), sucursalDestino.getIdSucursal());

        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());

        producto.setSucursal(sucursalDestino);
        producto.setCategoria(categoriaDestino);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);

        try {
            Producto actualizado = productoRepository.save(producto);
            VarianteReferencia referencia = productoVarianteRepository
                    .findFirstByProductoIdProductoAndDeletedAtIsNullOrderByIdProductoVarianteAsc(
                            actualizado.getIdProducto())
                    .map(variante -> new VarianteReferencia(variante.getSku()))
                    .orElse(null);
            return toListItemResponse(actualizado, referencia);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se pudo actualizar el producto por restriccion de datos unicos");
        }
    }

    @Transactional
    public void eliminar(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        validarRolEdicionProducto(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);
        producto.setEstado(ESTADO_PRODUCTO_ARCHIVADO);
        productoRepository.save(producto);

        List<ProductoVariante> variantes = productoVarianteRepository
                .findByProductoIdProductoAndDeletedAtIsNull(producto.getIdProducto());
        if (!variantes.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (ProductoVariante variante : variantes) {
                if (variante == null) {
                    continue;
                }
                variante.setEstado(ESTADO_VARIANTE_INACTIVA);
                variante.setActivo(VALOR_INACTIVO);
                variante.setDeletedAt(now);
            }
            productoVarianteRepository.saveAll(variantes);
        }

        List<ProductoColorImagen> imagenes = productoColorImagenRepository.findByProductoIdProducto(producto.getIdProducto());
        if (!imagenes.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (ProductoColorImagen imagen : imagenes) {
                if (imagen == null) {
                    continue;
                }
                imagen.setEstado(ESTADO_IMAGEN_INACTIVA);
                imagen.setEsPrincipal(false);
                imagen.setDeletedAt(now);
            }
            productoColorImagenRepository.saveAll(imagenes);
        }
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepository.findByIdProductoAndEstadoNot(id, ESTADO_PRODUCTO_ARCHIVADO)
                .orElseThrow(() -> new RuntimeException("Producto con ID " + id + " no encontrado"));
    }

    public Producto obtenerPorIdConAlcance(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        return obtenerProductoConAlcance(idProducto, usuarioAutenticado);
    }

    public ProductoDetalleResponse obtenerDetalle(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);

        List<ProductoVarianteDetalleResponse> variantes = productoVarianteRepository
                .findByProductoIdProductoAndDeletedAtIsNull(idProducto).stream()
                .map(this::toVarianteDetalleResponse)
                .sorted(Comparator
                        .comparing(ProductoVarianteDetalleResponse::colorId, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductoVarianteDetalleResponse::tallaId, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<ProductoImagenDetalleResponse> imagenes = productoColorImagenRepository.findByProductoIdProducto(idProducto).stream()
                .map(this::toImagenDetalleResponse)
                .sorted(Comparator
                        .comparing(ProductoImagenDetalleResponse::colorId, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductoImagenDetalleResponse::orden, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductoImagenDetalleResponse::idColorImagen, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        return new ProductoDetalleResponse(
                toListItemResponse(producto, resolverReferenciaDesdeDetalle(variantes)),
                variantes,
                imagenes);
    }

    private Producto obtenerProductoConAlcance(Integer idProducto, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return productoRepository.findByIdProductoAndEstadoNot(idProducto, ESTADO_PRODUCTO_ARCHIVADO)
                    .orElseThrow(() -> new RuntimeException("Producto con ID " + idProducto + " no encontrado"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return productoRepository.findByIdProductoAndSucursal_IdSucursalAndEstadoNot(
                idProducto,
                idSucursalUsuario,
                ESTADO_PRODUCTO_ARCHIVADO)
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

    private void validarSkuVarianteUnicoPorSucursal(String sku, Integer idSucursal, Integer idProductoExcluir) {
        boolean existe = productoVarianteRepository.existsSkuEnSucursalParaOtroProducto(idSucursal, sku, idProductoExcluir);
        if (existe) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }
    }

    private void validarCodigoBarrasVarianteUnicoPorSucursal(
            String codigoBarras,
            Integer idSucursal,
            Integer idProductoExcluir) {
        if (codigoBarras == null) {
            return;
        }
        boolean existe = productoVarianteRepository.existsCodigoBarrasEnSucursalParaOtroProducto(
                idSucursal,
                codigoBarras,
                idProductoExcluir);
        if (existe) {
            throw new RuntimeException("El codigo de barras '" + codigoBarras + "' ya existe en esta sucursal");
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

    private Integer resolverIdSucursalFiltroListado(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        if (esAdministrador(usuarioAutenticado)) {
            if (idSucursalRequest == null) {
                return null;
            }
            return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalRequest)
                    .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"))
                    .getIdSucursal();
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("No tiene permisos para consultar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para gestionar productos");
        }
    }

    private void validarRolEdicionProducto(Usuario usuario) {
        if (usuario.getRol() == Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para editar o eliminar productos");
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

    private Map<Integer, VarianteReferencia> obtenerReferenciasVariantes(List<Producto> productos) {
        List<Integer> productoIds = productos.stream()
                .map(Producto::getIdProducto)
                .toList();
        if (productoIds.isEmpty()) {
            return Map.of();
        }
        List<ProductoVarianteResumenRow> variantesResumen = productoVarianteRepository.obtenerResumenPorProductos(productoIds);
        return construirReferenciasVariantes(variantesResumen);
    }

    private Map<Integer, VarianteReferencia> construirReferenciasVariantes(
            List<ProductoVarianteResumenRow> variantesResumen) {
        Map<Integer, ProductoVarianteResumenRow> elegidas = new HashMap<>();
        for (ProductoVarianteResumenRow row : variantesResumen) {
            if (row.productoId() == null || row.varianteId() == null) {
                continue;
            }
            ProductoVarianteResumenRow actual = elegidas.get(row.productoId());
            if (actual == null || row.varianteId() < actual.varianteId()) {
                elegidas.put(row.productoId(), row);
            }
        }

        Map<Integer, VarianteReferencia> referencias = new HashMap<>();
        for (Map.Entry<Integer, ProductoVarianteResumenRow> entry : elegidas.entrySet()) {
            ProductoVarianteResumenRow row = entry.getValue();
            referencias.put(entry.getKey(), new VarianteReferencia(row.sku()));
        }
        return referencias;
    }

    private VarianteReferencia resolverReferenciaDesdeVariantes(List<ProductoVariante> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            return null;
        }
        ProductoVariante referencia = variantes.stream()
                .sorted(Comparator.comparing(
                        ProductoVariante::getIdProductoVariante,
                        Comparator.nullsLast(Integer::compareTo)))
                .findFirst()
                .orElse(null);
        if (referencia == null) {
            return null;
        }
        return new VarianteReferencia(referencia.getSku());
    }

    private VarianteReferencia resolverReferenciaDesdeDetalle(List<ProductoVarianteDetalleResponse> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            return null;
        }
        ProductoVarianteDetalleResponse referencia = variantes.stream()
                .min(Comparator.comparing(
                        ProductoVarianteDetalleResponse::idProductoVariante,
                        Comparator.nullsLast(Integer::compareTo)))
                .orElse(null);
        if (referencia == null) {
            return null;
        }
        return new VarianteReferencia(referencia.sku());
    }

    private ProductoListItemResponse toListItemResponse(Producto producto, VarianteReferencia referencia) {
        Integer idCategoria = producto.getCategoria() != null ? producto.getCategoria().getIdCategoria() : null;
        String nombreCategoria = producto.getCategoria() != null ? producto.getCategoria().getNombreCategoria() : null;
        Integer idSucursal = producto.getSucursal() != null ? producto.getSucursal().getIdSucursal() : null;
        String nombreSucursal = producto.getSucursal() != null ? producto.getSucursal().getNombre() : null;

        return new ProductoListItemResponse(
                producto.getIdProducto(),
                referencia != null ? referencia.sku() : null,
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
                idCategoria,
                nombreCategoria,
                idSucursal,
                nombreSucursal);
    }

    private ProductoVarianteDetalleResponse toVarianteDetalleResponse(ProductoVariante variante) {
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        String colorNombre = variante.getColor() != null ? variante.getColor().getNombre() : null;
        String colorHex = variante.getColor() != null ? variante.getColor().getCodigo() : null;
        Integer tallaId = variante.getTalla() != null ? variante.getTalla().getIdTalla() : null;
        String tallaNombre = variante.getTalla() != null ? variante.getTalla().getNombre() : null;

        return new ProductoVarianteDetalleResponse(
                variante.getIdProductoVariante(),
                variante.getSku(),
                variante.getCodigoBarras(),
                colorId,
                colorNombre,
                colorHex,
                tallaId,
                tallaNombre,
                variante.getPrecio(),
                variante.getPrecioMayor(),
                variante.getPrecioOferta(),
                variante.getOfertaInicio(),
                variante.getOfertaFin(),
                variante.getStock(),
                variante.getEstado());
    }

    private ProductoImagenDetalleResponse toImagenDetalleResponse(ProductoColorImagen imagen) {
        Integer colorId = imagen.getColor() != null ? imagen.getColor().getIdColor() : null;
        String colorNombre = imagen.getColor() != null ? imagen.getColor().getNombre() : null;
        String colorHex = imagen.getColor() != null ? imagen.getColor().getCodigo() : null;

        return new ProductoImagenDetalleResponse(
                imagen.getIdColorImagen(),
                colorId,
                colorNombre,
                colorHex,
                imagen.getUrl(),
                imagen.getUrlThumb(),
                imagen.getOrden(),
                imagen.getEsPrincipal(),
                imagen.getEstado());
    }

    private ProductoListadoResumenResponse toResumenResponse(
            Producto producto,
            VarianteReferencia referencia,
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
                referencia != null ? referencia.sku() : null,
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
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
            List<ProductoVarianteCreateItem> variantes,
            Integer idProductoExcluir) {
        if (variantes == null || variantes.isEmpty()) {
            throw new RuntimeException("Ingrese variantes del producto");
        }

        Set<String> combinaciones = new HashSet<>();
        Set<String> skusNormalizados = new HashSet<>();
        Set<String> codigosBarrasNormalizados = new HashSet<>();
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

                    String sku = normalizarRequerido(item.sku(), "Cada variante debe incluir sku");
                    String skuKey = sku.toLowerCase();
                    if (!skusNormalizados.add(skuKey)) {
                        throw new RuntimeException("No puede repetir SKU dentro del mismo producto");
                    }
                    validarSkuVarianteUnicoPorSucursal(sku, sucursal.getIdSucursal(), idProductoExcluir);
                    String codigoBarras = normalizar(item.codigoBarras());
                    if (codigoBarras != null && !codigosBarrasNormalizados.add(codigoBarras.toLowerCase())) {
                        throw new RuntimeException("No puede repetir codigo de barras dentro del mismo producto");
                    }
                    validarCodigoBarrasVarianteUnicoPorSucursal(
                            codigoBarras,
                            sucursal.getIdSucursal(),
                            idProductoExcluir);
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
                    variante.setPrecioMayor(precioMayor);
                    variante.setPrecioOferta(precioOferta);
                    variante.setOfertaInicio(ofertaInicio);
                    variante.setOfertaFin(ofertaFin);
                    variante.setStock(item.stock());
                    variante.setEstado(ESTADO_VARIANTE_ACTIVA);
                    variante.setActivo(VALOR_ACTIVO);
                    variante.setDeletedAt(null);
                    variante.setSku(sku);
                    variante.setCodigoBarras(codigoBarras);
                    return variante;
                })
                .toList();

        return result;
    }

    private Map<Integer, List<ProductoColorResumen>> construirColoresPorProducto(
            List<Integer> productoIds,
            List<ProductoVarianteResumenRow> variantesResumen,
            Map<Integer, Map<Integer, List<ProductoTallaResumen>>> tallasPorProductoColor) {
        Map<Integer, Map<Integer, ColorMeta>> metaPorProductoColor = new HashMap<>();

        for (ProductoVarianteResumenRow row : variantesResumen) {
            if (row.productoId() == null || row.colorId() == null) {
                continue;
            }

            Map<Integer, ColorMeta> porColor = metaPorProductoColor.computeIfAbsent(
                    row.productoId(),
                    key -> new HashMap<>());
            ColorMeta existing = porColor.get(row.colorId());
            String nombre = preferirNoVacio(
                    existing != null ? existing.nombre() : null,
                    row.colorNombre());
            String hex = preferirNoVacio(
                    existing != null ? existing.hex() : null,
                    row.colorHex());
            porColor.put(row.colorId(), new ColorMeta(nombre, hex));
        }

        List<ProductoImagenColorRow> rows = productoColorImagenRepository.obtenerResumenPorProductos(productoIds);
        Map<Integer, Map<Integer, ColorImagenPick>> imagenPorProductoColor = new HashMap<>();

        for (ProductoImagenColorRow row : rows) {
            if (row.productoId() == null || row.colorId() == null) {
                continue;
            }
            Map<Integer, ColorImagenPick> porColor = imagenPorProductoColor.computeIfAbsent(
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

        Set<Integer> productosConColores = new HashSet<>();
        productosConColores.addAll(metaPorProductoColor.keySet());
        productosConColores.addAll(imagenPorProductoColor.keySet());

        Map<Integer, List<ProductoColorResumen>> result = new HashMap<>();
        for (Integer productoId : productosConColores) {
            Map<Integer, ColorMeta> metaPorColor = metaPorProductoColor.getOrDefault(productoId, Map.of());
            Map<Integer, ColorImagenPick> imagenPorColor = imagenPorProductoColor.getOrDefault(productoId, Map.of());

            Set<Integer> colorIds = new HashSet<>();
            colorIds.addAll(metaPorColor.keySet());
            colorIds.addAll(imagenPorColor.keySet());

            List<ProductoColorResumen> colores = colorIds.stream()
                    .sorted(Integer::compareTo)
                    .map(colorId -> {
                        ColorMeta meta = metaPorColor.get(colorId);
                        ColorImagenPick imagen = imagenPorColor.get(colorId);
                        String nombre = preferirNoVacio(
                                imagen != null ? imagen.nombre() : null,
                                meta != null ? meta.nombre() : null);
                        String hex = preferirNoVacio(
                                imagen != null ? imagen.hex() : null,
                                meta != null ? meta.hex() : null);
                        return new ProductoColorResumen(
                                colorId,
                                nombre,
                                hex,
                                imagen != null ? imagen.imagen() : null,
                                tallasPorProductoColor
                                        .getOrDefault(productoId, Map.of())
                                        .getOrDefault(colorId, List.of()));
                    })
                    .toList();
            result.put(productoId, colores);
        }

        return result;
    }

    private List<ProductoVariante> sincronizarVariantesEnActualizacion(
            Producto producto,
            Sucursal sucursal,
            List<ProductoVarianteCreateItem> variantes,
            Integer idProducto) {
        if (variantes == null || variantes.isEmpty()) {
            throw new RuntimeException("Ingrese variantes del producto");
        }

        List<ProductoVariante> existentes = productoVarianteRepository.findByProductoIdProducto(idProducto);

        Map<String, ProductoVariante> existentesPorCombinacion = new HashMap<>();
        Map<String, ProductoVariante> existentesPorSku = new HashMap<>();
        Map<String, ProductoVariante> existentesPorCodigoBarras = new HashMap<>();
        for (ProductoVariante existente : existentes) {
            if (existente == null) {
                continue;
            }
            Integer colorId = existente.getColor() != null ? existente.getColor().getIdColor() : null;
            Integer tallaId = existente.getTalla() != null ? existente.getTalla().getIdTalla() : null;
            if (colorId != null && tallaId != null) {
                existentesPorCombinacion.put(colorId + "-" + tallaId, existente);
            }

            String skuExistente = normalizar(existente.getSku());
            if (skuExistente != null) {
                existentesPorSku.putIfAbsent(skuExistente.toLowerCase(), existente);
            }

            String codigoBarrasExistente = normalizar(existente.getCodigoBarras());
            if (codigoBarrasExistente != null) {
                existentesPorCodigoBarras.putIfAbsent(codigoBarrasExistente.toLowerCase(), existente);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Set<String> combinaciones = new HashSet<>();
        Set<String> skusNormalizados = new HashSet<>();
        Set<String> codigosBarrasNormalizados = new HashSet<>();
        Set<Integer> idsActivos = new HashSet<>();
        Set<Integer> coloresTocados = new HashSet<>();
        Map<Integer, Color> coloresCache = new HashMap<>();
        Map<Integer, Talla> tallasCache = new HashMap<>();

        List<ProductoVariante> variantesActivas = new ArrayList<>();
        List<ProductoVariante> variantesParaPersistir = new ArrayList<>();

        for (ProductoVarianteCreateItem item : variantes) {
            if (item.colorId() == null || item.tallaId() == null) {
                throw new RuntimeException("Cada variante debe incluir colorId y tallaId");
            }
            String key = item.colorId() + "-" + item.tallaId();
            if (!combinaciones.add(key)) {
                throw new RuntimeException("No puede repetir la misma combinacion de color y talla");
            }

            ProductoVariante varianteExistente = existentesPorCombinacion.get(key);

            String sku = normalizarRequerido(item.sku(), "Cada variante debe incluir sku");
            String skuKey = sku.toLowerCase();
            if (!skusNormalizados.add(skuKey)) {
                throw new RuntimeException("No puede repetir SKU dentro del mismo producto");
            }
            validarSkuVarianteUnicoPorSucursal(sku, sucursal.getIdSucursal(), idProducto);
            ProductoVariante varianteSkuExistente = existentesPorSku.get(skuKey);
            if (varianteSkuExistente != null
                    && (varianteExistente == null
                    || !varianteSkuExistente.getIdProductoVariante().equals(varianteExistente.getIdProductoVariante()))) {
                throw new RuntimeException("El SKU '" + sku
                        + "' ya existe en otra variante de este producto y no se puede reasignar");
            }
            String codigoBarras = normalizar(item.codigoBarras());
            String codigoBarrasKey = codigoBarras == null ? null : codigoBarras.toLowerCase();
            if (codigoBarrasKey != null && !codigosBarrasNormalizados.add(codigoBarrasKey)) {
                throw new RuntimeException("No puede repetir codigo de barras dentro del mismo producto");
            }
            validarCodigoBarrasVarianteUnicoPorSucursal(codigoBarras, sucursal.getIdSucursal(), idProducto);
            ProductoVariante varianteCodigoBarrasExistente = codigoBarrasKey == null
                    ? null
                    : existentesPorCodigoBarras.get(codigoBarrasKey);
            if (varianteCodigoBarrasExistente != null
                    && (varianteExistente == null
                    || !varianteCodigoBarrasExistente.getIdProductoVariante()
                            .equals(varianteExistente.getIdProductoVariante()))) {
                throw new RuntimeException("El codigo de barras '" + codigoBarras
                        + "' ya existe en otra variante de este producto y no se puede reasignar");
            }
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

            Color color = coloresCache.computeIfAbsent(item.colorId(), id -> colorService.obtenerPorId(id));
            if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
                throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
            }
            Talla talla = tallasCache.computeIfAbsent(item.tallaId(), id -> tallaService.obtenerPorId(id));
            if (!"ACTIVO".equalsIgnoreCase(talla.getEstado())) {
                throw new RuntimeException("No se puede usar la talla '" + talla.getNombre() + "' porque esta INACTIVA");
            }

            ProductoVariante destino = varianteExistente != null ? varianteExistente : new ProductoVariante();
            destino.setProducto(producto);
            destino.setSucursal(sucursal);
            destino.setColor(color);
            destino.setTalla(talla);
            destino.setPrecio(item.precio());
            destino.setPrecioMayor(precioMayor);
            destino.setPrecioOferta(precioOferta);
            destino.setOfertaInicio(ofertaInicio);
            destino.setOfertaFin(ofertaFin);
            destino.setStock(item.stock());
            destino.setEstado(ESTADO_VARIANTE_ACTIVA);
            destino.setActivo(VALOR_ACTIVO);
            destino.setDeletedAt(null);
            destino.setSku(sku);
            destino.setCodigoBarras(codigoBarras);
            coloresTocados.add(item.colorId());

            if (destino.getIdProductoVariante() != null) {
                idsActivos.add(destino.getIdProductoVariante());
            }
            variantesActivas.add(destino);
            variantesParaPersistir.add(destino);
        }

        for (ProductoVariante existente : existentes) {
            if (existente == null || existente.getIdProductoVariante() == null) {
                continue;
            }
            if (idsActivos.contains(existente.getIdProductoVariante())) {
                continue;
            }
            existente.setEstado(ESTADO_VARIANTE_INACTIVA);
            existente.setActivo(VALOR_INACTIVO);
            existente.setDeletedAt(now);
            if (existente.getColor() != null && existente.getColor().getIdColor() != null) {
                coloresTocados.add(existente.getColor().getIdColor());
            }
            variantesParaPersistir.add(existente);
        }

        if (!variantesParaPersistir.isEmpty()) {
            productoVarianteRepository.saveAll(variantesParaPersistir);
        }

        for (Integer colorId : coloresTocados) {
            limpiarImagenesColorSiNoHayVariantesActivas(idProducto, colorId);
        }

        return variantesActivas;
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

    private String preferirNoVacio(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private record VarianteReferencia(String sku) {
    }

    private record ColorMeta(String nombre, String hex) {
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
                    new ProductoTallaResumen(
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
                            row.stock(),
                            row.estado()));
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
            range.acumular(resolverPrecioVigente(
                    row.precio(),
                    row.precioOferta(),
                    row.ofertaInicio(),
                    row.ofertaFin()));
        }

        return precios;
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

    private Double normalizarPrecioMayor(Double precioMayor) {
        if (precioMayor == null) {
            return null;
        }
        if (precioMayor <= 0) {
            throw new RuntimeException("El precio por mayor debe ser mayor a 0");
        }
        return precioMayor;
    }

    private void validarPrecioMayor(Double precio, Double precioMayor) {
        if (precioMayor == null) {
            return;
        }
        if (precio == null) {
            throw new RuntimeException("El precio es obligatorio para validar el precio por mayor");
        }
        if (precioMayor >= precio) {
            throw new RuntimeException("El precio por mayor debe ser menor al precio regular");
        }
    }

    private void validarPrecioOferta(
            Double precio,
            Double precioOferta,
            LocalDateTime ofertaInicio,
            LocalDateTime ofertaFin) {
        if (precioOferta == null) {
            if (ofertaInicio != null || ofertaFin != null) {
                throw new RuntimeException("No puede registrar ofertaInicio/ofertaFin sin precioOferta");
            }
            return;
        }
        if (precio == null) {
            throw new RuntimeException("El precio es obligatorio para validar el precio de oferta");
        }
        if (precioOferta >= precio) {
            throw new RuntimeException("El precio de oferta debe ser menor al precio regular");
        }
        if ((ofertaInicio == null) != (ofertaFin == null)) {
            throw new RuntimeException("Debe enviar ofertaInicio y ofertaFin juntas");
        }
        if (ofertaInicio != null && ofertaFin != null && !ofertaFin.isAfter(ofertaInicio)) {
            throw new RuntimeException("ofertaFin debe ser mayor a ofertaInicio");
        }
    }

    private Double resolverPrecioVigente(
            Double precio,
            Double precioOferta,
            LocalDateTime ofertaInicio,
            LocalDateTime ofertaFin) {
        if (precioOferta == null || precio == null || precioOferta <= 0 || precioOferta >= precio) {
            return precio;
        }
        if (ofertaInicio == null && ofertaFin == null) {
            return precioOferta;
        }
        if (ofertaInicio == null || ofertaFin == null) {
            return precio;
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (ahora.isBefore(ofertaInicio) || ahora.isAfter(ofertaFin)) {
            return precio;
        }
        return precioOferta;
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
            return new ArrayList<>();
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

    private Set<String> extraerUrlsImagenes(List<ProductoColorImagen> imagenes) {
        Set<String> urls = new HashSet<>();
        if (imagenes == null || imagenes.isEmpty()) {
            return urls;
        }
        for (ProductoColorImagen imagen : imagenes) {
            agregarSiTieneValor(urls, imagen.getUrl());
            agregarSiTieneValor(urls, imagen.getUrlThumb());
        }
        return urls;
    }

    private void agregarSiTieneValor(Set<String> urls, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        urls.add(valor.trim());
    }

    private void eliminarImagenesObsoletasEnS3(Set<String> urlsActuales, Set<String> urlsNuevas) {
        if (urlsActuales == null || urlsActuales.isEmpty()) {
            return;
        }
        Set<String> urlsVigentes = urlsNuevas == null ? Set.of() : urlsNuevas;
        for (String url : urlsActuales) {
            if (!urlsVigentes.contains(url)) {
                s3StorageService.deleteByUrl(url);
            }
        }
    }

    public void limpiarImagenesColorSiNoHayVariantesActivas(Integer idProducto, Integer idColor) {
        if (idProducto == null || idColor == null) {
            return;
        }
        boolean existenVariantesActivas = productoVarianteRepository
                .existsVarianteActivaPorProductoYColor(idProducto, idColor);
        if (existenVariantesActivas) {
            return;
        }

        List<ProductoColorImagen> imagenes = productoColorImagenRepository
                .findByProductoIdProductoAndColorIdColor(idProducto, idColor);
        if (imagenes.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Set<String> urls = new HashSet<>();
        for (ProductoColorImagen imagen : imagenes) {
            if (imagen == null) {
                continue;
            }
            agregarSiTieneValor(urls, imagen.getUrl());
            agregarSiTieneValor(urls, imagen.getUrlThumb());
            imagen.setEstado(ESTADO_IMAGEN_INACTIVA);
            imagen.setEsPrincipal(false);
            imagen.setDeletedAt(now);
        }
        productoColorImagenRepository.saveAll(imagenes);

        for (String url : urls) {
            try {
                s3StorageService.deleteByUrl(url);
            } catch (RuntimeException ignored) {
                // Best-effort: no se revierte la transaccion por falla externa de S3.
            }
        }
    }
}
