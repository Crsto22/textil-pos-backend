package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.GuiaRemisionRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.cpe.ConsultaCpeRequest;
import com.sistemapos.sistematextil.util.cpe.ConsultaCpeResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsultaCpePublicService {

    private static final String BASE_DESCARGA = "/api/public/cpe/descargar/";

    private final VentaRepository ventaRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final GuiaRemisionRepository guiaRemisionRepository;
    private final VentaService ventaService;
    private final NotaCreditoService notaCreditoService;
    private final GuiaRemisionService guiaRemisionService;
    private final SecretKey secretKey;

    @Transactional(readOnly = true)
    public ConsultaCpeResponse consultar(ConsultaCpeRequest request) {
        CpeQuery query = CpeQuery.from(request);
        DocumentoCpe documento = buscar(query);

        boolean pdfDisponible = true;
        boolean xmlDisponible = documento.xmlKey() != null && !documento.xmlKey().isBlank();
        boolean cdrDisponible = documento.cdrKey() != null && !documento.cdrKey().isBlank();

        return new ConsultaCpeResponse(
                query.rucEmisor(),
                query.tipoComprobante(),
                query.serie(),
                query.correlativo(),
                query.fechaEmision(),
                query.importeTotal(),
                documento.estado(),
                documento.sunatEstado(),
                pdfDisponible,
                xmlDisponible,
                cdrDisponible,
                BASE_DESCARGA + firmar(query, "pdf") + "/pdf",
                xmlDisponible ? BASE_DESCARGA + firmar(query, "xml") + "/xml" : null,
                cdrDisponible ? BASE_DESCARGA + firmar(query, "cdr") + "/cdr" : null);
    }

    @Transactional
    public VentaService.ArchivoDescargable descargar(String token, String archivo) {
        TokenCpe tokenCpe = verificar(token);
        String archivoNormalizado = normalizarArchivo(archivo);
        if (!archivoNormalizado.equals(tokenCpe.archivo())) {
            throw new RuntimeException("Token de descarga invalido");
        }

        DocumentoCpe documento = buscar(tokenCpe.query());
        return switch (archivoNormalizado) {
            case "pdf" -> descargarPdf(documento);
            case "xml" -> descargarXml(documento);
            case "cdr" -> descargarCdr(documento);
            default -> throw new RuntimeException("Archivo no soportado");
        };
    }

    private DocumentoCpe buscar(CpeQuery query) {
        LocalDateTime inicio = query.fechaEmision().atStartOfDay();
        LocalDateTime fin = query.fechaEmision().plusDays(1).atStartOfDay();

        return switch (query.tipoComprobante()) {
            case "FACTURA", "BOLETA" -> ventaRepository
                    .buscarCpePublico(
                            query.rucEmisor(),
                            query.tipoComprobante(),
                            query.serie(),
                            query.correlativo(),
                            inicio,
                            fin,
                            query.importeTotal())
                    .map(DocumentoCpe::from)
                    .orElseThrow(() -> new RuntimeException("Comprobante no encontrado"));
            case "NOTA_CREDITO" -> notaCreditoRepository
                    .buscarCpePublico(
                            query.rucEmisor(),
                            query.serie(),
                            query.correlativo(),
                            inicio,
                            fin,
                            query.importeTotal())
                    .map(DocumentoCpe::from)
                    .orElseThrow(() -> new RuntimeException("Comprobante no encontrado"));
            case "GUIA_REMISION_REMITENTE" -> {
                if (query.importeTotal().compareTo(BigDecimal.ZERO) != 0) {
                    throw new RuntimeException("Comprobante no encontrado");
                }
                yield guiaRemisionRepository
                        .buscarCpePublico(query.rucEmisor(), query.serie(), query.correlativo(), inicio, fin)
                        .map(DocumentoCpe::from)
                        .orElseThrow(() -> new RuntimeException("Comprobante no encontrado"));
            }
            case "NOTA_DEBITO" -> throw new RuntimeException("Nota de debito no soportada");
            default -> throw new RuntimeException("Tipo de comprobante no soportado");
        };
    }

    private VentaService.ArchivoDescargable descargarPdf(DocumentoCpe documento) {
        return switch (documento.tipoDocumento()) {
            case VENTA -> ventaService.descargarComprobantePdfPublico(documento.id());
            case NOTA_CREDITO -> notaCreditoService.descargarComprobantePdfPublico(documento.id());
            case GUIA_REMISION -> toVentaArchivo(guiaRemisionService.descargarPdfPublico(documento.id()));
        };
    }

    private VentaService.ArchivoDescargable descargarXml(DocumentoCpe documento) {
        if (documento.xmlKey() == null || documento.xmlKey().isBlank()) {
            throw new RuntimeException("XML no disponible");
        }
        return switch (documento.tipoDocumento()) {
            case VENTA -> ventaService.descargarSunatXmlPublico(documento.id());
            case NOTA_CREDITO -> notaCreditoService.descargarSunatXmlPublico(documento.id());
            case GUIA_REMISION -> toVentaArchivo(guiaRemisionService.descargarSunatXmlPublico(documento.id()));
        };
    }

    private VentaService.ArchivoDescargable descargarCdr(DocumentoCpe documento) {
        if (documento.cdrKey() == null || documento.cdrKey().isBlank()) {
            throw new RuntimeException("CDR no disponible");
        }
        return switch (documento.tipoDocumento()) {
            case VENTA -> ventaService.descargarSunatCdrPublico(documento.id(), "zip");
            case NOTA_CREDITO -> notaCreditoService.descargarSunatCdrPublico(documento.id(), "zip");
            case GUIA_REMISION -> toVentaArchivo(guiaRemisionService.descargarSunatCdrPublico(documento.id()));
        };
    }

    private VentaService.ArchivoDescargable toVentaArchivo(GuiaRemisionService.ArchivoDescargable archivo) {
        return new VentaService.ArchivoDescargable(archivo.nombreArchivo(), archivo.contentType(), archivo.bytes());
    }

    private String firmar(CpeQuery query, String archivo) {
        String payload = query.toPayload() + "|" + archivo;
        return base64(payload.getBytes(StandardCharsets.UTF_8)) + "." + base64(hmac(payload));
    }

    private TokenCpe verificar(String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Token de descarga invalido");
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            throw new RuntimeException("Token de descarga invalido");
        }
        String payload;
        byte[] firma;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            firma = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Token de descarga invalido");
        }
        if (!MessageDigest.isEqual(firma, hmac(payload))) {
            throw new RuntimeException("Token de descarga invalido");
        }
        String[] campos = payload.split("\\|", -1);
        if (campos.length != 7) {
            throw new RuntimeException("Token de descarga invalido");
        }
        try {
            CpeQuery query = new CpeQuery(
                    campos[0],
                    CpeQuery.normalizarTipo(campos[1]),
                    CpeQuery.normalizarSerie(campos[2]),
                    Integer.valueOf(campos[3]),
                    LocalDate.parse(campos[4]),
                    normalizarImporte(new BigDecimal(campos[5])));
            return new TokenCpe(query, normalizarArchivo(campos[6]));
        } catch (RuntimeException e) {
            throw new RuntimeException("Token de descarga invalido");
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo firmar el token CPE");
        }
    }

    private String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizarArchivo(String archivo) {
        if (archivo == null) {
            throw new RuntimeException("Archivo no soportado");
        }
        String normalizado = archivo.trim().toLowerCase(Locale.ROOT);
        if (!normalizado.equals("pdf") && !normalizado.equals("xml") && !normalizado.equals("cdr")) {
            throw new RuntimeException("Archivo no soportado");
        }
        return normalizado;
    }

    private static BigDecimal normalizarImporte(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    private record TokenCpe(CpeQuery query, String archivo) {
    }

    private record CpeQuery(
            String rucEmisor,
            String tipoComprobante,
            String serie,
            Integer correlativo,
            LocalDate fechaEmision,
            BigDecimal importeTotal) {

        static CpeQuery from(ConsultaCpeRequest request) {
            return new CpeQuery(
                    request.rucEmisor().trim(),
                    normalizarTipo(request.tipoComprobante()),
                    normalizarSerie(request.serie()),
                    request.correlativo(),
                    request.fechaEmision(),
                    normalizarImporte(request.importeTotal()));
        }

        String toPayload() {
            return String.join("|",
                    rucEmisor,
                    tipoComprobante,
                    serie,
                    String.valueOf(correlativo),
                    fechaEmision.toString(),
                    importeTotal.toPlainString());
        }

        private static String normalizarTipo(String tipo) {
            if (tipo == null) {
                throw new RuntimeException("Tipo de comprobante no soportado");
            }
            String normalizado = Normalizer.normalize(tipo.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toUpperCase(Locale.ROOT)
                    .replace("-", "_")
                    .replace(" ", "_");
            return switch (normalizado) {
                case "FACTURA" -> "FACTURA";
                case "BOLETA" -> "BOLETA";
                case "NOTA_CREDITO", "NOTA_DE_CREDITO" -> "NOTA_CREDITO";
                case "GUIA_REMISION_REMITENTE", "GUIA_DE_REMISION_REMITENTE" -> "GUIA_REMISION_REMITENTE";
                case "NOTA_DEBITO", "NOTA_DE_DEBITO" -> "NOTA_DEBITO";
                default -> throw new RuntimeException("Tipo de comprobante no soportado");
            };
        }

        private static String normalizarSerie(String serie) {
            String normalizada = serie == null ? "" : serie.trim().toUpperCase(Locale.ROOT);
            if (normalizada.isBlank() || normalizada.contains("|")) {
                throw new RuntimeException("Serie invalida");
            }
            return normalizada;
        }
    }

    private record DocumentoCpe(
            TipoDocumentoCpe tipoDocumento,
            Integer id,
            String estado,
            String sunatEstado,
            String xmlKey,
            String cdrKey) {

        static DocumentoCpe from(Venta venta) {
            return new DocumentoCpe(
                    TipoDocumentoCpe.VENTA,
                    venta.getIdVenta(),
                    venta.getEstado(),
                    venta.getSunatEstado() == null ? null : venta.getSunatEstado().name(),
                    venta.getSunatXmlKey(),
                    venta.getSunatCdrKey());
        }

        static DocumentoCpe from(NotaCredito notaCredito) {
            return new DocumentoCpe(
                    TipoDocumentoCpe.NOTA_CREDITO,
                    notaCredito.getIdNotaCredito(),
                    notaCredito.getEstado(),
                    notaCredito.getSunatEstado() == null ? null : notaCredito.getSunatEstado().name(),
                    notaCredito.getSunatXmlKey(),
                    notaCredito.getSunatCdrKey());
        }

        static DocumentoCpe from(GuiaRemision guia) {
            return new DocumentoCpe(
                    TipoDocumentoCpe.GUIA_REMISION,
                    guia.getIdGuiaRemision(),
                    guia.getEstado(),
                    guia.getSunatEstado() == null ? null : guia.getSunatEstado().name(),
                    guia.getSunatXmlKey(),
                    guia.getSunatCdrKey());
        }
    }

    private enum TipoDocumentoCpe {
        VENTA,
        NOTA_CREDITO,
        GUIA_REMISION
    }
}
