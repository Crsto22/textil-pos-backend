package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaAnulacionRequest;
import com.sistemapos.sistematextil.util.venta.VentaAnulacionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VentaAnulacionService {

    private static final String ESTADO_EMITIDA = "EMITIDA";
    private static final String ESTADO_ANULADA = "ANULADA";
    private static final String ESTADO_NC_EMITIDA = "NC_EMITIDA";
    private static final String TIPO_INTERNA = "INTERNA";
    private static final String TIPO_BOLETA = "BOLETA";
    private static final String TIPO_FACTURA = "FACTURA";
    private static final String TIPO_NOTA_VENTA = "NOTA DE VENTA";

    private final TransactionTemplate transactionTemplate;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final HistorialStockRepository historialStockRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotaCreditoService notaCreditoService;

    public VentaAnulacionResponse anular(Integer idVenta, VentaAnulacionRequest request, String correoUsuarioAutenticado) {
        String descripcionMotivo = normalizarDescripcion(request.descripcionMotivo());
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolAnulacion(usuarioAutenticado);

        Venta venta = obtenerVentaConAlcance(idVenta, usuarioAutenticado);
        String tipoComprobante = normalizarTipoComprobante(venta.getTipoComprobante());

        if (TIPO_NOTA_VENTA.equals(tipoComprobante)) {
            return transactionTemplate.execute(status -> anularNotaVenta(idVenta, descripcionMotivo, usuarioAutenticado));
        }

        if (!requiereComprobanteElectronico(tipoComprobante)) {
            throw new RuntimeException("El tipo de comprobante no soporta anulacion");
        }

        if (!"01".equals(request.codigoMotivo())) {
            throw new RuntimeException("Para anular una venta electronica el codigoMotivo debe ser 01");
        }

        return notaCreditoService.anularConNotaCreditoTotal(idVenta, descripcionMotivo, usuarioAutenticado);
    }

    private VentaAnulacionResponse anularNotaVenta(Integer idVenta, String descripcionMotivo, Usuario usuarioAutenticado) {
        Venta venta = obtenerVentaConAlcanceForUpdate(idVenta, usuarioAutenticado);
        validarVentaDisponibleParaAnulacion(venta);

        revertirStock(venta, usuarioAutenticado, "ANULACION VENTA #" + venta.getIdVenta());

        venta.setEstado(ESTADO_ANULADA);
        venta.setTipoAnulacion(TIPO_INTERNA);
        venta.setMotivoAnulacion(descripcionMotivo);
        venta.setAnuladoAt(LocalDateTime.now());
        venta.setUsuarioAnulacion(usuarioAutenticado);
        ventaRepository.save(venta);

        return new VentaAnulacionResponse(
                venta.getIdVenta(),
                SunatComprobanteHelper.numeroComprobante(venta),
                venta.getTipoComprobante(),
                venta.getEstado(),
                venta.getTipoAnulacion(),
                venta.getMotivoAnulacion(),
                venta.getAnuladoAt(),
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                "Nota de venta anulada correctamente. Stock revertido.");
    }

    private void revertirStock(Venta venta, Usuario usuarioAutenticado, String motivoMovimiento) {
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        if (detalles.isEmpty()) {
            throw new RuntimeException("La venta no tiene detalles para revertir stock");
        }

        List<ProductoVariante> variantesActualizar = new ArrayList<>();
        List<HistorialStock> historial = new ArrayList<>();

        for (VentaDetalle detalle : detalles) {
            Integer idProductoVariante = detalle.getProductoVariante() != null
                    ? detalle.getProductoVariante().getIdProductoVariante()
                    : null;
            if (idProductoVariante == null) {
                throw new RuntimeException("Uno de los detalles de venta no tiene variante de producto");
            }

            ProductoVariante variante = productoVarianteRepository.findByIdProductoVarianteForUpdate(idProductoVariante)
                    .orElseThrow(() -> new RuntimeException(
                            "La variante con ID " + idProductoVariante + " no existe"));

            int stockAnterior = valorEntero(variante.getStock());
            int stockNuevo = stockAnterior + valorEntero(detalle.getCantidad());
            variante.setStock(stockNuevo);
            variante.setEstado(stockNuevo <= 0 ? "AGOTADO" : "ACTIVO");
            variantesActualizar.add(variante);

            HistorialStock movimiento = new HistorialStock();
            movimiento.setTipoMovimiento(HistorialStock.TipoMovimiento.DEVOLUCION);
            movimiento.setMotivo(motivoMovimiento);
            movimiento.setProductoVariante(variante);
            movimiento.setSucursal(venta.getSucursal());
            movimiento.setUsuario(usuarioAutenticado);
            movimiento.setCantidad(valorEntero(detalle.getCantidad()));
            movimiento.setStockAnterior(stockAnterior);
            movimiento.setStockNuevo(stockNuevo);
            historial.add(movimiento);
        }

        productoVarianteRepository.saveAll(variantesActualizar);
        historialStockRepository.saveAll(historial);
    }

    private void validarVentaDisponibleParaAnulacion(Venta venta) {
        String estado = normalizarTexto(venta.getEstado(), 20);
        if (ESTADO_ANULADA.equals(estado)) {
            throw new RuntimeException("La venta ya se encuentra anulada");
        }
        if (ESTADO_NC_EMITIDA.equals(estado)) {
            throw new RuntimeException("La venta ya tiene una nota de credito emitida");
        }
        if (!ESTADO_EMITIDA.equals(estado)) {
            throw new RuntimeException("Solo se pueden anular ventas en estado EMITIDA");
        }
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

    private Venta obtenerVentaConAlcanceForUpdate(Integer idVenta, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return ventaRepository.findByIdVentaForUpdate(idVenta)
                    .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return ventaRepository.findByIdVentaAndSucursalForUpdate(idVenta, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Venta con ID " + idVenta + " no encontrada"));
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolAnulacion(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR && usuario.getRol() != Rol.VENTAS) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para anular ventas");
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

    private String normalizarTipoComprobante(String tipoComprobante) {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            return TIPO_NOTA_VENTA;
        }
        String normalizado = tipoComprobante.trim().toUpperCase(Locale.ROOT);
        return switch (normalizado) {
            case "NOTA_DE_VENTA", "NOTA DE VENTA" -> TIPO_NOTA_VENTA;
            case TIPO_BOLETA -> TIPO_BOLETA;
            case TIPO_FACTURA -> TIPO_FACTURA;
            default -> normalizado;
        };
    }

    private String normalizarDescripcion(String descripcionMotivo) {
        String normalizado = normalizarTexto(descripcionMotivo, 255);
        if (normalizado == null || normalizado.length() < 5) {
            throw new RuntimeException("La descripcionMotivo debe tener entre 5 y 255 caracteres");
        }
        return normalizado;
    }

    private String normalizarTexto(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String normalizado = value.trim();
        if (normalizado.isEmpty()) {
            return null;
        }
        return normalizado.length() <= maxLen ? normalizado : normalizado.substring(0, maxLen);
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean requiereComprobanteElectronico(String tipoComprobante) {
        return TIPO_BOLETA.equals(tipoComprobante) || TIPO_FACTURA.equals(tipoComprobante);
    }
}
