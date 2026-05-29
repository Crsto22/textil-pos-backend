package com.sistemapos.sistematextil.services;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.NotaCreditoDetalle;
import com.sistemapos.sistematextil.model.SunatBajaItem;
import com.sistemapos.sistematextil.model.SunatBajaLote;
import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoDetalleRepository;
import com.sistemapos.sistematextil.repositories.SunatBajaItemRepository;
import com.sistemapos.sistematextil.repositories.SunatBajaLoteRepository;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.sunat.SunatBajaEstado;
import com.sistemapos.sistematextil.util.sunat.SunatBajaResult;
import com.sistemapos.sistematextil.util.sunat.SunatBajaTipo;
import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;
import com.sistemapos.sistematextil.util.sunat.SunatSoapFaultException;
import com.sistemapos.sistematextil.util.notacredito.NotaCreditoBajaResponse;
import com.sistemapos.sistematextil.util.venta.VentaAnulacionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatBajaService {

    private static final String ESTADO_EMITIDA = "EMITIDA";
    private static final String ESTADO_ANULADA = "ANULADA";
    private static final String ESTADO_NC_EMITIDA = "NC_EMITIDA";
    private static final String TIPO_ANULACION_SUNAT = "SUNAT_BAJA";
    private static final String TIPO_FACTURA = "FACTURA";
    private static final String TIPO_BOLETA = "BOLETA";
    private static final String TIPO_NC_FACTURA = "NOTA_CREDITO_FACTURA";
    private static final String TIPO_NC_BOLETA = "NOTA_CREDITO_BOLETA";
    private static final String CODIGO_SUNAT_YA_ANULADO = "SUNAT_YA_ANULADO";
    private static final String MENSAJE_SUNAT_YA_ANULADO = "SUNAT indica que la boleta ya fue informada y se encuentra anulada o rechazada. Se marca como anulada localmente.";
    private static final Pattern TICKET_PATTERN = Pattern.compile("ticket\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Set<SunatEstado> ESTADOS_SUNAT_VALIDOS = EnumSet.of(SunatEstado.ACEPTADO, SunatEstado.OBSERVADO);
    private static final Set<SunatBajaEstado> ESTADOS_BAJA_BLOQUEANTES = EnumSet.of(
            SunatBajaEstado.PENDIENTE_ENVIO,
            SunatBajaEstado.ENVIANDO,
            SunatBajaEstado.PENDIENTE_CDR,
            SunatBajaEstado.PENDIENTE,
            SunatBajaEstado.ERROR_TRANSITORIO,
            SunatBajaEstado.ACEPTADO,
            SunatBajaEstado.OBSERVADO);

    private final SunatProperties sunatProperties;
    private final SunatBajaLoteRepository sunatBajaLoteRepository;
    private final SunatBajaItemRepository sunatBajaItemRepository;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoDetalleRepository notaCreditoDetalleRepository;
    private final SunatConfigRepository sunatConfigRepository;
    private final SunatBajaXmlBuilderService sunatBajaXmlBuilderService;
    private final SunatXmlSignatureService sunatXmlSignatureService;
    private final SunatDocumentStorageService sunatDocumentStorageService;
    private final SunatSoapClientService sunatSoapClientService;
    private final SunatCdrParserService sunatCdrParserService;
    private final SunatErrorClassifierService sunatErrorClassifierService;
    private final SunatJobService sunatJobService;
    private final StockMovimientoService stockMovimientoService;
    private final SunatConfigValidationService sunatConfigValidationService;

    @Transactional
    public VentaAnulacionResponse solicitarBaja(Venta venta, String motivo, Usuario usuarioAutenticado) {
        validarVentaDisponibleParaBaja(venta);
        validarCertificadoSunatParaOperacion(venta);

        SunatBajaTipo tipoEnvio = resolverTipoEnvio(venta);
        validarDatosParaEnvioSunat(venta, tipoEnvio);
        SunatBajaLote lote = obtenerOCrearLote(venta, tipoEnvio);

        SunatBajaItem item = new SunatBajaItem();
        item.setLote(lote);
        item.setVenta(venta);
        item.setTipoComprobante(normalizarTexto(venta.getTipoComprobante(), 20));
        item.setSerie(normalizarTexto(venta.getSerie(), 10));
        item.setCorrelativo(venta.getCorrelativo());
        item.setFechaDocumento(venta.getFecha().toLocalDate());
        item.setMotivo(normalizarTexto(motivo, 255));
        sunatBajaItemRepository.save(item);

        LocalDateTime now = LocalDateTime.now();
        venta.setTipoAnulacion(TIPO_ANULACION_SUNAT);
        venta.setMotivoAnulacion(item.getMotivo());
        venta.setUsuarioAnulacion(usuarioAutenticado);
        venta.setSunatBajaEstado(SunatBajaEstado.PENDIENTE_ENVIO);
        venta.setSunatBajaCodigo(null);
        venta.setSunatBajaMensaje("Solicitud de baja registrada. Pendiente de envio a SUNAT.");
        venta.setSunatBajaTicket(null);
        venta.setSunatBajaTipo(tipoEnvio);
        venta.setSunatBajaLote(lote);
        venta.setSunatBajaSolicitadaAt(now);
        venta.setSunatBajaRespondidaAt(null);
        ventaRepository.save(venta);

        if (lote.getEstado() == null || lote.getEstado() == SunatBajaEstado.PENDIENTE_ENVIO) {
            lote.setEstado(SunatBajaEstado.PENDIENTE_ENVIO);
            lote.setMensaje("Lote pendiente de envio a SUNAT.");
            sunatBajaLoteRepository.save(lote);
        }

        sunatJobService.enqueueBajaLote(lote.getIdSunatBajaLote());
        return toResponse(venta, lote, false, "Solicitud de baja enviada a la cola SUNAT.");
    }

    @Transactional
    public NotaCreditoBajaResponse solicitarBaja(NotaCredito notaCredito, String motivo, Usuario usuarioAutenticado) {
        validarNotaCreditoDisponibleParaBaja(notaCredito);
        validarCertificadoSunatParaOperacion(notaCredito);

        SunatBajaTipo tipoEnvio = resolverTipoEnvio(notaCredito);
        validarDatosParaEnvioSunat(notaCredito, tipoEnvio);
        SunatBajaLote lote = obtenerOCrearLote(notaCredito, tipoEnvio);

        SunatBajaItem item = new SunatBajaItem();
        item.setLote(lote);
        item.setNotaCredito(notaCredito);
        item.setTipoComprobante(normalizarTexto(notaCredito.getTipoComprobante(), 20));
        item.setSerie(normalizarTexto(notaCredito.getSerie(), 10));
        item.setCorrelativo(notaCredito.getCorrelativo());
        item.setFechaDocumento(notaCredito.getFecha().toLocalDate());
        item.setMotivo(normalizarTexto(motivo, 255));
        sunatBajaItemRepository.save(item);

        LocalDateTime now = LocalDateTime.now();
        notaCredito.setTipoAnulacion(TIPO_ANULACION_SUNAT);
        notaCredito.setMotivoAnulacion(item.getMotivo());
        notaCredito.setUsuarioAnulacion(usuarioAutenticado);
        notaCredito.setSunatBajaEstado(SunatBajaEstado.PENDIENTE_ENVIO);
        notaCredito.setSunatBajaCodigo(null);
        notaCredito.setSunatBajaMensaje("Solicitud de baja registrada. Pendiente de envio a SUNAT.");
        notaCredito.setSunatBajaTicket(null);
        notaCredito.setSunatBajaTipo(tipoEnvio);
        notaCredito.setSunatBajaLote(lote);
        notaCredito.setSunatBajaSolicitadaAt(now);
        notaCredito.setSunatBajaRespondidaAt(null);
        notaCreditoRepository.save(notaCredito);

        if (lote.getEstado() == null || lote.getEstado() == SunatBajaEstado.PENDIENTE_ENVIO) {
            lote.setEstado(SunatBajaEstado.PENDIENTE_ENVIO);
            lote.setMensaje("Lote pendiente de envio a SUNAT.");
            sunatBajaLoteRepository.save(lote);
        }

        sunatJobService.enqueueBajaLote(lote.getIdSunatBajaLote());
        return toResponse(notaCredito, lote, false, "Solicitud de baja de nota de credito enviada a la cola SUNAT.");
    }

    public void procesarLoteEnCola(Integer idSunatBajaLote) {
        SunatBajaLote lote = obtenerLote(idSunatBajaLote);
        List<SunatBajaItem> items = obtenerItems(lote.getIdSunatBajaLote());
        lote.setEstado(SunatBajaEstado.ENVIANDO);
        lote.setCodigo(null);
        lote.setMensaje("Procesando envio del lote de baja a SUNAT.");
        sunatBajaLoteRepository.save(lote);
        actualizarDocumentosDesdeLote(items, lote, SunatBajaEstado.ENVIANDO, null, lote.getMensaje(), lote.getTicketSunat(), null);

        SunatBajaResult result = emitir(lote, items);
        aplicarResultadoLote(lote, items, result);
        consultarCdrAutomaticamenteSiHayTicket(lote, items, result);
    }

    public void consultarTicketPendiente(Integer idSunatBajaLote) {
        SunatBajaLote lote = obtenerLote(idSunatBajaLote);
        List<SunatBajaItem> items = obtenerItems(lote.getIdSunatBajaLote());
        SunatBajaResult result = consultarEstado(lote, items);
        aplicarResultadoLote(lote, items, result);
    }

    @Transactional
    public VentaAnulacionResponse consultarBajaPorVenta(Venta venta) {
        if (venta == null || venta.getIdVenta() == null) {
            throw new RuntimeException("Venta no valida para consultar baja SUNAT");
        }
        SunatBajaLote lote = venta.getSunatBajaLote();
        if (lote == null || lote.getIdSunatBajaLote() == null) {
            throw new RuntimeException("La venta no tiene lote de baja SUNAT asociado");
        }
        String ticket = primerTextoNoVacio(
                lote.getTicketSunat(),
                extraerTicket(lote.getMensaje()),
                extraerTicket(venta.getSunatBajaMensaje()));
        if (ticket == null) {
            throw new RuntimeException("El lote de baja no tiene ticket SUNAT para consultar");
        }
        lote.setTicketSunat(ticket);

        List<SunatBajaItem> items = obtenerItems(lote.getIdSunatBajaLote());
        SunatBajaResult result = consultarEstado(lote, items);
        aplicarResultadoLote(lote, items, result);

        Venta actualizada = ventaRepository.findByIdVentaAndDeletedAtIsNull(venta.getIdVenta()).orElse(venta);
        boolean stockDevuelto = result.estado() == SunatBajaEstado.ACEPTADO
                || result.estado() == SunatBajaEstado.OBSERVADO;
        return toResponse(actualizada, lote, stockDevuelto, "Consulta del ticket de baja SUNAT finalizada.");
    }

    @Transactional
    public NotaCreditoBajaResponse consultarBajaPorNotaCredito(NotaCredito notaCredito) {
        if (notaCredito == null || notaCredito.getIdNotaCredito() == null) {
            throw new RuntimeException("Nota de credito no valida para consultar baja SUNAT");
        }
        SunatBajaLote lote = notaCredito.getSunatBajaLote();
        if (lote == null || lote.getIdSunatBajaLote() == null) {
            throw new RuntimeException("La nota de credito no tiene lote de baja SUNAT asociado");
        }
        String ticket = primerTextoNoVacio(
                lote.getTicketSunat(),
                extraerTicket(lote.getMensaje()),
                extraerTicket(notaCredito.getSunatBajaMensaje()));
        if (ticket == null) {
            throw new RuntimeException("El lote de baja no tiene ticket SUNAT para consultar");
        }
        lote.setTicketSunat(ticket);

        List<SunatBajaItem> items = obtenerItems(lote.getIdSunatBajaLote());
        SunatBajaResult result = consultarEstado(lote, items);
        aplicarResultadoLote(lote, items, result);

        NotaCredito actualizada = notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(notaCredito.getIdNotaCredito())
                .orElse(notaCredito);
        boolean stockRevertido = result.estado() == SunatBajaEstado.ACEPTADO
                || result.estado() == SunatBajaEstado.OBSERVADO;
        return toResponse(actualizada, lote, stockRevertido, "Consulta del ticket de baja SUNAT finalizada.");
    }

    @Transactional
    public void marcarErrorDefinitivo(Integer idSunatBajaLote, String codigo, String mensaje, LocalDateTime now) {
        SunatBajaLote lote = sunatBajaLoteRepository.findByIdSunatBajaLoteAndDeletedAtIsNull(idSunatBajaLote)
                .orElse(null);
        if (lote == null) {
            return;
        }
        List<SunatBajaItem> items = obtenerItems(idSunatBajaLote);
        SunatBajaResult result = new SunatBajaResult(
                SunatBajaEstado.ERROR_DEFINITIVO,
                normalizarTexto(codigo, 20),
                normalizarTexto(mensaje, 500),
                lote.getSunatHash(),
                lote.getTicketSunat(),
                lote.getSunatXmlNombre(),
                lote.getSunatXmlKey(),
                lote.getSunatZipNombre(),
                lote.getSunatCdrNombre(),
                lote.getSunatCdrKey(),
                lote.getSunatEnviadoAt(),
                now);
        aplicarResultadoLote(lote, items, result);
    }

    private SunatBajaResult emitir(SunatBajaLote lote, List<SunatBajaItem> items) {
        String mode = sunatProperties.normalizedMode();
        if ("DISABLED".equals(mode)) {
            LocalDateTime now = LocalDateTime.now();
            return new SunatBajaResult(
                    SunatBajaEstado.ERROR_DEFINITIVO,
                    "DISABLED",
                    "Integracion SUNAT deshabilitada. Active la configuracion para emitir la baja.",
                    lote.getSunatHash(),
                    lote.getTicketSunat(),
                    SunatComprobanteHelper.construirNombreArchivoXml(lote),
                    lote.getSunatXmlKey(),
                    SunatComprobanteHelper.construirNombreArchivoZip(lote),
                    lote.getSunatCdrNombre(),
                    lote.getSunatCdrKey(),
                    now,
                    now);
        }
        if ("SIMULATED".equals(mode)) {
            LocalDateTime now = LocalDateTime.now();
            return new SunatBajaResult(
                    SunatBajaEstado.ACEPTADO,
                    "0",
                    "Baja SUNAT aceptada en modo simulado.",
                    "SIM-BAJA-" + lote.getIdSunatBajaLote(),
                    "SIM-" + lote.getIdSunatBajaLote(),
                    SunatComprobanteHelper.construirNombreArchivoXml(lote),
                    lote.getSunatXmlKey(),
                    SunatComprobanteHelper.construirNombreArchivoZip(lote),
                    lote.getSunatCdrNombre(),
                    lote.getSunatCdrKey(),
                    now,
                    now);
        }
        if (!isRealMode(mode)) {
            LocalDateTime now = LocalDateTime.now();
            return new SunatBajaResult(
                    SunatBajaEstado.ERROR_DEFINITIVO,
                    "CONFIG",
                    "Modo SUNAT no soportado: " + mode,
                    lote.getSunatHash(),
                    lote.getTicketSunat(),
                    SunatComprobanteHelper.construirNombreArchivoXml(lote),
                    lote.getSunatXmlKey(),
                    SunatComprobanteHelper.construirNombreArchivoZip(lote),
                    lote.getSunatCdrNombre(),
                    lote.getSunatCdrKey(),
                    now,
                    now);
        }

        String xmlName = SunatComprobanteHelper.construirNombreArchivoXml(lote);
        String zipName = SunatComprobanteHelper.construirNombreArchivoZip(lote);
        try {
            SunatConfig config = resolveConfig(lote);
            Document xmlDocument = sunatBajaXmlBuilderService.build(lote, items);
            SunatXmlSignatureService.SignedXml signedXml = sunatXmlSignatureService
                    .sign(xmlDocument, config, "SIGN-" + lote.getEmpresa().getRuc().trim());
            byte[] zipBytes = zip(xmlName, signedXml.bytes());
            SunatDocumentStorageService.StoredDocument storedXml = sunatDocumentStorageService
                    .storeSunatBajaXml(lote, xmlName, signedXml.bytes());
            LocalDateTime sentAt = LocalDateTime.now();

            lote.setSunatHash(signedXml.digestValue());
            lote.setSunatXmlNombre(xmlName);
            lote.setSunatXmlKey(storedXml.key());
            lote.setSunatZipNombre(zipName);
            lote.setSunatEnviadoAt(sentAt);
            sunatBajaLoteRepository.save(lote);

            try {
                SunatSoapClientService.SendSummaryResponse response = sunatSoapClientService
                        .sendSummary(config, zipName, zipBytes);
                LocalDateTime respondedAt = LocalDateTime.now();
                String mensaje = "Lote de baja enviado a SUNAT. Ticket pendiente de procesamiento.";
                return new SunatBajaResult(
                        SunatBajaEstado.PENDIENTE_CDR,
                        normalizarTexto(response.ticket(), 20),
                        mensaje,
                        signedXml.digestValue(),
                        response.ticket(),
                        xmlName,
                        storedXml.key(),
                        zipName,
                        null,
                        null,
                        sentAt,
                        respondedAt);
            } catch (SunatSoapFaultException e) {
                LocalDateTime respondedAt = LocalDateTime.now();
                String duplicateTicket = extraerTicket(e.getMessage());
                if (duplicateTicket != null && esMensajeArchivoYaPresentado(e.getMessage())) {
                    return new SunatBajaResult(
                            SunatBajaEstado.PENDIENTE_CDR,
                            normalizarTexto(duplicateTicket, 20),
                            normalizarTexto("SUNAT indica que el resumen ya fue enviado. Se consultara el ticket existente.", 500),
                            signedXml.digestValue(),
                            duplicateTicket,
                            xmlName,
                            storedXml.key(),
                            zipName,
                            null,
                            null,
                            sentAt,
                            respondedAt);
                }
                if (esBoletaYaAnuladaORechazadaEnSunat(lote, items, e.getMessage())) {
                    return resultadoBoletaYaAnuladaORechazada(
                            e.getCode(),
                            e.getMessage(),
                            signedXml.digestValue(),
                            duplicateTicket,
                            xmlName,
                            storedXml.key(),
                            zipName,
                            null,
                            null,
                            sentAt,
                            respondedAt);
                }
                return new SunatBajaResult(
                        SunatBajaEstado.RECHAZADO,
                        normalizarTexto(e.getCode(), 20),
                        normalizarTexto(mensajeBajaSunat(e.getMessage()), 500),
                        signedXml.digestValue(),
                        null,
                        xmlName,
                        storedXml.key(),
                        zipName,
                        null,
                        null,
                        sentAt,
                        respondedAt);
            } catch (RuntimeException e) {
                LocalDateTime respondedAt = LocalDateTime.now();
                if (esBoletaYaAnuladaORechazadaEnSunat(lote, items, e.getMessage())) {
                    return resultadoBoletaYaAnuladaORechazada(
                            "ENVIO",
                            e.getMessage(),
                            signedXml.digestValue(),
                            primerTextoNoVacio(extraerTicket(e.getMessage()), lote.getTicketSunat()),
                            xmlName,
                            storedXml.key(),
                            zipName,
                            null,
                            null,
                            sentAt,
                            respondedAt);
                }
                SunatBajaEstado estadoError = SunatBajaEstado.fromSunatEstado(sunatErrorClassifierService.classify(e.getMessage()));
                return new SunatBajaResult(
                        estadoError,
                        "ENVIO",
                        normalizarTexto(e.getMessage(), 500),
                        signedXml.digestValue(),
                        lote.getTicketSunat(),
                        xmlName,
                        storedXml.key(),
                        zipName,
                        null,
                        null,
                        sentAt,
                        respondedAt);
            }
        } catch (RuntimeException e) {
            LocalDateTime now = LocalDateTime.now();
            return new SunatBajaResult(
                    SunatBajaEstado.ERROR_DEFINITIVO,
                    "CONFIG",
                    normalizarTexto(e.getMessage(), 500),
                    lote.getSunatHash(),
                    lote.getTicketSunat(),
                    xmlName,
                    lote.getSunatXmlKey(),
                    zipName,
                    lote.getSunatCdrNombre(),
                    lote.getSunatCdrKey(),
                    now,
                    now);
        }
    }

    private SunatBajaResult consultarEstado(SunatBajaLote lote, List<SunatBajaItem> items) {
        String mode = sunatProperties.normalizedMode();
        if ("SIMULATED".equals(mode)) {
            LocalDateTime now = LocalDateTime.now();
            return new SunatBajaResult(
                    SunatBajaEstado.ACEPTADO,
                    "0",
                    "Baja SUNAT aceptada en modo simulado.",
                    lote.getSunatHash(),
                    lote.getTicketSunat(),
                    lote.getSunatXmlNombre(),
                    lote.getSunatXmlKey(),
                    lote.getSunatZipNombre(),
                    lote.getSunatCdrNombre(),
                    lote.getSunatCdrKey(),
                    lote.getSunatEnviadoAt(),
                    now);
        }
        try {
            if (lote.getTicketSunat() == null || lote.getTicketSunat().isBlank()) {
                throw new RuntimeException("El lote de baja no tiene ticket SUNAT para consultar");
            }
            SunatConfig config = resolveConfig(lote);
            SunatSoapClientService.GetStatusResponse response = sunatSoapClientService
                    .getStatus(config, lote.getTicketSunat());
            LocalDateTime respondedAt = LocalDateTime.now();
            if ("98".equals(response.statusCode())) {
                return new SunatBajaResult(
                        SunatBajaEstado.PENDIENTE_CDR,
                        response.statusCode(),
                        "SUNAT aun no genera el CDR del lote de baja.",
                        lote.getSunatHash(),
                        lote.getTicketSunat(),
                        lote.getSunatXmlNombre(),
                        lote.getSunatXmlKey(),
                        lote.getSunatZipNombre(),
                        lote.getSunatCdrNombre(),
                        lote.getSunatCdrKey(),
                        lote.getSunatEnviadoAt(),
                        respondedAt);
            }
            if (response.cdrZipBytes() != null && response.cdrZipBytes().length > 0) {
                SunatCdrResult cdrResult = sunatCdrParserService.parse(response.cdrZipBytes());
                String cdrFileName = response.cdrZipFileName() == null || response.cdrZipFileName().isBlank()
                        ? SunatComprobanteHelper.construirNombreArchivoCdrZip(lote)
                        : response.cdrZipFileName();
                SunatDocumentStorageService.StoredDocument storedCdr = sunatDocumentStorageService
                        .storeSunatBajaCdr(lote, cdrFileName, response.cdrZipBytes());
                if (esBoletaYaAnuladaORechazadaEnSunat(lote, items, cdrResult.mensaje())) {
                    return resultadoBoletaYaAnuladaORechazada(
                            cdrResult.codigo(),
                            cdrResult.mensaje(),
                            lote.getSunatHash(),
                            lote.getTicketSunat(),
                            lote.getSunatXmlNombre(),
                            lote.getSunatXmlKey(),
                            lote.getSunatZipNombre(),
                            storedCdr.fileName(),
                            storedCdr.key(),
                            lote.getSunatEnviadoAt(),
                            respondedAt);
                }
                return new SunatBajaResult(
                        SunatBajaEstado.fromSunatEstado(cdrResult.estado()),
                        normalizarTexto(cdrResult.codigo(), 20),
                        normalizarTexto(cdrResult.mensaje(), 500),
                        lote.getSunatHash(),
                        lote.getTicketSunat(),
                        lote.getSunatXmlNombre(),
                        lote.getSunatXmlKey(),
                        lote.getSunatZipNombre(),
                        storedCdr.fileName(),
                        storedCdr.key(),
                        lote.getSunatEnviadoAt(),
                        respondedAt);
            }
            if (esBoletaYaAnuladaORechazadaEnSunat(lote, items, response.statusMessage())) {
                return resultadoBoletaYaAnuladaORechazada(
                        response.statusCode(),
                        response.statusMessage(),
                        lote.getSunatHash(),
                        lote.getTicketSunat(),
                        lote.getSunatXmlNombre(),
                        lote.getSunatXmlKey(),
                        lote.getSunatZipNombre(),
                        lote.getSunatCdrNombre(),
                        lote.getSunatCdrKey(),
                        lote.getSunatEnviadoAt(),
                        respondedAt);
            }
            return new SunatBajaResult(
                    SunatBajaEstado.RECHAZADO,
                    normalizarTexto(response.statusCode(), 20),
                    normalizarTexto(response.statusMessage(), 500),
                    lote.getSunatHash(),
                    lote.getTicketSunat(),
                    lote.getSunatXmlNombre(),
                    lote.getSunatXmlKey(),
                    lote.getSunatZipNombre(),
                    lote.getSunatCdrNombre(),
                    lote.getSunatCdrKey(),
                    lote.getSunatEnviadoAt(),
                    respondedAt);
        } catch (RuntimeException e) {
            LocalDateTime respondedAt = LocalDateTime.now();
            if (esBoletaYaAnuladaORechazadaEnSunat(lote, items, e.getMessage())) {
                return resultadoBoletaYaAnuladaORechazada(
                        "STATUS",
                        e.getMessage(),
                        lote.getSunatHash(),
                        primerTextoNoVacio(extraerTicket(e.getMessage()), lote.getTicketSunat()),
                        lote.getSunatXmlNombre(),
                        lote.getSunatXmlKey(),
                        lote.getSunatZipNombre(),
                        lote.getSunatCdrNombre(),
                        lote.getSunatCdrKey(),
                        lote.getSunatEnviadoAt(),
                        respondedAt);
            }
            SunatBajaEstado estadoError = SunatBajaEstado.fromSunatEstado(sunatErrorClassifierService.classify(e.getMessage()));
            return new SunatBajaResult(
                    estadoError,
                    "STATUS",
                    normalizarTexto(e.getMessage(), 500),
                    lote.getSunatHash(),
                    lote.getTicketSunat(),
                    lote.getSunatXmlNombre(),
                    lote.getSunatXmlKey(),
                    lote.getSunatZipNombre(),
                    lote.getSunatCdrNombre(),
                    lote.getSunatCdrKey(),
                    lote.getSunatEnviadoAt(),
                    respondedAt);
        }
    }

    private SunatBajaResult resultadoBoletaYaAnuladaORechazada(
            String codigo,
            String mensajeOriginal,
            String hash,
            String ticket,
            String xmlNombre,
            String xmlKey,
            String zipNombre,
            String cdrNombre,
            String cdrKey,
            LocalDateTime fechaEnvio,
            LocalDateTime fechaRespuesta) {
        String codigoNormalizado = normalizarTexto(codigo, 20);
        String ticketNormalizado = primerTextoNoVacio(ticket, extraerTicket(mensajeOriginal));
        return new SunatBajaResult(
                SunatBajaEstado.OBSERVADO,
                codigoNormalizado == null ? CODIGO_SUNAT_YA_ANULADO : codigoNormalizado,
                normalizarTexto(MENSAJE_SUNAT_YA_ANULADO, 500),
                hash,
                ticketNormalizado,
                xmlNombre,
                xmlKey,
                zipNombre,
                cdrNombre,
                cdrKey,
                fechaEnvio,
                fechaRespuesta);
    }

    private boolean esBoletaYaAnuladaORechazadaEnSunat(SunatBajaLote lote, List<SunatBajaItem> items, String mensaje) {
        if (lote == null || lote.getTipoEnvio() != SunatBajaTipo.RC || items == null || items.isEmpty()) {
            return false;
        }
        if (!esMensajeComprobanteYaAnuladoORechazado(mensaje)) {
            return false;
        }
        boolean todosSonBoletasVenta = items.stream().allMatch(item -> {
            Venta venta = item.getVenta();
            if (venta == null || item.getNotaCredito() != null) {
                return false;
            }
            String tipoVenta = normalizarTexto(venta.getTipoComprobante(), 20);
            String tipoItem = normalizarTexto(item.getTipoComprobante(), 20);
            return TIPO_BOLETA.equalsIgnoreCase(tipoVenta) && TIPO_BOLETA.equalsIgnoreCase(tipoItem);
        });
        if (!todosSonBoletasVenta) {
            return false;
        }
        if (items.size() == 1) {
            return true;
        }
        String normalized = mensaje.toLowerCase(Locale.ROOT);
        return items.stream().allMatch(item -> mensajeContieneReferenciaBoleta(normalized, item));
    }

    private boolean esMensajeComprobanteYaAnuladoORechazado(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return false;
        }
        String normalized = mensaje.toLowerCase(Locale.ROOT);
        return normalized.contains("comprobante ya fue informado")
                && (normalized.contains("anulado") || normalized.contains("rechazado"));
    }

    private boolean mensajeContieneReferenciaBoleta(String mensajeNormalizado, SunatBajaItem item) {
        if (mensajeNormalizado == null || item == null || item.getSerie() == null || item.getCorrelativo() == null) {
            return false;
        }
        String serie = item.getSerie().trim().toLowerCase(Locale.ROOT);
        String correlativo = String.valueOf(item.getCorrelativo());
        String correlativoPadded = String.format(Locale.ROOT, "%08d", item.getCorrelativo());
        return mensajeNormalizado.contains("03-" + serie + "-" + correlativo)
                || mensajeNormalizado.contains("03-" + serie + "-" + correlativoPadded);
    }

    @Transactional
    protected void aplicarResultadoLote(SunatBajaLote lote, List<SunatBajaItem> items, SunatBajaResult result) {
        lote.setEstado(result.estado());
        lote.setCodigo(normalizarTexto(result.codigo(), 20));
        lote.setMensaje(normalizarTexto(result.mensaje(), 500));
        lote.setTicketSunat(normalizarTexto(result.ticket(), 120));
        lote.setSunatHash(normalizarTexto(result.hash(), 120));
        lote.setSunatXmlNombre(normalizarTexto(result.xmlNombre(), 180));
        lote.setSunatXmlKey(normalizarTexto(result.xmlKey(), 600));
        lote.setSunatZipNombre(normalizarTexto(result.zipNombre(), 180));
        lote.setSunatCdrNombre(normalizarTexto(result.cdrNombre(), 180));
        lote.setSunatCdrKey(normalizarTexto(result.cdrKey(), 600));
        lote.setSunatEnviadoAt(result.fechaEnvio());
        lote.setSunatRespondidoAt(result.fechaRespuesta());
        sunatBajaLoteRepository.save(lote);

        LocalDateTime responseTime = result.fechaRespuesta() != null ? result.fechaRespuesta() : LocalDateTime.now();
        actualizarDocumentosDesdeLote(
                items,
                lote,
                result.estado(),
                result.codigo(),
                result.mensaje(),
                result.ticket(),
                responseTime);

        if (result.estado() == SunatBajaEstado.ACEPTADO || result.estado() == SunatBajaEstado.OBSERVADO) {
            for (SunatBajaItem item : items) {
                Venta venta = item.getVenta();
                NotaCredito notaCredito = item.getNotaCredito();
                if (venta != null && !ESTADO_ANULADA.equals(normalizarTexto(venta.getEstado(), 20))) {
                    revertirStock(venta);
                    venta.setEstado(ESTADO_ANULADA);
                    venta.setAnuladoAt(responseTime);
                    ventaRepository.save(venta);
                }
                if (notaCredito != null && !ESTADO_ANULADA.equals(normalizarTexto(notaCredito.getEstado(), 20))) {
                    revertirStockNotaCredito(notaCredito);
                    notaCredito.setEstado(ESTADO_ANULADA);
                    notaCredito.setAnuladoAt(responseTime);
                    notaCreditoRepository.save(notaCredito);
                }
            }
        }
    }

    private void actualizarDocumentosDesdeLote(
            List<SunatBajaItem> items,
            SunatBajaLote lote,
            SunatBajaEstado estado,
            String codigo,
            String mensaje,
            String ticket,
            LocalDateTime respondedAt) {
        for (SunatBajaItem item : items) {
            Venta venta = item.getVenta();
            if (venta != null) {
                venta.setSunatBajaEstado(estado);
                venta.setSunatBajaCodigo(normalizarTexto(codigo, 20));
                venta.setSunatBajaMensaje(normalizarTexto(mensaje, 500));
                venta.setSunatBajaTicket(normalizarTexto(ticket, 120));
                venta.setSunatBajaTipo(lote.getTipoEnvio());
                venta.setSunatBajaLote(lote);
                venta.setSunatBajaRespondidaAt(respondedAt);
                if (venta.getSunatBajaSolicitadaAt() == null) {
                    venta.setSunatBajaSolicitadaAt(LocalDateTime.now());
                }
                ventaRepository.save(venta);
            }

            NotaCredito notaCredito = item.getNotaCredito();
            if (notaCredito != null) {
                notaCredito.setSunatBajaEstado(estado);
                notaCredito.setSunatBajaCodigo(normalizarTexto(codigo, 20));
                notaCredito.setSunatBajaMensaje(normalizarTexto(mensaje, 500));
                notaCredito.setSunatBajaTicket(normalizarTexto(ticket, 120));
                notaCredito.setSunatBajaTipo(lote.getTipoEnvio());
                notaCredito.setSunatBajaLote(lote);
                notaCredito.setSunatBajaRespondidaAt(respondedAt);
                if (notaCredito.getSunatBajaSolicitadaAt() == null) {
                    notaCredito.setSunatBajaSolicitadaAt(LocalDateTime.now());
                }
                notaCreditoRepository.save(notaCredito);
            }
        }
    }

    private void consultarCdrAutomaticamenteSiHayTicket(
            SunatBajaLote lote,
            List<SunatBajaItem> items,
            SunatBajaResult result) {
        if (result == null || result.estado() != SunatBajaEstado.PENDIENTE_CDR) {
            return;
        }
        String ticket = primerTextoNoVacio(result.ticket(), lote.getTicketSunat());
        if (ticket == null) {
            return;
        }
        lote.setTicketSunat(ticket);
        SunatBajaResult consultaResult = consultarEstado(lote, items);
        aplicarResultadoLote(lote, items, consultaResult);
    }

    private void revertirStock(Venta venta) {
        List<VentaDetalle> detalles = ventaDetalleRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(venta.getIdVenta());
        if (detalles.isEmpty()) {
            throw new RuntimeException("La venta no tiene detalles para revertir stock en la baja SUNAT");
        }
        Integer idSucursal = venta.getSucursal() != null ? venta.getSucursal().getIdSucursal() : null;
        if (idSucursal == null) {
            throw new RuntimeException("La venta no tiene sucursal asociada");
        }
        for (VentaDetalle detalle : detalles) {
            Integer idProductoVariante = detalle.getProductoVariante() != null
                    ? detalle.getProductoVariante().getIdProductoVariante()
                    : null;
            if (idProductoVariante == null) {
                throw new RuntimeException("Uno de los detalles de venta no tiene variante de producto");
            }
            stockMovimientoService.incrementar(
                    idSucursal,
                    idProductoVariante,
                    valorEntero(detalle.getCantidad()),
                    HistorialStock.TipoMovimiento.DEVOLUCION,
                    "ANULACION SUNAT VENTA #" + venta.getIdVenta(),
                    venta.getUsuarioAnulacion());
        }
    }

    private void revertirStockNotaCredito(NotaCredito notaCredito) {
        if (!Boolean.TRUE.equals(notaCredito.getStockDevuelto())) {
            return;
        }
        List<NotaCreditoDetalle> detalles = notaCreditoDetalleRepository
                .findByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(notaCredito.getIdNotaCredito());
        if (detalles.isEmpty()) {
            throw new RuntimeException("La nota de credito no tiene detalles para revertir stock en la baja SUNAT");
        }
        Integer idSucursal = notaCredito.getSucursal() != null ? notaCredito.getSucursal().getIdSucursal() : null;
        if (idSucursal == null) {
            throw new RuntimeException("La nota de credito no tiene sucursal asociada");
        }
        for (NotaCreditoDetalle detalle : detalles) {
            Integer idProductoVariante = detalle.getProductoVariante() != null
                    ? detalle.getProductoVariante().getIdProductoVariante()
                    : null;
            if (idProductoVariante == null) {
                throw new RuntimeException("Uno de los detalles de la nota de credito no tiene variante de producto");
            }
            stockMovimientoService.descontar(
                    idSucursal,
                    idProductoVariante,
                    valorEntero(detalle.getCantidad()),
                    HistorialStock.TipoMovimiento.AJUSTE,
                    "BAJA SUNAT NOTA CREDITO #" + notaCredito.getIdNotaCredito(),
                    notaCredito.getUsuarioAnulacion());
        }
        notaCredito.setStockDevuelto(false);
    }

    private void validarVentaDisponibleParaBaja(Venta venta) {
        String estadoVenta = normalizarTexto(venta.getEstado(), 20);
        if (ESTADO_ANULADA.equals(estadoVenta)) {
            throw new RuntimeException("La venta ya se encuentra anulada");
        }
        if (ESTADO_NC_EMITIDA.equals(estadoVenta)) {
            throw new RuntimeException("La venta ya tiene una nota de credito emitida");
        }
        if (!ESTADO_EMITIDA.equals(estadoVenta)) {
            throw new RuntimeException("Solo se pueden dar de baja ventas en estado EMITIDA");
        }
        if (!ESTADOS_SUNAT_VALIDOS.contains(venta.getSunatEstado())) {
            throw new RuntimeException("La venta debe estar aceptada u observada por SUNAT antes de solicitar la baja");
        }
        if (venta.getSunatBajaEstado() != null && ESTADOS_BAJA_BLOQUEANTES.contains(venta.getSunatBajaEstado())) {
            throw new RuntimeException("La venta ya tiene una baja SUNAT en proceso o aceptada");
        }
        NotaCredito notaCredito = notaCreditoRepository.findTopByVentaReferencia_IdVentaAndDeletedAtIsNullOrderByIdNotaCreditoDesc(
                venta.getIdVenta()).orElse(null);
        if (notaCredito != null) {
            throw new RuntimeException("La venta ya tiene una nota de credito asociada y no puede darse de baja");
        }
    }

    private void validarNotaCreditoDisponibleParaBaja(NotaCredito notaCredito) {
        String estado = normalizarTexto(notaCredito.getEstado(), 20);
        if (ESTADO_ANULADA.equals(estado)) {
            throw new RuntimeException("La nota de credito ya se encuentra anulada");
        }
        if (!ESTADO_EMITIDA.equals(estado)) {
            throw new RuntimeException("Solo se pueden dar de baja notas de credito en estado EMITIDA");
        }
        if (!ESTADOS_SUNAT_VALIDOS.contains(notaCredito.getSunatEstado())) {
            throw new RuntimeException("La nota de credito debe estar aceptada u observada por SUNAT antes de solicitar la baja");
        }
        if (notaCredito.getSunatBajaEstado() != null && ESTADOS_BAJA_BLOQUEANTES.contains(notaCredito.getSunatBajaEstado())) {
            throw new RuntimeException("La nota de credito ya tiene una baja SUNAT en proceso o aceptada");
        }
        if (sunatBajaItemRepository.existsByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(notaCredito.getIdNotaCredito())) {
            throw new RuntimeException("La nota de credito ya tiene un item de baja SUNAT asociado");
        }
    }

    private void validarDatosParaEnvioSunat(Venta venta, SunatBajaTipo tipoEnvio) {
        String tipoComprobante = normalizarTexto(venta.getTipoComprobante(), 20);
        if (TIPO_FACTURA.equals(tipoComprobante) && tipoEnvio != SunatBajaTipo.RA) {
            throw new RuntimeException("La factura debe darse de baja con comunicacion RA.");
        }
        if (TIPO_BOLETA.equals(tipoComprobante) && tipoEnvio != SunatBajaTipo.RC) {
            throw new RuntimeException("La boleta debe anularse con resumen diario RC, no con RA.");
        }
        if (venta.getFecha() == null) {
            throw new RuntimeException("La venta no tiene fecha de emision para generar la baja SUNAT");
        }
        if (venta.getSerie() == null || venta.getSerie().isBlank()) {
            throw new RuntimeException("La venta no tiene serie para generar la baja SUNAT");
        }
        if (venta.getCorrelativo() == null || venta.getCorrelativo() < 1) {
            throw new RuntimeException("La venta no tiene correlativo valido para generar la baja SUNAT");
        }

        String serie = venta.getSerie().trim().toUpperCase();
        if (TIPO_FACTURA.equals(tipoComprobante) && !serie.startsWith("F")) {
            throw new RuntimeException("La factura debe tener una serie electronica que inicie con F.");
        }
        if (TIPO_BOLETA.equals(tipoComprobante) && !serie.startsWith("B")) {
            throw new RuntimeException("La boleta debe tener una serie electronica que inicie con B.");
        }

        if (!tieneEvidenciaEnvioGem(venta)) {
            throw new RuntimeException(
                    "No se puede enviar la baja por GEM: la venta no tiene XML/CDR SUNAT generado por este sistema. "
                            + "Si el comprobante fue emitido desde SUNAT Portal, App, OSE u otro proveedor, debe anularse en ese mismo canal.");
        }
    }

    private void validarDatosParaEnvioSunat(NotaCredito notaCredito, SunatBajaTipo tipoEnvio) {
        String tipoComprobante = normalizarTexto(notaCredito.getTipoComprobante(), 20);
        if (TIPO_NC_FACTURA.equals(tipoComprobante) && tipoEnvio != SunatBajaTipo.RA) {
            throw new RuntimeException("La nota de credito de factura debe darse de baja con comunicacion RA.");
        }
        if (TIPO_NC_BOLETA.equals(tipoComprobante) && tipoEnvio != SunatBajaTipo.RC) {
            throw new RuntimeException("La nota de credito de boleta debe anularse con resumen diario RC, no con RA.");
        }
        if (notaCredito.getFecha() == null) {
            throw new RuntimeException("La nota de credito no tiene fecha de emision para generar la baja SUNAT");
        }
        if (notaCredito.getSerie() == null || notaCredito.getSerie().isBlank()) {
            throw new RuntimeException("La nota de credito no tiene serie para generar la baja SUNAT");
        }
        if (notaCredito.getCorrelativo() == null || notaCredito.getCorrelativo() < 1) {
            throw new RuntimeException("La nota de credito no tiene correlativo valido para generar la baja SUNAT");
        }

        String serie = notaCredito.getSerie().trim().toUpperCase();
        if (TIPO_NC_FACTURA.equals(tipoComprobante) && !serie.startsWith("F")) {
            throw new RuntimeException("La nota de credito de factura debe tener una serie electronica que inicie con F.");
        }
        if (TIPO_NC_BOLETA.equals(tipoComprobante) && !serie.startsWith("B")) {
            throw new RuntimeException("La nota de credito de boleta debe tener una serie electronica que inicie con B.");
        }

        if (!tieneEvidenciaEnvioGem(notaCredito)) {
            throw new RuntimeException(
                    "No se puede enviar la baja por GEM: la nota de credito no tiene XML/CDR SUNAT generado por este sistema. "
                            + "Si fue emitida desde SUNAT Portal, App, OSE u otro proveedor, debe anularse en ese mismo canal.");
        }
    }

    private SunatBajaLote obtenerOCrearLote(Venta venta, SunatBajaTipo tipoEnvio) {
        Integer idEmpresa = venta.getSucursal() != null && venta.getSucursal().getEmpresa() != null
                ? venta.getSucursal().getEmpresa().getIdEmpresa()
                : null;
        if (idEmpresa == null) {
            throw new RuntimeException("La venta no tiene empresa asociada para gestionar la baja SUNAT");
        }
        LocalDate fechaDocumento = venta.getFecha().toLocalDate();
        LocalDate fechaGeneracion = LocalDate.now();
        List<SunatBajaLote> drafts = sunatBajaLoteRepository.findDraftLotes(
                idEmpresa,
                tipoEnvio,
                fechaDocumento,
                fechaGeneracion,
                PageRequest.of(0, 1));
        if (!drafts.isEmpty()) {
            return drafts.get(0);
        }
        Integer maxCorrelativo = sunatBajaLoteRepository.findMaxCorrelativoByEmpresaAndTipoAndFechaGeneracion(
                idEmpresa,
                tipoEnvio,
                fechaGeneracion);
        Integer siguienteIdLote = sunatBajaLoteRepository.findNextAutoIncrement();
        SunatBajaLote lote = new SunatBajaLote();
        lote.setEmpresa(venta.getSucursal().getEmpresa());
        lote.setTipoEnvio(tipoEnvio);
        lote.setFechaDocumento(fechaDocumento);
        lote.setFechaGeneracion(fechaGeneracion);
        lote.setCorrelativo(Math.max(
                (maxCorrelativo == null ? 0 : maxCorrelativo) + 1,
                siguienteIdLote == null ? 1 : siguienteIdLote));
        lote.setEstado(SunatBajaEstado.PENDIENTE_ENVIO);
        return sunatBajaLoteRepository.save(lote);
    }

    private SunatBajaLote obtenerOCrearLote(NotaCredito notaCredito, SunatBajaTipo tipoEnvio) {
        Integer idEmpresa = notaCredito.getSucursal() != null && notaCredito.getSucursal().getEmpresa() != null
                ? notaCredito.getSucursal().getEmpresa().getIdEmpresa()
                : null;
        if (idEmpresa == null) {
            throw new RuntimeException("La nota de credito no tiene empresa asociada para gestionar la baja SUNAT");
        }
        LocalDate fechaDocumento = notaCredito.getFecha().toLocalDate();
        LocalDate fechaGeneracion = LocalDate.now();
        List<SunatBajaLote> drafts = sunatBajaLoteRepository.findDraftLotes(
                idEmpresa,
                tipoEnvio,
                fechaDocumento,
                fechaGeneracion,
                PageRequest.of(0, 1));
        if (!drafts.isEmpty()) {
            return drafts.get(0);
        }
        Integer maxCorrelativo = sunatBajaLoteRepository.findMaxCorrelativoByEmpresaAndTipoAndFechaGeneracion(
                idEmpresa,
                tipoEnvio,
                fechaGeneracion);
        Integer siguienteIdLote = sunatBajaLoteRepository.findNextAutoIncrement();
        SunatBajaLote lote = new SunatBajaLote();
        lote.setEmpresa(notaCredito.getSucursal().getEmpresa());
        lote.setTipoEnvio(tipoEnvio);
        lote.setFechaDocumento(fechaDocumento);
        lote.setFechaGeneracion(fechaGeneracion);
        lote.setCorrelativo(Math.max(
                (maxCorrelativo == null ? 0 : maxCorrelativo) + 1,
                siguienteIdLote == null ? 1 : siguienteIdLote));
        lote.setEstado(SunatBajaEstado.PENDIENTE_ENVIO);
        return sunatBajaLoteRepository.save(lote);
    }

    private void validarCertificadoSunatParaOperacion(Venta venta) {
        Integer idEmpresa = venta != null
                && venta.getSucursal() != null
                && venta.getSucursal().getEmpresa() != null
                        ? venta.getSucursal().getEmpresa().getIdEmpresa()
                        : null;
        sunatConfigValidationService.validarCertificadoParaOperacionSunat(idEmpresa);
    }

    private void validarCertificadoSunatParaOperacion(NotaCredito notaCredito) {
        Integer idEmpresa = notaCredito != null
                && notaCredito.getSucursal() != null
                && notaCredito.getSucursal().getEmpresa() != null
                        ? notaCredito.getSucursal().getEmpresa().getIdEmpresa()
                        : null;
        sunatConfigValidationService.validarCertificadoParaOperacionSunat(idEmpresa);
    }

    private SunatBajaTipo resolverTipoEnvio(Venta venta) {
        String tipoComprobante = normalizarTexto(venta.getTipoComprobante(), 20);
        if (TIPO_FACTURA.equals(tipoComprobante)) {
            return SunatBajaTipo.RA;
        }
        if (TIPO_BOLETA.equals(tipoComprobante)) {
            return SunatBajaTipo.RC;
        }
        throw new RuntimeException("El tipo de comprobante no soporta baja SUNAT: " + tipoComprobante);
    }

    private SunatBajaTipo resolverTipoEnvio(NotaCredito notaCredito) {
        String tipoComprobante = normalizarTexto(notaCredito.getTipoComprobante(), 20);
        if (TIPO_NC_FACTURA.equals(tipoComprobante)) {
            return SunatBajaTipo.RA;
        }
        if (TIPO_NC_BOLETA.equals(tipoComprobante)) {
            return SunatBajaTipo.RC;
        }
        throw new RuntimeException("El tipo de nota de credito no soporta baja SUNAT: " + tipoComprobante);
    }

    private SunatConfig resolveConfig(SunatBajaLote lote) {
        Integer idEmpresa = lote.getEmpresa() != null ? lote.getEmpresa().getIdEmpresa() : null;
        if (idEmpresa == null) {
            throw new RuntimeException("El lote de baja no tiene empresa asociada");
        }
        List<SunatConfig> configs = sunatConfigRepository.findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSunatConfigAsc(idEmpresa);
        if (configs.isEmpty()) {
            throw new RuntimeException("No hay configuracion SUNAT registrada");
        }
        if (configs.size() > 1) {
            throw new RuntimeException("Existe mas de una configuracion SUNAT activa. Depure la tabla sunat_config");
        }
        SunatConfig config = configs.get(0);
        if (!"ACTIVO".equalsIgnoreCase(config.getActivo())) {
            throw new RuntimeException("La configuracion SUNAT esta inactiva");
        }
        if (config.getUrlBillService() == null || config.getUrlBillService().isBlank()) {
            throw new RuntimeException("La configuracion SUNAT no tiene urlBillService");
        }
        if (config.getCertificadoUrl() == null || config.getCertificadoUrl().isBlank()) {
            throw new RuntimeException("La configuracion SUNAT no tiene certificado digital");
        }
        return config;
    }

    private SunatBajaLote obtenerLote(Integer idSunatBajaLote) {
        return sunatBajaLoteRepository.findByIdSunatBajaLoteAndDeletedAtIsNull(idSunatBajaLote)
                .orElseThrow(() -> new RuntimeException("Lote de baja SUNAT con ID " + idSunatBajaLote + " no encontrado"));
    }

    private List<SunatBajaItem> obtenerItems(Integer idSunatBajaLote) {
        List<SunatBajaItem> items = sunatBajaItemRepository
                .findByLote_IdSunatBajaLoteAndDeletedAtIsNullOrderByIdSunatBajaItemAsc(idSunatBajaLote);
        if (items.isEmpty()) {
            throw new RuntimeException("El lote de baja no tiene items asociados");
        }
        return items;
    }

    private VentaAnulacionResponse toResponse(Venta venta, SunatBajaLote lote, boolean stockDevuelto, String message) {
        return new VentaAnulacionResponse(
                venta.getIdVenta(),
                SunatComprobanteHelper.numeroComprobante(venta),
                venta.getTipoComprobante(),
                venta.getEstado(),
                venta.getTipoAnulacion(),
                venta.getMotivoAnulacion(),
                venta.getAnuladoAt(),
                stockDevuelto,
                venta.getSunatBajaEstado(),
                venta.getSunatBajaCodigo(),
                venta.getSunatBajaMensaje(),
                venta.getSunatBajaTicket(),
                lote == null ? null : SunatComprobanteHelper.numeroLoteSunat(lote),
                message);
    }

    private NotaCreditoBajaResponse toResponse(NotaCredito notaCredito, SunatBajaLote lote, boolean stockRevertido, String message) {
        return new NotaCreditoBajaResponse(
                notaCredito.getIdNotaCredito(),
                SunatComprobanteHelper.numeroComprobante(notaCredito),
                notaCredito.getTipoComprobante(),
                notaCredito.getEstado(),
                notaCredito.getTipoAnulacion(),
                notaCredito.getMotivoAnulacion(),
                notaCredito.getAnuladoAt(),
                stockRevertido,
                notaCredito.getSunatBajaEstado(),
                notaCredito.getSunatBajaCodigo(),
                notaCredito.getSunatBajaMensaje(),
                notaCredito.getSunatBajaTicket(),
                lote == null ? null : SunatComprobanteHelper.numeroLoteSunat(lote),
                message);
    }

    private byte[] zip(String entryName, byte[] bytes) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(bytes);
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo comprimir el XML de baja para SUNAT");
        }
    }

    private boolean isRealMode(String mode) {
        return "REAL".equals(mode) || "PRODUCTION".equals(mode) || "BETA".equals(mode);
    }

    private boolean tieneEvidenciaEnvioGem(Venta venta) {
        return !isBlank(venta.getSunatXmlNombre())
                && !isBlank(venta.getSunatZipNombre())
                && !isBlank(venta.getSunatCdrNombre());
    }

    private boolean tieneEvidenciaEnvioGem(NotaCredito notaCredito) {
        return !isBlank(notaCredito.getSunatXmlNombre())
                && !isBlank(notaCredito.getSunatZipNombre())
                && !isBlank(notaCredito.getSunatCdrNombre());
    }

    private String mensajeBajaSunat(String mensajeSunat) {
        String mensaje = mensajeSunat == null ? "" : mensajeSunat.trim();
        String normalized = mensaje.toLowerCase();
        if (normalized.contains("no pertenece a gem")) {
            return mensaje
                    + ". La boleta existe en SUNAT, pero no pertenece al canal GEM/Sistemas del Contribuyente de este backend. "
                    + "Debe anularse desde el canal donde fue emitida: SUNAT Portal/App, OSE u otro proveedor.";
        }
        return mensaje;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean esMensajeArchivoYaPresentado(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return false;
        }
        String normalized = mensaje.toLowerCase(Locale.ROOT);
        return (normalized.contains("ya fue enviado") || normalized.contains("ya fue presentado"))
                && (normalized.contains("archivo") || normalized.contains("resumen"));
    }

    private String extraerTicket(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return null;
        }
        Matcher matcher = TICKET_PATTERN.matcher(mensaje);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String primerTextoNoVacio(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizarTexto(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
    }
}
