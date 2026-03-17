package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.ComunicacionBaja;
import com.sistemapos.sistematextil.model.ComunicacionBajaDetalle;
import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.NotaCreditoDetalle;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.ComunicacionBajaDetalleRepository;
import com.sistemapos.sistematextil.repositories.ComunicacionBajaRepository;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoDetalleRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.anulacion.AnulacionInfoResponse;
import com.sistemapos.sistematextil.util.anulacion.AnulacionRequest;
import com.sistemapos.sistematextil.util.anulacion.AnulacionResponse;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnulacionService {

    private static final Logger log = LoggerFactory.getLogger(AnulacionService.class);
    private static final int DIAS_LIMITE_BAJA = 7;

    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final ComunicacionBajaRepository comunicacionBajaRepository;
    private final ComunicacionBajaDetalleRepository comunicacionBajaDetalleRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoDetalleRepository notaCreditoDetalleRepository;
    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final HistorialStockRepository historialStockRepository;
    private final UsuarioRepository usuarioRepository;
    private final SunatBajaEmissionService sunatBajaEmissionService;
    private final SunatNotaCreditoEmissionService sunatNotaCreditoEmissionService;

    // ─── PRE-VERIFICACIÓN ────────────────────────────────────────────

    public AnulacionInfoResponse verificarAnulacion(Integer idVenta, String correoUsuario) {
        Venta venta = buscarVentaActiva(idVenta, correoUsuario);
        String tipoComprobante = normalizarTipo(venta.getTipoComprobante());
        String serieCorrelativo = SunatComprobanteHelper.numeroComprobante(venta);
        long diasDesdeEmision = ChronoUnit.DAYS.between(venta.getFecha().toLocalDate(), LocalDate.now());

        AnulacionInfoResponse.AnulacionInfoResponseBuilder builder = AnulacionInfoResponse.builder()
                .idVenta(venta.getIdVenta())
                .serieCorrelativo(serieCorrelativo)
                .tipoComprobante(venta.getTipoComprobante())
                .estadoVenta(venta.getEstado())
                .sunatEstadoVenta(venta.getSunatEstado() != null ? venta.getSunatEstado().name() : "NO_APLICA")
                .fechaEmision(venta.getFecha())
                .diasDesdeEmision(diasDesdeEmision);

        // Si ya está anulada
        if ("ANULADA".equals(venta.getEstado()) || "ANULACION_PENDIENTE".equals(venta.getEstado())) {
            return builder
                    .puedeAnularse(false)
                    .razonNoAnulable("La venta ya se encuentra en estado " + venta.getEstado())
                    .build();
        }

        // Nota de venta
        if ("NOTA DE VENTA".equals(tipoComprobante)) {
            return builder
                    .puedeAnularse(true)
                    .metodoAnulacion("INTERNA")
                    .build();
        }

        // Boleta o Factura - verificar estado SUNAT
        if (venta.getSunatEstado() != SunatEstado.ACEPTADO) {
            return builder
                    .puedeAnularse(false)
                    .razonNoAnulable("Solo se pueden anular comprobantes aceptados por SUNAT. Estado actual: "
                            + (venta.getSunatEstado() != null ? venta.getSunatEstado().name() : "N/A"))
                    .build();
        }

        // Determinar método
        String metodo = diasDesdeEmision <= DIAS_LIMITE_BAJA ? "COMUNICACION_BAJA" : "NOTA_CREDITO";
        return builder
                .puedeAnularse(true)
                .metodoAnulacion(metodo)
                .build();
    }

    // ─── ANULACIÓN PRINCIPAL ─────────────────────────────────────────

    @Transactional
    public AnulacionResponse anular(AnulacionRequest request, String correoUsuario) {
        Venta venta = buscarVentaActiva(request.getIdVenta(), correoUsuario);
        Usuario usuario = buscarUsuario(correoUsuario);
        String tipoComprobante = normalizarTipo(venta.getTipoComprobante());

        validarEstadoParaAnulacion(venta);

        return switch (tipoComprobante) {
            case "NOTA DE VENTA" -> anularNotaVenta(venta, usuario, request.getMotivo());
            case "BOLETA", "FACTURA" -> anularComprobanteElectronico(venta, usuario, request.getMotivo());
            default -> throw new RuntimeException("Tipo de comprobante no soportado para anulación: " + tipoComprobante);
        };
    }

    // ─── ANULACIÓN INTERNA (NOTA DE VENTA) ───────────────────────────

    private AnulacionResponse anularNotaVenta(Venta venta, Usuario usuario, String motivo) {
        log.info("Anulación interna de nota de venta #{}", venta.getIdVenta());

        revertirStock(venta, usuario);

        venta.setEstado("ANULADA");
        venta.setAnulacionTipo("INTERNA");
        venta.setAnulacionMotivo(motivo);
        venta.setAnulacionFecha(LocalDateTime.now());
        venta.setUsuarioAnulacion(usuario);
        ventaRepository.save(venta);

        String serieCorrelativo = SunatComprobanteHelper.numeroComprobante(venta);
        log.info("Nota de venta {} anulada internamente", serieCorrelativo);

        return AnulacionResponse.builder()
                .idVenta(venta.getIdVenta())
                .serieCorrelativo(serieCorrelativo)
                .tipoComprobante(venta.getTipoComprobante())
                .estadoVenta("ANULADA")
                .anulacionTipo("INTERNA")
                .anulacionMotivo(motivo)
                .anulacionFecha(venta.getAnulacionFecha())
                .stockDevuelto(true)
                .message("Nota de venta anulada correctamente. Stock revertido.")
                .build();
    }

    // ─── ANULACIÓN COMPROBANTE ELECTRÓNICO ───────────────────────────

    private AnulacionResponse anularComprobanteElectronico(Venta venta, Usuario usuario, String motivo) {
        if (venta.getSunatEstado() != SunatEstado.ACEPTADO) {
            throw new RuntimeException(
                    "Solo se pueden anular comprobantes electrónicos aceptados por SUNAT. Estado actual: "
                            + (venta.getSunatEstado() != null ? venta.getSunatEstado().name() : "N/A"));
        }

        long diasDesdeEmision = ChronoUnit.DAYS.between(venta.getFecha().toLocalDate(), LocalDate.now());

        if (diasDesdeEmision <= DIAS_LIMITE_BAJA) {
            return procesarComunicacionBaja(venta, usuario, motivo);
        } else {
            return procesarNotaCredito(venta, usuario, motivo);
        }
    }

    // ─── COMUNICACIÓN DE BAJA (≤7 días) ──────────────────────────────

    private AnulacionResponse procesarComunicacionBaja(Venta venta, Usuario usuario, String motivo) {
        log.info("Procesando comunicación de baja para venta #{}", venta.getIdVenta());

        LocalDate hoy = LocalDate.now();
        String identificadorBaja = generarIdentificadorBaja(venta, hoy);
        String serieCorrelativo = SunatComprobanteHelper.numeroComprobante(venta);

        // Crear comunicación de baja
        ComunicacionBaja baja = new ComunicacionBaja();
        baja.setVenta(venta);
        baja.setUsuarioSolicita(usuario);
        baja.setMotivoBaja(motivo);
        baja.setIdentificadorBaja(identificadorBaja);
        baja.setFechaEmisionOriginal(venta.getFecha().toLocalDate());
        baja.setFechaGeneracionBaja(LocalDateTime.now());
        baja.setSunatEstado("PENDIENTE");
        baja.setStockDevuelto(false);
        comunicacionBajaRepository.save(baja);

        // Crear detalle de baja
        ComunicacionBajaDetalle detalle = new ComunicacionBajaDetalle();
        detalle.setComunicacionBaja(baja);
        detalle.setVenta(venta);
        detalle.setTipoComprobante(venta.getTipoComprobante());
        detalle.setSerie(venta.getSerie());
        detalle.setCorrelativo(venta.getCorrelativo());
        detalle.setMotivo(motivo);
        comunicacionBajaDetalleRepository.save(detalle);

        // Actualizar venta a ANULACION_PENDIENTE
        venta.setEstado("ANULACION_PENDIENTE");
        venta.setAnulacionTipo("COMUNICACION_BAJA");
        venta.setAnulacionMotivo(motivo);
        venta.setAnulacionFecha(LocalDateTime.now());
        venta.setUsuarioAnulacion(usuario);
        ventaRepository.save(venta);

        log.info("Comunicación de baja {} creada para venta {}. Enviando a SUNAT...",
                identificadorBaja, serieCorrelativo);

        // Enviar XML a SUNAT (sendSummary → ticket)
        try {
            sunatBajaEmissionService.enviarBaja(baja);
        } catch (RuntimeException e) {
            log.error("Error al enviar comunicación de baja a SUNAT: {}", e.getMessage());
            // La baja queda registrada con estado PENDIENTE o ERROR según lo que haya actualizado el service
        }

        // Recargar baja para obtener estado actualizado por el emission service
        baja = comunicacionBajaRepository.findByIdBajaAndDeletedAtIsNull(baja.getIdBaja())
                .orElse(baja);
        String sunatEstadoActual = baja.getSunatEstado();
        String mensajeBase = "Comunicación de baja " + identificadorBaja;

        String mensaje;
        if ("ACEPTADO".equals(sunatEstadoActual)) {
            mensaje = mensajeBase + " aceptada por SUNAT. Comprobante " + serieCorrelativo + " anulado.";
        } else if ("RECHAZADO".equals(sunatEstadoActual)) {
            mensaje = mensajeBase + " rechazada por SUNAT: " + baja.getSunatMensaje();
        } else if (baja.getSunatTicket() != null && !baja.getSunatTicket().isBlank()) {
            mensaje = mensajeBase + " registrada. Ticket: " + baja.getSunatTicket()
                    + ". Pendiente de confirmación por SUNAT.";
        } else {
            mensaje = mensajeBase + " registrada. Pendiente de confirmación por SUNAT.";
        }

        return AnulacionResponse.builder()
                .idVenta(venta.getIdVenta())
                .serieCorrelativo(serieCorrelativo)
                .tipoComprobante(venta.getTipoComprobante())
                .estadoVenta(venta.getEstado())
                .anulacionTipo("COMUNICACION_BAJA")
                .anulacionMotivo(motivo)
                .anulacionFecha(venta.getAnulacionFecha())
                .sunatEstado(sunatEstadoActual)
                .sunatTicket(baja.getSunatTicket())
                .idComunicacionBaja(baja.getIdBaja())
                .stockDevuelto(Boolean.TRUE.equals(baja.getStockDevuelto()))
                .message(mensaje)
                .build();
    }

    // ─── NOTA DE CRÉDITO (>7 días) ───────────────────────────────────

    private AnulacionResponse procesarNotaCredito(Venta venta, Usuario usuario, String motivo) {
        log.info("Procesando nota de crédito para venta #{}", venta.getIdVenta());

        Sucursal sucursal = venta.getSucursal();
        String tipoNC = resolverTipoNotaCredito(venta.getTipoComprobante());
        String serieNC = resolverSerieNotaCredito(sucursal.getIdSucursal(), tipoNC);
        Integer correlativoNC = obtenerSiguienteCorrelativoNC(sucursal.getIdSucursal(), tipoNC, serieNC);
        List<VentaDetalle> detallesVenta = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());

        // Crear nota de crédito
        NotaCredito nc = new NotaCredito();
        nc.setVentaReferencia(venta);
        nc.setSucursal(sucursal);
        nc.setUsuario(usuario);
        nc.setCliente(venta.getCliente());
        nc.setTipoComprobante("NOTA DE CREDITO");
        nc.setSerie(serieNC);
        nc.setCorrelativo(correlativoNC);
        nc.setMoneda(venta.getMoneda());
        nc.setCodigoMotivo("01"); // Anulación de la operación
        nc.setDescripcionMotivo(motivo);
        nc.setTipoDocumentoRef(SunatComprobanteHelper.codigoTipoComprobante(venta.getTipoComprobante()));
        nc.setSerieRef(venta.getSerie());
        nc.setCorrelativoRef(venta.getCorrelativo());
        nc.setIgvPorcentaje(venta.getIgvPorcentaje());
        nc.setSubtotal(venta.getSubtotal());
        nc.setDescuentoTotal(venta.getDescuentoTotal());
        nc.setIgv(venta.getIgv());
        nc.setTotal(venta.getTotal());
        nc.setSunatEstado("PENDIENTE");
        nc.setStockDevuelto(false);
        notaCreditoRepository.save(nc);

        // Crear detalles de nota de crédito
        for (VentaDetalle dv : detallesVenta) {
            NotaCreditoDetalle ncd = new NotaCreditoDetalle();
            ncd.setNotaCredito(nc);
            ncd.setProductoVariante(dv.getProductoVariante());
            ncd.setDescripcion(dv.getDescripcion());
            ncd.setCantidad(dv.getCantidad());
            ncd.setUnidadMedida(dv.getUnidadMedida());
            ncd.setCodigoTipoAfectacionIgv(dv.getCodigoTipoAfectacionIgv());
            ncd.setPrecioUnitario(dv.getPrecioUnitario());
            ncd.setDescuento(dv.getDescuento());
            ncd.setIgvDetalle(dv.getIgvDetalle());
            ncd.setSubtotal(dv.getSubtotal());
            ncd.setTotalDetalle(dv.getTotalDetalle());
            notaCreditoDetalleRepository.save(ncd);
        }

        // Revertir stock inmediatamente para notas de crédito
        revertirStock(venta, usuario);
        nc.setStockDevuelto(true);
        notaCreditoRepository.save(nc);

        // Actualizar venta
        venta.setEstado("ANULADA");
        venta.setAnulacionTipo("NOTA_CREDITO");
        venta.setAnulacionMotivo(motivo);
        venta.setAnulacionFecha(LocalDateTime.now());
        venta.setUsuarioAnulacion(usuario);
        ventaRepository.save(venta);

        String serieCorrelativo = SunatComprobanteHelper.numeroComprobante(venta);
        String ncSerieCorrelativo = serieNC + "-" + String.format(Locale.ROOT, "%08d", correlativoNC);

        log.info("Nota de crédito {} creada para venta {}. Enviando a SUNAT...",
                ncSerieCorrelativo, serieCorrelativo);

        // Emitir NC a SUNAT (sendBill → CDR síncrono)
        String sunatEstadoFinal = "PENDIENTE";
        String mensajeExtra = "";
        try {
            com.sistemapos.sistematextil.util.sunat.SunatEmissionResult result =
                    sunatNotaCreditoEmissionService.emitir(nc);
            sunatEstadoFinal = result.estado() != null ? result.estado().name() : "PENDIENTE";
            if (result.mensaje() != null && !result.mensaje().isBlank()) {
                mensajeExtra = " SUNAT: " + result.mensaje();
            }
        } catch (RuntimeException e) {
            log.error("Error al emitir nota de crédito a SUNAT: {}", e.getMessage());
            mensajeExtra = " Error SUNAT: " + e.getMessage();
        }

        return AnulacionResponse.builder()
                .idVenta(venta.getIdVenta())
                .serieCorrelativo(serieCorrelativo)
                .tipoComprobante(venta.getTipoComprobante())
                .estadoVenta("ANULADA")
                .anulacionTipo("NOTA_CREDITO")
                .anulacionMotivo(motivo)
                .anulacionFecha(venta.getAnulacionFecha())
                .sunatEstado(sunatEstadoFinal)
                .idNotaCredito(nc.getIdNotaCredito())
                .stockDevuelto(true)
                .message("Nota de crédito " + ncSerieCorrelativo
                        + " generada para anular " + serieCorrelativo
                        + ". Stock revertido." + mensajeExtra)
                .build();
    }

    // ─── CONFIRMAR BAJA (después de respuesta SUNAT) ─────────────────

    @Transactional
    public AnulacionResponse confirmarBaja(Integer idBaja, String correoUsuario) {
        buscarUsuario(correoUsuario);

        ComunicacionBaja baja = comunicacionBajaRepository.findByIdBajaAndDeletedAtIsNull(idBaja)
                .orElseThrow(() -> new RuntimeException("Comunicación de baja no encontrada: " + idBaja));

        if ("ACEPTADO".equals(baja.getSunatEstado())) {
            Venta venta = baja.getVenta();

            if (!Boolean.TRUE.equals(baja.getStockDevuelto())) {
                Usuario usuario = baja.getUsuarioSolicita();
                revertirStock(venta, usuario);
                baja.setStockDevuelto(true);
                comunicacionBajaRepository.save(baja);
            }

            venta.setEstado("ANULADA");
            ventaRepository.save(venta);

            String serieCorrelativo = SunatComprobanteHelper.numeroComprobante(venta);

            return AnulacionResponse.builder()
                    .idVenta(venta.getIdVenta())
                    .serieCorrelativo(serieCorrelativo)
                    .tipoComprobante(venta.getTipoComprobante())
                    .estadoVenta("ANULADA")
                    .anulacionTipo("COMUNICACION_BAJA")
                    .sunatEstado("ACEPTADO")
                    .sunatMensaje(baja.getSunatMensaje())
                    .idComunicacionBaja(baja.getIdBaja())
                    .stockDevuelto(true)
                    .message("Baja confirmada por SUNAT. Venta " + serieCorrelativo + " anulada. Stock revertido.")
                    .build();
        }

        if ("RECHAZADO".equals(baja.getSunatEstado())) {
            Venta venta = baja.getVenta();
            venta.setEstado("EMITIDA");
            venta.setAnulacionTipo(null);
            venta.setAnulacionMotivo(null);
            venta.setAnulacionFecha(null);
            venta.setUsuarioAnulacion(null);
            ventaRepository.save(venta);

            String serieCorrelativo = SunatComprobanteHelper.numeroComprobante(venta);

            return AnulacionResponse.builder()
                    .idVenta(venta.getIdVenta())
                    .serieCorrelativo(serieCorrelativo)
                    .tipoComprobante(venta.getTipoComprobante())
                    .estadoVenta("EMITIDA")
                    .sunatEstado("RECHAZADO")
                    .sunatMensaje(baja.getSunatMensaje())
                    .idComunicacionBaja(baja.getIdBaja())
                    .stockDevuelto(false)
                    .message("SUNAT rechazó la comunicación de baja. La venta " + serieCorrelativo
                            + " vuelve a estado EMITIDA. Motivo: " + baja.getSunatMensaje())
                    .build();
        }

        throw new RuntimeException("La comunicación de baja aún está en estado " + baja.getSunatEstado()
                + ". Espere la respuesta de SUNAT.");
    }

    // ─── REVERSIÓN DE STOCK ──────────────────────────────────────────

    private void revertirStock(Venta venta, Usuario usuario) {
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());

        for (VentaDetalle detalle : detalles) {
            ProductoVariante variante = detalle.getProductoVariante();
            if (variante == null) {
                continue;
            }

            int cantidadDevolver = detalle.getCantidad() != null ? detalle.getCantidad() : 0;
            if (cantidadDevolver <= 0) {
                continue;
            }

            int stockAnterior = variante.getStock() != null ? variante.getStock() : 0;
            int stockNuevo = stockAnterior + cantidadDevolver;

            variante.setStock(stockNuevo);
            productoVarianteRepository.save(variante);

            HistorialStock historial = HistorialStock.builder()
                    .tipoMovimiento(HistorialStock.TipoMovimiento.DEVOLUCION)
                    .motivo("Anulación de venta " + SunatComprobanteHelper.numeroComprobante(venta))
                    .productoVariante(variante)
                    .sucursal(venta.getSucursal())
                    .usuario(usuario)
                    .cantidad(cantidadDevolver)
                    .stockAnterior(stockAnterior)
                    .stockNuevo(stockNuevo)
                    .build();
            historialStockRepository.save(historial);

            log.debug("Stock revertido: variante={}, +{}, {}→{}",
                    variante.getIdProductoVariante(), cantidadDevolver, stockAnterior, stockNuevo);
        }

        log.info("Stock revertido para venta #{}", venta.getIdVenta());
    }

    // ─── HELPERS ─────────────────────────────────────────────────────

    private Venta buscarVentaActiva(Integer idVenta, String correoUsuario) {
        if (idVenta == null) {
            throw new RuntimeException("El ID de la venta es obligatorio");
        }

        Usuario usuario = buscarUsuario(correoUsuario);
        Integer idSucursal = usuario.getSucursal() != null ? usuario.getSucursal().getIdSucursal() : null;

        Venta venta;
        if (idSucursal != null) {
            venta = ventaRepository.findByIdVentaAndDeletedAtIsNullAndSucursal_IdSucursal(idVenta, idSucursal)
                    .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + idVenta));
        } else {
            venta = ventaRepository.findByIdVentaAndDeletedAtIsNull(idVenta)
                    .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + idVenta));
        }

        return venta;
    }

    private Usuario buscarUsuario(String correo) {
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private void validarEstadoParaAnulacion(Venta venta) {
        if ("ANULADA".equals(venta.getEstado())) {
            throw new RuntimeException("La venta ya se encuentra anulada");
        }
        if ("ANULACION_PENDIENTE".equals(venta.getEstado())) {
            throw new RuntimeException("La venta ya tiene una anulación pendiente");
        }
    }

    private String normalizarTipo(String tipo) {
        return tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
    }

    private String generarIdentificadorBaja(Venta venta, LocalDate fecha) {
        Integer maxCorrelativo = comunicacionBajaRepository.obtenerMaxCorrelativoBaja(fecha);
        int siguiente = (maxCorrelativo != null ? maxCorrelativo : 0) + 1;

        return String.format(Locale.ROOT, "RA-%s-%05d",
                fecha.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")),
                siguiente);
    }

    private String resolverTipoNotaCredito(String tipoComprobanteVenta) {
        return switch (normalizarTipo(tipoComprobanteVenta)) {
            case "BOLETA" -> "NOTA_CREDITO_BOLETA";
            case "FACTURA" -> "NOTA_CREDITO_FACTURA";
            default -> throw new RuntimeException("No se puede emitir nota de crédito para: " + tipoComprobanteVenta);
        };
    }

    private String resolverSerieNotaCredito(Integer idSucursal, String tipoNC) {
        ComprobanteConfig config = comprobanteConfigRepository
                .findBySucursal_IdSucursalAndTipoComprobanteAndDeletedAtIsNull(idSucursal, tipoNC)
                .orElseThrow(() -> new RuntimeException(
                        "No hay serie configurada para " + tipoNC + " en la sucursal. "
                                + "Configure una serie en comprobante_config."));
        return config.getSerie();
    }

    private Integer obtenerSiguienteCorrelativoNC(Integer idSucursal, String tipoNC, String serie) {
        ComprobanteConfig config = comprobanteConfigRepository
                .findActivoForUpdate(idSucursal, tipoNC)
                .orElseThrow(() -> new RuntimeException(
                        "No hay serie activa para " + tipoNC + " en la sucursal"));

        Integer maxRepo = notaCreditoRepository.obtenerMaxCorrelativo(idSucursal, serie);
        int maxReal = Math.max(
                config.getUltimoCorrelativo() != null ? config.getUltimoCorrelativo() : 0,
                maxRepo != null ? maxRepo : 0);

        int siguiente = maxReal + 1;
        config.setUltimoCorrelativo(siguiente);
        comprobanteConfigRepository.save(config);

        return siguiente;
    }
}
