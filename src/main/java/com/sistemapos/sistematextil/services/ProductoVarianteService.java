package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.sistemapos.sistematextil.model.ProductoVarianteOfertaSucursal;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteOfertaSucursalRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteDisponibleExcelRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteListadoResumenPageResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteListadoResumenResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteItemRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaSucursalLoteItemRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaSucursalLoteUpdateRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaSucursalUpdateRequest;
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
    private static final String ESTADO_VARIANTE_INACTIVA = "INACTIVO";
    private static final String VALOR_ACTIVO = "ACTIVO";
    private static final String VALOR_INACTIVO = "INACTIVO";
    private static final DateTimeFormatter FECHA_HORA_EXCEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductoVarianteRepository repository;
    private final ProductoVarianteOfertaSucursalRepository productoVarianteOfertaSucursalRepository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final ProductoService productoService;
    private final TallaService tallaService;
    private final ColorService colorService;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final StockMovimientoService stockMovimientoService;
    private final PrecioOfertaService precioOfertaService;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;

    @PersistenceContext
    private final EntityManager entityManager;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public List<ProductoVariante> listarTodas(Integer idSucursal) {
        Integer idSucursalFiltro = normalizarIdSucursalFiltro(idSucursal);
        return idSucursalFiltro == null
                ? repository.findByDeletedAtIsNullOrderByStockDescCreatedAtDescIdProductoVarianteDesc()
                : repository.findByDeletedAtIsNullAndSucursal_IdSucursal(idSucursalFiltro);
    }

    public List<ProductoVariante> listarPorProducto(Integer idProducto, Integer idSucursal) {
        Integer idSucursalFiltro = normalizarIdSucursalFiltro(idSucursal);
        return idSucursalFiltro == null
                ? repository.findByProductoIdProductoAndDeletedAtIsNullOrderByStockDescCreatedAtDescIdProductoVarianteDesc(idProducto)
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
        ProductoVarianteOfertaSucursal ofertaSucursal = precioOfertaService.obtenerOfertaSucursal(
                varianteEscaneable.getIdProductoVariante(),
                idSucursalFiltro);
        return toPosResponse(varianteEscaneable, imagenes, ofertaSucursal);
    }

    public ProductoVariante obtenerVarianteEditableConAlcance(
            Integer idProductoVariante,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicion(usuarioAutenticado);
        return obtenerVarianteConAlcance(idProductoVariante, usuarioAutenticado);
    }

    public ProductoVarianteListadoResumenPageResponse listarResumenPaginado(
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
        PageRequest pageable = PageRequest.of(page, pageSize);
        List<String> tokens = tokensBusqueda(term);
        Page<ProductoVariante> variantes = repository.buscarResumenPaginado(
                term,
                token(tokens, 0),
                token(tokens, 1),
                token(tokens, 2),
                token(tokens, 3),
                token(tokens, 4),
                token(tokens, 5),
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
        Map<Integer, ProductoVarianteOfertaSucursal> ofertasSucursal = precioOfertaService
                .obtenerOfertasSucursalPorVariantes(varianteIds, idSucursalFiltro);
        Map<Integer, List<StockSucursalVentaResumen>> stocksPorVariante = agruparStocksVenta(stockRows);
        Sucursal sucursalContexto = idSucursalFiltro == null
                ? null
                : sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalFiltro).orElse(null);
        Page<ProductoVarianteListadoResumenResponse> pageResponse = variantes.map(variante -> toListadoResumenResponse(
                variante,
                imagenes,
                stocksPorVariante.getOrDefault(variante.getIdProductoVariante(), List.of()),
                sucursalContexto,
                false,
                ofertasSucursal.get(variante.getIdProductoVariante())));
        return ProductoVarianteListadoResumenPageResponse.fromPage(
                pageResponse,
                construirImagenesPorColorResponse(imagenes));
    }

    public PagedResponse<ProductoVarianteOfertaListItemResponse> listarConOfertaPaginado(
            int page,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        int pageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        PageRequest pageable = PageRequest.of(page, pageSize, ordenVariantesPorCreacionDesc());
        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado, idSucursal);
        Page<ProductoVariante> variantes = idSucursalFiltro == null
                ? repository.findByPrecioOfertaIsNotNullAndDeletedAtIsNull(pageable)
                : repository.findByPrecioOfertaIsNotNullAndDeletedAtIsNullAndSucursal_IdSucursal(
                        idSucursalFiltro,
                        pageable);
        Map<ProductoColorKey, String> imagenes = resolverImagenesPorProductoColor(variantes.getContent());
        Sucursal sucursalContexto = idSucursalFiltro == null ? null : obtenerSucursalValidaParaFiltro(idSucursalFiltro);
        return PagedResponse.fromPage(variantes.map(variante -> toOfertaListItemResponse(
                sincronizarVarianteSucursal(variante, sucursalContexto),
                imagenes,
                null)));
    }

    private Sort ordenVariantesPorCreacionDesc() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("idProductoVariante"));
    }

    private Sort ordenOfertasSucursalPorCreacionDesc() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("idProductoVarianteOfertaSucursal"));
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

        for (ProductoVariante variante : variantesVencidas) {
            variante.setPrecioOferta(null);
            variante.setOfertaInicio(null);
            variante.setOfertaFin(null);
            variante.setUsuarioCreacion(null);
        }

        if (!variantesVencidas.isEmpty()) {
            repository.saveAll(variantesVencidas);
        }

        List<ProductoVarianteOfertaSucursal> ofertasSucursalVencidas = productoVarianteOfertaSucursalRepository
                .findByPrecioOfertaIsNotNullAndOfertaFinLessThanEqualAndDeletedAtIsNull(ahora);
        for (ProductoVarianteOfertaSucursal ofertaSucursal : ofertasSucursalVencidas) {
            ofertaSucursal.setPrecioOferta(null);
            ofertaSucursal.setOfertaInicio(null);
            ofertaSucursal.setOfertaFin(null);
            ofertaSucursal.setUsuarioCreacion(null);
            ofertaSucursal.setDeletedAt(ahora);
        }
        if (!ofertasSucursalVencidas.isEmpty()) {
            productoVarianteOfertaSucursalRepository.saveAll(ofertasSucursalVencidas);
        }
    }

    @Transactional
    public ProductoVariante insertar(ProductoVariante variante, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

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
        usuarioSucursalAccessService.validarSucursalPermitida(
                usuarioAutenticado,
                variante.getSucursal().getIdSucursal(),
                "No tiene permisos para crear variantes en otra sucursal");

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
        destino.setUsuarioCreacion(precioOferta != null ? usuarioAutenticado : null);
        destino.setStock(variante.getStock());
        destino.setEstado(resolverEstadoVarianteSegunStock(variante.getStock()));
        destino.setActivo(VALOR_ACTIVO);
        destino.setDeletedAt(null);

        ProductoVariante guardado = repository.saveAndFlush(destino);
        entityManager.clear();

        return repository.findById(guardado.getIdProductoVariante())
                .orElseThrow(() -> new RuntimeException("Error al recuperar la variante guardada"));
    }

    public ProductoVariante actualizarStock(Integer id, Integer nuevoStock, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        ProductoVariante variante = obtenerVarianteConAlcance(id, usuarioAutenticado);

        variante.setStock(nuevoStock);
        variante.setEstado(resolverEstadoVarianteSegunStock(nuevoStock));
        return repository.save(variante);
    }

    public ProductoVariante actualizarPrecio(Integer id, Double nuevoPrecio, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        ProductoVariante variante = obtenerVarianteConAlcance(id, usuarioAutenticado);

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
        if (idSucursal != null) {
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
        actualizarUsuarioCreacionOferta(variante, precioOferta, usuarioAutenticado);
        variante.setPrecioOferta(precioOferta);
        variante.setOfertaInicio(ofertaInicio);
        variante.setOfertaFin(ofertaFin);
        if (request.stocksSucursales() != null && !request.stocksSucursales().isEmpty()) {
            int stockSolicitado = sumarStocksSolicitados(request.stocksSucursales());
            variante.setStock(stockSolicitado);
            variante.setEstado(resolverEstadoVarianteSegunStock(stockSolicitado));
        }

        ProductoVariante actualizada = repository.save(variante);

        if (idColorAnterior != null && !idColorAnterior.equals(color.getIdColor())) {
            productoService.limpiarImagenesColorSiNoHayVariantesActivas(idProducto, idColorAnterior);
        }

        Map<ProductoColorKey, ImagenDetalleGroup> imagenes = resolverImagenesDetallePorProductoColor(List.of(actualizada));
        return toListadoResumenResponse(actualizada, imagenes, List.of(), null, true, null);
    }

    public ProductoVariante actualizarOferta(
            Integer id,
            ProductoVarianteOfertaUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        ProductoVariante variante = obtenerVarianteConAlcance(id, usuarioAutenticado);

        aplicarOferta(variante, request.precioOferta(), request.ofertaInicio(), request.ofertaFin(), usuarioAutenticado);
        return repository.save(variante);
    }

    @Transactional
    public List<ProductoVarianteOfertaListItemResponse> actualizarOfertasLote(
            ProductoVarianteOfertaLoteUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
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

        List<ProductoVariante> variantes = esAdministrador(usuarioAutenticado)
                ? repository.findByIdProductoVarianteInAndDeletedAtIsNull(idsSolicitados)
                : idsSolicitados.stream()
                        .map(id -> obtenerVarianteConAlcance(id, usuarioAutenticado))
                        .toList();
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
            aplicarOferta(variante, item.precioOferta(), item.ofertaInicio(), item.ofertaFin(), usuarioAutenticado);
            actualizadas.add(variante);
        }

        repository.saveAll(actualizadas);
        Map<ProductoColorKey, String> imagenes = resolverImagenesPorProductoColor(actualizadas);
        return actualizadas.stream()
                .map(variante -> toOfertaListItemResponse(variante, imagenes, null))
                .toList();
    }

    public ProductoVarianteOfertaSucursal actualizarOfertaSucursal(
            Integer idProductoVariante,
            Integer idSucursal,
            ProductoVarianteOfertaSucursalUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        Integer idSucursalDestino = resolverIdSucursalFiltro(usuarioAutenticado, idSucursal);
        ProductoVariante variante = obtenerVarianteConAlcance(idProductoVariante, usuarioAutenticado);
        return guardarOfertaSucursal(
                variante,
                idSucursalDestino,
                request.precioOferta(),
                request.ofertaInicio(),
                request.ofertaFin(),
                usuarioAutenticado);
    }

    @Transactional
    public List<ProductoVarianteOfertaListItemResponse> actualizarOfertasSucursalLote(
            ProductoVarianteOfertaSucursalLoteUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        Set<String> claves = new HashSet<>();
        Map<Integer, ProductoVariante> variantesCache = new HashMap<>();
        List<OfertaSucursalContexto> ofertasRespuesta = new ArrayList<>();

        for (ProductoVarianteOfertaSucursalLoteItemRequest item : request.items()) {
            Integer idSucursalDestino = resolverIdSucursalFiltro(usuarioAutenticado, item.idSucursal());
            String clave = item.idProductoVariante() + "-" + idSucursalDestino;
            if (!claves.add(clave)) {
                throw new RuntimeException("No puede repetir la misma variante y sucursal en la misma actualizacion");
            }

            ProductoVariante variante = variantesCache.computeIfAbsent(
                    item.idProductoVariante(),
                    id -> obtenerVarianteConAlcance(id, usuarioAutenticado));
            ProductoVarianteOfertaSucursal oferta = guardarOfertaSucursal(
                    variante,
                    idSucursalDestino,
                    item.precioOferta(),
                    item.ofertaInicio(),
                    item.ofertaFin(),
                    usuarioAutenticado);
            Sucursal sucursalContexto = oferta != null ? oferta.getSucursal() : obtenerSucursalValidaParaFiltro(idSucursalDestino);
            ofertasRespuesta.add(new OfertaSucursalContexto(
                    sincronizarVarianteSucursal(variante, sucursalContexto),
                    oferta));
        }

        Map<ProductoColorKey, String> imagenes = resolverImagenesPorProductoColor(
                ofertasRespuesta.stream().map(OfertaSucursalContexto::variante).toList());
        return ofertasRespuesta.stream()
                .map(item -> toOfertaListItemResponse(
                        item.variante(),
                        imagenes,
                        item.ofertaSucursal()))
                .toList();
    }

    public PagedResponse<ProductoVarianteOfertaListItemResponse> listarOfertasSucursalPaginado(
            int page,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado, idSucursal);
        int pageSize = defaultPageSize > 0 ? defaultPageSize : 10;
        PageRequest pageable = PageRequest.of(page, pageSize, ordenOfertasSucursalPorCreacionDesc());
        Page<ProductoVarianteOfertaSucursal> ofertas = productoVarianteOfertaSucursalRepository
                .findActivasPorSucursal(idSucursalFiltro, pageable);
        List<ProductoVariante> variantes = ofertas.getContent().stream()
                .map(oferta -> sincronizarVarianteSucursal(oferta.getProductoVariante(), oferta.getSucursal()))
                .toList();
        Map<ProductoColorKey, String> imagenes = resolverImagenesPorProductoColor(variantes);
        return PagedResponse.fromPage(ofertas.map(oferta -> toOfertaListItemResponse(
                sincronizarVarianteSucursal(oferta.getProductoVariante(), oferta.getSucursal()),
                imagenes,
                oferta)));
    }

    public void eliminar(Integer id, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);
        ProductoVariante variante = obtenerVarianteConAlcance(id, usuarioAutenticado);

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

    private List<String> tokensBusqueda(String term) {
        if (term == null) {
            return List.of();
        }
        return java.util.Arrays.stream(term.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .limit(6)
                .toList();
    }

    private String token(List<String> tokens, int index) {
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private String resolverEstadoVarianteSegunStock(Integer stock) {
        return stock != null && stock <= 0 ? ESTADO_VARIANTE_INACTIVA : ESTADO_VARIANTE_ACTIVA;
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer resolverIdSucursalFiltro(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        return usuarioSucursalAccessService.resolverIdSucursalFiltro(
                usuarioAutenticado,
                idSucursalRequest,
                "No tiene permisos para consultar otra sucursal");
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
        return usuarioSucursalAccessService.resolverIdSucursalPermitida(
                usuarioAutenticado,
                idSucursalRequest,
                "No tiene permisos para consultar otra sucursal");
    }

    private Sucursal obtenerSucursalValidaParaFiltro(Integer idSucursal) {
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
    }

    private ProductoVariante obtenerVarianteConAlcance(Integer idProductoVariante, Usuario usuarioAutenticado) {
        ProductoVariante variante = repository.findByIdProductoVarianteAndDeletedAtIsNull(idProductoVariante)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));
        Integer idSucursal = variante.getSucursal() != null ? variante.getSucursal().getIdSucursal() : null;
        usuarioSucursalAccessService.validarSucursalPermitida(
                usuarioAutenticado,
                idSucursal,
                "Variante no encontrada");
        return variante;
    }

    private void validarRolPermitido(Usuario usuario) {
        if (!usuario.getRol().permiteVentas() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para gestionar productos");
        }
    }

    private void validarRolEdicion(Usuario usuario) {
        if (!usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para editar imagenes de variantes");
        }
    }

    private void validarRolPermitidoEscaneo(Usuario usuario) {
        if (!usuario.getRol().permiteVentas()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para escanear productos");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol().esAdministrador();
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
        return precioOfertaService.normalizarPrecioOferta(precioOferta);
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
        precioOfertaService.validarPrecioOferta(precio, precioOferta, ofertaInicio, ofertaFin);
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
            LocalDateTime ofertaFinSolicitada,
            Usuario usuarioAutenticado) {
        Double precioOferta = normalizarPrecioOferta(precioOfertaSolicitado);
        LocalDateTime ofertaInicio = ofertaInicioSolicitada;
        LocalDateTime ofertaFin = ofertaFinSolicitada;
        boolean sinOfertaAnterior = variante.getPrecioOferta() == null;

        validarPrecioOferta(variante.getPrecio(), precioOferta, ofertaInicio, ofertaFin);
        if (precioOferta == null) {
            ofertaInicio = null;
            ofertaFin = null;
            variante.setUsuarioCreacion(null);
        } else if (sinOfertaAnterior) {
            variante.setUsuarioCreacion(usuarioAutenticado);
        }

        variante.setPrecioOferta(precioOferta);
        variante.setOfertaInicio(ofertaInicio);
        variante.setOfertaFin(ofertaFin);
    }

    private void actualizarUsuarioCreacionOferta(
            ProductoVariante variante,
            Double precioOferta,
            Usuario usuarioAutenticado) {
        if (precioOferta == null) {
            variante.setUsuarioCreacion(null);
            return;
        }
        if (variante.getPrecioOferta() == null) {
            variante.setUsuarioCreacion(usuarioAutenticado);
        }
    }

    private ProductoVarianteOfertaSucursal guardarOfertaSucursal(
            ProductoVariante variante,
            Integer idSucursal,
            Double precioOfertaSolicitado,
            LocalDateTime ofertaInicioSolicitada,
            LocalDateTime ofertaFinSolicitada,
            Usuario usuarioAutenticado) {
        Double precioOferta = normalizarPrecioOferta(precioOfertaSolicitado);
        LocalDateTime ofertaInicio = ofertaInicioSolicitada;
        LocalDateTime ofertaFin = ofertaFinSolicitada;
        validarPrecioOferta(variante.getPrecio(), precioOferta, ofertaInicio, ofertaFin);

        ProductoVarianteOfertaSucursal oferta = productoVarianteOfertaSucursalRepository
                .findByProductoVarianteIdProductoVarianteAndSucursalIdSucursal(
                        variante.getIdProductoVariante(),
                        idSucursal)
                .orElseGet(() -> {
                    ProductoVarianteOfertaSucursal nueva = new ProductoVarianteOfertaSucursal();
                    nueva.setProductoVariante(variante);
                    nueva.setSucursal(obtenerSucursalValidaParaFiltro(idSucursal));
                    return nueva;
                });
        boolean ofertaNueva = oferta.getIdProductoVarianteOfertaSucursal() == null
                || oferta.getDeletedAt() != null
                || oferta.getPrecioOferta() == null;

        if (precioOferta == null) {
            if (oferta.getIdProductoVarianteOfertaSucursal() == null) {
                return null;
            }
            oferta.setPrecioOferta(null);
            oferta.setOfertaInicio(null);
            oferta.setOfertaFin(null);
            oferta.setUsuarioCreacion(null);
            oferta.setDeletedAt(LocalDateTime.now());
            return productoVarianteOfertaSucursalRepository.save(oferta);
        }

        oferta.setDeletedAt(null);
        if (ofertaNueva) {
            oferta.setUsuarioCreacion(usuarioAutenticado);
        }
        oferta.setPrecioOferta(precioOferta);
        oferta.setOfertaInicio(ofertaInicio);
        oferta.setOfertaFin(ofertaFin);
        return productoVarianteOfertaSucursalRepository.save(oferta);
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
            List<ProductoVarianteListadoResumenResponse.ImagenItem> deduplicadas = deduplicarImagenes(sorted);
            ProductoVarianteListadoResumenResponse.ImagenItem principal = deduplicadas.isEmpty() ? null : deduplicadas.get(0);
            result.put(entry.getKey(), new ImagenDetalleGroup(principal, deduplicadas));
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
        return toListadoResumenResponse(variante, imagenes, List.of(), null, true, null);
    }

    private ProductoVarianteListadoResumenResponse toListadoResumenResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, ImagenDetalleGroup> imagenes,
            List<StockSucursalVentaResumen> stocksSucursalesVenta,
            Sucursal sucursalContexto,
            boolean incluirImagenesEmbebidas,
            ProductoVarianteOfertaSucursal ofertaSucursal) {
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

        PrecioOfertaService.ResultadoPrecioOferta precioResuelto = precioOfertaService.resolver(variante, ofertaSucursal);

        return new ProductoVarianteListadoResumenResponse(
                variante.getIdProductoVariante(),
                variante.getSku(),
                variante.getCodigoBarras(),
                variante.getEstado(),
                stockTotal,
                stocksSucursalesVenta,
                variante.getPrecio(),
                variante.getPrecioMayor(),
                precioResuelto.precioOfertaAplicada(),
                precioResuelto.ofertaInicioAplicada(),
                precioResuelto.ofertaFinAplicada(),
                precioResuelto.precioVigente(),
                precioResuelto.tipoOfertaAplicada(),
                precioResuelto.sucursalOfertaId(),
                productoItem,
                color,
                talla,
                key != null ? new ProductoVarianteListadoResumenResponse.GrupoImagenRef(
                        key.value(),
                        key.productoId(),
                        key.colorId()) : null,
                incluirImagenesEmbebidas && imagenGroup != null ? imagenGroup.imagenPrincipal() : null,
                incluirImagenesEmbebidas && imagenGroup != null ? imagenGroup.imagenes() : List.of());
    }

    private List<ProductoVarianteListadoResumenPageResponse.ImagenGrupoItem> construirImagenesPorColorResponse(
            Map<ProductoColorKey, ImagenDetalleGroup> imagenes) {
        if (imagenes == null || imagenes.isEmpty()) {
            return List.of();
        }
        return imagenes.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<ProductoColorKey, ImagenDetalleGroup> entry) -> entry.getKey().productoId())
                        .thenComparing(entry -> entry.getKey().colorId()))
                .map(entry -> new ProductoVarianteListadoResumenPageResponse.ImagenGrupoItem(
                        entry.getKey().value(),
                        entry.getKey().productoId(),
                        entry.getKey().colorId(),
                        entry.getValue().imagenPrincipal(),
                        entry.getValue().imagenes()))
                .toList();
    }

    private List<ProductoVarianteListadoResumenResponse.ImagenItem> deduplicarImagenes(
            List<ProductoVarianteListadoResumenResponse.ImagenItem> imagenes) {
        if (imagenes == null || imagenes.isEmpty()) {
            return List.of();
        }
        Map<String, ProductoVarianteListadoResumenResponse.ImagenItem> unicas = new LinkedHashMap<>();
        for (ProductoVarianteListadoResumenResponse.ImagenItem imagen : imagenes) {
            if (imagen == null) {
                continue;
            }
            String key = imagen.idColorImagen() != null
                    ? "ID:" + imagen.idColorImagen()
                    : "URL:" + safe(imagen.url()) + "|" + safe(imagen.urlThumb());
            unicas.putIfAbsent(key, imagen);
        }
        return List.copyOf(unicas.values());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Double resolverPrecioVigente(
            Double precio,
            Double precioOferta,
            LocalDateTime ofertaInicio,
            LocalDateTime ofertaFin) {
        return precioOfertaService.resolverPrecioVigente(precio, precioOferta, ofertaInicio, ofertaFin);
    }

    private ProductoVarianteOfertaListItemResponse toOfertaListItemResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, String> imagenes,
            ProductoVarianteOfertaSucursal ofertaSucursal) {
        Integer productoId = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        String productoNombre = variante.getProducto() != null ? variante.getProducto().getNombre() : null;
        Sucursal sucursalOferta = ofertaSucursal != null ? ofertaSucursal.getSucursal() : variante.getSucursal();
        Integer sucursalId = sucursalOferta != null ? sucursalOferta.getIdSucursal() : null;
        String sucursalNombre = sucursalOferta != null ? sucursalOferta.getNombre() : null;
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        String colorNombre = variante.getColor() != null ? variante.getColor().getNombre() : null;
        String colorHex = variante.getColor() != null ? variante.getColor().getCodigo() : null;
        Integer tallaId = variante.getTalla() != null ? variante.getTalla().getIdTalla() : null;
        String tallaNombre = variante.getTalla() != null ? variante.getTalla().getNombre() : null;
        String imagenUrl = productoId != null && colorId != null
                ? imagenes.get(new ProductoColorKey(productoId, colorId))
                : null;

        PrecioOfertaService.ResultadoPrecioOferta precioResuelto = precioOfertaService.resolver(variante, ofertaSucursal);
        Usuario usuarioCreacion = ofertaSucursal != null
                ? ofertaSucursal.getUsuarioCreacion()
                : variante.getUsuarioCreacion();
        Integer usuarioCreacionId = usuarioCreacion != null ? usuarioCreacion.getIdUsuario() : null;
        String usuarioCreacionNombre = usuarioCreacion != null
                ? nombreCompletoUsuario(usuarioCreacion)
                : null;
        String usuarioCreacionCorreo = usuarioCreacion != null ? usuarioCreacion.getCorreo() : null;

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
                precioResuelto.precioOfertaAplicada(),
                precioResuelto.ofertaInicioAplicada(),
                precioResuelto.ofertaFinAplicada(),
                precioResuelto.precioVigente(),
                precioResuelto.tipoOfertaAplicada(),
                precioResuelto.sucursalOfertaId(),
                usuarioCreacionId,
                usuarioCreacionNombre,
                usuarioCreacionCorreo,
                imagenUrl,
                variante.getStock(),
                variante.getEstado());
    }

    private String nombreCompletoUsuario(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? null : completo;
    }

    private ProductoVariantePosResponse toPosResponse(
            ProductoVariante variante,
            Map<ProductoColorKey, ImagenDetalleGroup> imagenes,
            ProductoVarianteOfertaSucursal ofertaSucursal) {
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

        PrecioOfertaService.ResultadoPrecioOferta precioResuelto = precioOfertaService.resolver(variante, ofertaSucursal);

        return new ProductoVariantePosResponse(
                variante.getIdProductoVariante(),
                variante.getSucursal() != null ? variante.getSucursal().getIdSucursal() : null,
                variante.getCodigoBarras(),
                variante.getSku(),
                variante.getStock(),
                variante.getEstado(),
                variante.getPrecio(),
                variante.getPrecioMayor(),
                precioResuelto.precioOfertaAplicada(),
                precioResuelto.ofertaInicioAplicada(),
                precioResuelto.ofertaFinAplicada(),
                precioResuelto.precioVigente(),
                precioResuelto.tipoOfertaAplicada(),
                precioResuelto.sucursalOfertaId(),
                productoItem,
                colorItem,
                tallaItem,
                imagenPrincipal);
    }

    private ProductoVariante sincronizarVarianteSucursal(ProductoVariante variante, Sucursal sucursal) {
        if (variante != null) {
            variante.setSucursal(sucursal);
        }
        return variante;
    }

    private record ProductoColorKey(Integer productoId, Integer colorId) {
        private String value() {
            return productoId + "-" + colorId;
        }
    }

    private record ImagenDetalleGroup(
            ProductoVarianteListadoResumenResponse.ImagenItem imagenPrincipal,
            List<ProductoVarianteListadoResumenResponse.ImagenItem> imagenes) {
    }

    private record ImagenPick(String url, int orden, boolean esPrincipal) {
    }

    private record OfertaSucursalContexto(
            ProductoVariante variante,
            ProductoVarianteOfertaSucursal ofertaSucursal) {
    }
}
