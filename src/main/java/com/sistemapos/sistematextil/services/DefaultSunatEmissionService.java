package com.sistemapos.sistematextil.services;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;
import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;
import com.sistemapos.sistematextil.util.sunat.SunatEmissionResult;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;
import com.sistemapos.sistematextil.util.sunat.SunatSoapFaultException;

import lombok.RequiredArgsConstructor;
import org.w3c.dom.Document;

@Service
@RequiredArgsConstructor
public class DefaultSunatEmissionService implements SunatEmissionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSunatEmissionService.class);
    private static final DateTimeFormatter TICKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SunatProperties sunatProperties;
    private final SunatConfigRepository sunatConfigRepository;
    private final SunatXmlBuilderService sunatXmlBuilderService;
    private final SunatXmlSignatureService sunatXmlSignatureService;
    private final SunatDocumentStorageService sunatDocumentStorageService;
    private final SunatSoapClientService sunatSoapClientService;
    private final SunatCdrParserService sunatCdrParserService;

    @Override
    public SunatEmissionResult emitir(Venta venta, List<VentaDetalle> detalles) {
        String mode = sunatProperties.normalizedMode();

        if ("DISABLED".equals(mode)) {
            return new SunatEmissionResult(
                    SunatEstado.PENDIENTE,
                    null,
                    "Integracion SUNAT deshabilitada. La venta queda pendiente de envio electronico.",
                    null,
                    null,
                    SunatComprobanteHelper.construirNombreArchivoXml(venta),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        if ("SIMULATED".equals(mode)) {
            return emitirSimulado(venta, detalles);
        }

        if (isRealMode(mode)) {
            return emitirReal(venta, detalles);
        }

        LocalDateTime now = LocalDateTime.now();
        return new SunatEmissionResult(
                SunatEstado.ERROR,
                "CONFIG",
                "Modo SUNAT no soportado: " + mode,
                null,
                null,
                SunatComprobanteHelper.construirNombreArchivoXml(venta),
                null,
                null,
                null,
                null,
                null,
                now,
                now);
    }

    private SunatEmissionResult emitirSimulado(Venta venta, List<VentaDetalle> detalles) {
        LocalDateTime now = LocalDateTime.now();
        String resumen = SunatComprobanteHelper.construirCadenaResumen(venta, detalles);
        String hash = SunatComprobanteHelper.generarHashBase64(resumen);
        String ticket = "SIM-" + venta.getIdVenta() + "-" + now.format(TICKET_FORMAT);

        return new SunatEmissionResult(
                SunatEstado.ACEPTADO,
                "0",
                "Comprobante aceptado en modo simulado.",
                hash,
                ticket,
                SunatComprobanteHelper.construirNombreArchivoXml(venta),
                null,
                null,
                null,
                null,
                null,
                now,
                now);
    }

    private SunatEmissionResult emitirReal(Venta venta, List<VentaDetalle> detalles) {
        String xmlName = SunatComprobanteHelper.construirNombreArchivoXml(venta);
        String zipName = SunatComprobanteHelper.construirNombreArchivoZip(venta);
        String signatureId = "SIGN-" + venta.getSucursal().getEmpresa().getRuc().trim();

        try {
            SunatConfig config = resolveConfig(venta);
            log.info("SUNAT emision real: xmlName={}, zipName={}, endpoint={}",
                    xmlName, zipName, config.getUrlBillService());

            Document xmlDocument = sunatXmlBuilderService.build(venta, detalles);
            SunatXmlSignatureService.SignedXml signedXml = sunatXmlSignatureService.sign(xmlDocument, config, signatureId);
            log.info("XML firmado: {} bytes, digestValue={}", signedXml.bytes().length, signedXml.digestValue());

            byte[] zipBytes = zip(xmlName, signedXml.bytes());
            log.info("ZIP generado: {} bytes, entryName={}", zipBytes.length, xmlName);
            SunatDocumentStorageService.StoredUploadPair stored = sunatDocumentStorageService
                    .storeXmlAndZip(venta, xmlName, signedXml.bytes(), zipName, zipBytes);

            LocalDateTime sentAt = LocalDateTime.now();
            try {
                SunatSoapClientService.SendBillResponse soapResponse = sunatSoapClientService
                        .sendBill(config, zipName, zipBytes);
                SunatCdrResult cdrResult = sunatCdrParserService.parse(soapResponse.cdrZipBytes());
                log.info("SUNAT CDR: estado={}, codigo={}, mensaje={}",
                        cdrResult.estado(), cdrResult.codigo(), cdrResult.mensaje());
                LocalDateTime respondedAt = LocalDateTime.now();

                SunatDocumentStorageService.StoredDocument cdrStored = null;
                String cdrMessage = cdrResult.mensaje();
                String cdrXmlFileName = soapResponse.cdrZipFileName().replaceFirst("\\.zip$", ".xml");
                try {
                    cdrStored = sunatDocumentStorageService.storeCdr(
                            venta,
                            cdrXmlFileName,
                            cdrResult.xmlBytes());
                } catch (RuntimeException storageError) {
                    cdrMessage = cdrMessage + " | CDR recibido pero no se pudo guardar en S3";
                }

                return new SunatEmissionResult(
                        cdrResult.estado(),
                        cdrResult.codigo(),
                        cdrMessage,
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
                log.error("SUNAT SOAP Fault: code={}, message={}", e.getCode(), e.getMessage());
                LocalDateTime respondedAt = LocalDateTime.now();
                return new SunatEmissionResult(
                        SunatEstado.RECHAZADO,
                        normalizarCodigo(e.getCode()),
                        normalizarMensaje(e.getMessage(), "SUNAT rechazo el comprobante"),
                        signedXml.digestValue(),
                        null,
                        xmlName,
                        stored.xml().key(),
                        zipName,
                        stored.zip().key(),
                        null,
                        null,
                        sentAt,
                        respondedAt);
            } catch (RuntimeException e) {
                log.error("Error en envio SUNAT: {}", e.getMessage(), e);
                LocalDateTime respondedAt = LocalDateTime.now();
                return new SunatEmissionResult(
                        SunatEstado.ERROR,
                        "ENVIO",
                        normalizarMensaje(e.getMessage(), "No se pudo enviar el comprobante a SUNAT"),
                        signedXml.digestValue(),
                        null,
                        xmlName,
                        stored.xml().key(),
                        zipName,
                        stored.zip().key(),
                        null,
                        null,
                        sentAt,
                        respondedAt);
            }
        } catch (RuntimeException e) {
            LocalDateTime now = LocalDateTime.now();
            return new SunatEmissionResult(
                    SunatEstado.ERROR,
                    "CONFIG",
                    normalizarMensaje(e.getMessage(), "No se pudo preparar el comprobante para SUNAT"),
                    null,
                    null,
                    xmlName,
                    null,
                    zipName,
                    null,
                    null,
                    null,
                    now,
                    now);
        }
    }

    private SunatConfig resolveConfig(Venta venta) {
        if (venta == null
                || venta.getSucursal() == null
                || venta.getSucursal().getEmpresa() == null
                || venta.getSucursal().getEmpresa().getIdEmpresa() == null) {
            throw new RuntimeException("La venta no tiene empresa asociada para emitir en SUNAT");
        }

        if (!Boolean.TRUE.equals(venta.getSucursal().getEmpresa().getGeneraFacturacionElectronica())) {
            throw new RuntimeException("La empresa tiene deshabilitada la facturacion electronica");
        }

        List<SunatConfig> configs = sunatConfigRepository
                .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSunatConfigAsc(
                        venta.getSucursal().getEmpresa().getIdEmpresa());
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

    private byte[] zip(String entryName, byte[] bytes) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(bytes);
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo comprimir el XML para SUNAT");
        }
    }

    private boolean isRealMode(String mode) {
        return "REAL".equals(mode)
                || "LIVE".equals(mode)
                || "ENABLED".equals(mode)
                || "PRODUCCION".equals(mode)
                || "PRODUCTION".equals(mode);
    }

    private String normalizarCodigo(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim();
    }

    private String normalizarMensaje(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.trim();
    }
}
