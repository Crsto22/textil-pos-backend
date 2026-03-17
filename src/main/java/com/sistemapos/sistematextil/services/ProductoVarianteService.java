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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteItemRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteUpdateRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.model.Usuario;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoVarianteService {

    private static final String ESTADO_PRODUCTO_ARCHIVADO = "ARCHIVADO";

    private final ProductoVarianteRepository repository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final ProductoService productoService;
    private final TallaService tallaService;
    private final ColorService colorService;
    private final UsuarioRepository usuarioRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public List<ProductoVariante> listarTodas() {
        return repository.findByDeletedAtIsNull();
    }

    public List<ProductoVariante> listarPorProducto(Integer idProducto) {
        return repository.findByProductoIdProductoAndDeletedAtIsNull(idProducto);
    }

    public PagedResponse<ProductoVarianteListadoResumenResponse> listarResumenPaginado(
            String q,
            int page,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        String term = normalizar(q);
        int pageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        Integer idSucursalFiltro = esAdministrador(usuarioAutenticado) ? null : obtenerIdSucursalUsuario(usuarioAutenticado);
        PageRequest pageable = PageRequest.of(page, pageSize, Sort.by("idProductoVariante").ascending());
        Page<ProductoVariante> variantes = repository.buscarResumenPaginado(
                term,
                idSucursalFiltro,
                idCategoria,
                idColor,
                conOferta,
                ESTADO_PRODUCTO_ARCHIVADO,
                pageable);
        Map<ProductoColorKey, ImagenDetalleGroup> imagenes = resolverImagenesDetallePorProductoColor(variantes.getContent());
        return PagedResponse.fromPage(variantes.map(variante -> toListadoResumenResponse(variante, imagenes)));
    }

    public PagedResponse<ProductoVarianteOfertaListItemResponse> listarConOfertaPaginado(int page) {
        validarPagina(page);
        int pageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        PageRequest pageable = PageRequest.of(page, pageSize, Sort.by("idProductoVariante").ascending());
        Page<ProductoVariante> variantes = repository.findByPrecioOfertaIsNotNullAndDeletedAtIsNull(pageable);
        Map<ProductoColorKey, String> imagenes = resolverImagenesPorProductoColor(variantes.getContent());
        return PagedResponse.fromPage(variantes.map(variante -> toOfertaListItemResponse(variante, imagenes)));
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void limpiarOfertasVencidasProgramadas() {
        LocalDateTime ahora = LocalDateTime.now();
        List<ProductoVariante> variantesVencidas = repository
                .findByPrecioOfertaIsNotNullAndOfertaFinLessThanEqualAndDeletedAtIsNull(ahora);

        if (variantesVencidas.isEmpty()) {
            return;
        }

        for (ProductoVariante variante : variantesVencidas) {
            variante.setPrecioOferta(null);
            variante.setOfertaInicio(null);
            variante.setOfertaFin(null);
        }

        repository.saveAll(variantesVencidas);
    }

    @Transactional
    public ProductoVariante insertar(ProductoVariante variante) {
        if (variante.getProducto() == null || variante.getProducto().getIdProducto() == null) {
            throw new RuntimeException("Ingrese producto.idProducto");
        }
        if (variante.getSucursal() == null || variante.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("Ingrese sucursal.idSucursal");
        }
        if (variante.getTalla() == null || variante.getTalla().getIdTalla() == null) {
            throw new RuntimeException("Ingrese talla.idTalla");
        }
        if (variante.getColor() == null || variante.getColor().getIdColor() == null) {
            throw new RuntimeException("Ingrese color.idColor");
        }

        Producto producto = productoService.obtenerPorId(variante.getProducto().getIdProducto());
        Talla talla = tallaService.obtenerPorId(variante.getTalla().getIdTalla());
        Color color = colorService.obtenerPorId(variante.getColor().getIdColor());

        if (!"ACTIVO".equalsIgnoreCase(talla.getEstado())) {
            throw new RuntimeException("No se puede usar la talla '" + talla.getNombre() + "' porque esta INACTIVA");
        }
        if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
            throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
        }

        String sku = normalizarRequerido(variante.getSku(), "El SKU de la variante es obligatorio");
        Double precioMayor = normalizarPrecioMayor(variante.getPrecioMayor());
        Double precioOferta = normalizarPrecioOferta(variante.getPrecioOferta());
        LocalDateTime ofertaInicio = variante.getOfertaInicio();
        LocalDateTime ofertaFin = variante.getOfertaFin();
        validarPrecioMayor(variante.getPrecio(), precioMayor);
        validarPrecioOferta(variante.getPrecio(), precioOferta, ofertaInicio, ofertaFin);
        if (precioOferta == null) {
            ofertaInicio = null;
            ofertaFin = null;
        }

        ProductoVariante existente = repository
                .findByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
                        variante.getProducto().getIdProducto(),
                        variante.getTalla().getIdTalla(),
                        variante.getColor().getIdColor(),
                        variante.getSucursal().getIdSucursal())
                .orElse(null);

        boolean skuDuplicado = existente == null
                ? repository.existsBySucursalIdSucursalAndSku(variante.getSucursal().getIdSucursal(), sku)
                : repository.existsBySucursalIdSucursalAndSkuAndIdProductoVarianteNot(
                        variante.getSucursal().getIdSucursal(),
                        sku,
                        existente.getIdProductoVariante());
        if (skuDuplicado) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }

        ProductoVariante destino;
        if (existente != null) {
            if ("ACTIVO".equalsIgnoreCase(existente.getActivo()) && existente.getDeletedAt() == null) {
                throw new RuntimeException("Ya existe esta variante (talla/color) en esta sucursal");
            }
            destino = existente;
        } else {
            destino = variante;
        }

        destino.setProducto(producto);
        destino.setTalla(talla);
        destino.setColor(color);
        destino.setSku(sku);
        destino.setPrecio(variante.getPrecio());
        destino.setPrecioMayor(precioMayor);
        destino.setPrecioOferta(precioOferta);
        destino.setOfertaInicio(ofertaInicio);
        destino.setOfertaFin(ofertaFin);
        destino.setStock(variante.getStock());
        destino.setEstado("ACTIVO");
        destino.setActivo("ACTIVO");
        destino.setDeletedAt(null);

        ProductoVariante guardado = repository.saveAndFlush(destino);
        entityManager.clear();

        return repository.findById(guardado.getIdProductoVariante())
                .orElseThrow(() -> new RuntimeException("Error al recuperar la variante guardada"));
    }

    public ProductoVariante actualizarStock(Integer id, Integer nuevoStock) {
        ProductoVariante variante = repository.findByIdProductoVarianteAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));

        variante.setStock(nuevoStock);
        return repository.save(variante);
    }

    public ProductoVariante actualizarPrecio(Integer id, Double nuevoPrecio) {
        ProductoVariante variante = repository.findByIdProductoVarianteAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));

        if (nuevoPrecio == null || nuevoPrecio < 0) {
            throw new RuntimeException("El precio no puede ser negativo");
        }
        validarPrecioMayor(nuevoPrecio, variante.getPrecioMayor());
        validarPrecioOferta(
                nuevoPrecio,
                variante.getPrecioOferta(),
                variante.getOfertaInicio(),
                variante.getOfertaFin());
        variante.setPrecio(nuevoPrecio);
        return repository.save(variante);
    }

    @Transactional
    public ProductoVarianteListadoResumenResponse actualizar(
            Integer id,
            ProductoVarianteUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        ProductoVariante variante = obtenerVarianteConAlcance(id, usuarioAutenticado);
        Integer idProducto = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        Integer idSucursal = variante.getSucursal() != null ? variante.getSucursal().getIdSucursal() : null;
        Integer idColorAnterior = variante.getColor() != null ? variante.getColor().getIdColor() : null;

        if (idProducto == null) {
            throw new RuntimeException("La variante no tiene producto asociado");
        }
        if (idSucursal == null) {
            throw new RuntimeException("La variante no tiene sucursal asociada");
        }

        Color color = colorService.obtenerPorId(request.colorId());
        if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
            throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
        }

        Talla talla = tallaService.obtenerPorId(request.tallaId());
        if (!"ACTIVO".equalsIgnoreCase(talla.getEstado())) {
            throw new RuntimeException("No se puede usar la talla '" + talla.getNombre() + "' porque esta INACTIVA");
        }

        String sku = normalizarRequerido(request.sku(), "El SKU es obligatorio");
        if (repository.existsBySucursalIdSucursalAndSkuAndIdProductoVarianteNot(idSucursal, sku, id)) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }

        boolean combinacionDuplicada = repository
                .existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursalAndIdProductoVarianteNot(
                        idProducto,
                        talla.getIdTalla(),
                        color.getIdColor(),
                        idSucursal,
                        id);
        if (combinacionDuplicada) {
            throw new RuntimeException("Ya existe esta variante (talla/color) en esta sucursal");
        }

        Double precioMayor = normalizarPrecioMayor(request.precioMayor());
        Double precioOferta = normalizarPrecioOferta(request.precioOferta());
        LocalDateTime ofertaInicio = request.ofertaInicio();
        LocalDateTime ofertaFin = request.ofertaFin();

        validarPrecioMayor(request.precio(), precioMayor);
        validarPrecioOferta(request.precio(), precioOferta, ofertaInicio, ofertaFin);
        if (precioOferta == null) {
            ofertaInicio = null;
            ofertaFin = null;
        }

        variante.setColor(color);
        variante.setTalla(talla);
        variante.setSku(sku);
        variante.setPrecio(request.precio());
        variante.setPrecioMayor(precioMayor);
        variante.setPrecioOferta(precioOferta);
        variante.setOfertaInicio(ofertaInicio);
        variante.setOfertaFin(ofertaFin);
        variante.setStock(request.stock());

        ProductoVariante actualizada = repository.save(variante);

        if (idColorAnterior != null && !idColorAnterior.equals(color.getIdColor())) {
            productoService.limpiarImagenesColorSiNoHayVariantesActivas(idProducto, idColorAnterior);
        }

        Map<ProductoColorKey, ImagenDetalleGroup> imagenes = resolverImagenesDetallePorProductoColor(List.of(actualizada));
        return toListadoResumenResponse(actualizada, imagenes);
    }

    public ProductoVariante actualizarOferta(Integer id, ProductoVarianteOfertaUpdateRequest request) {
        ProductoVariante variante = repository.findByIdProductoVarianteAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));

        aplicarOferta(variante, request.precioOferta(), request.ofertaInicio(), request.ofertaFin());
        return repository.save(variante);
    }

    @Transactional
    public List<ProductoVarianteOfertaListItemResponse> actualizarOfertasLote(
            ProductoVarianteOfertaLoteUpdateRequest request) {
        Set<Integer> idsUnicos = new HashSet<>();
        List<Integer> idsSolicitados = new ArrayList<>();

        for (ProductoVarianteOfertaLoteItemRequest item : request.items()) {
            Integer idProductoVariante = item.idProductoVariante();
            if (!idsUnicos.add(idProductoVariante)) {
                throw new RuntimeException(
                        "No puede repetir la variante con ID " + idProductoVariante + " en la misma actualizacion");
            }
            idsSolicitados.add(idProductoVariante);
        }

        List<ProductoVariante> variantes = repository.findByIdProductoVarianteInAndDeletedAtIsNull(idsSolicitados);
        if (variantes.size() != idsSolicitados.size()) {
            Set<Integer> idsEncontrados = variantes.stream()
                    .map(ProductoVariante::getIdProductoVariante)
                    .collect(java.util.stream.Collectors.toSet());

            Integer idFaltante = idsSolicitados.stream()
                    .filter(id -> !idsEncontrados.contains(id))
                    .findFirst()
                    .orElse(null);

            throw new RuntimeException("La variante con ID " + idFaltante + " no existe");
        }

        java.util.Map<Integer, ProductoVariante> variantesPorId = variantes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProductoVariante::getIdProductoVariante,
                        variante -> variante));

        List<ProductoVariante> actualizadas = new ArrayList<>();
        for (ProductoVarianteOfertaLoteItemRequest item : request.items()) {
            ProductoVariante variante = variantesPorId.get(item.idProductoVariante());
            aplicarOferta(variante, item.precioOferta(), item.ofertaInicio(), item.ofertaFin());
            actualizadas.add(variante);
        }

        repository.saveAll(actualizadas);
        Map<ProductoColorKey, String> imagenes = resolverImagenesPorProductoColor(actualizadas);
        return actualizadas.stream()
                .map(variante -> toOfertaListItemResponse(variante, imagenes))
                .toList();
    }

    public void eliminar(Integer id) {
        ProductoVariante variante = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variante con ID " + id + " no encontrada"));

        if (!"ACTIVO".equalsIgnoreCase(variante.getActivo()) || variante.getDeletedAt() != null) {
            throw new RuntimeException("Variante con ID " + id + " ya se encuentra eliminada");
        }

        Integer idProducto = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        Integer idColor = variante.getColor() != null ? variante.getColor().getIdColor() : null;

        variante.setEstado("AGOTADO");
        variante.setActivo("INACTIVO");
        variante.setDeletedAt(LocalDateTime.now());
        repository.save(variante);

        productoService.limpiarImagenesColorSiNoHayVariantesActivas(idProducto, idColor);
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

    private ProductoVariante obtenerVarianteConAlcance(Integer idProductoVariante, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return repository.findByIdProductoVarianteAndDeletedAtIsNull(idProductoVariante)
                    .orElseThrow(() -> new RuntimeException("Variante no encontrada"));
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return repository.findByIdProductoVarianteAndDeletedAtIsNullAndSucursal_IdSucursal(
                idProductoVariante,
                idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));
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

    private void aplicarOferta(
            ProductoVariante variante,
            Double precioOfertaSolicitado,
            LocalDateTime ofertaInicioSolicitada,
            LocalDateTime ofertaFinSolicitada) {
        Double precioOferta = normalizarPrecioOferta(precioOfertaSolicitado);
        LocalDateTime ofertaInicio = ofertaInicioSolicitada;
        LocalDateTime ofertaFin = ofertaFinSolicitada;

        validarPrecioOferta(variante.getPrecio(), precioOferta, ofertaInicio, ofertaFin);
        if (precioOferta == null) {
            ofertaInicio = null;
            ofertaFin = null;
        }

        variante.setPrecioOferta(precioOferta);
        variante.setOfertaInicio(ofertaInicio);
        variante.setOfertaFin(ofertaFin);
    }

    private Map<ProductoColorKey, String> resolverImagenesPorProductoColor(List<ProductoVariante> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            return Map.of();
        }

        List<Integer> productoIds = variantes.stream()
                .map(ProductoVariante::getProducto)
                .filter(java.util.Objects::nonNull)
                .map(Producto::getIdProducto)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (productoIds.isEmpty()) {
            return Map.of();
        }

        List<ProductoImagenColorRow> rows = productoColorImagenRepository.obtenerResumenPorProductos(productoIds);
        Map<ProductoColorKey, ImagenPick> picks = new HashMap<>();

        for (ProductoImagenColorRow row : rows) {
            if (row.productoId() == null || row.colorId() == null) {
                continue;
            }

            String imageUrl = preferirNoVacio(row.url(), row.urlThumb());
            if (imageUrl == null) {
                continue;
            }

            ProductoColorKey key = new ProductoColorKey(row.productoId(), row.colorId());
            ImagenPick existing = picks.get(key);
            boolean rowPrincipal = Boolean.TRUE.equals(row.esPrincipal());
            int rowOrden = row.orden() == null ? Integer.MAX_VALUE : row.orden();

            if (existing == null || debeReemplazarImagen(existing, rowPrincipal, rowOrden)) {
                picks.put(key, new ImagenPick(imageUrl, rowOrden, rowPrincipal));
            }
        }

        Map<ProductoColorKey, String> result = new HashMap<>();
        for (Map.Entry<ProductoColorKey, ImagenPick> entry : picks.entrySet()) {
            result.put(entry.getKey(), entry.getValue().url());
        }
        return result;
    }

    private boolean debeReemplazarImagen(ImagenPick existing, boolean rowPrincipal, int rowOrden) {
        if (rowPrincipal && !existing.esPrincipal()) {
            return true;
        }
        if (!rowPrincipal && existing.esPrincipal()) {
            return false;
        }
        return rowOrden < existing.orden();
    }

    private String preferirNoVacio(String primary, String fallback) {
        String value = normalizar(primary);
        return value != null ? value : normalizar(fallback);
    }

    private Map<ProductoColorKey, ImagenDetalleGroup> resolverImagenesDetallePorProductoColor(List<ProductoVariante> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            return Map.of();
        }

        Set<ProductoColorKey> keys = new HashSet<>();
        List<Integer> productoIds = new ArrayList<>();
        Set<Integer> productoIdsUnicos = new HashSet<>();
        for (ProductoVariante variante : variantes) {
            Integer productoId = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
            Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
            if (productoId == null || colorId == null) {
                continue;
            }
            keys.add(new ProductoColorKey(productoId, colorId));
            if (productoIdsUnicos.add(productoId)) {
                productoIds.add(productoId);
            }
        }

        if (productoIds.isEmpty()) {
            return Map.of();
        }

        List<ProductoColorImagen> imagenes = productoColorImagenRepository.findByProductoIdProductoIn(productoIds);
        Map<ProductoColorKey, List<ProductoVarianteListadoResumenResponse.ImagenItem>> agrupadas = new HashMap<>();

        for (ProductoColorImagen imagen : imagenes) {
            if (imagen == null || imagen.getDeletedAt() != null || !"ACTIVO".equalsIgnoreCase(imagen.getEstado())) {
                continue;
            }
            Integer productoId = imagen.getProducto() != null ? imagen.getProducto().getIdProducto() : null;
            Integer colorId = imagen.getColor() != null ? imagen.getColor().getIdColor() : null;
            if (productoId == null || colorId == null) {
                continue;
            }

            ProductoColorKey key = new ProductoColorKey(productoId, colorId);
            if (!keys.contains(key)) {
                continue;
            }

            agrupadas.computeIfAbsent(key, unused -> new ArrayList<>())
                    .add(new ProductoVarianteListadoResumenResponse.ImagenItem(
                            imagen.getIdColorImagen(),
                            imagen.getUrl(),
                            imagen.getUrlThumb(),
                            imagen.getOrden(),
                            imagen.getEsPrincipal(),
                            imagen.getEstado()));
        }

        Map<ProductoColorKey, ImagenDetalleGroup> result = new HashMap<>();
        for (Map.Entry<ProductoColorKey, List<ProductoVarianteListadoResumenResponse.ImagenItem>> entry : agrupadas.entrySet()) {
            List<ProductoVarianteListadoResumenResponse.ImagenItem> sorted = entry.getValue().stream()
                    .sorted(Comparator
                            .comparing((ProductoVarianteListadoResumenResponse.ImagenItem image) -> !Boolean.TRUE.equals(image.esPrincipal()))
                            .thenComparing(image -> image.orden() == null ? Integer.MAX_VALUE : image.orden())
                            .thenComparing(image -> image.idColorImagen() == null ? Integer.MAX_VALUE : image.idColorImagen()))
                    .toList();
            ProductoVarianteListadoResumenResponse.ImagenItem principal = sorted.isEmpty() ? null : sorted.get(0);
            result.put(entry.getKey(), new ImagenDetalleGroup(principal, sorted));
        }
        return result;
    }

    private ProductoVarianteListadoResumenResponse toListadoResumenResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, ImagenDetalleGroup> imagenes) {
        Producto producto = variante.getProducto();
        Integer productoId = producto != null ? producto.getIdProducto() : null;
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        ProductoColorKey key = productoId != null && colorId != null ? new ProductoColorKey(productoId, colorId) : null;
        ImagenDetalleGroup imagenGroup = key != null ? imagenes.get(key) : null;

        ProductoVarianteListadoResumenResponse.CategoriaItem categoria = producto != null
                && producto.getCategoria() != null
                        ? new ProductoVarianteListadoResumenResponse.CategoriaItem(
                                producto.getCategoria().getIdCategoria(),
                                producto.getCategoria().getNombreCategoria())
                        : null;

        ProductoVarianteListadoResumenResponse.SucursalItem sucursal = producto != null
                && producto.getSucursal() != null
                        ? new ProductoVarianteListadoResumenResponse.SucursalItem(
                                producto.getSucursal().getIdSucursal(),
                                producto.getSucursal().getNombre())
                        : null;

        ProductoVarianteListadoResumenResponse.ProductoItem productoItem = producto != null
                ? new ProductoVarianteListadoResumenResponse.ProductoItem(
                        producto.getIdProducto(),
                        producto.getNombre(),
                        producto.getDescripcion(),
                        producto.getEstado(),
                        producto.getFechaCreacion(),
                        categoria,
                        sucursal)
                : null;

        ProductoVarianteListadoResumenResponse.ColorItem color = variante.getColor() != null
                ? new ProductoVarianteListadoResumenResponse.ColorItem(
                        variante.getColor().getIdColor(),
                        variante.getColor().getNombre(),
                        variante.getColor().getCodigo())
                : null;

        ProductoVarianteListadoResumenResponse.TallaItem talla = variante.getTalla() != null
                ? new ProductoVarianteListadoResumenResponse.TallaItem(
                        variante.getTalla().getIdTalla(),
                        variante.getTalla().getNombre())
                : null;

        return new ProductoVarianteListadoResumenResponse(
                variante.getIdProductoVariante(),
                variante.getSku(),
                variante.getEstado(),
                variante.getStock(),
                variante.getPrecio(),
                variante.getPrecioMayor(),
                variante.getPrecioOferta(),
                variante.getOfertaInicio(),
                variante.getOfertaFin(),
                resolverPrecioVigente(
                        variante.getPrecio(),
                        variante.getPrecioOferta(),
                        variante.getOfertaInicio(),
                        variante.getOfertaFin()),
                productoItem,
                color,
                talla,
                imagenGroup != null ? imagenGroup.imagenPrincipal() : null,
                imagenGroup != null ? imagenGroup.imagenes() : List.of());
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

    private ProductoVarianteOfertaListItemResponse toOfertaListItemResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, String> imagenes) {
        Integer productoId = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        String productoNombre = variante.getProducto() != null ? variante.getProducto().getNombre() : null;
        Integer sucursalId = variante.getSucursal() != null ? variante.getSucursal().getIdSucursal() : null;
        String sucursalNombre = variante.getSucursal() != null ? variante.getSucursal().getNombre() : null;
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        String colorNombre = variante.getColor() != null ? variante.getColor().getNombre() : null;
        String colorHex = variante.getColor() != null ? variante.getColor().getCodigo() : null;
        Integer tallaId = variante.getTalla() != null ? variante.getTalla().getIdTalla() : null;
        String tallaNombre = variante.getTalla() != null ? variante.getTalla().getNombre() : null;
        String imagenUrl = productoId != null && colorId != null
                ? imagenes.get(new ProductoColorKey(productoId, colorId))
                : null;

        return new ProductoVarianteOfertaListItemResponse(
                variante.getIdProductoVariante(),
                productoId,
                productoNombre,
                sucursalId,
                sucursalNombre,
                variante.getSku(),
                colorId,
                colorNombre,
                colorHex,
                tallaId,
                tallaNombre,
                variante.getPrecio(),
                variante.getPrecioOferta(),
                variante.getOfertaInicio(),
                variante.getOfertaFin(),
                imagenUrl,
                variante.getStock(),
                variante.getEstado());
    }

    private record ProductoColorKey(Integer productoId, Integer colorId) {
    }

    private record ImagenDetalleGroup(
            ProductoVarianteListadoResumenResponse.ImagenItem imagenPrincipal,
            List<ProductoVarianteListadoResumenResponse.ImagenItem> imagenes) {
    }

    private record ImagenPick(String url, int orden, boolean esPrincipal) {
    }
}
