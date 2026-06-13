package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.ColorRepository;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.TallaRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.producto.ProductoImportConfigRequest;
import com.sistemapos.sistematextil.util.producto.ProductoImportProductoRequest;
import com.sistemapos.sistematextil.util.producto.ProductoImportRequest;
import com.sistemapos.sistematextil.util.producto.ProductoImportResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImportVarianteRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoImportService {

    private static final String ORIGEN_IMPORTACION_JSON = "frontend_json_import";

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoService productoService;
    private final ColorRepository colorRepository;
    private final TallaRepository tallaRepository;
    private final ImportacionProductoHistorialService importacionProductoHistorialService;
    private final StockMovimientoService stockMovimientoService;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;

    @Transactional
    public ProductoImportResponse importar(ProductoImportRequest request, String correoUsuarioAutenticado) {
        long inicioMillis = System.currentTimeMillis();
        Usuario usuario = null;
        int filasProcesadas = 0;
        ResumenImportacion resumen = new ResumenImportacion();

        try {
            validarRequest(request);
            usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
            validarRolPermitido(usuario);

            Sucursal sucursalDestino = resolverSucursalDestino(request.configuracionImportacion(), usuario);
            List<FilaImportacion> filas = normalizarFilas(request, sucursalDestino);
            filasProcesadas = filas.size();

            Map<ProductoClave, ProductoAgrupado> productosAgrupados = agruparFilas(filas);
            resumen = persistirAgrupados(productosAgrupados, usuario);

            importacionProductoHistorialService.registrarExitosa(
                    usuario,
                    sucursalDestino.getIdSucursal(),
                    ORIGEN_IMPORTACION_JSON,
                    0L,
                    filasProcesadas,
                    resumen.productosCreados,
                    resumen.productosActualizados,
                    resumen.variantesGuardadas,
                    resumen.categoriasCreadas,
                    resumen.coloresCreados,
                    resumen.tallasCreadas,
                    calcularDuracionMs(inicioMillis));

            return new ProductoImportResponse(
                    filasProcesadas,
                    resumen.productosCreados,
                    resumen.productosActualizados,
                    resumen.variantesGuardadas,
                    resumen.coloresCreados,
                    resumen.tallasCreadas);
        } catch (RuntimeException e) {
            registrarFalloSinInterrumpir(usuario, request, filasProcesadas, resumen, inicioMillis, e);
            throw e;
        }
    }

    private void validarRequest(ProductoImportRequest request) {
        if (request == null) {
            throw new RuntimeException("Debe enviar datos de importacion");
        }
        if (request.configuracionImportacion() == null) {
            throw new RuntimeException("Debe enviar configuracionImportacion");
        }
        if (request.productos() == null || request.productos().isEmpty()) {
            throw new RuntimeException("Debe enviar productos");
        }
    }

    private void registrarFalloSinInterrumpir(
            Usuario usuario,
            ProductoImportRequest request,
            int filasProcesadas,
            ResumenImportacion resumen,
            long inicioMillis,
            RuntimeException causa) {
        if (usuario == null || usuario.getIdUsuario() == null) {
            return;
        }

        Integer idSucursal = null;
        if (request != null && request.configuracionImportacion() != null) {
            try {
                idSucursal = resolverIdSucursalHistorial(request.configuracionImportacion(), usuario);
            } catch (RuntimeException ignored) {
                idSucursal = resolverIdSucursalHistorialDesdeUsuario(usuario);
            }
        } else {
            idSucursal = resolverIdSucursalHistorialDesdeUsuario(usuario);
        }

        try {
            importacionProductoHistorialService.registrarFallida(
                    usuario,
                    idSucursal,
                    ORIGEN_IMPORTACION_JSON,
                    0L,
                    filasProcesadas,
                    resumen.productosCreados,
                    resumen.productosActualizados,
                    resumen.variantesGuardadas,
                    resumen.categoriasCreadas,
                    resumen.coloresCreados,
                    resumen.tallasCreadas,
                    causa.getMessage(),
                    calcularDuracionMs(inicioMillis));
        } catch (RuntimeException ignored) {
            // Si historial falla, no tapar error original.
        }
    }

    private Integer resolverIdSucursalHistorial(ProductoImportConfigRequest config, Usuario usuario) {
        return resolverSucursalDestino(config, usuario).getIdSucursal();
    }

    private Integer resolverIdSucursalHistorialDesdeUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        if (usuario.getRol().esAdministrador()) {
            return null;
        }
        return usuario.getSucursal() == null ? null : usuario.getSucursal().getIdSucursal();
    }

    private int calcularDuracionMs(long inicioMillis) {
        long duracion = System.currentTimeMillis() - inicioMillis;
        if (duracion <= 0) {
            return 0;
        }
        if (duracion > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) duracion;
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolPermitido(Usuario usuario) {
        if (!usuario.getRol().permiteVentas() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para importar productos");
        }
    }

    private Sucursal resolverSucursalDestino(ProductoImportConfigRequest config, Usuario usuario) {
        if (usuario.getRol().esAdministrador()) {
            Integer idSucursal = config.idSucursalDestino();
            if (idSucursal == null) {
                throw new RuntimeException("configuracionImportacion.idSucursalDestino es obligatorio");
            }
            Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                    .orElseThrow(() -> new RuntimeException("Sucursal destino no encontrada"));
            if (!"ACTIVO".equalsIgnoreCase(sucursal.getEstado())) {
                throw new RuntimeException("La sucursal destino esta INACTIVA");
            }
            return sucursal;
        }

        Integer idSucursalDestino = usuarioSucursalAccessService.resolverIdSucursalPermitida(
                usuario,
                config.idSucursalDestino(),
                "No tiene permisos para importar en otra sucursal");
        Sucursal sucursalDestino = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalDestino)
                .orElseThrow(() -> new RuntimeException("Sucursal destino no encontrada"));
        if (!"ACTIVO".equalsIgnoreCase(sucursalDestino.getEstado())) {
            throw new RuntimeException("La sucursal destino esta INACTIVA");
        }
        return sucursalDestino;
    }

    private List<FilaImportacion> normalizarFilas(ProductoImportRequest request, Sucursal sucursalDestino) {
        List<FilaImportacion> filas = new ArrayList<>();
        int fila = 1;

        for (ProductoImportProductoRequest producto : request.productos()) {
            if (producto == null) {
                throw new RuntimeException("Producto invalido en posicion " + fila);
            }
            if (producto.variantes() == null || producto.variantes().isEmpty()) {
                throw new RuntimeException("Producto '" + safe(producto.nombreProducto()) + "' no tiene variantes");
            }

            String nombreProducto = requerido(producto.nombreProducto(), "nombreProducto", fila);
            validarLongitudMaxima(nombreProducto, 150, "nombreProducto", fila);

            String descripcion = opcional(producto.descripcion());
            validarLongitudMaxima(descripcion, 500, "descripcion", fila);

            String categoriaNombre = limpiarTextoCatalogo(requerido(producto.categoriaNombre(), "categoriaNombre", fila));
            validarLongitudMaxima(categoriaNombre, 100, "categoriaNombre", fila);

            for (ProductoImportVarianteRequest variante : producto.variantes()) {
                filas.add(construirFilaImportacion(
                        fila,
                        request.configuracionImportacion(),
                        sucursalDestino,
                        nombreProducto,
                        descripcion,
                        categoriaNombre,
                        variante));
                fila++;
            }
        }

        if (filas.isEmpty()) {
            throw new RuntimeException("El payload no contiene filas de productos");
        }
        return filas;
    }

    private FilaImportacion construirFilaImportacion(
            int fila,
            ProductoImportConfigRequest config,
            Sucursal sucursalDestino,
            String nombreProducto,
            String descripcion,
            String categoriaNombre,
            ProductoImportVarianteRequest variante) {
        if (variante == null) {
            throw new RuntimeException("Variante invalida en producto '" + nombreProducto + "'");
        }
        String colorNombre = limpiarTextoCatalogo(requerido(variante.colorNombre(), "colorNombre", fila));
        validarLongitudMaxima(colorNombre, 50, "colorNombre", fila);

        String tallaNombre = limpiarTextoCatalogo(requerido(variante.tallaNombre(), "tallaNombre", fila));
        validarLongitudMaxima(tallaNombre, 20, "tallaNombre", fila);

        String colorHex = opcional(variante.colorHex());
        validarLongitudMaxima(colorHex, 20, "colorHex", fila);
        colorHex = normalizarHex(colorHex, fila);

        BigDecimal precio = decimalRequerido(variante.precio(), "precio", fila);
        BigDecimal precioMayor = decimalOpcionalNoNegativo(variante.precioMayor(), "precioMayor", fila);
        validarPrecioMayor(precio, precioMayor, fila);
        int stock = enteroNoNegativo(variante.stock(), "stock", fila);

        String sku = requerido(variante.sku(), "sku", fila);
        validarLongitudMaxima(sku, 100, "sku", fila);

        String codigoBarras = requerido(variante.codigoBarras(), "codigoBarras", fila);
        validarLongitudMaxima(codigoBarras, 100, "codigoBarras", fila);

        return new FilaImportacion(
                fila,
                sucursalDestino,
                nombreProducto,
                descripcion,
                categoriaNombre,
                colorNombre,
                colorHex,
                tallaNombre,
                sku,
                codigoBarras,
                precio,
                precioMayor,
                stock);
    }

    private Map<ProductoClave, ProductoAgrupado> agruparFilas(List<FilaImportacion> filas) {
        Map<ProductoClave, ProductoAgrupado> agrupados = new LinkedHashMap<>();
        Map<SkuSucursalClave, Integer> skuPorSucursal = new HashMap<>();
        Map<CodigoBarrasSucursalClave, Integer> codigoPorSucursal = new HashMap<>();

        for (FilaImportacion fila : filas) {
            String skuNormalizado = normalizarComparable(fila.sku());
            SkuSucursalClave skuClave = new SkuSucursalClave(fila.sucursal().getIdSucursal(), skuNormalizado);
            Integer filaDuplicadaSku = skuPorSucursal.putIfAbsent(skuClave, fila.fila());
            if (filaDuplicadaSku != null) {
                throw new RuntimeException("Fila " + fila.fila()
                        + ": el SKU '" + fila.sku()
                        + "' ya fue enviado en la fila " + filaDuplicadaSku + " para la misma sucursal");
            }

            String codigoBarrasNormalizado = normalizarComparable(fila.codigoBarras());
            if (codigoBarrasNormalizado != null) {
                CodigoBarrasSucursalClave codigoClave = new CodigoBarrasSucursalClave(
                        fila.sucursal().getIdSucursal(),
                        codigoBarrasNormalizado);
                Integer filaDuplicadaCodigo = codigoPorSucursal.putIfAbsent(codigoClave, fila.fila());
                if (filaDuplicadaCodigo != null) {
                    throw new RuntimeException("Fila " + fila.fila()
                            + ": el codigoBarras '" + fila.codigoBarras()
                            + "' ya fue enviado en la fila " + filaDuplicadaCodigo + " para la misma sucursal");
                }
            }

            ProductoClave clave = new ProductoClave(
                    fila.sucursal().getIdSucursal(),
                    normalizarComparable(fila.nombreProducto()),
                    normalizarComparable(fila.categoriaNombre()));

            ProductoAgrupado actual = agrupados.get(clave);
            if (actual == null) {
                actual = ProductoAgrupado.crear(fila);
                agrupados.put(clave, actual);
            } else if (actual.descripcion() == null && fila.descripcion() != null) {
                actual = actual.conDescripcion(fila.descripcion());
                agrupados.put(clave, actual);
            }
            actual.agregarVariante(fila);
        }

        return agrupados;
    }

    private ResumenImportacion persistirAgrupados(Map<ProductoClave, ProductoAgrupado> agrupados, Usuario usuario) {
        ResumenImportacion resumen = new ResumenImportacion();

        for (ProductoAgrupado agrupado : agrupados.values()) {
            Categoria categoria = resolverCategoria(agrupado, resumen);
            Optional<Producto> existente = productoRepository
                    .findFirstByCategoria_IdCategoriaAndNombreIgnoreCaseAndDeletedAtIsNullOrderByIdProductoAsc(
                            categoria.getIdCategoria(),
                            agrupado.nombreProducto());

            Producto producto = existente.orElseGet(Producto::new);
            if (existente.isEmpty()) {
                producto.setFechaCreacion(LocalDateTime.now());
                resumen.productosCreados++;
            } else {
                resumen.productosActualizados++;
            }

            producto.setSucursal(agrupado.sucursal());
            producto.setCategoria(categoria);
            producto.setNombre(agrupado.nombreProducto());
            if (existente.isEmpty() || producto.getSlug() == null || producto.getSlug().isBlank()) {
                producto.setSlug(productoService.resolverSlugParaGuardar(
                        null,
                        producto.getNombre(),
                        producto.getIdProducto(),
                        true));
            }
            producto.setEstado("ACTIVO");
            producto.setActivo("ACTIVO");
            producto.setDeletedAt(null);
            if (agrupado.descripcion() != null || existente.isEmpty()) {
                producto.setDescripcion(agrupado.descripcion());
            }

            Producto guardado;
            try {
                guardado = productoRepository.save(producto);
            } catch (DataIntegrityViolationException e) {
                throw new RuntimeException("Fila " + agrupado.filaBase() + ": no se pudo guardar el producto por datos duplicados");
            }

            resumen.variantesGuardadas += sincronizarVariantesImportadas(guardado, agrupado, resumen, usuario);
        }

        return resumen;
    }

    private int sincronizarVariantesImportadas(
            Producto producto,
            ProductoAgrupado agrupado,
            ResumenImportacion resumen,
            Usuario usuario) {
        List<ProductoVariante> existentes = productoVarianteRepository.findByProductoIdProducto(producto.getIdProducto());

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
            String skuExistente = normalizarComparable(existente.getSku());
            if (skuExistente != null) {
                existentesPorSku.putIfAbsent(skuExistente, existente);
            }
            String codigoBarrasExistente = normalizarComparable(existente.getCodigoBarras());
            if (codigoBarrasExistente != null) {
                existentesPorCodigoBarras.putIfAbsent(codigoBarrasExistente, existente);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Set<String> combinaciones = new HashSet<>();
        Set<String> skusNormalizados = new HashSet<>();
        Set<String> codigosNormalizados = new HashSet<>();
        Set<Integer> idsActivos = new HashSet<>();
        Set<Integer> coloresTocados = new HashSet<>();
        List<ProductoVariante> variantesParaPersistir = new ArrayList<>();
        Map<String, Integer> stockObjetivoPorCombinacion = new HashMap<>();

        for (VarianteFila varianteFila : agrupado.variantes().values()) {
            Color color = resolverColor(varianteFila, resumen);
            Talla talla = resolverTalla(varianteFila, resumen);

            if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
                throw new RuntimeException("Fila " + varianteFila.fila() + ": el color '" + color.getNombre() + "' esta INACTIVO");
            }
            if (!"ACTIVO".equalsIgnoreCase(talla.getEstado())) {
                throw new RuntimeException("Fila " + varianteFila.fila() + ": la talla '" + talla.getNombre() + "' esta INACTIVA");
            }

            String combinacionKey = color.getIdColor() + "-" + talla.getIdTalla();
            if (!combinaciones.add(combinacionKey)) {
                throw new RuntimeException("Fila " + varianteFila.fila()
                        + ": no puede repetir la misma combinacion de color y talla en el mismo producto");
            }

            if (productoVarianteRepository.existsSkuEnSucursalParaOtroProducto(
                    agrupado.sucursal().getIdSucursal(),
                    varianteFila.sku(),
                    producto.getIdProducto())) {
                throw new RuntimeException("Fila " + varianteFila.fila()
                        + ": el SKU '" + varianteFila.sku() + "' ya existe en la sucursal");
            }

            String skuKey = normalizarComparable(varianteFila.sku());
            if (skuKey == null || !skusNormalizados.add(skuKey)) {
                throw new RuntimeException("Fila " + varianteFila.fila()
                        + ": no puede repetir SKU dentro del mismo producto");
            }

            ProductoVariante varianteExistente = existentesPorCombinacion.get(combinacionKey);
            ProductoVariante varianteSkuExistente = existentesPorSku.get(skuKey);
            if (varianteSkuExistente != null
                    && (varianteExistente == null
                    || !varianteSkuExistente.getIdProductoVariante().equals(varianteExistente.getIdProductoVariante()))) {
                throw new RuntimeException("Fila " + varianteFila.fila()
                        + ": el SKU '" + varianteFila.sku()
                        + "' ya existe en otra variante de este producto y no se puede reasignar");
            }

            String codigoBarrasKey = normalizarComparable(varianteFila.codigoBarras());
            ProductoVariante varianteCodigoExistente = codigoBarrasKey == null
                    ? null
                    : existentesPorCodigoBarras.get(codigoBarrasKey);
            if (codigoBarrasKey != null) {
                if (!codigosNormalizados.add(codigoBarrasKey)) {
                    throw new RuntimeException("Fila " + varianteFila.fila()
                            + ": no puede repetir codigoBarras dentro del mismo producto");
                }
                if (productoVarianteRepository.existsCodigoBarrasParaOtroProducto(
                        agrupado.sucursal().getIdSucursal(),
                        varianteFila.codigoBarras(),
                        producto.getIdProducto())) {
                    throw new RuntimeException("Fila " + varianteFila.fila()
                            + ": el codigoBarras '" + varianteFila.codigoBarras() + "' ya existe en la sucursal");
                }
                if (varianteCodigoExistente != null
                        && (varianteExistente == null
                        || !varianteCodigoExistente.getIdProductoVariante().equals(varianteExistente.getIdProductoVariante()))) {
                    throw new RuntimeException("Fila " + varianteFila.fila()
                            + ": el codigoBarras '" + varianteFila.codigoBarras()
                            + "' ya existe en otra variante de este producto y no se puede reasignar");
                }
            }

            ProductoVariante variante = varianteExistente != null ? varianteExistente : new ProductoVariante();
            variante.setProducto(producto);
            variante.setSucursal(agrupado.sucursal());
            variante.setColor(color);
            variante.setTalla(talla);
            variante.setSku(varianteFila.sku());
            variante.setCodigoBarras(varianteFila.codigoBarras());
            variante.setPrecio(varianteFila.precio().doubleValue());
            variante.setPrecioMayor(varianteFila.precioMayor() == null ? null : varianteFila.precioMayor().doubleValue());
            variante.setPrecioOferta(null);
            variante.setOfertaInicio(null);
            variante.setOfertaFin(null);
            variante.setUsuarioCreacion(null);
            variante.setStock(varianteFila.stock());
            variante.setEstado(resolverEstadoVarianteSegunStock(varianteFila.stock()));
            variante.setActivo("ACTIVO");
            variante.setDeletedAt(null);
            coloresTocados.add(color.getIdColor());

            if (variante.getIdProductoVariante() != null) {
                idsActivos.add(variante.getIdProductoVariante());
            }
            stockObjetivoPorCombinacion.put(combinacionKey, varianteFila.stock());
            variantesParaPersistir.add(variante);
        }

        for (ProductoVariante existente : existentes) {
            if (existente == null || existente.getIdProductoVariante() == null) {
                continue;
            }
            if (idsActivos.contains(existente.getIdProductoVariante())) {
                continue;
            }
            existente.setEstado("INACTIVO");
            existente.setActivo("INACTIVO");
            existente.setDeletedAt(now);
            if (existente.getColor() != null && existente.getColor().getIdColor() != null) {
                coloresTocados.add(existente.getColor().getIdColor());
            }
            variantesParaPersistir.add(existente);
        }

        try {
            productoVarianteRepository.saveAllAndFlush(variantesParaPersistir);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Fila " + agrupado.filaBase() + ": variantes repetidas para el mismo producto");
        }

        for (ProductoVariante variante : variantesParaPersistir) {
            if (variante == null || variante.getIdProductoVariante() == null || variante.getSucursal() == null) {
                continue;
            }
            Integer stockObjetivo = variante.getDeletedAt() == null
                    ? stockObjetivoPorCombinacion.get(claveVariante(variante))
                    : 0;
            if (stockObjetivo == null) {
                stockObjetivo = 0;
            }
            stockMovimientoService.ajustar(
                    variante.getSucursal().getIdSucursal(),
                    variante.getIdProductoVariante(),
                    stockObjetivo,
                    "IMPORTACION JSON - " + variante.getSku(),
                    usuario);
            variante.setStock(stockObjetivo);
            variante.setEstado(resolverEstadoVarianteSegunStock(stockObjetivo));
        }

        if (!variantesParaPersistir.isEmpty()) {
            productoVarianteRepository.saveAll(variantesParaPersistir);
        }

        for (Integer colorId : coloresTocados) {
            productoService.limpiarImagenesColorSiNoHayVariantesActivas(producto.getIdProducto(), colorId);
        }

        return agrupado.variantes().size();
    }

    private String claveVariante(ProductoVariante variante) {
        Integer colorId = variante.getColor() != null ? variante.getColor().getIdColor() : null;
        Integer tallaId = variante.getTalla() != null ? variante.getTalla().getIdTalla() : null;
        return colorId + "-" + tallaId;
    }

    private Categoria resolverCategoria(ProductoAgrupado agrupado, ResumenImportacion resumen) {
        String categoriaNombre = agrupado.categoriaNombre();
        Categoria categoriaExistente = buscarCategoriaExistenteFlexible(categoriaNombre);
        if (categoriaExistente != null) {
            if (!estaDisponible(categoriaExistente)) {
                categoriaExistente.setNombreCategoria(categoriaNombre);
                categoriaExistente.setEstado("ACTIVO");
                categoriaExistente.setDeletedAt(null);
                try {
                    Categoria reactivada = categoriaRepository.saveAndFlush(categoriaExistente);
                    resumen.categoriasCreadas++;
                    return reactivada;
                } catch (DataIntegrityViolationException e) {
                    String detalle = obtenerDetalleError(e);
                    throw new RuntimeException("Fila " + agrupado.filaBase()
                            + ": no se pudo reactivar la categoria '" + categoriaNombre
                            + "'. Detalle: " + detalle);
                }
            }
            return categoriaExistente;
        }

        Categoria categoria = new Categoria();
        categoria.setNombreCategoria(categoriaNombre);
        categoria.setEstado("ACTIVO");

        try {
            Categoria creada = categoriaRepository.saveAndFlush(categoria);
            resumen.categoriasCreadas++;
            return creada;
        } catch (DataIntegrityViolationException e) {
            String detalle = obtenerDetalleError(e);
            throw new RuntimeException("Fila " + agrupado.filaBase()
                    + ": no se pudo crear la categoria '" + categoriaNombre
                    + "'. Verifique nombre y longitud maxima. Detalle: " + detalle);
        }
    }

    private Categoria buscarCategoriaExistenteFlexible(String nombreCategoria) {
        List<Categoria> categorias = categoriaRepository.findAll();
        String objetivo = normalizarTextoComparacion(nombreCategoria);
        for (Categoria categoria : categorias) {
            if (categoria == null) {
                continue;
            }
            String actual = normalizarTextoComparacion(categoria.getNombreCategoria());
            if (objetivo.equals(actual)) {
                return categoria;
            }
        }
        return null;
    }

    private Color resolverColor(VarianteFila varianteFila, ResumenImportacion resumen) {
        String colorNombre = varianteFila.colorNombre();
        Optional<Color> existente = colorRepository.findByNombreIgnoreCase(colorNombre);
        if (existente.isPresent()) {
            Color color = existente.get();
            if (!estaDisponible(color)) {
                color.setNombre(colorNombre);
                color.setCodigo(varianteFila.colorHex());
                color.setEstado("ACTIVO");
                color.setDeletedAt(null);
                try {
                    Color reactivado = colorRepository.saveAndFlush(color);
                    resumen.coloresCreados++;
                    return reactivado;
                } catch (DataIntegrityViolationException e) {
                    throw new RuntimeException("Fila " + varianteFila.fila()
                            + ": no se pudo reactivar el color '" + colorNombre + "'");
                }
            }
            if ((color.getCodigo() == null || color.getCodigo().isBlank()) && varianteFila.colorHex() != null) {
                color.setCodigo(varianteFila.colorHex());
                return colorRepository.save(color);
            }
            return color;
        }

        Color color = new Color();
        color.setNombre(colorNombre);
        color.setCodigo(varianteFila.colorHex());
        color.setEstado("ACTIVO");

        try {
            Color creado = colorRepository.saveAndFlush(color);
            resumen.coloresCreados++;
            return creado;
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Fila " + varianteFila.fila()
                    + ": no se pudo crear el color '" + colorNombre + "'");
        }
    }

    private Talla resolverTalla(VarianteFila varianteFila, ResumenImportacion resumen) {
        String tallaNombre = varianteFila.tallaNombre();
        Optional<Talla> existente = tallaRepository.findByNombreIgnoreCase(tallaNombre);
        if (existente.isPresent()) {
            Talla talla = existente.get();
            if (!estaDisponible(talla)) {
                talla.setNombre(tallaNombre);
                talla.setEstado("ACTIVO");
                talla.setDeletedAt(null);
                try {
                    Talla reactivada = tallaRepository.saveAndFlush(talla);
                    resumen.tallasCreadas++;
                    return reactivada;
                } catch (DataIntegrityViolationException e) {
                    throw new RuntimeException("Fila " + varianteFila.fila()
                            + ": no se pudo reactivar la talla '" + tallaNombre + "'");
                }
            }
            return talla;
        }

        Talla talla = new Talla();
        talla.setNombre(tallaNombre);
        talla.setEstado("ACTIVO");

        try {
            Talla creada = tallaRepository.saveAndFlush(talla);
            resumen.tallasCreadas++;
            return creada;
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Fila " + varianteFila.fila()
                    + ": no se pudo crear la talla '" + tallaNombre + "'");
        }
    }

    private String normalizarHex(String hex, int fila) {
        String value = opcional(hex);
        if (value == null) {
            return null;
        }
        String normalized = value.startsWith("#") ? value.toUpperCase(Locale.ROOT) : ("#" + value.toUpperCase(Locale.ROOT));
        if (!normalized.matches("^#[0-9A-F]{6}$")) {
            throw new RuntimeException("Fila " + fila + ": colorHex invalido. Use formato #RRGGBB");
        }
        return normalized;
    }

    private String requerido(String value, String field, int fila) {
        String normalized = opcional(value);
        if (normalized == null) {
            throw new RuntimeException("Fila " + fila + ": la columna '" + field + "' es obligatoria");
        }
        return normalized;
    }

    private void validarLongitudMaxima(String value, int max, String field, int fila) {
        if (value == null) {
            return;
        }
        if (value.length() > max) {
            throw new RuntimeException("Fila " + fila + ": la columna '" + field
                    + "' supera el maximo de " + max + " caracteres");
        }
    }

    private String opcional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String limpiarTextoCatalogo(String value) {
        String normalized = opcional(value);
        if (normalized == null) {
            return null;
        }
        String sinNoBreakSpace = normalized.replace('\u00A0', ' ');
        return sinNoBreakSpace.replaceAll("\\s+", " ").trim();
    }

    private BigDecimal decimalRequerido(String value, String field, int fila) {
        String normalized = requerido(value, field, fila).replace(",", ".");
        try {
            BigDecimal parsed = new BigDecimal(normalized);
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Fila " + fila + ": '" + field + "' no puede ser negativo");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new RuntimeException("Fila " + fila + ": '" + field + "' debe ser numerico");
        }
    }

    private BigDecimal decimalOpcionalNoNegativo(String value, String field, int fila) {
        String raw = opcional(value);
        if (raw == null) {
            return null;
        }
        String normalized = raw.replace(",", ".");
        try {
            BigDecimal parsed = new BigDecimal(normalized);
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Fila " + fila + ": '" + field + "' no puede ser negativo");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new RuntimeException("Fila " + fila + ": '" + field + "' debe ser numerico");
        }
    }

    private int enteroNoNegativo(String value, String field, int fila) {
        BigDecimal parsed = decimalRequerido(value, field, fila);
        try {
            return parsed.intValueExact();
        } catch (ArithmeticException ex) {
            throw new RuntimeException("Fila " + fila + ": '" + field + "' debe ser entero");
        }
    }

    private void validarPrecioMayor(BigDecimal precio, BigDecimal precioMayor, int fila) {
        if (precioMayor == null) {
            return;
        }
        if (precioMayor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Fila " + fila + ": 'precioMayor' debe ser mayor a 0");
        }
        if (precioMayor.compareTo(precio) >= 0) {
            throw new RuntimeException("Fila " + fila + ": 'precioMayor' debe ser menor a 'precio'");
        }
    }

    private String normalizarComparable(String value) {
        return opcional(value) == null ? null : opcional(value).toLowerCase(Locale.ROOT);
    }

    private String normalizarTextoComparacion(String value) {
        String limpio = limpiarTextoCatalogo(value);
        if (limpio == null) {
            return "";
        }
        String sinDiacriticos = Normalizer.normalize(limpio, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinDiacriticos.toLowerCase(Locale.ROOT);
    }

    private String obtenerDetalleError(DataIntegrityViolationException e) {
        Throwable causa = e.getMostSpecificCause();
        if (causa == null || causa.getMessage() == null || causa.getMessage().isBlank()) {
            return "restriccion de base de datos";
        }
        String msg = causa.getMessage();
        return msg.length() > 250 ? msg.substring(0, 250) : msg;
    }

    private String resolverEstadoVarianteSegunStock(int stock) {
        return stock <= 0 ? "INACTIVO" : "ACTIVO";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean estaDisponible(Categoria categoria) {
        return categoria.getDeletedAt() == null && "ACTIVO".equalsIgnoreCase(categoria.getEstado());
    }

    private boolean estaDisponible(Color color) {
        return color.getDeletedAt() == null && "ACTIVO".equalsIgnoreCase(color.getEstado());
    }

    private boolean estaDisponible(Talla talla) {
        return talla.getDeletedAt() == null && "ACTIVO".equalsIgnoreCase(talla.getEstado());
    }

    private record FilaImportacion(
            int fila,
            Sucursal sucursal,
            String nombreProducto,
            String descripcion,
            String categoriaNombre,
            String colorNombre,
            String colorHex,
            String tallaNombre,
            String sku,
            String codigoBarras,
            BigDecimal precio,
            BigDecimal precioMayor,
            int stock
    ) {
    }

    private record ProductoClave(
            Integer idSucursal,
            String nombreNormalizado,
            String categoriaNormalizada
    ) {
    }

    private record SkuSucursalClave(Integer idSucursal, String skuNormalizado) {
    }

    private record CodigoBarrasSucursalClave(Integer idSucursal, String codigoBarrasNormalizado) {
    }

    private record VarianteClave(String colorNormalizado, String tallaNormalizada) {
    }

    private record VarianteFila(
            int fila,
            String sku,
            String codigoBarras,
            String colorNombre,
            String colorHex,
            String tallaNombre,
            BigDecimal precio,
            BigDecimal precioMayor,
            int stock
    ) {
    }

    private record ProductoAgrupado(
            int filaBase,
            Sucursal sucursal,
            String nombreProducto,
            String descripcion,
            String categoriaNombre,
            Map<VarianteClave, VarianteFila> variantes
    ) {
        private static ProductoAgrupado crear(FilaImportacion fila) {
            Map<VarianteClave, VarianteFila> variantes = new LinkedHashMap<>();
            return new ProductoAgrupado(
                    fila.fila(),
                    fila.sucursal(),
                    fila.nombreProducto(),
                    fila.descripcion(),
                    fila.categoriaNombre(),
                    variantes);
        }

        private ProductoAgrupado conDescripcion(String nuevaDescripcion) {
            return new ProductoAgrupado(
                    this.filaBase,
                    this.sucursal,
                    this.nombreProducto,
                    nuevaDescripcion,
                    this.categoriaNombre,
                    this.variantes);
        }

        private void agregarVariante(FilaImportacion fila) {
            VarianteClave clave = new VarianteClave(
                    lower(fila.colorNombre()),
                    lower(fila.tallaNombre()));
            if (variantes.containsKey(clave)) {
                throw new RuntimeException("Fila " + fila.fila()
                        + ": combinacion color+talla repetida para el mismo producto");
            }
            variantes.put(clave, new VarianteFila(
                    fila.fila(),
                    fila.sku(),
                    fila.codigoBarras(),
                    fila.colorNombre(),
                    fila.colorHex(),
                    fila.tallaNombre(),
                    fila.precio(),
                    fila.precioMayor(),
                    fila.stock()));
        }

        private static String lower(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }
    }

    private static class ResumenImportacion {
        private int productosCreados;
        private int productosActualizados;
        private int variantesGuardadas;
        private int categoriasCreadas;
        private int coloresCreados;
        private int tallasCreadas;
    }
}
