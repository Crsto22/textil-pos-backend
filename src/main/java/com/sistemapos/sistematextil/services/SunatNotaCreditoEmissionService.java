package com.sistemapos.sistematextil.services;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.NotaCreditoDetalle;
import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.NotaCreditoDetalleRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;
import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatEmissionResult;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;
import com.sistemapos.sistematextil.util.sunat.SunatSoapFaultException;

import lombok.RequiredArgsConstructor;
import org.w3c.dom.Document;

/**
 * Orquesta el flujo completo de Nota de Crédito (tipo 07) con SUNAT:
 * 1. Construir XML CreditNote UBL 2.1
 * 2. Firmar con certificado digital
 * 3. Comprimir en ZIP
 * 4. Almacenar XML y ZIP en S3
 * 5. Enviar via SOAP sendBill → obtener CDR (síncrono)
 * 6. Parsear CDR y almacenar
 * 7. Actualizar estado en BD
 *
 * Sigue las mismas convenciones que DefaultSunatEmissionService.
 */
@Service
@RequiredArgsConstructor
public class SunatNotaCreditoEmissionService {

    private static final Logger log = LoggerFactory.getLogger(SunatNotaCreditoEmissionService.class);

    private final SunatProperties sunatProperties;
    private final SunatConfigRepository sunatConfigRepository;
    private final SunatNotaCreditoXmlBuilderService sunatNotaCreditoXmlBuilderService;
    private final SunatXmlSignatureService sunatXmlSignatureService;
    private final SunatDocumentStorageService sunatDocumentStorageService;
    private final SunatSoapClientService sunatSoapClientService;
    private final SunatCdrParserService sunatCdrParserService;
    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoDetalleRepository notaCreditoDetalleRepository;

    // ─── EMITIR NOTA DE CRÉDITO ──────────────────────────────────────

    @Transactional
    public SunatEmissionResult emitir(NotaCredito nc) {
        String mode = sunatProperties.normalizedMode();

        if ("DISABLED".equals(mode)) {
            String xmlName = sunatNotaCreditoXmlBuilderService.construirNombreArchivoXml(nc);
            nc.setSunatXmlNombre(xmlName);
            nc.setSunatEstado("PENDIENTE");
            nc.setSunatMensaje("Integración SUNAT deshabilitada. La nota de crédito queda pendiente de envío.");
            notaCreditoRepository.save(nc);

            return new SunatEmissionResult(
                    SunatEstado.PENDIENTE, null,
                    "Integración SUNAT deshabilitada. La nota de crédito queda pendiente de envío.",
                    null, null, xmlName, null, null, null, null, null, null, null);
        }

        if ("SIMULATED".equals(mode)) {
            return emitirSimulado(nc);
        }

        if (isRealMode(mode)) {
            return emitirReal(nc);
        }

        LocalDateTime now = LocalDateTime.now();
        return new SunatEmissionResult(
                SunatEstado.ERROR, "CONFIG",
                "Modo SUNAT no soportado: " + mode,
                null, null,
                sunatNotaCreditoXmlBuilderService.construirNombreArchivoXml(nc),
                null, null, null, null, null, now, now);
    }

    private SunatEmissionResult emitirSimulado(NotaCredito nc) {
        LocalDateTime now = LocalDateTime.now();
        String xmlName = sunatNotaCreditoXmlBuilderService.construirNombreArchivoXml(nc);
        String ticket = "SIM-NC-" + nc.getIdNotaCredito() + "-"
                + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        nc.setSunatEstado("ACEPTADO");
        nc.setSunatCodigo("0");
        nc.setSunatMensaje("Nota de crédito aceptada en modo simulado.");
        nc.setSunatHash(ticket);
        nc.setSunatXmlNombre(xmlName);
        nc.setSunatEnviadoAt(now);
        nc.setSunatRespondidoAt(now);
        notaCreditoRepository.save(nc);

        log.info("Nota de crédito {} simulada: ticket={}", sunatNotaCreditoXmlBuilderService.numeroComprobante(nc), ticket);

        return new SunatEmissionResult(
                SunatEstado.ACEPTADO, "0",
                "Nota de crédito aceptada en modo simulado.",
                ticket, null, xmlName, null, null, null, null, null, now, now);
    }

