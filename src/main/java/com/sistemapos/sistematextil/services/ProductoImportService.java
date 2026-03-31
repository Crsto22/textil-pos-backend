package com.sistemapos.sistematextil.services;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
import com.sistemapos.sistematextil.util.producto.ProductoImportResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoImportService {

    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of(".xlsx", ".xls");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.US);

    private static final String H_SKU = "sku";
    private static final String H_NOMBRE_PRODUCTO = "nombreproducto";
    private static final String H_DESCRIPCION = "descripcion";
    private static final String H_CATEGORIA_NOMBRE = "categorianombre";
    private static final String H_COLOR_NOMBRE = "colornombre";
    private static final String H_COLOR_HEX = "colorhex";
    private static final String H_TALLA_NOMBRE = "tallanombre";
    private static final String H_PRECIO = "precio";
    private static final String H_PRECIO_OFERTA = "preciooferta";
    private static final String H_STOCK = "stock";
    private static final String H_SUCURSAL = "sucursal";

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoService productoService;
    private final ColorRepository colorRepository;
    private final TallaRepository tallaRepository;
    private final ImportacionProductoHistorialService importacionProductoHistorialService;

    @Transactional
    public ProductoImportResponse importarDesdeExcel(MultipartFile file, String correoUsuarioAutenticado) {
        long inicioMillis = System.currentTimeMillis();
        String nombreArchivo = obtenerNombreArchivoSeguro(file);
        long tamanoBytes = obtenerTamanoSeguro(file);
        Usuario usuario = null;
        int filasProcesadas = 0;
        ResumenImportacion resumen = new ResumenImportacion();

        try {
            validarArchivo(file);
            usuario = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
            validarRolPermitido(usuario);

            List<FilaExcel> filas = leerFilas(file, usuario);
            filasProcesadas = filas.size();
            if (filas.isEmpty()) {
                throw new RuntimeException("El archivo no contiene filas de productos");
            }

            Map<ProductoClave, ProductoAgrupado> productosAgrupados = agruparFilas(filas, usuario);
            resumen = persistirAgrupados(productosAgrupados);

            importacionProductoHistorialService.registrarExitosa(
                    usuario,
                    resolverIdSucursalHistorial(usuario),
                    nombreArchivo,
                    tamanoBytes,
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
            registrarFalloSinInterrumpir(
                    usuario,
                    nombreArchivo,
                    tamanoBytes,
                    filasProcesadas,
                    resumen,
                    inicioMillis,
                    e);
            throw e;
        }
    }

    private void registrarFalloSinInterrumpir(
            Usuario usuario,
            String nombreArchivo,
            long tamanoBytes,
            int filasProcesadas,
            ResumenImportacion resumen,
            long inicioMillis,
            RuntimeException causa) {
        if (usuario == null || usuario.getIdUsuario() == null) {
            return;
        }
        try {
            importacionProductoHistorialService.registrarFallida(
                    usuario,
                    resolverIdSucursalHistorial(usuario),
                    nombreArchivo,
                    tamanoBytes,
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
            // Si el historial falla, no se reemplaza el error original de la importacion.
        }
    }

    private Integer resolverIdSucursalHistorial(Usuario usuario) {
        if (usuario == null || usuario.getRol() == Rol.ADMINISTRADOR) {
            return null;
        }
        if (usuario.getSucursal() == null) {
            return null;
        }
        return usuario.getSucursal().getIdSucursal();
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

    private String obtenerNombreArchivoSeguro(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            return "archivo_sin_nombre.xlsx";
        }
        String nombre = file.getOriginalFilename().trim();
        return nombre.length() > 255 ? nombre.substring(0, 255) : nombre;
    }

    private long obtenerTamanoSeguro(MultipartFile file) {
        if (file == null) {
            return 0L;
        }
        return Math.max(file.getSize(), 0L);
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Debe enviar un archivo Excel");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("Nombre de archivo invalido");
        }
        String lower = fileName.toLowerCase();
        boolean extensionValida = EXTENSIONES_PERMITIDAS.stream().anyMatch(lower::endsWith);
        if (!extensionValida) {
            throw new RuntimeException("Formato de archivo no permitido. Use .xlsx o .xls");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para importar productos");
        }
    }

    private List<FilaExcel> leerFilas(MultipartFile file, Usuario usuario) {
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new RuntimeException("No se encontro una hoja en el archivo");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("El archivo no contiene encabezados");
            }
            Map<String, Integer> headers = leerHeaders(headerRow);
            validarHeadersRequeridos(headers, usuario);

            List<FilaExcel> filas = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (filaVacia(row, headers)) {
                    continue;
                }
                int rowNumber = i + 1;
                filas.add(leerFila(row, headers, rowNumber));
            }
            return filas;
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo Excel");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("El archivo Excel tiene un formato invalido");
        }
    }

    private Map<String, Integer> leerHeaders(Row headerRow) {
        Map<String, Integer> headers = new HashMap<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            String raw = texto(headerRow.getCell(i));
            if (raw == null) {
                continue;
            }
            headers.put(normalizarClave(raw), i);
        }
        return headers;
    }

    private void validarHeadersRequeridos(Map<String, Integer> headers, Usuario usuario) {
        List<String> requeridos = List.of(
                H_SKU,
                H_NOMBRE_PRODUCTO,
                H_CATEGORIA_NOMBRE,
                H_COLOR_NOMBRE,
                H_COLOR_HEX,
                H_TALLA_NOMBRE,
                H_PRECIO,
                H_STOCK);

        if (usuario != null && usuario.getRol() == Rol.ADMINISTRADOR) {
            requeridos = new ArrayList<>(requeridos);
            requeridos.add(H_SUCURSAL);
        }

        for (String header : requeridos) {
            if (!headers.containsKey(header)) {
                throw new RuntimeException("Falta la columna requerida '" + headerOriginal(header) + "'");
            }
        }
    }

    private FilaExcel leerFila(Row row, Map<String, Integer> headers, int rowNumber) {
        String sku = requerido(texto(row.getCell(headers.get(H_SKU))), "sku", rowNumber);
        validarLongitudMaxima(sku, 100, "sku", rowNumber);

        String nombreProducto = requerido(texto(row.getCell(headers.get(H_NOMBRE_PRODUCTO))), "nombreProducto", rowNumber);
        validarLongitudMaxima(nombreProducto, 150, "nombreProducto", rowNumber);

        Integer idxDescripcion = headers.get(H_DESCRIPCION);
        String descripcion = idxDescripcion == null ? null : opcional(texto(row.getCell(idxDescripcion)));
        validarLongitudMaxima(descripcion, 500, "descripcion", rowNumber);

        String categoriaNombre = requerido(texto(row.getCell(headers.get(H_CATEGORIA_NOMBRE))), "categoriaNombre", rowNumber);
        categoriaNombre = limpiarTextoCatalogo(categoriaNombre);
        validarLongitudMaxima(categoriaNombre, 50, "categoriaNombre", rowNumber);

        String colorNombre = requerido(texto(row.getCell(headers.get(H_COLOR_NOMBRE))), "colorNombre", rowNumber);
        colorNombre = limpiarTextoCatalogo(colorNombre);
        validarLongitudMaxima(colorNombre, 50, "colorNombre", rowNumber);

        String colorHex = opcional(texto(row.getCell(headers.get(H_COLOR_HEX))));
        validarLongitudMaxima(colorHex, 20, "colorHex", rowNumber);

        String tallaNombre = requerido(texto(row.getCell(headers.get(H_TALLA_NOMBRE))), "tallaNombre", rowNumber);
        tallaNombre = limpiarTextoCatalogo(tallaNombre);
        validarLongitudMaxima(tallaNombre, 20, "tallaNombre", rowNumber);

        BigDecimal precio = decimal(row.getCell(headers.get(H_PRECIO)), "precio", rowNumber);
        BigDecimal precioOferta = decimalOpcionalNoNegativo(row.getCell(headers.get(H_PRECIO_OFERTA)), "precioOferta", rowNumber);
        validarPrecioOferta(precio, precioOferta, rowNumber);
        int stock = enteroNoNegativo(row.getCell(headers.get(H_STOCK)), "stock", rowNumber);
        String sucursal = opcional(texto(row.getCell(headers.get(H_SUCURSAL))));
        validarLongitudMaxima(sucursal, 100, "sucursal", rowNumber);

        return new FilaExcel(
                rowNumber,
                sku,
                nombreProducto,
                descripcion,
                categoriaNombre,
                colorNombre,
                colorHex,
                tallaNombre,
                precio,
                precioOferta,
                stock,
                sucursal);
    }

    private boolean filaVacia(Row row, Map<String, Integer> headers) {
        if (row == null) {
            return true;
        }
        for (Integer index : headers.values()) {
            String value = texto(row.getCell(index));
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Map<ProductoClave, ProductoAgrupado> agruparFilas(List<FilaExcel> filas, Usuario usuario) {
        Map<ProductoClave, ProductoAgrupado> agrupados = new LinkedHashMap<>();
        Map<SkuSucursalClave, Integer> skuPorSucursal = new HashMap<>();
        for (FilaExcel fila : filas) {
            Sucursal sucursal = resolverSucursal(fila, usuario);
            String skuNormalizado = normalizarComparable(fila.sku());
            SkuSucursalClave skuClave = new SkuSucursalClave(sucursal.getIdSucursal(), skuNormalizado);
            Integer filaDuplicadaSku = skuPorSucursal.putIfAbsent(skuClave, fila.fila());
            if (filaDuplicadaSku != null) {
                throw new RuntimeException("Fila " + fila.fila()
                        + ": el SKU '" + fila.sku()
                        + "' ya fue enviado en la fila " + filaDuplicadaSku + " para la misma sucursal");
            }

            ProductoClave clave = new ProductoClave(
                    sucursal.getIdSucursal(),
                    normalizarComparable(fila.nombreProducto()),
                    normalizarComparable(fila.categoriaNombre()));

            ProductoAgrupado actual = agrupados.get(clave);
            if (actual == null) {
                actual = ProductoAgrupado.crear(fila, sucursal);
                agrupados.put(clave, actual);
            } else {
                if (actual.descripcion() == null && fila.descripcion() != null) {
                    actual = actual.conDescripcion(fila.descripcion());
                    agrupados.put(clave, actual);
                }
            }
            actual.agregarVariante(fila);
        }
        return agrupados;
    }

    private ResumenImportacion persistirAgrupados(Map<ProductoClave, ProductoAgrupado> agrupados) {
        ResumenImportacion resumen = new ResumenImportacion();

        for (ProductoAgrupado agrupado : agrupados.values()) {
            Categoria categoria = resolverCategoria(agrupado, resumen);
            Optional<Producto> existente = productoRepository
                    .findFirstBySucursal_IdSucursalAndCategoria_IdCategoriaAndNombreIgnoreCaseAndDeletedAtIsNullOrderByIdProductoAsc(
                            agrupado.sucursal().getIdSucursal(),
                            categoria.getIdCategoria(),
                            agrupado.nombreProducto());
            Producto producto = existente.orElseGet(Producto::new);

            if (existente.isEmpty()) {
                producto.setFechaCreacion(LocalDateTime.now());
                producto.setEstado("ACTIVO");
                resumen.productosCreados++;
            } else {
                resumen.productosActualizados++;
            }

            producto.setSucursal(agrupado.sucursal());
            producto.setCategoria(categoria);
            producto.setNombre(agrupado.nombreProducto());
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

            resumen.variantesGuardadas += sincronizarVariantesImportadas(guardado, agrupado, resumen);
        }

        return resumen;
    }

    private int sincronizarVariantesImportadas(
            Producto producto,
            ProductoAgrupado agrupado,
            ResumenImportacion resumen) {
        List<ProductoVariante> existentes = productoVarianteRepository.findByProductoIdProducto(producto.getIdProducto());

        Map<String, ProductoVariante> existentesPorCombinacion = new HashMap<>();
        Map<String, ProductoVariante> existentesPorSku = new HashMap<>();
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
        }

        LocalDateTime now = LocalDateTime.now();
        Set<String> combinaciones = new HashSet<>();
        Set<String> skusNormalizados = new HashSet<>();
        Set<Integer> idsActivos = new HashSet<>();
        Set<Integer> coloresTocados = new HashSet<>();
        List<ProductoVariante> variantesParaPersistir = new ArrayList<>();

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

            ProductoVariante variante = varianteExistente != null ? varianteExistente : new ProductoVariante();
            variante.setProducto(producto);
            variante.setSucursal(agrupado.sucursal());
            variante.setColor(color);
            variante.setTalla(talla);
            variante.setSku(varianteFila.sku());
            variante.setCodigoBarras(null);
            variante.setPrecio(varianteFila.precio().doubleValue());
            variante.setPrecioMayor(null);
            variante.setPrecioOferta(varianteFila.precioOferta() == null ? null : varianteFila.precioOferta().doubleValue());
            variante.setOfertaInicio(null);
            variante.setOfertaFin(null);
            variante.setStock(varianteFila.stock());
            variante.setEstado(resolverEstadoVarianteSegunStock(varianteFila.stock()));
            variante.setActivo("ACTIVO");
            variante.setDeletedAt(null);
            coloresTocados.add(color.getIdColor());

            if (variante.getIdProductoVariante() != null) {
                idsActivos.add(variante.getIdProductoVariante());
            }
            variantesParaPersistir.add(variante);
        }

        for (ProductoVariante existente : existentes) {
            if (existente == null || existente.getIdProductoVariante() == null) {
                continue;
            }
            if (idsActivos.contains(existente.getIdProductoVariante())) {
                continue;
            }
            existente.setEstado(resolverEstadoVarianteSegunStock(existente.getStock()));
            existente.setActivo("INACTIVO");
            existente.setDeletedAt(now);
            if (existente.getColor() != null && existente.getColor().getIdColor() != null) {
                coloresTocados.add(existente.getColor().getIdColor());
            }
            variantesParaPersistir.add(existente);
        }

        try {
            productoVarianteRepository.saveAll(variantesParaPersistir);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Fila " + agrupado.filaBase() + ": variantes repetidas para el mismo producto");
        }

        for (Integer colorId : coloresTocados) {
            productoService.limpiarImagenesColorSiNoHayVariantesActivas(producto.getIdProducto(), colorId);
        }

        return agrupado.variantes().size();
    }

    private Categoria resolverCategoria(ProductoAgrupado agrupado, ResumenImportacion resumen) {
        String categoriaNombre = agrupado.categoriaNombre();
        Categoria categoriaExistente = buscarCategoriaExistenteFlexible(
                agrupado.sucursal().getIdSucursal(),
                categoriaNombre);
        if (categoriaExistente != null) {
            Categoria categoria = categoriaExistente;
            if (!"ACTIVO".equalsIgnoreCase(categoria.getEstado())) {
                throw new RuntimeException("Fila " + agrupado.filaBase()
                        + ": la categoria '" + categoria.getNombreCategoria() + "' esta INACTIVA");
            }
            return categoria;
        }

        Categoria categoria = new Categoria();
        categoria.setSucursal(agrupado.sucursal());
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
                    + "'. Verifique nombre/sucursal y longitud maxima. Detalle: " + detalle);
        }
    }

    private Categoria buscarCategoriaExistenteFlexible(Integer idSucursal, String nombreCategoria) {
        List<Categoria> categorias = categoriaRepository.findBySucursal_IdSucursal(idSucursal);
        String objetivo = normalizarTextoComparacion(nombreCategoria);
        for (Categoria categoria : categorias) {
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
            return existente.get();
        }

        Color color = new Color();
        color.setNombre(colorNombre);
        color.setCodigo(normalizarHex(varianteFila.colorHex(), varianteFila.fila()));
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
            return existente.get();
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

    private Sucursal resolverSucursal(FilaExcel fila, Usuario usuario) {
        if (usuario.getRol() == Rol.ADMINISTRADOR) {
            String nombreSucursal = requerido(fila.sucursal(), "sucursal", fila.fila());
            Sucursal sucursal = sucursalRepository.findByNombreIgnoreCaseAndDeletedAtIsNull(nombreSucursal)
                    .orElseThrow(() -> new RuntimeException(
                            "Fila " + fila.fila() + ": sucursal '" + nombreSucursal + "' no existe"));
            if (!"ACTIVO".equalsIgnoreCase(sucursal.getEstado())) {
                throw new RuntimeException("Fila " + fila.fila() + ": la sucursal '" + nombreSucursal + "' esta INACTIVA");
            }
            return sucursal;
        }

        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }

        Sucursal sucursalUsuario = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(usuario.getSucursal().getIdSucursal())
                .orElseThrow(() -> new RuntimeException("Sucursal del usuario autenticado no encontrada"));

        // Para VENTAS/ALMACEN la sucursal siempre se toma del token.
        // La columna "sucursal" del Excel se ignora (si viene o no viene).
        return sucursalUsuario;
    }

    private String normalizarHex(String hex, int fila) {
        String value = opcional(hex);
        if (value == null) {
            return null;
        }

        String normalized = value.startsWith("#") ? value.toUpperCase() : ("#" + value.toUpperCase());
        if (!normalized.matches("^#[0-9A-F]{6}$")) {
            throw new RuntimeException("Fila " + fila + ": colorHex invalido. Use formato #RRGGBB");
        }
        return normalized;
    }

    private String normalizarComparable(String value) {
        return opcional(value) == null ? null : opcional(value).toLowerCase(Locale.ROOT);
    }

    private String resolverEstadoVarianteSegunStock(int stock) {
        return stock <= 0 ? "AGOTADO" : "ACTIVO";
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

    private String texto(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = DATA_FORMATTER.formatCellValue(cell);
        return value == null ? null : value.trim();
    }

    private BigDecimal decimal(Cell cell, String field, int fila) {
        String value = requerido(texto(cell), field, fila).replace(",", ".");
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Fila " + fila + ": '" + field + "' no puede ser negativo");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new RuntimeException("Fila " + fila + ": '" + field + "' debe ser numerico");
        }
    }

    private int enteroNoNegativo(Cell cell, String field, int fila) {
        BigDecimal parsed = decimal(cell, field, fila);
        try {
            return parsed.intValueExact();
        } catch (ArithmeticException ex) {
            throw new RuntimeException("Fila " + fila + ": '" + field + "' debe ser entero");
        }
    }

    private String normalizarClave(String header) {
        return header == null
                ? ""
                : header
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .replace(" ", "")
                        .replace("_", "")
                        .replace("-", "");
    }

    private String headerOriginal(String headerNormalizado) {
        return switch (headerNormalizado) {
            case H_NOMBRE_PRODUCTO -> "nombreProducto";
            case H_CATEGORIA_NOMBRE -> "categoriaNombre";
            case H_COLOR_NOMBRE -> "colorNombre";
            case H_COLOR_HEX -> "colorHex";
            case H_TALLA_NOMBRE -> "tallaNombre";
            case H_PRECIO_OFERTA -> "precioOferta";
            default -> headerNormalizado;
        };
    }

    private record FilaExcel(
            int fila,
            String sku,
            String nombreProducto,
            String descripcion,
            String categoriaNombre,
            String colorNombre,
            String colorHex,
            String tallaNombre,
            BigDecimal precio,
            BigDecimal precioOferta,
            int stock,
            String sucursal
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

    private record VarianteClave(String colorNormalizado, String tallaNormalizada) {
    }

    private record VarianteFila(
            int fila,
            String sku,
            String colorNombre,
            String colorHex,
            String tallaNombre,
            BigDecimal precio,
            BigDecimal precioOferta,
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
        private static ProductoAgrupado crear(FilaExcel fila, Sucursal sucursal) {
            Map<VarianteClave, VarianteFila> variantes = new LinkedHashMap<>();
            return new ProductoAgrupado(
                    fila.fila(),
                    sucursal,
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

        private void agregarVariante(FilaExcel fila) {
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
                    fila.colorNombre(),
                    fila.colorHex(),
                    fila.tallaNombre(),
                    fila.precio(),
                    fila.precioOferta(),
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

    private BigDecimal decimalOpcionalNoNegativo(Cell cell, String field, int fila) {
        String raw = texto(cell);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.replace(",", ".");
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Fila " + fila + ": '" + field + "' no puede ser negativo");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new RuntimeException("Fila " + fila + ": '" + field + "' debe ser numerico");
        }
    }

    private void validarPrecioOferta(BigDecimal precio, BigDecimal precioOferta, int fila) {
        if (precioOferta == null) {
            return;
        }
        if (precioOferta.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Fila " + fila + ": 'precioOferta' debe ser mayor a 0");
        }
        if (precio == null || precioOferta.compareTo(precio) >= 0) {
            throw new RuntimeException("Fila " + fila + ": 'precioOferta' debe ser menor a 'precio'");
        }
    }
}
