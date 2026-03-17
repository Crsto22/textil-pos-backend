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
import com.sistemapos.sistematextil.model.ComunicacionBaja;
import com.sistemapos.sistematextil.model.ComunicacionBajaDetalle;
import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ComunicacionBajaRepository;
import com.sistemapos.sistematextil.repositories.ComunicacionBajaDetalleRepository;
import com.sistemapos.sistematextil.repositories.SunatConfigRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatSoapFaultException;

import lombok.RequiredArgsConstructor;
import org.w3c.dom.Document;

/**
 * Orquesta el flujo completo de Comunicación de Baja con SUNAT:
 * 1. Construir XML VoidedDocuments
 * 2. Firmar con certificado digital
 * 3. Comprimir en ZIP
 * 4. Almacenar XML en S3
 * 5. Enviar via SOAP sendSummary → obtener ticket
 * 6. Actualizar estado en BD
 *
 * Flujo de consulta de ticket:
 * 1. SOAP getStatus con el ticket
 * 2. Parsear CDR
 * 3. Actualizar estado ComunicacionBaja y Venta
 *
 * Sigue las mismas convenciones que DefaultSunatEmissionService.
 */
@Service
@RequiredArgsConstructor
public class SunatBajaEmissionService {

    private static final Logger log = LoggerFactory.getLogger(SunatBajaEmissionService.class);

    private final SunatProperties sunatProperties;
    private final SunatConfigRepository sunatConfigRepository;
    private final SunatBajaXmlBuilderService sunatBajaXmlBuilderService;
    private final SunatXmlSignatureService sunatXmlSignatureService;
    private final SunatDocumentStorageService sunatDocumentStorageService;
    private final SunatSoapClientService sunatSoapClientService;
    private final SunatCdrParserService sunatCdrParserService;
    private final ComunicacionBajaRepository comunicacionBajaRepository;
    private final ComunicacionBajaDetalleRepository comunicacionBajaDetalleRepository;
    private final VentaRepository ventaRepository;

    // ─── ENVIAR COMUNICACIÓN DE BAJA ─────────────────────────────────

    @Transactional
    public void enviarBaja(ComunicacionBaja baja) {
        String mode = sunatProperties.normalizedMode();

        if ("DISABLED".equals(mode)) {
            log.info("SUNAT deshabilitada. Comunicación de baja {} queda PENDIENTE.", baja.getIdentificadorBaja());
            return;
        }

        if ("SIMULATED".equals(mode)) {
            enviarBajaSimulado(baja);
            return;
        }

        if (isRealMode(mode)) {
            enviarBajaReal(baja);
            return;
        }

        log.warn("Modo SUNAT no soportado: {}. Comunicación de baja queda PENDIENTE.", mode);
    }

    private void enviarBajaSimulado(ComunicacionBaja baja) {
        LocalDateTime now = LocalDateTime.now();
        String ticket = "SIM-BAJA-" + baja.getIdBaja() + "-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        baja.setSunatTicket(ticket);
        baja.setSunatEstado("ACEPTADO");
        baja.setSunatCodigo("0");
        baja.setSunatMensaje("Comunicación de baja aceptada en modo simulado.");
        baja.setSunatXmlNombre(sunatBajaXmlBuilderService.construirNombreArchivoXml(baja));
        comunicacionBajaRepository.save(baja);

        // Marcar venta como ANULADA en simulado
        Venta venta = baja.getVenta();
        venta.setEstado("ANULADA");
        ventaRepository.save(venta);

        log.info("Comunicación de baja {} simulada: ticket={}", baja.getIdentificadorBaja(), ticket);
    }

