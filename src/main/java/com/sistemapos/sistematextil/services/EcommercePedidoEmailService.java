package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.EcommercePedido;
import com.sistemapos.sistematextil.model.EcommercePedidoDetalle;
import com.sistemapos.sistematextil.model.Venta;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcommercePedidoEmailService {

    private static final Logger log = LoggerFactory.getLogger(EcommercePedidoEmailService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ICON_PAYMENT_PENDING = "https://cdn-icons-png.flaticon.com/512/3240/3240587.png";
    private static final String ICON_SUCCESS = "https://cdn-icons-png.flaticon.com/512/845/845646.png";
    private static final String ICON_ERROR = "https://cdn-icons-png.flaticon.com/512/7068/7068033.png";

    private final BrevoEmailService brevoEmailService;
    private final VentaService ventaService;

    @Value("${ecommerce.frontend-url:http://localhost:3000}")
    private String ecommerceFrontendUrl;

    public void enviarPedidoCreado(EcommercePedido pedido, String token) {
        String linkPago = linkPago(token);
        String html = layout(
                iconoImg(ICON_PAYMENT_PENDING),
                "Completa tu pago",
                "Tu pedido fue reservado por 10 minutos. Sube tu comprobante para terminar la compra.",
                pedido,
                """
                <div style="background:#fafafa;padding:18px;margin:22px 0">
                  <p style="margin:0 0 4px;color:#111;font-size:12px;text-transform:uppercase;letter-spacing:.12em">Reserva vigente hasta</p>
                  <p style="margin:0;color:#111;font-size:18px;font-weight:500">%s</p>
                </div>
                %s
                """.formatted(pedido.getReservaExpiraAt().format(FECHA), boton("Subir comprobante", linkPago)));
        brevoEmailService.enviarHtml(
                pedido.getClienteCorreo(),
                nombreCliente(pedido),
                "Completa tu pago - Pedido " + pedido.getCodigo(),
                html,
                Collections.emptyList());
    }

    public void enviarPedidoAceptado(EcommercePedido pedido, Venta venta, String correoUsuario) {
        try {
            VentaService.ArchivoDescargable pdf = ventaService.descargarComprobantePdf(venta.getIdVenta(), correoUsuario);
            String numero = venta.getSerie() + "-" + venta.getCorrelativo();
            String html = layout(
                    iconoImg(ICON_SUCCESS),
                    "Pedido aceptado",
                    "Tu pago fue validado y tu venta ya fue registrada.",
                    pedido,
                    """
                    <p style="margin:0 0 16px;color:#555;font-size:14px;line-height:1.7">Adjuntamos el comprobante PDF de tu compra.</p>
                    <div style="background:#fafafa;padding:18px;color:#111;font-size:14px">
                      <span style="display:block;color:#777;font-size:11px;text-transform:uppercase;letter-spacing:.12em;margin-bottom:4px">Comprobante</span>
                      <b>%s</b>
                    </div>
                    """.formatted(escape(numero)));
            brevoEmailService.enviarHtml(
                    pedido.getClienteCorreo(),
                    nombreCliente(pedido),
                    "Tu pedido fue aceptado - " + pedido.getCodigo(),
                    html,
                    Collections.singletonList(new BrevoEmailService.Adjunto(pdf.nombreArchivo(), pdf.bytes())));
        } catch (Exception e) {
            log.error("No se pudo preparar correo de pedido aceptado {}", pedido.getCodigo(), e);
        }
    }

    public void enviarPedidoRechazado(EcommercePedido pedido) {
        String html = layout(
                iconoImg(ICON_ERROR),
                "Pedido rechazado",
                "No pudimos validar el pago de tu pedido.",
                pedido,
                """
                <p style="margin:0;color:#555;font-size:14px;line-height:1.7">Si consideras que fue un error, contacta con la tienda para revisar tu comprobante.</p>
                <div style="margin-top:18px;background:#fff5f5;padding:18px;color:#b91c1c;font-size:14px;font-weight:500">Pedido cancelado</div>
                """);
        brevoEmailService.enviarHtml(
                pedido.getClienteCorreo(),
                nombreCliente(pedido),
                "Tu pedido fue rechazado - " + pedido.getCodigo(),
                html,
                Collections.emptyList());
    }

    private String layout(String icono, String titulo, String subtitulo, EcommercePedido pedido, String contenido) {
        return """
                <div style="margin:0;background:#ffffff;padding:30px 12px;font-family:Poppins,Arial,Helvetica,sans-serif;color:#222">
                  <div style="max-width:620px;margin:0 auto;background:#ffffff">
                    <div style="text-align:center;padding:18px 0 26px">
                      <div style="font-family:Versailles,Georgia,serif;font-size:34px;letter-spacing:.16em;font-weight:400;color:#111;line-height:1">KIMENTS</div>
                      <div style="margin-top:6px;font-size:9px;text-transform:uppercase;letter-spacing:.24em;color:#555">Tienda de ropa</div>
                    </div>
                    <div style="background:#fafafa;padding:32px 26px;text-align:center">
                      <table role="presentation" align="center" cellpadding="0" cellspacing="0" style="margin:0 auto 18px"><tr><td align="center">%s</td></tr></table>
                      <h1 style="margin:0;color:#222;font-size:26px;font-weight:500;line-height:1.2">%s</h1>
                      <p style="margin:10px auto 0;max-width:420px;color:#666;font-size:14px;line-height:1.7">%s</p>
                    </div>
                    <div style="padding:28px 0 0">
                      <div style="background:#111;color:#fff;padding:22px 24px;margin-bottom:24px">
                        <p style="margin:0 0 5px;color:#aaa;font-size:11px;text-transform:uppercase;letter-spacing:.14em">Pedido</p>
                        <p style="margin:0;font-size:24px;font-weight:600;letter-spacing:.04em">%s</p>
                        <p style="margin:12px 0 0;color:#ddd;font-size:13px">Cliente: %s</p>
                        <p style="margin:4px 0 0;color:#fff;font-size:15px">Total: <b>%s</b></p>
                      </div>
                      %s
                      <div style="margin-top:28px">
                        <p style="margin:0 0 14px;font-size:12px;font-weight:500;color:#222;text-transform:uppercase;letter-spacing:.14em">Resumen</p>
                        %s
                      </div>
                      <p style="margin:28px 0 0;color:#888;font-size:12px;line-height:1.7;text-align:center">Gracias por comprar en KIMENTS.</p>
                    </div>
                  </div>
                </div>
                """.formatted(
                icono,
                escape(titulo),
                escape(subtitulo),
                escape(pedido.getCodigo()),
                escape(nombreCliente(pedido)),
                escape(moneda(pedido.getTotal())),
                contenido,
                resumenProductos(pedido));
    }

    private String resumenProductos(EcommercePedido pedido) {
        StringBuilder html = new StringBuilder();
        for (EcommercePedidoDetalle detalle : pedido.getDetalles()) {
            html.append("""
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-bottom:1px solid #eee">
                      <tr>
                        <td style="padding:13px 12px 13px 0;vertical-align:top">
                          <div style="font-size:13px;font-weight:500;color:#222;text-transform:uppercase;letter-spacing:.04em">%s</div>
                          <div style="font-size:12px;color:#777;margin-top:3px">Cantidad: %s</div>
                        </td>
                        <td align="right" style="padding:13px 0;vertical-align:top;font-size:13px;font-weight:600;color:#222;white-space:nowrap">%s</td>
                      </tr>
                    </table>
                    """.formatted(
                    escape(detalle.getDescripcion()),
                    detalle.getCantidad(),
                    escape(moneda(detalle.getSubtotal()))));
        }
        return html.toString();
    }

    private String boton(String texto, String url) {
        return """
                <div style="text-align:center">
                  <a href="%s" style="display:inline-block;background:#111;color:#fff;text-decoration:none;padding:16px 26px;font-size:13px;font-weight:500;letter-spacing:.08em;text-transform:uppercase">%s</a>
                </div>
                """.formatted(escape(url), escape(texto));
    }

    private String iconoImg(String url) {
        return """
                <img src="%s" alt="" width="48" height="48" style="display:block;width:48px;height:48px;border:0;outline:none;text-decoration:none">
                """.formatted(escape(url));
    }

    public String linkPago(String token) {
        return baseUrl(ecommerceFrontendUrl) + "/pago/" + url(token);
    }

    private String nombreCliente(EcommercePedido pedido) {
        return (texto(pedido.getClienteNombres()) + " " + texto(pedido.getClienteApellidos())).trim();
    }

    private String moneda(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.of("es", "PE")).format(value == null ? BigDecimal.ZERO : value);
    }

    private String baseUrl(String value) {
        String clean = texto(value);
        return clean.endsWith("/") ? clean.substring(0, clean.length() - 1) : clean;
    }

    private String url(String value) {
        return URLEncoder.encode(texto(value), StandardCharsets.UTF_8);
    }

    private String texto(String value) {
        return value == null ? "" : value.trim();
    }

    private String escape(String value) {
        return texto(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
