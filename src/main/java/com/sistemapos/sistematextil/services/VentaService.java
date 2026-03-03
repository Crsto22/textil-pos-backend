package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.Pago;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaDetalleResponse;
import com.sistemapos.sistematextil.util.venta.VentaListItemResponse;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaPagoResponse;
import com.sistemapos.sistematextil.util.venta.VentaResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VentaService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final PagoRepository pagoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final MetodoPagoConfigRepository metodoPagoConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final ClienteRepository clienteRepository;
    private final HistorialStockRepository historialStockRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<VentaListItemResponse> listarPaginado(int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idVenta").descending());
        Page<Venta> ventas = esAdministrador(usuarioAutenticado)
                ? ventaRepository.findByDeletedAtIsNullOrderByIdVentaDesc(pageable)
                : ventaRepository.findByDeletedAtIsNullAndSucursal_IdSucursalOrderByIdVentaDesc(
                        obtenerIdSucursalUsuario(usuarioAutenticado),
                        pageable);

        return PagedResponse.fromPage(ventas.map(this::toListItemResponse));
    }

    public VentaResponse obtenerDetalle(Integer idVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        List<Pago> pagos = pagoRepository.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(venta.getIdVenta());

        return toResponse(venta, detalles, pagos);
    }

    @Transactional
    public VentaResponse registrarVenta(VentaCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolVenta(usuarioAutenticado);

        Sucursal sucursalVenta = resolverSucursalParaVenta(request.idSucursal(), usuarioAutenticado);
        Cliente cliente = resolverCliente(request.idCliente(), sucursalVenta.getIdSucursal());

        List<DetalleCalculado> detallesCalculados = calcularDetalles(
                request.detalles(),
                sucursalVenta.getIdSucursal());
        TotalesVenta totales = calcularTotales(
                detallesCalculados,
                request.descuentoTotal(),
                request.tipoDescuento(),
                request.igvPorcentaje());
        List<PagoCalculado> pagosCalculados = calcularPagos(request.pagos(), totales.total());

        Venta venta = new Venta();
        venta.setSucursal(sucursalVenta);
        venta.setUsuario(usuarioAutenticado);
        venta.setCliente(cliente);
        venta.setTipoComprobante(normalizarTipoComprobante(request.tipoComprobante()));
        venta.setSerie(normalizarTexto(request.serie(), 10));
        venta.setCorrelativo(request.correlativo());
        venta.setIgvPorcentaje(totales.igvPorcentaje());
        venta.setSubtotal(totales.subtotal());
        venta.setDescuentoTotal(totales.descuentoAplicado());
        venta.setTipoDescuento(totales.tipoDescuento());
        venta.setIgv(totales.igv());
        venta.setTotal(totales.total());
        venta.setEstado("EMITIDA");
        venta.setActivo("ACTIVO");

        Venta ventaGuardada = ventaRepository.save(venta);

        List<VentaDetalle> detallesGuardar = new ArrayList<>();
        for (DetalleCalculado detalleCalculado : detallesCalculados) {
            VentaDetalle detalle = new VentaDetalle();
            detalle.setVenta(ventaGuardada);
            detalle.setProductoVariante(detalleCalculado.variante());
            detalle.setCantidad(detalleCalculado.cantidad());
            detalle.setPrecioUnitario(detalleCalculado.precioUnitario());
            detalle.setDescuento(detalleCalculado.descuento());
            detalle.setSubtotal(detalleCalculado.subtotal());
            detalle.setActivo("ACTIVO");
            detallesGuardar.add(detalle);
        }
        List<VentaDetalle> detallesGuardados = ventaDetalleRepository.saveAll(detallesGuardar);

        List<Pago> pagosGuardar = new ArrayList<>();
        for (PagoCalculado pagoCalculado : pagosCalculados) {
            Pago pago = new Pago();
            pago.setVenta(ventaGuardada);
            pago.setMetodoPago(pagoCalculado.metodoPago());
            pago.setMonto(pagoCalculado.monto());
            pago.setReferencia(pagoCalculado.referencia());
            pago.setActivo("ACTIVO");
            pagosGuardar.add(pago);
        }
        List<Pago> pagosGuardados = pagoRepository.saveAll(pagosGuardar);

        List<HistorialStock> historial = new ArrayList<>();
        for (DetalleCalculado detalleCalculado : detallesCalculados) {
            ProductoVariante variante = detalleCalculado.variante();
            int stockAnterior = valorEntero(variante.getStock());
            int stockNuevo = stockAnterior - detalleCalculado.cantidad();
            variante.setStock(stockNuevo);
            variante.setEstado(stockNuevo <= 0 ? "AGOTADO" : "ACTIVO");

            HistorialStock movimiento = new HistorialStock();
            movimiento.setTipoMovimiento(HistorialStock.TipoMovimiento.VENTA);
            movimiento.setMotivo("VENTA #" + ventaGuardada.getIdVenta());
            movimiento.setProductoVariante(variante);
            movimiento.setSucursal(sucursalVenta);
            movimiento.setUsuario(usuarioAutenticado);
            movimiento.setCantidad(detalleCalculado.cantidad());
            movimiento.setStockAnterior(stockAnterior);
            movimiento.setStockNuevo(stockNuevo);
            historial.add(movimiento);
        }
        productoVarianteRepository.saveAll(detallesCalculados.stream().map(DetalleCalculado::variante).toList());
        historialStockRepository.saveAll(historial);

        return toResponse(ventaGuardada, detallesGuardados, pagosGuardados);
    }

    private List<DetalleCalculado> calcularDetalles(List<VentaDetalleCreateItem> detalles, Integer idSucursalVenta) {
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un detalle de venta");
        }

        Set<Integer> variantesUnicas = new HashSet<>();
        List<DetalleCalculado> calculados = new ArrayList<>();

        for (VentaDetalleCreateItem item : detalles) {
            Integer idProductoVariante = item.idProductoVariante();
            if (idProductoVariante == null) {
                throw new RuntimeException("Cada detalle debe incluir idProductoVariante");
            }
            if (!variantesUnicas.add(idProductoVariante)) {
                throw new RuntimeException("No puede repetir la misma variante en el detalle de venta");
            }

            ProductoVariante variante = productoVarianteRepository.findByIdProductoVarianteForUpdate(idProductoVariante)
                    .orElseThrow(() -> new RuntimeException(
                            "La variante con ID " + idProductoVariante + " no existe"));

            if (variante.getSucursal() == null
                    || variante.getSucursal().getIdSucursal() == null
                    || !idSucursalVenta.equals(variante.getSucursal().getIdSucursal())) {
                throw new RuntimeException("La variante con ID " + idProductoVariante + " no pertenece a la sucursal de la venta");
            }

            if (!"ACTIVO".equalsIgnoreCase(variante.getEstado())) {
                throw new RuntimeException("La variante con SKU '" + variante.getSku() + "' no esta ACTIVA");
            }

            int cantidad = item.cantidad();
            int stockActual = valorEntero(variante.getStock());
            if (stockActual < cantidad) {
                throw new RuntimeException("Stock insuficiente para SKU '" + variante.getSku()
                        + "'. Disponible: " + stockActual + ", solicitado: " + cantidad);
            }

            BigDecimal precioUnitario = item.precioUnitario() == null
                    ? decimalDesdeDouble(variante.getPrecio())
                    : decimalPositivo(item.precioUnitario(), "precioUnitario");
            BigDecimal descuento = item.descuento() == null
                    ? BigDecimal.ZERO
                    : decimalNoNegativo(item.descuento(), "descuento");

            BigDecimal totalLinea = precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
            if (descuento.compareTo(totalLinea) > 0) {
                throw new RuntimeException("El descuento de la linea no puede superar el total de la linea para SKU '"
                        + variante.getSku() + "'");
            }
            BigDecimal subtotal = totalLinea.subtract(descuento).setScale(2, RoundingMode.HALF_UP);

            calculados.add(new DetalleCalculado(variante, cantidad, precioUnitario, descuento, subtotal));
        }

        return calculados;
    }

    private TotalesVenta calcularTotales(
            List<DetalleCalculado> detalles,
            Double descuentoTotalInput,
            String tipoDescuentoInput,
            Double igvPorcentajeInput) {
        BigDecimal subtotal = detalles.stream()
                .map(DetalleCalculado::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal descuentoInput = descuentoTotalInput == null
                ? BigDecimal.ZERO
                : decimalNoNegativo(descuentoTotalInput, "descuentoTotal");
        String tipoDescuento = normalizarTipoDescuento(tipoDescuentoInput, descuentoInput);

        BigDecimal descuentoAplicado = BigDecimal.ZERO;
        if (descuentoInput.compareTo(BigDecimal.ZERO) > 0) {
            if ("MONTO".equals(tipoDescuento)) {
                descuentoAplicado = descuentoInput;
            } else {
                descuentoAplicado = subtotal
                        .multiply(descuentoInput)
                        .divide(CIEN, 2, RoundingMode.HALF_UP);
            }
        }

        if (descuentoAplicado.compareTo(subtotal) > 0) {
            throw new RuntimeException("El descuento total no puede superar el subtotal");
        }

        BigDecimal subtotalConDescuento = subtotal.subtract(descuentoAplicado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igvPorcentaje = normalizarIgv(igvPorcentajeInput);
        BigDecimal igv = subtotalConDescuento
                .multiply(igvPorcentaje)
                .divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotalConDescuento.add(igv).setScale(2, RoundingMode.HALF_UP);

        return new TotalesVenta(
                igvPorcentaje,
                subtotalConDescuento,
                descuentoAplicado.setScale(2, RoundingMode.HALF_UP),
                tipoDescuento,
                igv,
                total);
    }

    private List<PagoCalculado> calcularPagos(List<VentaPagoCreateItem> pagos, BigDecimal totalVenta) {
        if (pagos == null || pagos.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un pago");
        }

        List<PagoCalculado> calculados = new ArrayList<>();
        BigDecimal sumaPagos = BigDecimal.ZERO;

        for (VentaPagoCreateItem item : pagos) {
            Integer idMetodoPago = item.idMetodoPago();
            if (idMetodoPago == null) {
                throw new RuntimeException("Cada pago debe incluir idMetodoPago");
            }

            MetodoPagoConfig metodoPago = metodoPagoConfigRepository.findById(idMetodoPago)
                    .orElseThrow(() -> new RuntimeException("Metodo de pago con ID " + idMetodoPago + " no encontrado"));
            if (!"ACTIVO".equalsIgnoreCase(metodoPago.getEstado())) {
                throw new RuntimeException("El metodo de pago '" + metodoPago.getNombre() + "' esta INACTIVO");
            }

            BigDecimal monto = decimalPositivo(item.monto(), "monto");
            String referencia = normalizarTexto(item.referencia(), 100);
            sumaPagos = sumaPagos.add(monto).setScale(2, RoundingMode.HALF_UP);

            calculados.add(new PagoCalculado(metodoPago, monto, referencia));
        }

        if (sumaPagos.compareTo(totalVenta) != 0) {
            throw new RuntimeException("La suma de pagos (" + sumaPagos
                    + ") debe ser igual al total de la venta (" + totalVenta + ")");
        }

        return calculados;
    }

    private Venta obtenerVentaConAlcance(Integer idVenta, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return ventaRepository.findByIdVentaAndDeletedAtIsNull(idVenta)
                    .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return ventaRepository.findByIdVentaAndDeletedAtIsNullAndSucursal_IdSucursal(idVenta, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
    }

    private Sucursal resolverSucursalParaVenta(Integer idSucursalRequest, Usuario usuarioAutenticado) {
        Integer idSucursalDestino = esAdministrador(usuarioAutenticado)
                ? idSucursalRequeridaParaAdmin(idSucursalRequest)
                : obtenerIdSucursalUsuario(usuarioAutenticado);
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalDestino)
                .filter(s -> "ACTIVO".equalsIgnoreCase(s.getEstado()))
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada o inactiva"));
    }

    private Cliente resolverCliente(Integer idCliente, Integer idSucursal) {
        if (idCliente == null) {
            return null;
        }
        return clienteRepository.findByIdClienteAndDeletedAtIsNullAndSucursal_IdSucursal(idCliente, idSucursal)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado para la sucursal"));
    }

    private VentaListItemResponse toListItemResponse(Venta venta) {
        String nombreCliente = venta.getCliente() != null ? venta.getCliente().getNombres() : null;
        String nombreUsuario = nombreUsuario(venta.getUsuario());
        Integer idSucursal = venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null;
        String nombreSucursal = venta.getSucursal() != null ? venta.getSucursal().getNombre() : null;
        long items = ventaDetalleRepository.countByVenta_IdVentaAndDeletedAtIsNull(venta.getIdVenta());
        long pagos = pagoRepository.countByVenta_IdVentaAndDeletedAtIsNull(venta.getIdVenta());

        return new VentaListItemResponse(
                venta.getIdVenta(),
                venta.getFecha(),
                venta.getTipoComprobante(),
                venta.getSerie(),
                venta.getCorrelativo(),
                venta.getTotal(),
                venta.getEstado(),
                venta.getCliente() != null ? venta.getCliente().getIdCliente() : null,
                nombreCliente,
                venta.getUsuario() != null ? venta.getUsuario().getIdUsuario() : null,
                nombreUsuario,
                idSucursal,
                nombreSucursal,
                items,
                pagos);
    }

    private VentaResponse toResponse(Venta venta, List<VentaDetalle> detalles, List<Pago> pagos) {
        List<VentaDetalleResponse> detalleResponses = detalles.stream()
                .map(this::toDetalleResponse)
                .toList();
        List<VentaPagoResponse> pagoResponses = pagos.stream()
                .map(this::toPagoResponse)
                .toList();

        return new VentaResponse(
                venta.getIdVenta(),
                venta.getFecha(),
                venta.getTipoComprobante(),
                venta.getSerie(),
                venta.getCorrelativo(),
                venta.getIgvPorcentaje(),
                venta.getSubtotal(),
                venta.getDescuentoTotal(),
                venta.getTipoDescuento(),
                venta.getIgv(),
                venta.getTotal(),
                venta.getEstado(),
                venta.getCliente() != null ? venta.getCliente().getIdCliente() : null,
                venta.getCliente() != null ? venta.getCliente().getNombres() : null,
                venta.getUsuario() != null ? venta.getUsuario().getIdUsuario() : null,
                nombreUsuario(venta.getUsuario()),
                venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null,
                venta.getSucursal() != null ? venta.getSucursal().getNombre() : null,
                detalleResponses,
                pagoResponses);
    }

    private VentaDetalleResponse toDetalleResponse(VentaDetalle detalle) {
        ProductoVariante variante = detalle.getProductoVariante();
        return new VentaDetalleResponse(
                detalle.getIdVentaDetalle(),
                variante != null ? variante.getIdProductoVariante() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getIdProducto() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getNombre() : null,
                variante != null ? variante.getSku() : null,
                variante != null ? variante.getCodigoExterno() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getIdColor() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getIdTalla() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getDescuento(),
                detalle.getSubtotal());
    }

    private VentaPagoResponse toPagoResponse(Pago pago) {
        MetodoPagoConfig metodo = pago.getMetodoPago();
        return new VentaPagoResponse(
                pago.getIdPago(),
                metodo != null ? metodo.getIdMetodoPago() : null,
                metodo != null ? metodo.getNombre() : null,
                pago.getMonto(),
                pago.getReferencia(),
                pago.getFecha());
    }

    private String nombreUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String normalizarTipoComprobante(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return "TICKET";
        }
        String normalized = tipoComprobante.trim().toUpperCase();
        if (!"TICKET".equals(normalized)
                && !"BOLETA".equals(normalized)
                && !"FACTURA".equals(normalized)) {
            throw new RuntimeException("tipoComprobante permitido: TICKET, BOLETA o FACTURA");
        }
        return normalized;
    }

    private String normalizarTipoDescuento(String tipoDescuento, BigDecimal descuentoInput) {
        if (descuentoInput == null || descuentoInput.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (tipoDescuento == null || tipoDescuento.isBlank()) {
            throw new RuntimeException("tipoDescuento es obligatorio cuando descuentoTotal es mayor a 0");
        }
        String normalized = tipoDescuento.trim().toUpperCase();
        if (!"MONTO".equals(normalized) && !"PORCENTAJE".equals(normalized)) {
            throw new RuntimeException("tipoDescuento permitido: MONTO o PORCENTAJE");
        }
        if ("PORCENTAJE".equals(normalized) && descuentoInput.compareTo(CIEN) > 0) {
            throw new RuntimeException("descuentoTotal no puede superar 100 cuando tipoDescuento es PORCENTAJE");
        }
        return normalized;
    }

    private BigDecimal normalizarIgv(Double igvPorcentaje) {
        BigDecimal igv = igvPorcentaje == null
                ? BigDecimal.valueOf(18)
                : decimalNoNegativo(igvPorcentaje, "igvPorcentaje");
        if (igv.compareTo(CIEN) > 0) {
            throw new RuntimeException("igvPorcentaje no puede ser mayor a 100");
        }
        return igv.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer idSucursalRequeridaParaAdmin(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            throw new RuntimeException("idSucursal es obligatorio para ADMINISTRADOR");
        }
        return idSucursalRequest;
    }

    private BigDecimal decimalDesdeDouble(Double value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalPositivo(Double value, String field) {
        BigDecimal decimal = decimalDesdeDouble(value);
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El campo '" + field + "' debe ser mayor a 0");
        }
        return decimal;
    }

    private BigDecimal decimalNoNegativo(Double value, String field) {
        BigDecimal decimal = decimalDesdeDouble(value);
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El campo '" + field + "' no puede ser negativo");
        }
        return decimal;
    }

    private String normalizarTexto(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro 'page' no puede ser negativo");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolLectura(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar ventas");
        }
    }

    private void validarRolVenta(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para registrar ventas");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private record DetalleCalculado(
            ProductoVariante variante,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal descuento,
            BigDecimal subtotal) {
    }

    private record PagoCalculado(
            MetodoPagoConfig metodoPago,
            BigDecimal monto,
            String referencia) {
    }

    private record TotalesVenta(
            BigDecimal igvPorcentaje,
            BigDecimal subtotal,
            BigDecimal descuentoAplicado,
            String tipoDescuento,
            BigDecimal igv,
            BigDecimal total) {
    }
}