    private void enviarBajaReal(ComunicacionBaja baja) {
        String xmlName = sunatBajaXmlBuilderService.construirNombreArchivoXml(baja);
        String zipName = sunatBajaXmlBuilderService.construirNombreArchivoZip(baja);
        String signatureId = "SIGN-" + baja.getVenta().getSucursal().getEmpresa().getRuc().trim();

        try {
            SunatConfig config = resolveConfig(baja.getVenta());
            log.info("SUNAT enviar baja real: xmlName={}, zipName={}, endpoint={}",
                    xmlName, zipName, config.getUrlBillService());

            List<ComunicacionBajaDetalle> detalles = comunicacionBajaDetalleRepository
                    .findByComunicacionBaja_IdBajaAndDeletedAtIsNull(baja.getIdBaja());

            // 1. Construir XML
            Document xmlDocument = sunatBajaXmlBuilderService.build(baja, detalles);

            // 2. Firmar
            SunatXmlSignatureService.SignedXml signedXml = sunatXmlSignatureService.sign(xmlDocument, config, signatureId);
            log.info("XML baja firmado: {} bytes", signedXml.bytes().length);

            // 3. ZIP
            byte[] zipBytes = zip(xmlName, signedXml.bytes());
            log.info("ZIP baja generado: {} bytes", zipBytes.length);

            // 4. Almacenar XML en S3 (usamos la venta original como referencia de ruta)
            Venta venta = baja.getVenta();
            try {
                SunatDocumentStorageService.StoredDocument storedXml = sunatDocumentStorageService
                        .storeXml(venta, xmlName, signedXml.bytes());
                baja.setSunatXmlNombre(storedXml.fileName());
                baja.setSunatXmlKey(storedXml.key());
            } catch (RuntimeException e) {
                log.warn("No se pudo almacenar XML de baja en S3: {}", e.getMessage());
                baja.setSunatXmlNombre(xmlName);
            }

            // 5. Enviar vía SOAP sendSummary
            try {
                SunatSoapClientService.SendSummaryResponse soapResponse = sunatSoapClientService
                        .sendSummary(config, zipName, zipBytes);

                baja.setSunatTicket(soapResponse.ticket());
                baja.setSunatEstado("PENDIENTE");
                baja.setSunatMensaje("Ticket recibido: " + soapResponse.ticket() + ". Pendiente de consulta de estado.");
                comunicacionBajaRepository.save(baja);

                log.info("Comunicación de baja {} enviada. Ticket: {}",
                        baja.getIdentificadorBaja(), soapResponse.ticket());

            } catch (SunatSoapFaultException e) {
                log.error("SUNAT SOAP Fault en sendSummary: code={}, message={}", e.getCode(), e.getMessage());
                baja.setSunatEstado("RECHAZADO");
                baja.setSunatCodigo(e.getCode());
                baja.setSunatMensaje(e.getMessage());
                comunicacionBajaRepository.save(baja);

                // Restaurar venta a EMITIDA
                venta.setEstado("EMITIDA");
                venta.setAnulacionTipo(null);
                venta.setAnulacionMotivo(null);
                venta.setAnulacionFecha(null);
                venta.setUsuarioAnulacion(null);
                ventaRepository.save(venta);
            }

        } catch (RuntimeException e) {
            log.error("Error preparando comunicación de baja: {}", e.getMessage(), e);
            baja.setSunatEstado("ERROR");
            baja.setSunatCodigo("CONFIG");
            baja.setSunatMensaje(e.getMessage() != null ? e.getMessage() : "Error preparando comunicación de baja");
            comunicacionBajaRepository.save(baja);
        }
    }

    // ─── CONSULTAR TICKET (getStatus) ────────────────────────────────

