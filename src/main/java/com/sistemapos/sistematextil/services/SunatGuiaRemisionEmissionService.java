package com.sistemapos.sistematextil.services;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.model.GuiaRemisionConductor;
import com.sistemapos.sistematextil.model.GuiaRemisionDetalle;
import com.sistemapos.sistematextil.model.GuiaRemisionDocumentoRelacionado;
import com.sistemapos.sistematextil.model.GuiaRemisionTransportista;
import com.sistemapos.sistematextil.model.GuiaRemisionVehiculo;
import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.repositories.GuiaRemisionConductorRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionDetalleRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionDocumentoRelacionadoRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionTransportistaRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionVehiculoRepository;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;
import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

import org.w3c.dom.Document;

@Service
public class SunatGuiaRemisionEmissionService {

    private static final Logger log = LoggerFactory.getLogger(SunatGuiaRemisionEmissionService.class);
    private static final DateTimeFormatter TICKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");
    private static final String ESTADO_ACEPTADA = "ACEPTADA";
    private static final String ESTADO_RECHAZADA = "RECHAZADA";

    private final SunatProperties sunatProperties;
    private final SunatConfigRepository sunatConfigRepository;
    private final SunatGuiaRemisionXmlBuilderService xmlBuilderService;
    private final SunatXmlSignatureService sunatXmlSignatureService;
    private final SunatDocumentStorageService sunatDocumentStorageService;
    private final SunatRestApiClientService sunatRestApiClientService;
    private final SunatCdrParserService sunatCdrParserService;
    private final SunatErrorClassifierService sunatErrorClassifierService;
    private final GuiaRemisionDetalleRepository detalleRepository;
    private final GuiaRemisionDocumentoRelacionadoRepository documentoRelacionadoRepository;
    private final GuiaRemisionConductorRepository conductorRepository;
    private final GuiaRemisionTransportistaRepository transportistaRepository;
    private final GuiaRemisionVehiculoRepository vehiculoRepository;

    public SunatGuiaRemisionEmissionService(
            SunatProperties sunatProperties,
            SunatConfigRepository sunatConfigRepository,
            SunatGuiaRemisionXmlBuilderService xmlBuilderService,
            SunatXmlSignatureService sunatXmlSignatureService,
            SunatDocumentStorageService sunatDocumentStorageService,
            SunatRestApiClientService sunatRestApiClientService,
            SunatCdrParserService sunatCdrParserService,
            SunatErrorClassifierService sunatErrorClassifierService,
            GuiaRemisionDetalleRepository detalleRepository,
            GuiaRemisionDocumentoRelacionadoRepository documentoRelacionadoRepository,
            GuiaRemisionConductorRepository conductorRepository,
            GuiaRemisionTransportistaRepository transportistaRepository,
            GuiaRemisionVehiculoRepository vehiculoRepository) {
        this.sunatProperties = sunatProperties;
        this.sunatConfigRepository = sunatConfigRepository;
        this.xmlBuilderService = xmlBuilderService;
        this.sunatXmlSignatureService = sunatXmlSignatureService;
        this.sunatDocumentStorageService = sunatDocumentStorageService;
        this.sunatRestApiClientService = sunatRestApiClientService;
        this.sunatCdrParserService = sunatCdrParserService;
        this.sunatErrorClassifierService = sunatErrorClassifierService;
        this.detalleRepository = detalleRepository;
        this.documentoRelacionadoRepository = documentoRelacionadoRepository;
        this.conductorRepository = conductorRepository;
        this.transportistaRepository = transportistaRepository;
        this.vehiculoRepository = vehiculoRepository;
    }

