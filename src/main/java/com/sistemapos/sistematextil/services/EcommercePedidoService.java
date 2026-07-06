package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.EcommercePedido;
import com.sistemapos.sistematextil.model.EcommercePedidoDetalle;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.EcommercePedidoRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceCarritoValidarRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceCarritoValidarResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoCreateRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoAceptarRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoAdminPageResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoAdminResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoEstadisticasResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceDniValidationResponse;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;
import com.sistemapos.sistematextil.util.documento.ConsultaDniResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcommercePedidoService {

    public static final String ESPERANDO_COMPROBANTE = "ESPERANDO_COMPROBANTE";
    public static final String PAGO_EN_REVISION = "PAGO_EN_REVISION";
    public static final String CANCELADO_POR_TIEMPO = "CANCELADO_POR_TIEMPO";
    public static final String CANCELADO = "CANCELADO";
    public static final String ACEPTADO = "ACEPTADO";
    private static final int MAX_CANTIDAD_POR_VARIANTE = 5;
    private static final BigDecimal MONTO_MINIMO_DNI_BOLETA = BigDecimal.valueOf(700);
    private static final long MAX_COMPROBANTE_SIZE = 5 * 1024 * 1024;
    private static final DateTimeFormatter CODIGO_FECHA = DateTimeFormatter.ofPattern("ddMM");
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final EcommercePedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoColorImagenRepository productoColorImagenRepository;
    private final MetodoPagoConfigRepository metodoPagoConfigRepository;
    private final PrecioOfertaService precioOfertaService;
    private final StockMovimientoService stockMovimientoService;
    private final S3StorageService storageService;
    private final VentaService ventaService;
    private final EcommercePedidoEmailService ecommercePedidoEmailService;
    private final TurnstileService turnstileService;
    private final DocumentoConsultaService documentoConsultaService;

    @Transactional(readOnly = true)
    public EcommerceCarritoValidarResponse validarCarrito(EcommerceCarritoValidarRequest request) {
        validarItemsCarrito(request);
        Sucursal sucursal = obtenerSucursalEcommerce();
        List<EcommerceCarritoValidarResponse.Item> items = request.items().stream()
                .map(item -> validarItemCarrito(item, sucursal.getIdSucursal()))
                .toList();
        boolean valido = items.stream().allMatch(item ->
                item.disponible()
                        && item.cantidadValida()
                        && !item.precioCambiado());
        return new EcommerceCarritoValidarResponse(valido, items);
    }

    @Transactional
    public EcommercePedidoResponse crear(EcommercePedidoCreateRequest request, String ipCliente) {
        validarRequest(request);
        turnstileService.validar(request.turnstileToken(), ipCliente);
        Sucursal sucursal = obtenerSucursalEcommerce();
        Usuario usuarioSistema = obtenerUsuarioSistema();
        MetodoPagoConfig metodoPago = obtenerMetodoPago(request.metodoPago());
        LocalDateTime ahora = LocalDateTime.now();

        EcommercePedido pedido = new EcommercePedido();
        pedido.setCodigo(generarCodigo());
        pedido.setSucursal(sucursal);
        pedido.setMetodoPago(metodoPago);
        pedido.setEstado(ESPERANDO_COMPROBANTE);
        LocalDateTime expiraAt = ahora.plusMinutes(10);
        String comprobanteToken = generarToken();
        pedido.setReservaExpiraAt(expiraAt);
        pedido.setComprobanteTokenExpiraAt(expiraAt);
        pedido.setComprobanteTokenHash(hashToken(comprobanteToken));
        llenarCliente(pedido, request.cliente());
        llenarEnvio(pedido, request.envio());

        List<ReservaItemContexto> itemsReserva = new ArrayList<>();
        for (EcommercePedidoCreateRequest.Item item : request.items().stream()
                .sorted(Comparator.comparing(EcommercePedidoCreateRequest.Item::idProductoVariante))
                .toList()) {
            ProductoVariante variante = productoVarianteRepository
                    .findByIdProductoVarianteAndDeletedAtIsNullAndSucursal_IdSucursal(
                            item.idProductoVariante(),
                            sucursal.getIdSucursal())
                    .filter(v -> v.getProducto() != null && Boolean.TRUE.equals(v.getProducto().getPublicarEcommerce()))
                    .orElseThrow(() -> new RuntimeException("Producto no disponible para ecommerce"));
            int stockActual = stockMovimientoService
                    .obtenerContextoConBloqueo(sucursal.getIdSucursal(), variante.getIdProductoVariante())
                    .stockActual();
            itemsReserva.add(new ReservaItemContexto(item, variante, stockActual));
        }

        List<String> faltantes = itemsReserva.stream()
                .filter(contexto -> contexto.stockActual() < contexto.item().cantidad())
                .map(contexto -> "Stock insuficiente para '" + descripcion(contexto.variante()) + "' (variante "
                        + contexto.variante().getIdProductoVariante() + "). Disponible: " + contexto.stockActual()
                        + ", solicitado: " + contexto.item().cantidad())
                .toList();
        if (!faltantes.isEmpty()) {
            throw new RuntimeException(String.join("\n", faltantes));
        }

        Map<Integer, BigDecimal> preciosPorVariante = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ReservaItemContexto contexto : itemsReserva) {
            EcommercePedidoCreateRequest.Item item = contexto.item();
            ProductoVariante variante = contexto.variante();
            BigDecimal precio = BigDecimal.valueOf(precioOfertaService.resolverPrecioVigente(variante, sucursal.getIdSucursal()));
            preciosPorVariante.put(variante.getIdProductoVariante(), precio);
            total = total.add(precio.multiply(BigDecimal.valueOf(item.cantidad())));
        }

        for (ReservaItemContexto contexto : itemsReserva) {
            EcommercePedidoCreateRequest.Item item = contexto.item();
            ProductoVariante variante = contexto.variante();
            stockMovimientoService.descontar(
                    sucursal.getIdSucursal(),
                    variante.getIdProductoVariante(),
                    item.cantidad(),
                    HistorialStock.TipoMovimiento.RESERVA,
                    "Reserva ecommerce " + pedido.getCodigo(),
                    usuarioSistema);
            BigDecimal precio = preciosPorVariante.get(variante.getIdProductoVariante());
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(item.cantidad()));
            EcommercePedidoDetalle detalle = new EcommercePedidoDetalle();
            detalle.setProductoVariante(variante);
            detalle.setCantidad(item.cantidad());
            detalle.setPrecioUnitario(precio);
            detalle.setSubtotal(subtotal);
            detalle.setDescripcion(descripcion(variante));
            pedido.addDetalle(detalle);
        }

        pedido.setSubtotal(total);
        pedido.setTotal(total);
        EcommercePedido guardado = pedidoRepository.save(pedido);
        ecommercePedidoEmailService.enviarPedidoCreado(guardado, comprobanteToken);
        return toResponse(guardado, comprobanteToken);
    }

    private EcommerceCarritoValidarResponse.Item validarItemCarrito(
            EcommerceCarritoValidarRequest.Item item,
            Integer idSucursal) {
        ProductoVariante variante = productoVarianteRepository
                .findByIdProductoVarianteAndDeletedAtIsNullAndSucursal_IdSucursal(
                        item.idProductoVariante(),
                        idSucursal)
                .filter(v -> v.getProducto() != null && Boolean.TRUE.equals(v.getProducto().getPublicarEcommerce()))
                .orElse(null);
        if (variante == null) {
            return new EcommerceCarritoValidarResponse.Item(
                    item.idProductoVariante(),
                    "Producto no disponible",
                    item.cantidad(),
                    0,
                    false,
                    0,
                    false,
                    BigDecimal.ZERO,
                    false,
                    "Producto no disponible");
        }

        int stockActual = stockMovimientoService
                .obtenerContexto(idSucursal, variante.getIdProductoVariante())
                .stockActual();
        BigDecimal precioVigente = BigDecimal.valueOf(precioOfertaService.resolverPrecioVigente(variante, idSucursal));
        boolean precioCambiado = item.precio() != null && precioVigente.compareTo(item.precio()) != 0;
        int cantidadPermitida = Math.min(Math.max(stockActual, 0), MAX_CANTIDAD_POR_VARIANTE);
        boolean cantidadValida = item.cantidad() != null && stockActual >= item.cantidad();
        boolean disponible = stockActual > 0;
        String mensaje = resolverMensajeCarrito(item.cantidad(), stockActual, precioCambiado);

        return new EcommerceCarritoValidarResponse.Item(
                variante.getIdProductoVariante(),
                descripcion(variante),
                item.cantidad(),
                cantidadPermitida,
                cantidadValida,
                stockActual,
                disponible,
                precioVigente,
                precioCambiado,
                mensaje);
    }

    public EcommerceDniValidationResponse validarDniEcommerce(String dni) {
        String valor = normalizar(dni);
        if (!valor.matches("\\d{8}")) {
            return new EcommerceDniValidationResponse(false, valor, null, null, "DNI no valido");
        }
        try {
            ConsultaDniResponse response = documentoConsultaService.consultarDni(valor);
            if (!tieneTexto(response.nombres())) {
                return new EcommerceDniValidationResponse(false, valor, null, null, "DNI no valido");
            }
            String apellidos = (normalizar(response.apellidoPaterno()) + " " + normalizar(response.apellidoMaterno())).trim();
            return new EcommerceDniValidationResponse(true, response.dni(), response.nombres(), apellidos, null);
        } catch (RuntimeException e) {
            if (esDniNoValido(e)) {
                return new EcommerceDniValidationResponse(false, valor, null, null, "DNI no valido");
            }
            return new EcommerceDniValidationResponse(
                    null,
                    valor,
                    null,
                    null,
                    "No se pudo validar el DNI en este momento");
        }
    }

    @Transactional
    public EcommercePedidoResponse subirComprobante(String codigo, MultipartFile file) {
        EcommercePedido pedido = pedidoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        return guardarComprobante(pedido, file);
    }

    @Transactional
    public EcommercePedidoResponse obtenerPorToken(String token) {
        EcommercePedido pedido = buscarPorToken(token);
        if (ESPERANDO_COMPROBANTE.equals(pedido.getEstado()) && tokenExpirado(pedido)) {
            cancelarPorTiempo(pedido);
        }
        return toResponse(pedido);
    }

    @Transactional
    public EcommercePedidoResponse subirComprobantePorToken(String token, MultipartFile file) {
        return guardarComprobante(buscarPorToken(token), file);
    }

    @Transactional(readOnly = true)
    public EcommercePedidoAdminPageResponse listarAdmin(
            String estado,
            String q,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            int page,
            int size,
            String correoUsuario) {
        obtenerUsuarioConAccesoPedidos(correoUsuario);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String estadoNormalizado = blankToNull(estado) == null ? null : normalizar(estado).toUpperCase(Locale.ROOT);
        String term = blankToNull(q);
        LocalDateTime fechaInicio = fechaDesde == null ? null : fechaDesde.atStartOfDay();
        LocalDateTime fechaFinExclusive = fechaHasta == null ? null : fechaHasta.plusDays(1).atStartOfDay();
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new RuntimeException("La fecha desde no puede ser mayor a la fecha hasta");
        }
        Page<EcommercePedido> pedidosPage = pedidoRepository.buscarAdmin(
                estadoNormalizado,
                term,
                fechaInicio,
                fechaFinExclusive,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "fecha")));
        List<EcommercePedido> pedidos = cargarPedidosConDetalles(pedidosPage);
        Map<ImagenDetalleKey, String> imagenes = resolverImagenesDetalle(pedidos);
        PagedResponse<EcommercePedidoAdminResponse> pagina = PagedResponse.fromPage(new PageImpl<>(
                pedidos.stream().map(pedido -> toAdminResponse(pedido, imagenes)).toList(),
                pedidosPage.getPageable(),
                pedidosPage.getTotalElements()));
        return EcommercePedidoAdminPageResponse.from(
                pagina,
                toEstadisticasResponse(pedidoRepository.obtenerEstadisticasAdmin(
                        term,
                        fechaInicio,
                        fechaFinExclusive)));
    }

    @Transactional(readOnly = true)
    public EcommercePedidoAdminResponse obtenerAdmin(Integer id, String correoUsuario) {
        obtenerUsuarioConAccesoPedidos(correoUsuario);
        return toAdminResponse(pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido ecommerce no encontrado")));
    }

    @Transactional(readOnly = true)
    public byte[] exportarPedidosExcel(LocalDate fechaDesde, LocalDate fechaHasta, String correoUsuario) {
        obtenerUsuarioConAccesoPedidos(correoUsuario);
        LocalDateTime fechaInicio = fechaDesde == null ? null : fechaDesde.atStartOfDay();
        LocalDateTime fechaFinExclusive = fechaHasta == null ? null : fechaHasta.plusDays(1).atStartOfDay();
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new RuntimeException("La fecha desde no puede ser mayor a la fecha hasta");
        }
        List<EcommercePedido> pedidos = pedidoRepository.listarReporteExcel(fechaInicio, fechaFinExclusive);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = crearEstiloHeaderExcel(workbook);
            CellStyle moneyStyle = crearEstiloMonedaExcel(workbook);
            List<EcommercePedido> pedidosConVenta = pedidos.stream()
                    .filter(pedido -> ACEPTADO.equals(pedido.getEstado()) && pedido.getVenta() != null)
                    .toList();
            agregarHojaDetalleVentasWebExcel(workbook, pedidosConVenta, headerStyle, moneyStyle);
            agregarHojaItemsVentaWebExcel(workbook, pedidosConVenta, headerStyle, moneyStyle);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el Excel de pedidos");
        }
    }

    @Transactional
    public EcommercePedidoAdminResponse aceptar(Integer id, EcommercePedidoAceptarRequest request, String correoUsuario) {
        Usuario usuario = obtenerUsuarioConAccesoPedidos(correoUsuario);
        EcommercePedido pedido = pedidoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Pedido ecommerce no encontrado"));
        if (pedido.getVenta() != null) {
            return toAdminResponse(pedido);
        }
        if (!PAGO_EN_REVISION.equals(pedido.getEstado())) {
            throw new RuntimeException("Solo se aceptan pedidos en PAGO_EN_REVISION");
        }

        String tipoComprobante = normalizar(request.tipoComprobante()).toUpperCase(Locale.ROOT);
        String facturaRuc = tipoComprobante.equals("FACTURA") ? normalizar(request.facturaRuc()) : null;
        String dniCliente = tipoComprobante.equals("FACTURA") ? null : dniAceptacion(pedido, request, tipoComprobante);
        validarTipoComprobanteAceptacion(tipoComprobante, pedido, request, facturaRuc, dniCliente);
        Cliente cliente = obtenerOCrearCliente(pedido, tipoComprobante, facturaRuc, dniCliente, request.razonSocial(), usuario);
        VentaCreateRequest ventaRequest = new VentaCreateRequest(
                pedido.getSucursal().getIdSucursal(),
                cliente.getIdCliente(),
                tipoComprobante,
                request.serie(),
                null,
                "PEN",
                "CONTADO",
                null,
                0.0,
                "MONTO",
                pedido.getDetalles().stream()
                        .map(detalle -> new VentaDetalleCreateItem(
                                detalle.getProductoVariante().getIdProductoVariante(),
                                detalle.getDescripcion(),
                                detalle.getCantidad(),
                                null,
                                null,
                                detalle.getPrecioUnitario().doubleValue(),
                                0.0))
                        .toList(),
                List.of(new VentaPagoCreateItem(
                        pedido.getMetodoPago().getIdMetodoPago(),
                        pedido.getTotal().doubleValue(),
                        pedido.getCodigo(),
                        LocalDateTime.now())));

        VentaResponse ventaResponse = ventaService.registrarVentaDesdeEcommerce(
                ventaRequest,
                correoUsuario,
                pedido.getSucursal());
        Venta venta = ventaRepository.findById(ventaResponse.idVenta())
                .orElseThrow(() -> new RuntimeException("Venta ecommerce no encontrada"));
        pedido.setVenta(venta);
        pedido.setUsuarioAceptacion(usuario);
        pedido.setAceptadoAt(LocalDateTime.now());
        pedido.setEstado(ACEPTADO);
        if (tipoComprobante.equals("FACTURA")) {
            pedido.setFacturaRuc(facturaRuc);
        }
        EcommercePedido guardado = pedidoRepository.save(pedido);
        ecommercePedidoEmailService.enviarPedidoAceptado(guardado, venta, correoUsuario);
        return toAdminResponse(guardado);
    }

    @Transactional
    public EcommercePedidoAdminResponse cancelar(Integer id, String correoUsuario) {
        Usuario usuario = obtenerUsuarioConAccesoPedidos(correoUsuario);
        EcommercePedido pedido = pedidoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Pedido ecommerce no encontrado"));
        if (!PAGO_EN_REVISION.equals(pedido.getEstado())) {
            throw new RuntimeException("Solo se cancelan pedidos en PAGO_EN_REVISION");
        }
        if (pedido.getVenta() != null) {
            throw new RuntimeException("El pedido ya tiene una venta generada");
        }

        liberarReserva(pedido, usuario, "Cancelacion pedido ecommerce " + pedido.getCodigo());
        pedido.setEstado(CANCELADO);
        EcommercePedido guardado = pedidoRepository.save(pedido);
        ecommercePedidoEmailService.enviarPedidoRechazado(guardado);
        return toAdminResponse(guardado);
    }

    private EcommercePedido buscarPorToken(String token) {
        String normalizado = normalizar(token);
        if (normalizado.isBlank()) {
            throw new RuntimeException("Token obligatorio");
        }
        return pedidoRepository.findByComprobanteTokenHash(hashToken(normalizado))
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
    }

    private EcommercePedidoResponse guardarComprobante(EcommercePedido pedido, MultipartFile file) {
        if (!ESPERANDO_COMPROBANTE.equals(pedido.getEstado())) {
            throw new RuntimeException("El pedido no acepta comprobante en su estado actual");
        }
        if (tokenExpirado(pedido)) {
            cancelarPorTiempo(pedido);
            return toResponse(pedido);
        }
        validarImagen(file);

        try {
            String key = "ecommerce/pedidos/%s/comprobante-%s%s".formatted(
                    pedido.getCodigo(),
                    UUID.randomUUID(),
                    extension(file.getContentType()));
            String url = storageService.upload(file.getBytes(), key, file.getContentType());
            pedido.setComprobanteUrl(url);
            pedido.setEstado(PAGO_EN_REVISION);
            EcommercePedido guardado = pedidoRepository.save(pedido);
            return toResponse(guardado);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar el comprobante");
        }
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    @Transactional
    public void cancelarReservasVencidas() {
        for (EcommercePedido pedido : pedidoRepository.findByEstadoAndReservaExpiraAtLessThanEqualOrderByReservaExpiraAtAsc(
                ESPERANDO_COMPROBANTE,
                LocalDateTime.now(),
                PageRequest.of(0, 50))) {
            cancelarPorTiempo(pedido);
        }
    }

    private void cancelarPorTiempo(EcommercePedido pedido) {
        Usuario usuarioSistema = obtenerUsuarioSistema();
        liberarReserva(pedido, usuarioSistema, "Liberacion reserva ecommerce " + pedido.getCodigo());
        pedido.setEstado(CANCELADO_POR_TIEMPO);
        pedidoRepository.save(pedido);
    }

    private void liberarReserva(EcommercePedido pedido, Usuario usuario, String motivo) {
        for (EcommercePedidoDetalle detalle : pedido.getDetalles()) {
            stockMovimientoService.incrementar(
                    pedido.getSucursal().getIdSucursal(),
                    detalle.getProductoVariante().getIdProductoVariante(),
                    detalle.getCantidad(),
                    HistorialStock.TipoMovimiento.LIBERACION,
                    motivo,
                    usuario);
        }
    }

    private void validarRequest(EcommercePedidoCreateRequest request) {
        EcommercePedidoCreateRequest.Cliente cliente = request.cliente();
        EcommercePedidoCreateRequest.Envio envio = request.envio();
        Map<Integer, Integer> cantidadesPorVariante = new HashMap<>();
        requerido(cliente.nombres(), "Nombres obligatorios");
        requerido(cliente.apellidos(), "Apellidos obligatorios");
        requerido(cliente.correo(), "Correo obligatorio");
        requerido(cliente.telefono(), "Telefono obligatorio");
        if (Boolean.TRUE.equals(cliente.deseaFactura()) && !normalizar(cliente.ruc()).matches("\\d{11}")) {
            throw new RuntimeException("RUC invalido");
        }
        String tipoEnvio = normalizar(envio.tipo()).toUpperCase(Locale.ROOT);
        if (!tipoEnvio.equals("DELIVERY") && !tipoEnvio.equals("PICKUP")) {
            throw new RuntimeException("Tipo de envio invalido");
        }
        if (tipoEnvio.equals("DELIVERY")) {
            requerido(envio.direccion(), "Direccion obligatoria");
            requerido(envio.departamento(), "Departamento obligatorio");
            requerido(envio.provincia(), "Provincia obligatoria");
            requerido(envio.distrito(), "Distrito obligatorio");
            requerido(envio.tarifa(), "Tarifa obligatoria");
        }
        for (EcommercePedidoCreateRequest.Item item : request.items()) {
            if (item.cantidad() == null || item.cantidad() <= 0) {
                throw new RuntimeException("La cantidad debe ser mayor a 0");
            }
            int cantidadAcumulada = cantidadesPorVariante.getOrDefault(item.idProductoVariante(), 0) + item.cantidad();
            if (cantidadAcumulada > MAX_CANTIDAD_POR_VARIANTE) {
                throw new RuntimeException("Solo se permiten 5 unidades maximo por variante");
            }
            cantidadesPorVariante.put(item.idProductoVariante(), cantidadAcumulada);
        }
    }

    private void validarItemsCarrito(EcommerceCarritoValidarRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new RuntimeException("Ingrese productos");
        }
        Map<Integer, Integer> cantidadesPorVariante = new HashMap<>();
        for (EcommerceCarritoValidarRequest.Item item : request.items()) {
            if (item.idProductoVariante() == null || item.cantidad() == null || item.cantidad() <= 0) {
                throw new RuntimeException("La cantidad debe ser mayor a 0");
            }
            int cantidadAcumulada = cantidadesPorVariante.getOrDefault(item.idProductoVariante(), 0) + item.cantidad();
            if (cantidadAcumulada > MAX_CANTIDAD_POR_VARIANTE) {
                throw new RuntimeException("Solo se permiten 5 unidades maximo por variante");
            }
            cantidadesPorVariante.put(item.idProductoVariante(), cantidadAcumulada);
        }
    }

    private String resolverMensajeCarrito(Integer cantidadSolicitada, int stockActual, boolean precioCambiado) {
        if (stockActual <= 0) {
            return "Producto agotado";
        }
        if (cantidadSolicitada != null && stockActual < cantidadSolicitada) {
            return "Solo queda " + stockActual + " unidad" + (stockActual == 1 ? "" : "es");
        }
        if (precioCambiado) {
            return "El precio fue actualizado";
        }
        return null;
    }

    private boolean esDniNoValido(RuntimeException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("dni invalido")
                || message.contains("ingrese dni")
                || message.contains("no se pudo obtener informacion para el dni");
    }

    private void llenarCliente(EcommercePedido pedido, EcommercePedidoCreateRequest.Cliente cliente) {
        pedido.setClienteDni(normalizar(cliente.dni()));
        pedido.setClienteNombres(normalizarNombre(cliente.nombres()));
        pedido.setClienteApellidos(normalizarNombre(cliente.apellidos()));
        pedido.setClienteCorreo(normalizar(cliente.correo()));
        pedido.setClienteTelefono(normalizar(cliente.telefono()));
        pedido.setDeseaFactura(Boolean.TRUE.equals(cliente.deseaFactura()));
        pedido.setFacturaRuc(Boolean.TRUE.equals(cliente.deseaFactura()) ? normalizar(cliente.ruc()) : null);
    }

    private void llenarEnvio(EcommercePedido pedido, EcommercePedidoCreateRequest.Envio envio) {
        pedido.setEnvioTipo(normalizar(envio.tipo()).toUpperCase(Locale.ROOT));
        pedido.setEnvioDireccion(blankToNull(envio.direccion()));
        pedido.setEnvioReferencia(blankToNull(envio.referencia()));
        pedido.setEnvioDepartamento(blankToNull(envio.departamento()));
        pedido.setEnvioProvincia(blankToNull(envio.provincia()));
        pedido.setEnvioDistrito(blankToNull(envio.distrito()));
        pedido.setEnvioTarifa(blankToNull(envio.tarifa()));
    }

    private Sucursal obtenerSucursalEcommerce() {
        return sucursalRepository
                .findFirstByPublicarEcommerceTrueAndDeletedAtIsNullAndEstadoAndTipoOrderByIdSucursalAsc(
                        "ACTIVO",
                        SucursalTipo.VENTA)
                .orElseThrow(() -> new RuntimeException("Tienda ecommerce no configurada"));
    }

    private Usuario obtenerUsuarioSistema() {
        return usuarioRepository.findByCorreoAndDeletedAtIsNull("sistema@gmail.com")
                .orElseThrow(() -> new RuntimeException("Usuario SISTEMA no configurado"));
    }

    private MetodoPagoConfig obtenerMetodoPago(String metodoPago) {
        String normalizado = normalizar(metodoPago).toUpperCase(Locale.ROOT);
        if (normalizado.equals("BCP")) {
            normalizado = "TRANSFERENCIA";
        }
        if (!normalizado.equals("YAPE") && !normalizado.equals("TRANSFERENCIA")) {
            throw new RuntimeException("Metodo de pago no permitido");
        }
        return metodoPagoConfigRepository.findAllByNombreNormalizado(normalizado).stream()
                .filter(metodo -> metodo.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Metodo de pago no configurado"));
    }

    private String generarCodigo() {
        for (int i = 0; i < 10; i++) {
            String codigo = "KMT-" + CODIGO_FECHA.format(LocalDateTime.now()) + "-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 4)
                    .toUpperCase(Locale.ROOT);
            if (!pedidoRepository.existsByCodigo(codigo)) {
                return codigo;
            }
        }
        throw new RuntimeException("No se pudo generar codigo de pedido");
    }

    private EcommercePedidoResponse toResponse(EcommercePedido pedido) {
        return toResponse(pedido, null);
    }

    private EcommercePedidoAdminResponse toAdminResponse(EcommercePedido pedido) {
        return toAdminResponse(pedido, null);
    }

    private EcommercePedidoAdminResponse toAdminResponse(EcommercePedido pedido, Map<ImagenDetalleKey, String> imagenes) {
        Venta venta = pedido.getVenta();
        Usuario aceptador = pedido.getUsuarioAceptacion();
        return new EcommercePedidoAdminResponse(
                pedido.getIdEcommercePedido(),
                pedido.getCodigo(),
                pedido.getEstado(),
                pedido.getFecha(),
                pedido.getReservaExpiraAt(),
                pedido.getTotal(),
                pedido.getMetodoPago() != null ? pedido.getMetodoPago().getNombre() : null,
                pedido.getComprobanteUrl(),
                new EcommercePedidoAdminResponse.Cliente(
                        pedido.getClienteDni(),
                        pedido.getClienteNombres(),
                        pedido.getClienteApellidos(),
                        pedido.getClienteCorreo(),
                        pedido.getClienteTelefono(),
                        pedido.getDeseaFactura(),
                        pedido.getFacturaRuc()),
                clientePosResponse(pedido),
                new EcommercePedidoAdminResponse.Envio(
                        pedido.getEnvioTipo(),
                        pedido.getEnvioDireccion(),
                        pedido.getEnvioReferencia(),
                        pedido.getEnvioDepartamento(),
                        pedido.getEnvioProvincia(),
                        pedido.getEnvioDistrito(),
                        pedido.getEnvioTarifa()),
                pedido.getSucursal() != null ? pedido.getSucursal().getIdSucursal() : null,
                pedido.getSucursal() != null ? pedido.getSucursal().getNombre() : null,
                venta != null ? venta.getIdVenta() : null,
                venta != null ? venta.getSerie() + "-" + venta.getCorrelativo() : null,
                aceptador != null ? aceptador.getIdUsuario() : null,
                nombreUsuario(aceptador),
                pedido.getAceptadoAt(),
                toDetalles(pedido, imagenes));
    }

    private EcommercePedidoAdminResponse.ClientePos clientePosResponse(EcommercePedido pedido) {
        Empresa empresa = pedido.getSucursal() != null ? pedido.getSucursal().getEmpresa() : null;
        String telefono = blankToNull(pedido.getClienteTelefono());
        if (empresa == null || empresa.getIdEmpresa() == null || telefono == null) {
            return null;
        }
        return clienteRepository
                .findFirstByTelefonoAndDeletedAtIsNullAndEmpresa_IdEmpresaOrderByIdClienteAsc(
                        telefono,
                        empresa.getIdEmpresa())
                .map(cliente -> new EcommercePedidoAdminResponse.ClientePos(
                        cliente.getIdCliente(),
                        cliente.getTipoDocumento() != null ? cliente.getTipoDocumento().name() : null,
                        cliente.getNroDocumento(),
                        cliente.getNombres(),
                        cliente.getTelefono(),
                        nombreDiferente(cliente, pedido),
                        documentoDiferente(cliente, pedido)))
                .orElse(null);
    }

    private EcommercePedidoResponse toResponse(EcommercePedido pedido, String comprobanteToken) {
        return new EcommercePedidoResponse(
                pedido.getCodigo(),
                pedido.getEstado(),
                pedido.getReservaExpiraAt(),
                pedido.getTotal(),
                pedido.getMetodoPago() != null ? pedido.getMetodoPago().getNombre() : null,
                pedido.getComprobanteUrl(),
                comprobanteToken,
                toDetalles(pedido, null));
    }

    private EcommercePedidoEstadisticasResponse toEstadisticasResponse(Object[] row) {
        Object[] data = row != null && row.length == 1 && row[0] instanceof Object[] nested ? nested : row;
        if (data == null || data.length < 4) {
            return new EcommercePedidoEstadisticasResponse(0, 0, 0, BigDecimal.ZERO);
        }
        return new EcommercePedidoEstadisticasResponse(
                toLong(data[0]),
                toLong(data[1]),
                toLong(data[2]),
                toBigDecimal(data[3]));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private CellStyle crearEstiloHeaderExcel(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle crearEstiloMonedaExcel(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("\"S/\" #,##0.00"));
        return style;
    }

    private void agregarHojaDetalleVentasWebExcel(
            Workbook workbook,
            List<EcommercePedido> pedidos,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Detalle Ventas Web");
        Row header = sheet.createRow(0);
        String[] headers = {
                "Fecha", "Codigo Pedido", "Comprobante", "Estado", "Cliente", "Celular",
                "Sucursal", "Items", "Metodo pago", "Envio", "Subtotal", "Total"
        };
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            header.getCell(i).setCellStyle(headerStyle);
        }

        BigDecimal totalPedidos = BigDecimal.ZERO;
        int rowIdx = 1;
        for (EcommercePedido pedido : pedidos) {
            BigDecimal total = pedido.getTotal() == null ? BigDecimal.ZERO : pedido.getTotal();
            totalPedidos = totalPedidos.add(total);
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(pedido.getFecha() == null ? "" : pedido.getFecha().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            row.createCell(1).setCellValue(normalizar(pedido.getCodigo()));
            row.createCell(2).setCellValue(pedido.getVenta() != null ? normalizar(pedido.getVenta().getSerie()) + "-" + pedido.getVenta().getCorrelativo() : "");
            row.createCell(3).setCellValue(normalizar(pedido.getEstado()));
            row.createCell(4).setCellValue(nombreCliente(pedido));
            row.createCell(5).setCellValue(normalizar(pedido.getClienteTelefono()));
            row.createCell(6).setCellValue(pedido.getSucursal() != null ? normalizar(pedido.getSucursal().getNombre()) : "");
            row.createCell(7).setCellValue(pedido.getDetalles().size());
            row.createCell(8).setCellValue(pedido.getMetodoPago() != null ? normalizar(pedido.getMetodoPago().getNombre()) : "");
            row.createCell(9).setCellValue(metodoEntregaPedido(pedido));
            row.createCell(10).setCellValue((pedido.getSubtotal() == null ? BigDecimal.ZERO : pedido.getSubtotal()).doubleValue());
            row.createCell(11).setCellValue(total.doubleValue());
            row.getCell(10).setCellStyle(moneyStyle);
            row.getCell(11).setCellStyle(moneyStyle);
        }

        Row totalRow = sheet.createRow(rowIdx);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.createCell(11).setCellValue(totalPedidos.doubleValue());
        totalRow.getCell(11).setCellStyle(moneyStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void agregarHojaItemsVentaWebExcel(
            Workbook workbook,
            List<EcommercePedido> pedidos,
            CellStyle headerStyle,
            CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Items Venta Web");
        Row header = sheet.createRow(0);
        String[] headers = {
                "Codigo Pedido", "FechaVenta", "Estado", "Cliente", "Producto", "Color", "Talla",
                "Cantidad", "PrecioUnitario", "Descuento", "IGV", "Total"
        };
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            header.getCell(i).setCellStyle(headerStyle);
        }

        BigDecimal totalItems = BigDecimal.ZERO;
        int rowIdx = 1;
        for (EcommercePedido pedido : pedidos) {
            for (EcommercePedidoDetalle detalle : pedido.getDetalles()) {
                ProductoVariante variante = detalle.getProductoVariante();
                BigDecimal totalLinea = detalle.getSubtotal() == null ? BigDecimal.ZERO : detalle.getSubtotal();
                totalItems = totalItems.add(totalLinea);
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(normalizar(pedido.getCodigo()));
                row.createCell(1).setCellValue(pedido.getFecha() == null ? "" : pedido.getFecha().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                row.createCell(2).setCellValue(normalizar(pedido.getEstado()));
                row.createCell(3).setCellValue(nombreCliente(pedido));
                row.createCell(4).setCellValue(variante != null && variante.getProducto() != null
                        ? normalizar(variante.getProducto().getNombre())
                        : normalizar(detalle.getDescripcion()));
                row.createCell(5).setCellValue(variante != null && variante.getColor() != null ? normalizar(variante.getColor().getNombre()) : "");
                row.createCell(6).setCellValue(variante != null && variante.getTalla() != null ? normalizar(variante.getTalla().getNombre()) : "");
                row.createCell(7).setCellValue(detalle.getCantidad() == null ? 0 : detalle.getCantidad());
                row.createCell(8).setCellValue((detalle.getPrecioUnitario() == null ? BigDecimal.ZERO : detalle.getPrecioUnitario()).doubleValue());
                row.createCell(9).setCellValue(0);
                row.createCell(10).setCellValue(0);
                row.createCell(11).setCellValue(totalLinea.doubleValue());
                row.getCell(8).setCellStyle(moneyStyle);
                row.getCell(9).setCellStyle(moneyStyle);
                row.getCell(10).setCellStyle(moneyStyle);
                row.getCell(11).setCellStyle(moneyStyle);
            }
        }

        Row totalRow = sheet.createRow(rowIdx);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.createCell(11).setCellValue(totalItems.doubleValue());
        totalRow.getCell(11).setCellStyle(moneyStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String dniAceptacion(EcommercePedido pedido, EcommercePedidoAceptarRequest request, String tipoComprobante) {
        if (tipoComprobante.equals("BOLETA")
                && Boolean.TRUE.equals(request.consumidorFinal())
                && pedido.getTotal() != null
                && pedido.getTotal().compareTo(MONTO_MINIMO_DNI_BOLETA) < 0) {
            return "";
        }
        String dniRequest = normalizar(request.dniCliente());
        return dniRequest.isBlank() ? normalizar(pedido.getClienteDni()) : dniRequest;
    }

    private void validarTipoComprobanteAceptacion(String tipoComprobante, EcommercePedido pedido, EcommercePedidoAceptarRequest request, String facturaRuc, String dniCliente) {
        if (!tipoComprobante.equals("NOTA DE VENTA") && !tipoComprobante.equals("BOLETA") && !tipoComprobante.equals("FACTURA")) {
            throw new RuntimeException("tipoComprobante permitido: NOTA DE VENTA, BOLETA o FACTURA");
        }
        if (tipoComprobante.equals("FACTURA") && !Boolean.TRUE.equals(pedido.getDeseaFactura())) {
            throw new RuntimeException("El pedido no solicito FACTURA");
        }
        if (tipoComprobante.equals("FACTURA") && !facturaRuc.matches("\\d{11}")) {
            throw new RuntimeException("El RUC verificado es obligatorio para FACTURA");
        }
        if (tipoComprobante.equals("FACTURA") && normalizar(request.razonSocial()).isBlank()) {
            throw new RuntimeException("La razon social verificada es obligatoria para FACTURA");
        }
        if (tipoComprobante.equals("BOLETA")
                && pedido.getTotal() != null
                && pedido.getTotal().compareTo(MONTO_MINIMO_DNI_BOLETA) >= 0
                && !normalizar(dniCliente).matches("\\d{8}")) {
            throw new RuntimeException("DNI obligatorio para boletas desde S/ 700");
        }
        if (tipoComprobante.equals("BOLETA") && !normalizar(dniCliente).isBlank()) {
            EcommerceDniValidationResponse response = validarDniEcommerce(dniCliente);
            if (Boolean.FALSE.equals(response.valid())) {
                throw new RuntimeException("DNI no valido");
            }
        }
    }

    private Cliente obtenerOCrearCliente(EcommercePedido pedido, String tipoComprobante, String facturaRuc, String dniCliente, String razonSocial, Usuario usuario) {
        Empresa empresa = pedido.getSucursal() != null ? pedido.getSucursal().getEmpresa() : null;
        if (empresa == null || empresa.getIdEmpresa() == null) {
            throw new RuntimeException("La sucursal ecommerce no tiene empresa");
        }
        TipoDocumento tipoDocumento = tipoComprobante.equals("FACTURA")
                ? TipoDocumento.RUC
                : normalizar(dniCliente).isBlank() ? TipoDocumento.SIN_DOC : TipoDocumento.DNI;
        String documento = tipoDocumento == TipoDocumento.RUC
                ? facturaRuc
                : tipoDocumento == TipoDocumento.SIN_DOC ? null : normalizar(dniCliente);
        String nombreCliente = nombreCliente(pedido, tipoDocumento, razonSocial);
        String telefono = blankToNull(pedido.getClienteTelefono());
        if (telefono != null) {
            return clienteRepository
                    .findFirstByTelefonoAndDeletedAtIsNullAndEmpresa_IdEmpresaOrderByIdClienteAsc(
                            telefono,
                            empresa.getIdEmpresa())
                    .map(cliente -> cliente)
                    .orElseGet(() -> buscarOCrearClientePorDocumento(pedido, usuario, empresa, tipoDocumento, documento, nombreCliente, telefono));
        }
        return buscarOCrearClientePorDocumento(pedido, usuario, empresa, tipoDocumento, documento, nombreCliente, null);
    }

    private Cliente buscarOCrearClientePorDocumento(
            EcommercePedido pedido,
            Usuario usuario,
            Empresa empresa,
            TipoDocumento tipoDocumento,
            String documento,
            String nombreCliente,
            String telefono) {
        if (tipoDocumento == TipoDocumento.SIN_DOC) {
            return crearClientePedido(pedido, usuario, empresa, tipoDocumento, null, nombreCliente, telefono);
        }
        return clienteRepository
                .findFirstByEmpresa_IdEmpresaAndTipoDocumentoAndNroDocumentoAndDeletedAtIsNullOrderByIdClienteAsc(
                        empresa.getIdEmpresa(),
                        tipoDocumento,
                        documento)
                .map(cliente -> actualizarClientePedidoSinDocumento(cliente, pedido, telefono))
                .orElseGet(() -> crearClientePedido(pedido, usuario, empresa, tipoDocumento, documento, nombreCliente, telefono));
    }

    private Cliente crearClientePedido(
            EcommercePedido pedido,
            Usuario usuario,
            Empresa empresa,
            TipoDocumento tipoDocumento,
            String documento,
            String nombreCliente,
            String telefono) {
        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresa);
        cliente.setUsuarioCreacion(usuario);
        cliente.setTipoDocumento(tipoDocumento);
        cliente.setNroDocumento(documento);
        cliente.setNombres(nombreCliente);
        cliente.setTelefono(telefono);
        cliente.setCorreo(blankToNull(pedido.getClienteCorreo()));
        cliente.setDireccion(blankToNull(pedido.getEnvioDireccion()));
        cliente.setEstado("ACTIVO");
        return clienteRepository.save(cliente);
    }

    private Cliente actualizarClientePedidoSinDocumento(
            Cliente cliente,
            EcommercePedido pedido,
            String telefono) {
        if (blankToNull(cliente.getTelefono()) == null) {
            cliente.setTelefono(telefono);
        }
        cliente.setCorreo(blankToNull(pedido.getClienteCorreo()));
        cliente.setDireccion(blankToNull(pedido.getEnvioDireccion()));
        cliente.setEstado("ACTIVO");
        return clienteRepository.save(cliente);
    }

    private boolean nombreDiferente(Cliente cliente, EcommercePedido pedido) {
        String nombrePedido = normalizar((normalizar(pedido.getClienteNombres()) + " " + normalizar(pedido.getClienteApellidos())).trim());
        String nombrePos = normalizar(cliente.getNombres());
        return !nombrePedido.isBlank() && !nombrePos.isBlank() && !nombrePedido.equalsIgnoreCase(nombrePos);
    }

    private boolean documentoDiferente(Cliente cliente, EcommercePedido pedido) {
        String documentoPedido = Boolean.TRUE.equals(pedido.getDeseaFactura())
                ? normalizar(pedido.getFacturaRuc())
                : normalizar(pedido.getClienteDni());
        String documentoPos = normalizar(cliente.getNroDocumento());
        return !documentoPedido.isBlank() && !documentoPos.isBlank() && !documentoPedido.equals(documentoPos);
    }

    private Usuario obtenerUsuarioAutenticado(String correo) {
        if (normalizar(correo).isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correo)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    public void validarAccesoPedidos(String correo) {
        obtenerUsuarioConAccesoPedidos(correo);
    }

    private Usuario obtenerUsuarioConAccesoPedidos(String correo) {
        Usuario usuario = obtenerUsuarioAutenticado(correo);
        Rol rol = usuario.getRol();
        if (rol != null && rol.esAdministrador()) {
            return usuario;
        }
        if ((rol == Rol.VENTAS || rol == Rol.VENTAS_ALMACEN)
                && Boolean.TRUE.equals(usuario.getPuedeAceptarPedidos())) {
            return usuario;
        }
        throw new AccessDeniedException("No tiene permisos para gestionar pedidos ecommerce");
    }

    private String nombreCliente(EcommercePedido pedido) {
        return (normalizar(pedido.getClienteNombres()) + " " + normalizar(pedido.getClienteApellidos())).trim();
    }

    private String nombreCliente(EcommercePedido pedido, TipoDocumento tipoDocumento, String razonSocial) {
        if (tipoDocumento == TipoDocumento.RUC && !normalizar(razonSocial).isBlank()) {
            return normalizar(razonSocial);
        }
        return nombreCliente(pedido);
    }

    private String metodoEntregaPedido(EcommercePedido pedido) {
        String tipo = normalizar(pedido.getEnvioTipo()).toUpperCase(Locale.ROOT);
        if (tipo.equals("PICKUP")) {
            return "Recojo en tienda";
        }
        String tarifa = normalizar(pedido.getEnvioTarifa()).toUpperCase(Locale.ROOT);
        if (tarifa.equals("MOTORIZADO")) {
            return "Motorizado";
        }
        if (tarifa.equals("SHALOM")) {
            return "Shalom";
        }
        if (tarifa.equals("OLVA")) {
            return "Olva";
        }
        return !tarifa.isBlank() ? pedido.getEnvioTarifa() : pedido.getEnvioTipo();
    }

    private String nombreUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return (normalizar(usuario.getNombre()) + " " + normalizar(usuario.getApellido())).trim();
    }

    private boolean tokenExpirado(EcommercePedido pedido) {
        LocalDateTime now = LocalDateTime.now();
        return !pedido.getReservaExpiraAt().isAfter(now) || !pedido.getComprobanteTokenExpiraAt().isAfter(now);
    }

    private String generarToken() {
        byte[] bytes = new byte[64];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<EcommercePedidoResponse.Detalle> toDetalles(EcommercePedido pedido, Map<ImagenDetalleKey, String> imagenes) {
        return pedido.getDetalles().stream()
                .map(detalle -> {
                    ProductoVariante variante = detalle.getProductoVariante();
                    String producto = variante != null && variante.getProducto() != null
                            ? variante.getProducto().getNombre()
                            : detalle.getDescripcion();
                    String color = variante != null && variante.getColor() != null
                            ? variante.getColor().getNombre()
                            : "-";
                    String talla = variante != null && variante.getTalla() != null
                            ? variante.getTalla().getNombre()
                            : "-";
                    String imagen = resolverImagenDetalle(variante, imagenes);
                    Integer idVariante = variante != null ? variante.getIdProductoVariante() : null;
                    return new EcommercePedidoResponse.Detalle(
                            idVariante,
                            producto,
                            color,
                            talla,
                            detalle.getCantidad(),
                            detalle.getPrecioUnitario(),
                            detalle.getSubtotal(),
                            imagen);
                })
                .toList();
    }

    private String resolverImagenDetalle(ProductoVariante variante, Map<ImagenDetalleKey, String> imagenes) {
        if (variante == null || variante.getProducto() == null) {
            return null;
        }
        if (imagenes != null) {
            String imagen = variante.getColor() == null
                    ? null
                    : imagenes.get(new ImagenDetalleKey(
                            variante.getProducto().getIdProducto(),
                            variante.getColor().getIdColor()));
            return tieneTexto(imagen) ? imagen : null;
        }
        if (variante.getColor() != null) {
            String imagenColor = productoColorImagenRepository
                    .findByProductoIdProductoAndColorIdColorAndDeletedAtIsNull(
                            variante.getProducto().getIdProducto(),
                            variante.getColor().getIdColor())
                    .stream()
                    .sorted(Comparator
                            .comparing((com.sistemapos.sistematextil.model.ProductoColorImagen imagen) -> !Boolean.TRUE.equals(imagen.getEsPrincipal()))
                            .thenComparing(com.sistemapos.sistematextil.model.ProductoColorImagen::getOrden))
                    .map(imagen -> preferirNoVacio(imagen.getUrl(), imagen.getUrlThumb()))
                    .filter(this::tieneTexto)
                    .findFirst()
                    .orElse(null);
            if (imagenColor != null) {
                return imagenColor;
            }
        }
        return null;
    }

    private List<EcommercePedido> cargarPedidosConDetalles(Page<EcommercePedido> pedidosPage) {
        List<Integer> ids = pedidosPage.getContent().stream()
                .map(EcommercePedido::getIdEcommercePedido)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Integer, EcommercePedido> pedidosBase = new HashMap<>();
        pedidosPage.getContent().forEach(pedido -> pedidosBase.put(pedido.getIdEcommercePedido(), pedido));
        Map<Integer, EcommercePedido> pedidosConDetalles = new HashMap<>();
        pedidoRepository.findAllByIdWithDetalles(ids)
                .forEach(pedido -> pedidosConDetalles.put(pedido.getIdEcommercePedido(), pedido));
        return ids.stream()
                .map(id -> pedidosConDetalles.getOrDefault(id, pedidosBase.get(id)))
                .toList();
    }

    private Map<ImagenDetalleKey, String> resolverImagenesDetalle(List<EcommercePedido> pedidos) {
        List<Integer> productoIds = pedidos.stream()
                .flatMap(pedido -> pedido.getDetalles().stream())
                .map(EcommercePedidoDetalle::getProductoVariante)
                .filter(variante -> variante != null && variante.getProducto() != null)
                .map(variante -> variante.getProducto().getIdProducto())
                .distinct()
                .toList();
        Map<ImagenDetalleKey, String> imagenes = new HashMap<>();
        if (productoIds.isEmpty()) {
            return imagenes;
        }
        for (ProductoImagenColorRow row : productoColorImagenRepository.obtenerResumenPorProductos(productoIds)) {
            String url = preferirNoVacio(row.url(), row.urlThumb());
            if (!tieneTexto(url)) {
                continue;
            }
            ImagenDetalleKey key = new ImagenDetalleKey(row.productoId(), row.colorId());
            imagenes.putIfAbsent(key, url);
        }
        return imagenes;
    }

    private String preferirNoVacio(String principal, String fallback) {
        return tieneTexto(principal) ? principal : tieneTexto(fallback) ? fallback : null;
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo procesar el token");
        }
    }

    private String descripcion(ProductoVariante variante) {
        String producto = variante.getProducto() != null ? variante.getProducto().getNombre() : "Producto";
        String color = variante.getColor() != null ? variante.getColor().getNombre() : "-";
        String talla = variante.getTalla() != null ? variante.getTalla().getNombre() : "-";
        return producto + " / " + color + " / " + talla;
    }

    private record ReservaItemContexto(
            EcommercePedidoCreateRequest.Item item,
            ProductoVariante variante,
            int stockActual) {
    }

    private record ImagenDetalleKey(Integer productoId, Integer colorId) {
    }

    private void validarImagen(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Comprobante obligatorio");
        }
        if (file.getSize() > MAX_COMPROBANTE_SIZE) {
            throw new RuntimeException("El comprobante no debe superar 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("El comprobante debe ser una imagen");
        }
    }

    private String extension(String contentType) {
        if ("image/png".equalsIgnoreCase(contentType)) return ".png";
        if ("image/webp".equalsIgnoreCase(contentType)) return ".webp";
        return ".jpg";
    }

    private void requerido(String value, String message) {
        if (normalizar(value).isBlank()) {
            throw new RuntimeException(message);
        }
    }

    private String blankToNull(String value) {
        String normalizado = normalizar(value);
        return normalizado.isBlank() ? null : normalizado;
    }

    private String normalizar(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizarNombre(String value) {
        String limpio = normalizar(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (limpio.isBlank()) {
            return "";
        }
        StringBuilder resultado = new StringBuilder(limpio.length());
        for (String parte : limpio.split(" ")) {
            if (parte.isBlank()) {
                continue;
            }
            if (!resultado.isEmpty()) {
                resultado.append(' ');
            }
            resultado.append(parte.substring(0, 1).toUpperCase(Locale.ROOT));
            if (parte.length() > 1) {
                resultado.append(parte.substring(1));
            }
        }
        return resultado.toString();
    }
}
