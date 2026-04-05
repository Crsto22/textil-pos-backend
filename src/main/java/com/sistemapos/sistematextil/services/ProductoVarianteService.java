package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteDisponibleExcelRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteItemRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVariantePosResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteStockSucursalRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteUpdateRequest;
import com.sistemapos.sistematextil.util.producto.StockSucursalVentaResumen;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.model.Usuario;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoVarianteService {

    private static final String ESTADO_VARIANTE_ACTIVA = "ACTIVO";
    private static final String ESTADO_VARIANTE_AGOTADA = "AGOTADO";
    private static final String VALOR_ACTIVO = "ACTIVO";
    private static final String VALOR_INACTIVO = "INACTIVO";
    private static final DateTimeFormatter FECHA_HORA_EXCEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductoVarianteRepository repository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final ProductoService productoService;
    private final TallaService tallaService;
    private final ColorService colorService;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final StockMovimientoService stockMovimientoService;

    @PersistenceContext
    private final EntityManager entityManager;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public List<ProductoVariante> listarTodas(Integer idSucursal) {
        Integer idSucursalFiltro = normalizarIdSucursalFiltro(idSucursal);
        return idSucursalFiltro == null
                ? repository.findByDeletedAtIsNull()
                : repository.findByDeletedAtIsNullAndSucursal_IdSucursal(idSucursalFiltro);
    }

    public List<ProductoVariante> listarPorProducto(Integer idProducto, Integer idSucursal) {
        Integer idSucursalFiltro = normalizarIdSucursalFiltro(idSucursal);
        return idSucursalFiltro == null
                ? repository.findByProductoIdProductoAndDeletedAtIsNull(idProducto)
                : repository.findByProductoIdProductoAndDeletedAtIsNullAndSucursal_IdSucursal(idProducto, idSucursalFiltro);
    }

    public ProductoVariantePosResponse escanearPorCodigoBarras(
            String codigoBarras,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitidoEscaneo(usuarioAutenticado);

        String codigoNormalizado = normalizar(codigoBarras);
        if (codigoNormalizado == null) {
            throw new RuntimeException("Ingrese codigoBarras");
        }

        Integer idSucursalFiltro = resolverIdSucursalEscaneo(usuarioAutenticado, idSucursal);
        ProductoVariante variante = repository.findEscaneableByCodigoBarras(codigoNormalizado)
                .orElseThrow(() -> new RuntimeException(
                        "No existe una variante con el codigo de barras '" + codigoNormalizado + "'"));

        StockMovimientoService.StockContexto contextoStock = stockMovimientoService.obtenerContexto(
                idSucursalFiltro,
                variante.getIdProductoVariante());
        ProductoVariante varianteEscaneable = contextoStock.sucursalStock().getProductoVariante();

        validarVarianteVendibleParaEscaneo(varianteEscaneable);

        Map<ProductoColorKey, ImagenDetalleGroup> imagenes = resolverImagenesDetallePorProductoColor(List.of(varianteEscaneable));
        return toPosResponse(varianteEscaneable, imagenes);
    }

    public PagedResponse<ProductoVarianteListadoResumenResponse> listarResumenPaginado(
            String q,
            int page,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Boolean soloDisponibles,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        String term = normalizar(q);
        int pageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado, idSucursal);
        PageRequest pageable = PageRequest.of(page, pageSize, Sort.by("idProductoVariante").ascending());
        Page<ProductoVariante> variantes = repository.buscarResumenPaginado(
                term,
                idSucursalFiltro,
                idCategoria,
                idColor,
                conOferta,
                SucursalTipo.VENTA,
                Boolean.TRUE.equals(soloDisponibles),
                pageable);
        Map<ProductoColorKey, ImagenDetalleGroup> imagenes = resolverImagenesDetallePorProductoColor(variantes.getContent());
        List<Integer> varianteIds = variantes.getContent().stream()
                .map(ProductoVariante::getIdProductoVariante)
                .filter(id -> id != null)
                .toList();
        List<ProductoVarianteStockSucursalRow> stockRows = varianteIds.isEmpty()
                ? List.of()
                : repository.obtenerStocksCatalogoPorVariantes(varianteIds, idSucursalFiltro, SucursalTipo.VENTA);
        Map<Integer, List<StockSucursalVentaResumen>> stocksPorVariante = agruparStocksVenta(stockRows);
        Sucursal sucursalContexto = idSucursalFiltro == null
                ? null
                : sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalFiltro).orElse(null);
        return PagedResponse.fromPage(variantes.map(variante -> toListadoResumenResponse(
                variante,
                imagenes,
                stocksPorVariante.getOrDefault(variante.getIdProductoVariante(), List.of()),
                sucursalContexto)));
    }

    public PagedResponse<ProductoVarianteOfertaListItemResponse> listarConOfertaPaginado(int page, Integer idSucursal) {
        validarPagina(page);
        int pageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        PageRequest pageable = PageRequest.of(page, pageSize, Sort.by("idProductoVariante").ascending());
        Integer idSucursalFiltro = normalizarIdSucursalFiltro(idSucursal);
        Page<ProductoVariante> variantes = idSucursalFiltro == null
                ? repository.findByPrecioOfertaIsNotNullAndDeletedAtIsNull(pageable)
                : repository.findByPrecioOfertaIsNotNullAndDeletedAtIsNullAndSucursal_IdSucursal(
                        idSucursalFiltro,
                        pageable);
        Map<ProductoColorKey, String> imagenes = resolverImagenesPorProductoColor(variantes.getContent());
        return PagedResponse.fromPage(variantes.map(variante -> toOfertaListItemResponse(variante, imagenes)));
    }

    public byte[] exportarDisponiblesExcel(String correoUsuarioAutenticado, Integer idSucursal) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado, idSucursal);

        List<ProductoVarianteDisponibleExcelRow> filasConStock = repository
                .listarDisponiblesParaReporte(idSucursalFiltro);

        List<ProductoVarianteDisponibleExcelRow> filasSinStock = repository
                .listarSinStockParaReporte(idSucursalFiltro);

        return construirExcelDisponibles(filasConStock, filasSinStock);
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
        String codigoBarras = normalizar(variante.getCodigoBarras());
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

        boolean codigoBarrasDuplicado = codigoBarras != null && (existente == null
                ? repository.existsBySucursalIdSucursalAndCodigoBarras(variante.getSucursal().getIdSucursal(), codigoBarras)
                : repository.existsBySucursalIdSucursalAndCodigoBarrasAndIdProductoVarianteNot(
                        variante.getSucursal().getIdSucursal(),
                        codigoBarras,
                        existente.getIdProductoVariante()));
        if (codigoBarrasDuplicado) {
            throw new RuntimeException("El codigo de barras '" + codigoBarras + "' ya existe en esta sucursal");
        }

        ProductoVariante destino;
        if (existente != null) {
            if (existente.getDeletedAt() == null) {
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
        destino.setCodigoBarras(codigoBarras);
        destino.setPrecio(variante.getPrecio());
        destino.setPrecioMayor(precioMayor);
        destino.setPrecioOferta(precioOferta);
        destino.setOfertaInicio(ofertaInicio);
        destino.setOfertaFin(ofertaFin);
        destino.setStock(variante.getStock());
        destino.setEstado(resolverEstadoVarianteSegunStock(variante.getStock()));
        destino.setActivo(VALOR_ACTIVO);
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
        variante.setEstado(resolverEstadoVarianteSegunStock(nuevoStock));
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
        String codigoBarras = normalizar(request.codigoBarras());
        if (repository.existsBySucursalIdSucursalAndSkuAndIdProductoVarianteNot(idSucursal, sku, id)) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }
        if (codigoBarras != null
                && repository.existsBySucursalIdSucursalAndCodigoBarrasAndIdProductoVarianteNot(
                        idSucursal,
                        codigoBarras,
                        id)) {
            throw new RuntimeException("El codigo de barras '" + codigoBarras + "' ya existe en esta sucursal");
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
        variante.setCodigoBarras(codigoBarras);
        variante.setPrecio(request.precio());
        variante.setPrecioMayor(precioMayor);
        variante.setPrecioOferta(precioOferta);
        variante.setOfertaInicio(ofertaInicio);
        variante.setOfertaFin(ofertaFin);
        int stockSolicitado = sumarStocksSolicitados(request.stocksSucursales());
        variante.setStock(stockSolicitado);
        variante.setEstado(resolverEstadoVarianteSegunStock(stockSolicitado));

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

        if (variante.getDeletedAt() != null) {
            throw new RuntimeException("Variante con ID " + id + " ya se encuentra eliminada");
        }

        Integer idProducto = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        Integer idColor = variante.getColor() != null ? variante.getColor().getIdColor() : null;

        variante.setEstado(resolverEstadoVarianteSegunStock(variante.getStock()));
        variante.setActivo(VALOR_INACTIVO);
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

    private String resolverEstadoVarianteSegunStock(Integer stock) {
        return stock != null && stock <= 0 ? ESTADO_VARIANTE_AGOTADA : ESTADO_VARIANTE_ACTIVA;
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

    private Integer resolverIdSucursalFiltro(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        if (esAdministrador(usuarioAutenticado)) {
            return normalizarIdSucursalFiltro(idSucursalRequest);
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("No tiene permisos para consultar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private Integer normalizarIdSucursalFiltro(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            return null;
        }
        if (idSucursalRequest <= 0) {
            throw new RuntimeException("idSucursal debe ser mayor a 0");
        }
        return obtenerSucursalValidaParaFiltro(idSucursalRequest).getIdSucursal();
    }

    private Integer resolverIdSucursalEscaneo(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        if (esAdministrador(usuarioAutenticado)) {
            if (idSucursalRequest == null) {
                throw new RuntimeException("Ingrese idSucursal");
            }
            return normalizarIdSucursalFiltro(idSucursalRequest);
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("No tiene permisos para consultar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private Sucursal obtenerSucursalValidaParaFiltro(Integer idSucursal) {
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
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

    private void validarRolPermitidoEscaneo(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para escanear productos");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private void validarVarianteVendibleParaEscaneo(ProductoVariante variante) {
        String nombreProducto = descripcionVariante(variante);
        int stockActual = variante.getStock() == null ? 0 : variante.getStock();

        if (!ESTADO_VARIANTE_ACTIVA.equalsIgnoreCase(variante.getEstado())) {
            throw new RuntimeException("El producto '" + nombreProducto + "' no esta disponible");
        }
        if (stockActual <= 0) {
            throw new RuntimeException("El producto '" + nombreProducto + "' no tiene stock disponible");
        }
    }

    private String descripcionVariante(ProductoVariante variante) {
        if (variante == null) {
            return "Producto";
        }

        List<String> partes = new ArrayList<>();
        if (variante.getProducto() != null && variante.getProducto().getNombre() != null) {
            partes.add(variante.getProducto().getNombre().trim());
        }
        if (variante.getColor() != null && variante.getColor().getNombre() != null) {
            partes.add(variante.getColor().getNombre().trim());
        }
        if (variante.getTalla() != null && variante.getTalla().getNombre() != null) {
            partes.add("Talla " + variante.getTalla().getNombre().trim());
        }
        return partes.isEmpty() ? "Producto" : String.join(" ", partes);
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private byte[] construirExcelDisponibles(
            List<ProductoVarianteDisponibleExcelRow> filasConStock,
            List<ProductoVarianteDisponibleExcelRow> filasSinStock) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = crearEstiloHeader(workbook);
            CellStyle moneyStyle = crearEstiloMoneda(workbook);

            construirHojaReporteStock(
                    workbook,
                    "Productos Disponibles",
                    filasConStock,
                    headerStyle,
                    moneyStyle);
            construirHojaReporteStock(
                    workbook,
                    "Productos Sin Stock",
                    filasSinStock,
                    headerStyle,
                    moneyStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el Excel de productos disponibles");
        }
    }

    private void construirHojaReporteStock(
            Workbook workbook,
            String nombreHoja,
            List<ProductoVarianteDisponibleExcelRow> filas,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet(nombreHoja);
        String[] headers = {
                "ID Variante",
                "SKU",
                "Codigo de Barras",
                "Producto",
                "Categoria",
                "Sucursal",
                "Color",
                "Talla",
                "Stock",
                "Precio",
                "Precio Mayor",
                "Precio Oferta",
                "Precio Vigente",
                "Estado",
                "Oferta Inicio",
                "Oferta Fin"
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
            headerRow.getCell(i).setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (ProductoVarianteDisponibleExcelRow fila : filas) {
            Row row = sheet.createRow(rowIdx++);
            Double precioVigente = resolverPrecioVigente(
                    fila.precio(),
                    fila.precioOferta(),
                    fila.ofertaInicio(),
                    fila.ofertaFin());
            row.createCell(0).setCellValue(fila.idProductoVariante() == null ? 0 : fila.idProductoVariante());
            row.createCell(1).setCellValue(valorTexto(fila.sku()));
            row.createCell(2).setCellValue(valorTexto(fila.codigoBarras()));
            row.createCell(3).setCellValue(valorTexto(fila.productoNombre()));
            row.createCell(4).setCellValue(valorTexto(fila.categoriaNombre()));
            row.createCell(5).setCellValue(valorTexto(fila.sucursalNombre()));
            row.createCell(6).setCellValue(valorTexto(fila.colorNombre()));
            row.createCell(7).setCellValue(valorTexto(fila.tallaNombre()));
            row.createCell(8).setCellValue(fila.stock() == null ? 0 : fila.stock());
            row.createCell(9).setCellValue(fila.precio() == null ? 0 : fila.precio());
            row.createCell(10).setCellValue(fila.precioMayor() == null ? 0 : fila.precioMayor());
            row.createCell(11).setCellValue(fila.precioOferta() == null ? 0 : fila.precioOferta());
            row.createCell(12).setCellValue(precioVigente == null ? 0 : precioVigente);
            row.createCell(13).setCellValue(valorTexto(fila.estado()));
            row.createCell(14).setCellValue(
                    fila.ofertaInicio() == null ? "" : fila.ofertaInicio().format(FECHA_HORA_EXCEL));
            row.createCell(15).setCellValue(fila.ofertaFin() == null ? "" : fila.ofertaFin().format(FECHA_HORA_EXCEL));

            row.getCell(9).setCellStyle(moneyStyle);
            row.getCell(10).setCellStyle(moneyStyle);
            row.getCell(11).setCellStyle(moneyStyle);
            row.getCell(12).setCellStyle(moneyStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle crearEstiloHeader(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloMoneda(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private String valorTexto(String value) {
        return value == null ? "" : value;
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

    private int sumarStocksSolicitados(
            java.util.List<com.sistemapos.sistematextil.util.producto.ProductoVarianteStockCreateItem> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return 0;
        }
        return stocks.stream()
                .map(com.sistemapos.sistematextil.util.producto.ProductoVarianteStockCreateItem::cantidad)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
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

        List<ProductoColorImagen> imagenes = productoColorImagenRepository
                .findByProductoIdProductoInAndDeletedAtIsNull(productoIds);
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

    private Map<Integer, List<StockSucursalVentaResumen>> agruparStocksVenta(
            List<ProductoVarianteStockSucursalRow> rows) {
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

    private ProductoVarianteListadoResumenResponse toListadoResumenResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, ImagenDetalleGroup> imagenes) {
        return toListadoResumenResponse(variante, imagenes, List.of(), null);
    }

    private ProductoVarianteListadoResumenResponse toListadoResumenResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, ImagenDetalleGroup> imagenes,
            List<StockSucursalVentaResumen> stocksSucursalesVenta,
            Sucursal sucursalContexto) {
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

        ProductoVarianteListadoResumenResponse.SucursalItem sucursal = null;
        if (sucursalContexto != null) {
            sucursal = new ProductoVarianteListadoResumenResponse.SucursalItem(
                    sucursalContexto.getIdSucursal(),
                    sucursalContexto.getNombre());
        } else if (producto != null && producto.getSucursal() != null) {
            sucursal = new ProductoVarianteListadoResumenResponse.SucursalItem(
                    producto.getSucursal().getIdSucursal(),
                    producto.getSucursal().getNombre());
        } else if (stocksSucursalesVenta.size() == 1) {
            StockSucursalVentaResumen stockSucursal = stocksSucursalesVenta.get(0);
            sucursal = new ProductoVarianteListadoResumenResponse.SucursalItem(
                    stockSucursal.idSucursal(),
                    stockSucursal.nombreSucursal());
        }

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

        int stockTotal = stocksSucursalesVenta.isEmpty()
                ? (variante.getStock() == null ? 0 : variante.getStock())
                : stocksSucursalesVenta.stream()
                        .map(StockSucursalVentaResumen::stock)
                        .filter(stock -> stock != null)
                        .mapToInt(Integer::intValue)
                        .sum();

        return new ProductoVarianteListadoResumenResponse(
                variante.getIdProductoVariante(),
                variante.getSku(),
                variante.getCodigoBarras(),
                variante.getEstado(),
                stockTotal,
                stocksSucursalesVenta,
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
                variante.getCodigoBarras(),
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

    private ProductoVariantePosResponse toPosResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, ImagenDetalleGroup> imagenes) {
        Producto producto = variante.getProducto();
        Integer productoId = producto != null ? producto.getIdProducto() : null;
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        ProductoColorKey key = productoId != null && colorId != null ? new ProductoColorKey(productoId, colorId) : null;
        ImagenDetalleGroup imagenGroup = key != null ? imagenes.get(key) : null;

        ProductoVariantePosResponse.ProductoItem productoItem = producto != null
                ? new ProductoVariantePosResponse.ProductoItem(
                        producto.getIdProducto(),
                        producto.getNombre(),
                        producto.getDescripcion())
                : null;

        ProductoVariantePosResponse.ColorItem colorItem = variante.getColor() != null
                ? new ProductoVariantePosResponse.ColorItem(
                        variante.getColor().getIdColor(),
                        variante.getColor().getNombre(),
                        variante.getColor().getCodigo())
                : null;

        ProductoVariantePosResponse.TallaItem tallaItem = variante.getTalla() != null
                ? new ProductoVariantePosResponse.TallaItem(
                        variante.getTalla().getIdTalla(),
                        variante.getTalla().getNombre())
                : null;

        ProductoVariantePosResponse.ImagenItem imagenPrincipal = imagenGroup != null
                && imagenGroup.imagenPrincipal() != null
                        ? new ProductoVariantePosResponse.ImagenItem(
                                imagenGroup.imagenPrincipal().url(),
                                imagenGroup.imagenPrincipal().urlThumb())
                        : null;

        return new ProductoVariantePosResponse(
                variante.getIdProductoVariante(),
                variante.getSucursal() != null ? variante.getSucursal().getIdSucursal() : null,
                variante.getCodigoBarras(),
                variante.getSku(),
                variante.getStock(),
                variante.getEstado(),
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
                colorItem,
                tallaItem,
                imagenPrincipal);
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