    private SunatEmissionResult emitirReal(NotaCredito nc) {
        String xmlName = sunatNotaCreditoXmlBuilderService.construirNombreArchivoXml(nc);
        String zipName = sunatNotaCreditoXmlBuilderService.construirNombreArchivoZip(nc);
        String signatureId = "SIGN-" + nc.getSucursal().getEmpresa().getRuc().trim();

        try {
            Venta ventaRef = nc.getVentaReferencia();
            SunatConfig config = resolveConfig(ventaRef);
            log.info("SUNAT emitir nota de crédito real: xmlName={}, zipName={}, endpoint={}",
                    xmlName, zipName, config.getUrlBillService());

            List<NotaCreditoDetalle> detalles = notaCreditoDetalleRepository
                    .findByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(nc.getIdNotaCredito());

            // 1. Construir XML CreditNote
            Document xmlDocument = sunatNotaCreditoXmlBuilderService.build(nc, detalles);

            // 2. Firmar
            SunatXmlSignatureService.SignedXml signedXml = sunatXmlSignatureService.sign(xmlDocument, config, signatureId);
            log.info("XML NC firmado: {} bytes, digestValue={}", signedXml.bytes().length, signedXml.digestValue());

            // 3. ZIP
            byte[] zipBytes = zip(xmlName, signedXml.bytes());
            log.info("ZIP NC generado: {} bytes", zipBytes.length);

            // 4. Almacenar XML y ZIP en S3
            SunatDocumentStorageService.StoredUploadPair stored = sunatDocumentStorageService
                    .storeXmlAndZip(ventaRef, xmlName, signedXml.bytes(), zipName, zipBytes);

            nc.setSunatXmlNombre(stored.xml().fileName());
            nc.setSunatXmlKey(stored.xml().key());
            nc.setSunatZipNombre(stored.zip().fileName());
            nc.setSunatZipKey(stored.zip().key());

            // 5. Enviar vía SOAP sendBill (síncrono, devuelve CDR)
            LocalDateTime sentAt = LocalDateTime.now();
            nc.setSunatEnviadoAt(sentAt);

            try {
                SunatSoapClientService.SendBillResponse soapResponse = sunatSoapClientService
                        .sendBill(config, zipName, zipBytes);

                // 6. Parsear CDR
                SunatCdrResult cdrResult = sunatCdrParserService.parse(soapResponse.cdrZipBytes());
                log.info("SUNAT CDR NC: estado={}, codigo={}, mensaje={}",
                        cdrResult.estado(), cdrResult.codigo(), cdrResult.mensaje());
                LocalDateTime respondedAt = LocalDateTime.now();

                nc.setSunatEstado(cdrResult.estado().name());
                nc.setSunatCodigo(cdrResult.codigo());
                nc.setSunatMensaje(cdrResult.mensaje());
                nc.setSunatHash(signedXml.digestValue());
                nc.setSunatRespondidoAt(respondedAt);

                // 7. Almacenar CDR
                SunatDocumentStorageService.StoredDocument cdrStored = null;
                String cdrXmlFileName = soapResponse.cdrZipFileName().replaceFirst("\\.zip$", ".xml");
                try {
                    cdrStored = sunatDocumentStorageService.storeCdr(ventaRef, cdrXmlFileName, cdrResult.xmlBytes());
                    nc.setSunatCdrNombre(cdrStored.fileName());
                    nc.setSunatCdrKey(cdrStored.key());
                } catch (RuntimeException storageError) {
                    nc.setSunatMensaje(nc.getSunatMensaje() + " | CDR recibido pero no se pudo guardar en S3");
                    log.warn("CDR NC recibido pero no se pudo guardar en S3: {}", storageError.getMessage());
                }

                notaCreditoRepository.save(nc);

                return new SunatEmissionResult(
                        cdrResult.estado(),
                        cdrResult.codigo(),
                        nc.getSunatMensaje(),
                        signedXml.digestValue(),
                        null,
                        xmlName,
                        stored.xml().key(),
                        zipName,
                        stored.zip().key(),
                        cdrStored != null ? cdrStored.fileName() : soapResponse.cdrZipFileName(),
                        cdrStored != null ? cdrStored.key() : null,
                        sentAt,
                        respondedAt);

            } catch (SunatSoapFaultException e) {
                log.error("SUNAT SOAP Fault en sendBill NC: code={}, message={}", e.getCode(), e.getMessage());
                LocalDateTime respondedAt = LocalDateTime.now();

                nc.setSunatEstado("RECHAZADO");
                nc.setSunatCodigo(e.getCode());
                nc.setSunatMensaje(e.getMessage());
                nc.setSunatHash(signedXml.digestValue());
                nc.setSunatRespondidoAt(respondedAt);
                notaCreditoRepository.save(nc);

                return new SunatEmissionResult(
                        SunatEstado.RECHAZADO,
                        e.getCode(),
                        e.getMessage(),
                        signedXml.digestValue(),
                        null,
                        xmlName,
                        stored.xml().key(),
                        zipName,
                        stored.zip().key(),
                        null, null,
                        sentAt,
                        respondedAt);

            } catch (RuntimeException e) {
                log.error("Error enviando NC a SUNAT: {}", e.getMessage(), e);
                LocalDateTime respondedAt = LocalDateTime.now();
                String msg = e.getMessage() != null ? e.getMessage() : "No se pudo enviar la nota de crédito a SUNAT";

                nc.setSunatEstado("ERROR");
                nc.setSunatCodigo("ENVIO");
                nc.setSunatMensaje(msg);
                nc.setSunatHash(signedXml.digestValue());
                nc.setSunatRespondidoAt(respondedAt);
                notaCreditoRepository.save(nc);

                return new SunatEmissionResult(
                        SunatEstado.ERROR,
                        "ENVIO",
                        msg,
                        signedXml.digestValue(),
                        null,
                        xmlName,
                        stored.xml().key(),
                        zipName,
                        stored.zip().key(),
                        null, null,
                        sentAt,
                        respondedAt);
            }

        } catch (RuntimeException e) {
            LocalDateTime now = LocalDateTime.now();
            String msg = e.getMessage() != null ? e.getMessage() : "No se pudo preparar la nota de crédito para SUNAT";

            nc.setSunatEstado("ERROR");
            nc.setSunatCodigo("CONFIG");
            nc.setSunatMensaje(msg);
            notaCreditoRepository.save(nc);

            return new SunatEmissionResult(
                    SunatEstado.ERROR,
                    "CONFIG",
                    msg,
                    null, null,
                    xmlName, null,
                    zipName, null,
                    null, null,
                    now, now);
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────

    private SunatConfig resolveConfig(Venta venta) {
        if (venta == null
                || venta.getSucursal() == null
                || venta.getSucursal().getEmpresa() == null
                || venta.getSucursal().getEmpresa().getIdEmpresa() == null) {
            throw new RuntimeException("La venta referenciada no tiene empresa asociada");
        }

        if (!Boolean.TRUE.equals(venta.getSucursal().getEmpresa().getGeneraFacturacionElectronica())) {
            throw new RuntimeException("La empresa tiene deshabilitada la facturación electrónica");
        }

        List<SunatConfig> configs = sunatConfigRepository
                .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSunatConfigAsc(
                        venta.getSucursal().getEmpresa().getIdEmpresa());
        if (configs.isEmpty()) {
            throw new RuntimeException("No hay configuración SUNAT registrada");
        }
        if (configs.size() > 1) {
            throw new RuntimeException("Existe más de una configuración SUNAT activa");
        }

        SunatConfig config = configs.get(0);
        if (!"ACTIVO".equalsIgnoreCase(config.getActivo())) {
            throw new RuntimeException("La configuración SUNAT está inactiva");
        }
        if (config.getUrlBillService() == null || config.getUrlBillService().isBlank()) {
            throw new RuntimeException("La configuración SUNAT no tiene urlBillService");
        }
        if (config.getCertificadoUrl() == null || config.getCertificadoUrl().isBlank()) {
            throw new RuntimeException("La configuración SUNAT no tiene certificado digital");
        }
        return config;
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
            throw new RuntimeException("No se pudo comprimir el XML de nota de crédito para SUNAT");
        }
    }

    private boolean isRealMode(String mode) {
        return "REAL".equals(mode)
                || "LIVE".equals(mode)
                || "ENABLED".equals(mode)
                || "PRODUCCION".equals(mode)
                || "PRODUCTION".equals(mode);
    }
}