    @Transactional
    public void consultarTicket(ComunicacionBaja baja) {
        String mode = sunatProperties.normalizedMode();

        if ("DISABLED".equals(mode) || "SIMULATED".equals(mode)) {
            log.info("Consulta de ticket omitida en modo {}", mode);
            return;
        }

        if (!isRealMode(mode)) {
            log.warn("Modo SUNAT no soportado para consulta de ticket: {}", mode);
            return;
        }

        if (baja.getSunatTicket() == null || baja.getSunatTicket().isBlank()) {
            log.warn("Comunicación de baja {} no tiene ticket para consultar", baja.getIdentificadorBaja());
            return;
        }

        if (!"PENDIENTE".equals(baja.getSunatEstado())) {
            log.info("Comunicación de baja {} ya con estado {}. No se consulta.",
                    baja.getIdentificadorBaja(), baja.getSunatEstado());
            return;
        }

        try {
            SunatConfig config = resolveConfig(baja.getVenta());
            SunatSoapClientService.GetStatusResponse statusResponse = sunatSoapClientService
                    .getStatus(config, baja.getSunatTicket());

            if (statusResponse.isEnProceso()) {
                log.info("Ticket {} aún en proceso en SUNAT", baja.getSunatTicket());
                return;
            }

            if (statusResponse.hasCdr()) {
                SunatCdrResult cdrResult = sunatCdrParserService.parse(statusResponse.cdrZipBytes());
                log.info("SUNAT CDR baja: estado={}, codigo={}, mensaje={}",
                        cdrResult.estado(), cdrResult.codigo(), cdrResult.mensaje());

                baja.setSunatEstado(cdrResult.estado().name());
                baja.setSunatCodigo(cdrResult.codigo());
                baja.setSunatMensaje(cdrResult.mensaje());

                // Guardar CDR
                try {
                    Venta venta = baja.getVenta();
                    String cdrXmlFileName = statusResponse.cdrZipFileName() != null
                            ? statusResponse.cdrZipFileName().replaceFirst("\\.zip$", ".xml")
                            : "R-" + baja.getIdentificadorBaja() + ".xml";
                    SunatDocumentStorageService.StoredDocument cdrStored = sunatDocumentStorageService
                            .storeCdr(venta, cdrXmlFileName, cdrResult.xmlBytes());
                    baja.setSunatCdrNombre(cdrStored.fileName());
                    baja.setSunatCdrKey(cdrStored.key());
                } catch (RuntimeException e) {
                    log.warn("CDR de baja recibido pero no se pudo guardar en S3: {}", e.getMessage());
                }

                comunicacionBajaRepository.save(baja);

                // Si fue aceptado, actualizar venta
                if ("ACEPTADO".equals(baja.getSunatEstado())) {
                    Venta venta = baja.getVenta();
                    venta.setEstado("ANULADA");
                    ventaRepository.save(venta);
                    log.info("Baja {} ACEPTADA. Venta {} marcada como ANULADA.",
                            baja.getIdentificadorBaja(), venta.getIdVenta());
                } else if ("RECHAZADO".equals(baja.getSunatEstado())) {
                    Venta venta = baja.getVenta();
                    venta.setEstado("EMITIDA");
                    venta.setAnulacionTipo(null);
                    venta.setAnulacionMotivo(null);
                    venta.setAnulacionFecha(null);
                    venta.setUsuarioAnulacion(null);
                    ventaRepository.save(venta);
                    log.info("Baja {} RECHAZADA. Venta {} restaurada a EMITIDA.",
                            baja.getIdentificadorBaja(), venta.getIdVenta());
                }
            } else {
                baja.setSunatEstado("ERROR");
                baja.setSunatCodigo(statusResponse.statusCode());
                baja.setSunatMensaje("SUNAT no devolvió CDR para el ticket " + baja.getSunatTicket());
                comunicacionBajaRepository.save(baja);
            }

        } catch (SunatSoapFaultException e) {
            log.error("SOAP Fault al consultar ticket {}: code={}, message={}",
                    baja.getSunatTicket(), e.getCode(), e.getMessage());
            baja.setSunatCodigo(e.getCode());
            baja.setSunatMensaje(e.getMessage());
            comunicacionBajaRepository.save(baja);
        } catch (RuntimeException e) {
            log.error("Error consultando ticket {}: {}", baja.getSunatTicket(), e.getMessage());
        }
    }

    // ─── PROCESO MASIVO DE TICKETS PENDIENTES ────────────────────────

    @Transactional
    public int consultarTicketsPendientes() {
        List<ComunicacionBaja> pendientes = comunicacionBajaRepository.findPendientesConTicket();
        log.info("Tickets de baja pendientes: {}", pendientes.size());
        int procesados = 0;
        for (ComunicacionBaja baja : pendientes) {
            try {
                consultarTicket(baja);
                procesados++;
            } catch (Exception e) {
                log.error("Error consultando ticket baja {}: {}",
                        baja.getIdentificadorBaja(), e.getMessage());
            }
        }
        return procesados;
    }

    // ─── HELPERS ─────────────────────────────────────────────────────

    private SunatConfig resolveConfig(Venta venta) {
        if (venta == null
                || venta.getSucursal() == null
                || venta.getSucursal().getEmpresa() == null
                || venta.getSucursal().getEmpresa().getIdEmpresa() == null) {
            throw new RuntimeException("La venta no tiene empresa asociada");
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
            throw new RuntimeException("No se pudo comprimir el XML de baja para SUNAT");
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