    public void emitir(GuiaRemision guia) {
        String mode = sunatProperties.normalizedMode();

        if ("DISABLED".equals(mode)) {
            guia.setSunatEstado(SunatEstado.ERROR_DEFINITIVO);
            guia.setSunatCodigo("DISABLED");
            guia.setSunatMensaje("Integracion SUNAT deshabilitada. Active la configuracion para emitir la guia.");
            return;
        }

        if ("SIMULATED".equals(mode)) {
            emitirSimulado(guia);
            return;
        }

        if (isRealMode(mode)) {
            emitirReal(guia);
            return;
        }

        guia.setSunatEstado(SunatEstado.ERROR_DEFINITIVO);
        guia.setSunatCodigo("CONFIG");
        guia.setSunatMensaje("Modo SUNAT no soportado: " + mode);
        guia.setSunatRespondidoAt(nowLima());
    }

    private void emitirSimulado(GuiaRemision guia) {
        LocalDateTime now = nowLima();
        String hash = com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper.generarHashBase64(
                SunatGuiaRemisionXmlBuilderService.numeroGuia(guia));
        String ticket = "SIM-GR-" + guia.getIdGuiaRemision() + "-" + now.format(TICKET_FORMAT);

        guia.setEstado(ESTADO_ACEPTADA);
        guia.setSunatEstado(SunatEstado.ACEPTADO);
        guia.setSunatCodigo("0");
        guia.setSunatMensaje("Guia de remision aceptada en modo simulado.");
        guia.setSunatHash(hash);
        guia.setSunatTicket(ticket);
        guia.setSunatXmlNombre(construirNombreArchivoXml(guia));
        guia.setSunatEnviadoAt(now);
        guia.setSunatRespondidoAt(now);
    }

