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
import org.w3c.dom.Document;

import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.NotaCreditoDetalle;
import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.repositories.NotaCreditoDetalleRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;
import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;
import com.sistemapos.sistematextil.util.sunat.SunatEmissionResult;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;
import com.sistemapos.sistematextil.util.sunat.SunatSoapFaultException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatNotaCreditoEmissionService {

    private static final Logger log = LoggerFactory.getLogger(SunatNotaCreditoEmissionService.class);
    private static final DateTimeFormatter TICKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SunatProperties sunatProperties;
    private final SunatConfigRepository sunatConfigRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoDetalleRepository notaCreditoDetalleRepository;
    private final SunatNotaCreditoXmlBuilderService sunatNotaCreditoXmlBuilderService;
    private final SunatXmlSignatureService sunatXmlSignatureService;
    private final SunatDocumentStorageService sunatDocumentStorageService;
    private final SunatSoapClientService sunatSoapClientService;
    private final SunatCdrParserService sunatCdrParserService;

    public SunatEmissionResult emitir(Integer idNotaCredito) {
        NotaCredito notaCredito = notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(idNotaCredito)
                .orElseThrow(() -> new RuntimeException("Nota de credito con ID " + idNotaCredito + " no encontrada"));
        List<NotaCreditoDetalle> detalles = notaCreditoDetalleRepository
                .findByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(notaCredito.getIdNotaCredito());

        String mode = sunatProperties.normalizedMode();
        if ("DISABLED".equals(mode)) {
            return emitirDeshabilitado(notaCredito);
        }
        if ("SIMULATED".equals(mode)) {
            return emitirSimulado(notaCredito);
        }
        if (isRealMode(mode)) {
            return emitirReal(notaCredito, detalles);
        }

        LocalDateTime now = LocalDateTime.now();
        notaCredito.setSunatEstado(SunatEstado.ERROR);
        notaCredito.setSunatCodigo("CONFIG");
        notaCredito.setSunatMensaje("Modo SUNAT no soportado: " + mode);
        notaCredito.setSunatRespondidoAt(now);
        notaCreditoRepository.save(notaCredito);
        return new SunatEmissionResult(
                SunatEstado.ERROR,
                "CONFIG",
                notaCredito.getSunatMensaje(),
                notaCredito.getSunatHash(),
                notaCredito.getSunatTicket(),
                SunatComprobanteHelper.construirNombreArchivoXml(notaCredito),
                notaCredito.getSunatXmlKey(),
                SunatComprobanteHelper.construirNombreArchivoZip(notaCredito),
                notaCredito.getSunatZipKey(),
                notaCredito.getSunatCdrNombre(),
                notaCredito.getSunatCdrKey(),
                now,
                now);
    }

    private SunatEmissionResult emitirDeshabilitado(NotaCredito notaCredito) {
        notaCredito.setSunatEstado(SunatEstado.PENDIENTE);
        notaCredito.setSunatMensaje("Integracion SUNAT deshabilitada. La nota de credito queda pendiente de envio.");
        notaCredito.setSunatXmlNombre(SunatComprobanteHelper.construirNombreArchivoXml(notaCredito));
        notaCreditoRepository.save(notaCredito);
        return new SunatEmissionResult(
                SunatEstado.PENDIENTE,
                null,
                notaCredito.getSunatMensaje(),
                notaCredito.getSunatHash(),
                notaCredito.getSunatTicket(),
                notaCredito.getSunatXmlNombre(),
                notaCredito.getSunatXmlKey(),
                notaCredito.getSunatZipNombre(),
                notaCredito.getSunatZipKey(),
                notaCredito.getSunatCdrNombre(),
                notaCredito.getSunatCdrKey(),
                notaCredito.getSunatEnviadoAt(),
                notaCredito.getSunatRespondidoAt());
    }

    private SunatEmissionResult emitirSimulado(NotaCredito notaCredito) {
        LocalDateTime now = LocalDateTime.now();
        String hash = "SIM-NC-" + notaCredito.getIdNotaCredito() + "-" + now.format(TICKET_FORMAT);
        notaCredito.setSunatEstado(SunatEstado.ACEPTADO);
        notaCredito.setSunatCodigo("0");
        notaCredito.setSunatMensaje("Nota de credito aceptada en modo simulado.");
        notaCredito.setSunatHash(hash);
        notaCredito.setSunatXmlNombre(SunatComprobanteHelper.construirNombreArchivoXml(notaCredito));
        notaCredito.setSunatEnviadoAt(now);
        notaCredito.setSunatRespondidoAt(now);
        notaCreditoRepository.save(notaCredito);

        return new SunatEmissionResult(
                SunatEstado.ACEPTADO,
                "0",
                notaCredito.getSunatMensaje(),
                hash,
                null,
                notaCredito.getSunatXmlNombre(),
                notaCredito.getSunatXmlKey(),
                notaCredito.getSunatZipNombre(),
                notaCredito.getSunatZipKey(),
                notaCredito.getSunatCdrNombre(),
                notaCredito.getSunatCdrKey(),
                now,
                now);
    }

    private SunatEmissionResult emitirReal(NotaCredito notaCredito, List<NotaCreditoDetalle> detalles) {
        String xmlName = SunatComprobanteHelper.construirNombreArchivoXml(notaCredito);
        String zipName = SunatComprobanteHelper.construirNombreArchivoZip(notaCredito);
        String signatureId = "SIGN-" + notaCredito.getSucursal().getEmpresa().getRuc().trim();

        try {
            SunatConfig config = resolveConfig(notaCredito);
            Document xmlDocument = sunatNotaCreditoXmlBuilderService.build(notaCredito, detalles);
            SunatXmlSignatureService.SignedXml signedXml = sunatXmlSignatureService.sign(xmlDocument, config, signatureId);
            byte[] zipBytes = zip(xmlName, signedXml.bytes());
            SunatDocumentStorageService.StoredUploadPair stored = sunatDocumentStorageService
                    .storeXmlAndZip(notaCredito, xmlName, signedXml.bytes(), zipName, zipBytes);

            LocalDateTime sentAt = LocalDateTime.now();
            notaCredito.setSunatHash(signedXml.digestValue());
            notaCredito.setSunatXmlNombre(xmlName);
            notaCredito.setSunatXmlKey(stored.xml().key());
            notaCredito.setSunatZipNombre(zipName);
            notaCredito.setSunatZipKey(stored.zip().key());
            notaCredito.setSunatEnviadoAt(sentAt);
            notaCreditoRepository.save(notaCredito);

            try {
                SunatSoapClientService.SendBillResponse soapResponse = sunatSoapClientService.sendBill(config, zipName, zipBytes);
                SunatCdrResult cdrResult = sunatCdrParserService.parse(soapResponse.cdrZipBytes());
                LocalDateTime respondedAt = LocalDateTime.now();

                SunatDocumentStorageService.StoredDocument cdrStored = null;
                String cdrMessage = cdrResult.mensaje();
                String cdrXmlFileName = soapResponse.cdrZipFileName().replaceFirst("\\.zip$", ".xml");
                try {
                    cdrStored = sunatDocumentStorageService.storeCdr(notaCredito, cdrXmlFileName, cdrResult.xmlBytes());
                } catch (RuntimeException storageError) {
                    cdrMessage = cdrMessage + " | CDR recibido pero no se pudo guardar en S3";
                }

                notaCredito.setSunatEstado(cdrResult.estado());
                notaCredito.setSunatCodigo(normalizarTexto(cdrResult.codigo()));
                notaCredito.setSunatMensaje(normalizarTexto(cdrMessage));
                notaCredito.setSunatCdrNombre(cdrStored != null ? cdrStored.fileName() : cdrXmlFileName);
                notaCredito.setSunatCdrKey(cdrStored != null ? cdrStored.key() : null);
                notaCredito.setSunatRespondidoAt(respondedAt);
                notaCreditoRepository.save(notaCredito);

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
                        notaCredito.getSunatCdrNombre(),
                        notaCredito.getSunatCdrKey(),
                        sentAt,
                        respondedAt);
            } catch (SunatSoapFaultException e) {
                LocalDateTime respondedAt = LocalDateTime.now();
                notaCredito.setSunatEstado(SunatEstado.RECHAZADO);
                notaCredito.setSunatCodigo(normalizarTexto(e.getCode()));
                notaCredito.setSunatMensaje(normalizarTexto(e.getMessage()));
                notaCredito.setSunatRespondidoAt(respondedAt);
                notaCreditoRepository.save(notaCredito);

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
                        null,
                        null,
                        sentAt,
                        respondedAt);
            } catch (RuntimeException e) {
                LocalDateTime respondedAt = LocalDateTime.now();
                notaCredito.setSunatEstado(SunatEstado.ERROR);
                notaCredito.setSunatCodigo("ENVIO");
                notaCredito.setSunatMensaje(normalizarTexto(e.getMessage()));
                notaCredito.setSunatRespondidoAt(respondedAt);
                notaCreditoRepository.save(notaCredito);

                return new SunatEmissionResult(
                        SunatEstado.ERROR,
                        "ENVIO",
                        notaCredito.getSunatMensaje(),
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
            notaCredito.setSunatEstado(SunatEstado.ERROR);
            notaCredito.setSunatCodigo("CONFIG");
            notaCredito.setSunatMensaje(normalizarTexto(e.getMessage()));
            notaCredito.setSunatRespondidoAt(now);
            notaCreditoRepository.save(notaCredito);

            return new SunatEmissionResult(
                    SunatEstado.ERROR,
                    "CONFIG",
                    notaCredito.getSunatMensaje(),
                    notaCredito.getSunatHash(),
                    notaCredito.getSunatTicket(),
                    xmlName,
                    notaCredito.getSunatXmlKey(),
                    zipName,
                    notaCredito.getSunatZipKey(),
                    notaCredito.getSunatCdrNombre(),
                    notaCredito.getSunatCdrKey(),
                    now,
                    now);
        }
    }

    private SunatConfig resolveConfig(NotaCredito notaCredito) {
        if (notaCredito == null
                || notaCredito.getSucursal() == null
                || notaCredito.getSucursal().getEmpresa() == null
                || notaCredito.getSucursal().getEmpresa().getIdEmpresa() == null) {
            throw new RuntimeException("La nota de credito no tiene empresa asociada para emitir en SUNAT");
        }

        if (!Boolean.TRUE.equals(notaCredito.getSucursal().getEmpresa().getGeneraFacturacionElectronica())) {
            throw new RuntimeException("La empresa tiene deshabilitada la facturacion electronica");
        }

        List<SunatConfig> configs = sunatConfigRepository
                .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSunatConfigAsc(
                        notaCredito.getSucursal().getEmpresa().getIdEmpresa());
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
            throw new RuntimeException("No se pudo comprimir el XML de nota de credito para SUNAT");
        }
    }

    private boolean isRealMode(String mode) {
        return "REAL".equals(mode)
                || "LIVE".equals(mode)
                || "ENABLED".equals(mode)
                || "PRODUCCION".equals(mode)
                || "PRODUCTION".equals(mode);
    }

    private String normalizarTexto(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