    private void emitirReal(GuiaRemision guia) {
        String xmlName = construirNombreArchivoXml(guia);
        String zipName = construirNombreArchivoZip(guia);
        String ruc = guia.getSucursal() != null && guia.getSucursal().getEmpresa() != null
                ? guia.getSucursal().getEmpresa().getRuc().trim() : "SINRUC";
        String signatureId = "SIGN-" + ruc;

        try {
            SunatConfig config = resolveConfig(guia);
            log.info("SUNAT GRE emision real: xmlName={}, zipName={}", xmlName, zipName);

            List<GuiaRemisionDetalle> detalles = detalleRepository
                    .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaRemisionDetalleAsc(
                            guia.getIdGuiaRemision());
            List<GuiaRemisionDocumentoRelacionado> documentosRelacionados = documentoRelacionadoRepository
                    .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaDocumentoRelacionadoAsc(
                            guia.getIdGuiaRemision());
            List<GuiaRemisionConductor> conductores = conductorRepository
                    .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                            guia.getIdGuiaRemision());
            List<GuiaRemisionTransportista> transportistas = transportistaRepository
                    .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(guia.getIdGuiaRemision());
            List<GuiaRemisionVehiculo> vehiculos = vehiculoRepository
                    .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(
                            guia.getIdGuiaRemision());

            // 1. Build XML
            Document xmlDocument = xmlBuilderService.build(
                    guia, detalles, documentosRelacionados, conductores, transportistas, vehiculos);

            // 2. Sign XML
            SunatXmlSignatureService.SignedXml signedXml = sunatXmlSignatureService.sign(xmlDocument, config,
                    signatureId);
            log.info("XML GRE firmado: {} bytes, digestValue={}", signedXml.bytes().length, signedXml.digestValue());

            // 3. Zip
            byte[] zipBytes = zip(xmlName, signedXml.bytes());
            log.info("ZIP GRE generado: {} bytes", zipBytes.length);

            // 4. Store XML on local disk
            SunatDocumentStorageService.StoredDocument storedXml = sunatDocumentStorageService
                    .storeGuiaRemisionXml(guia, xmlName, signedXml.bytes());

            guia.setSunatXmlNombre(normalizeText(xmlName));
            guia.setSunatXmlKey(normalizeText(storedXml.key()));
            guia.setSunatZipNombre(normalizeText(zipName));
            guia.setSunatHash(signedXml.digestValue());

            LocalDateTime sentAt = nowLima();
            guia.setSunatEnviadoAt(sentAt);

            // 5. Obtain token
            SunatRestApiClientService.TokenResponse tokenResponse = sunatRestApiClientService.obtenerToken(config);

            // 6. Send ZIP via REST API
            try {
                SunatRestApiClientService.SendCpeResponse cpeResponse = sunatRestApiClientService
                        .enviarCpe(config, tokenResponse.accessToken(), zipName, zipBytes);

                guia.setSunatTicket(normalizeText(cpeResponse.numTicket()));
                guia.setSunatCodigo(normalizeText(cpeResponse.codRespuesta()));

                // 7. If ticket received, consult it for the CDR
                if (cpeResponse.numTicket() != null && !cpeResponse.numTicket().isBlank()) {
                    try {
                        Thread.sleep(2000); // Wait for SUNAT to process
                        SunatRestApiClientService.TicketResponse ticketResponse = sunatRestApiClientService
                                .consultarTicket(config, tokenResponse.accessToken(), cpeResponse.numTicket());

                        if (ticketResponse.cdrBytes() != null && ticketResponse.cdrBytes().length > 0) {
                            procesarCdr(guia, ticketResponse.cdrBytes());
                        } else {
                            guia.setSunatEstado(SunatEstado.PENDIENTE_CDR);
                            guia.setSunatMensaje(
                                    "Guia enviada a SUNAT. Ticket: " + cpeResponse.numTicket()
                                            + ". CDR aun no disponible, consultar mas tarde.");
                        }
                    } catch (Exception ticketEx) {
                        log.warn("No se pudo consultar ticket GRE {}: {}",
                                cpeResponse.numTicket(), ticketEx.getMessage());
                        guia.setSunatEstado(SunatEstado.PENDIENTE_CDR);
                        guia.setSunatMensaje("Guia enviada a SUNAT. Ticket: " + cpeResponse.numTicket()
                                + ". Consultar CDR manualmente.");
                    }
                } else {
                    guia.setSunatEstado(SunatEstado.PENDIENTE_CDR);
                    guia.setSunatMensaje("Guia enviada a SUNAT. Sin ticket en respuesta.");
                }

                guia.setSunatRespondidoAt(nowLima());

            } catch (RuntimeException e) {
                log.error("Error al enviar GRE a SUNAT: {}", e.getMessage(), e);
                guia.setSunatEstado(sunatErrorClassifierService.classify(e.getMessage()));
                guia.setSunatCodigo("SEND_ERROR");
                guia.setSunatMensaje(normalizeText(e.getMessage()));
                guia.setSunatRespondidoAt(nowLima());
            }

        } catch (RuntimeException e) {
            log.error("Error en emision GRE: {}", e.getMessage(), e);
            guia.setSunatEstado(SunatEstado.ERROR_DEFINITIVO);
            guia.setSunatCodigo("EXCEPTION");
            guia.setSunatMensaje(normalizeText(e.getMessage()));
            guia.setSunatRespondidoAt(nowLima());
        }
    }

    private void procesarCdr(GuiaRemision guia, byte[] cdrBytes) {
        try {
            SunatCdrResult cdrResult = sunatCdrParserService.parse(cdrBytes);

            String cdrZipName = "R-" + construirNombreArchivoZip(guia);
            SunatDocumentStorageService.StoredDocument storedCdr = sunatDocumentStorageService
                    .storeGuiaRemisionCdr(guia, cdrZipName, cdrBytes);

            guia.setSunatEstado(cdrResult.estado());
            guia.setSunatCodigo(normalizeText(cdrResult.codigo()));
            guia.setSunatMensaje(normalizeText(cdrResult.mensaje()));
            guia.setSunatCdrNombre(normalizeText(cdrZipName));
            guia.setSunatCdrKey(normalizeText(storedCdr.key()));

            if (cdrResult.estado() == SunatEstado.ACEPTADO) {
                guia.setEstado(ESTADO_ACEPTADA);
            } else if (cdrResult.estado() == SunatEstado.RECHAZADO) {
                guia.setEstado(ESTADO_RECHAZADA);
            }

            log.info("CDR GRE procesado: estado={}, codigo={}", cdrResult.estado(), cdrResult.codigo());
        } catch (Exception e) {
            log.warn("No se pudo procesar CDR GRE: {}", e.getMessage());
            guia.setSunatEstado(SunatEstado.PENDIENTE_CDR);
            guia.setSunatMensaje("Guia enviada pero no se pudo procesar el CDR: " + e.getMessage());
        }
    }

    public void consultarTicketYActualizar(GuiaRemision guia) {
        if (guia.getSunatTicket() == null || guia.getSunatTicket().isBlank()) {
            throw new RuntimeException("La guia no tiene ticket SUNAT para consultar");
        }

        try {
            SunatConfig config = resolveConfig(guia);
            SunatRestApiClientService.TokenResponse tokenResponse = sunatRestApiClientService.obtenerToken(config);
            SunatRestApiClientService.TicketResponse ticketResponse = sunatRestApiClientService
                    .consultarTicket(config, tokenResponse.accessToken(), guia.getSunatTicket());

            if (ticketResponse.cdrBytes() != null && ticketResponse.cdrBytes().length > 0) {
                procesarCdr(guia, ticketResponse.cdrBytes());
            } else {
                guia.setSunatEstado(SunatEstado.PENDIENTE_CDR);
                guia.setSunatMensaje("CDR aun no disponible. Estado ticket: " + ticketResponse.codRespuesta());
            }
            guia.setSunatRespondidoAt(nowLima());
        } catch (RuntimeException e) {
            SunatEstado estadoError = sunatErrorClassifierService.classify(e.getMessage());
            guia.setSunatEstado(estadoError == SunatEstado.ERROR_DEFINITIVO
                    ? SunatEstado.ERROR_DEFINITIVO
                    : SunatEstado.PENDIENTE_CDR);
            guia.setSunatMensaje("Error al consultar ticket: " + e.getMessage());
            guia.setSunatRespondidoAt(nowLima());
        }
    }

    private SunatConfig resolveConfig(GuiaRemision guia) {
        final Integer idEmpresa = (guia.getSucursal() != null && guia.getSucursal().getEmpresa() != null)
                ? guia.getSucursal().getEmpresa().getIdEmpresa()
                : null;
        if (idEmpresa == null) {
            throw new RuntimeException("No se pudo determinar la empresa para buscar configuracion SUNAT");
        }
        return sunatConfigRepository.findByEmpresa_IdEmpresaAndDeletedAtIsNullAndActivo(idEmpresa, "ACTIVO")
                .orElseThrow(() -> new RuntimeException(
                        "No hay configuracion SUNAT activa para la empresa " + idEmpresa));
    }

    private byte[] zip(String fileName, byte[] content) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(fileName));
            zos.write(content);
            zos.closeEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo comprimir el XML de la guia de remision");
        }
    }

    private boolean isRealMode(String mode) {
        return "REAL".equals(mode) || "PRODUCTION".equals(mode) || "BETA".equals(mode);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private LocalDateTime nowLima() {
        return LocalDateTime.now(LIMA_ZONE);
    }

    public static String construirNombreArchivoXml(GuiaRemision guia) {
        return construirBaseNombreArchivo(guia) + ".xml";
    }

    public static String construirNombreArchivoZip(GuiaRemision guia) {
        return construirBaseNombreArchivo(guia) + ".zip";
    }

    private static String construirBaseNombreArchivo(GuiaRemision guia) {
        String ruc = guia.getSucursal() != null && guia.getSucursal().getEmpresa() != null
                ? valorTexto(guia.getSucursal().getEmpresa().getRuc())
                : "SINRUC";
        return ruc + "-09-" + SunatGuiaRemisionXmlBuilderService.numeroGuia(guia);
    }

    private static String valorTexto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }
}
