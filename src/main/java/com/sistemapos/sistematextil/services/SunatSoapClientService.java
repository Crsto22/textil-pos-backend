package com.sistemapos.sistematextil.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.sistemapos.sistematextil.model.SunatConfig;
import com.sistemapos.sistematextil.util.sunat.SunatSoapFaultException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatSoapClientService {

    private static final Logger log = LoggerFactory.getLogger(SunatSoapClientService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("^(\\d{1,6})\\s*[-:]?\\s*(.*)$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final SunatSecretCryptoService sunatSecretCryptoService;

    public SendBillResponse sendBill(SunatConfig config, String zipFileName, byte[] zipBytes) {
        if (config == null) {
            throw new RuntimeException("No hay configuracion SUNAT registrada");
        }

        String username = requireText(config.getUsuarioSol(), "La configuracion SUNAT no tiene usuarioSol");
        String password = requireText(
                sunatSecretCryptoService.decrypt(config.getClaveSol()),
                "La configuracion SUNAT no tiene claveSol");
        String endpoint = resolveEndpoint(config.getUrlBillService());
        String requestXml = buildSendBillEnvelope(
                username,
                password,
                requireText(zipFileName, "No hay nombre ZIP para enviar a SUNAT"),
                Base64.getEncoder().encodeToString(zipBytes));

        log.info("SUNAT sendBill -> endpoint={}, zipFile={}, zipSize={} bytes",
                endpoint, zipFileName, zipBytes.length);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"urn:sendBill\"")
                .POST(HttpRequest.BodyPublishers.ofString(requestXml, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("SUNAT respuesta HTTP status={}, bodyLength={}",
                    response.statusCode(), response.body() == null ? 0 : response.body().length());
            log.debug("SUNAT respuesta body: {}", response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseSuccessResponse(response.body(), zipFileName);
            }
            throw parseFault(response.body(), response.statusCode());
        } catch (SunatSoapFaultException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error de conexion con SUNAT: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con SUNAT: " + e.getMessage());
        }
    }

    /**
     * Envía VoidedDocuments (Comunicación de Baja) a SUNAT.
     * Retorna un ticket para consultar el resultado de forma asíncrona.
     */
    public SendSummaryResponse sendSummary(SunatConfig config, String zipFileName, byte[] zipBytes) {
        if (config == null) {
            throw new RuntimeException("No hay configuracion SUNAT registrada");
        }

        String username = requireText(config.getUsuarioSol(), "La configuracion SUNAT no tiene usuarioSol");
        String password = requireText(
                sunatSecretCryptoService.decrypt(config.getClaveSol()),
                "La configuracion SUNAT no tiene claveSol");
        String endpoint = resolveEndpoint(config.getUrlBillService());
        String requestXml = buildSendSummaryEnvelope(
                username,
                password,
                requireText(zipFileName, "No hay nombre ZIP para enviar a SUNAT"),
                Base64.getEncoder().encodeToString(zipBytes));

        log.info("SUNAT sendSummary -> endpoint={}, zipFile={}, zipSize={} bytes",
                endpoint, zipFileName, zipBytes.length);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"urn:sendSummary\"")
                .POST(HttpRequest.BodyPublishers.ofString(requestXml, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("SUNAT sendSummary respuesta HTTP status={}, bodyLength={}",
                    response.statusCode(), response.body() == null ? 0 : response.body().length());
            log.debug("SUNAT sendSummary body: {}", response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseSendSummaryResponse(response.body());
            }
            throw parseFault(response.body(), response.statusCode());
        } catch (SunatSoapFaultException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error de conexion con SUNAT sendSummary: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con SUNAT para enviar comunicación de baja: " + e.getMessage());
        }
    }

    /**
     * Consulta el estado de un ticket de Comunicación de Baja en SUNAT.
     * Retorna el CDR si SUNAT ya procesó el documento.
     */
    public GetStatusResponse getStatus(SunatConfig config, String ticket) {
        if (config == null) {
            throw new RuntimeException("No hay configuracion SUNAT registrada");
        }
        if (ticket == null || ticket.isBlank()) {
            throw new RuntimeException("No hay ticket SUNAT para consultar");
        }

        String username = requireText(config.getUsuarioSol(), "La configuracion SUNAT no tiene usuarioSol");
        String password = requireText(
                sunatSecretCryptoService.decrypt(config.getClaveSol()),
                "La configuracion SUNAT no tiene claveSol");
        String endpoint = resolveEndpointConsultaTicket(config);
        String requestXml = buildGetStatusEnvelope(username, password, ticket.trim());

        log.info("SUNAT getStatus -> endpoint={}, ticket={}", endpoint, ticket);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"urn:getStatus\"")
                .POST(HttpRequest.BodyPublishers.ofString(requestXml, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("SUNAT getStatus respuesta HTTP status={}, bodyLength={}",
                    response.statusCode(), response.body() == null ? 0 : response.body().length());
            log.debug("SUNAT getStatus body: {}", response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseGetStatusResponse(response.body(), ticket);
            }
            throw parseFault(response.body(), response.statusCode());
        } catch (SunatSoapFaultException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error de conexion con SUNAT getStatus: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo consultar ticket SUNAT: " + e.getMessage());
        }
    }

    private SendBillResponse parseSuccessResponse(String body, String zipFileName) {
        try {
            Document document = parseXml(body);

            String faultText = firstTagText(document, "faultstring");
            if (faultText != null && !faultText.isBlank()) {
                log.warn("SUNAT devolvio SOAP Fault con HTTP 200: {}", faultText.trim());
                throw toFault(faultText.trim());
            }

            String appResponse = firstTagText(document, "applicationResponse");
            if (appResponse == null || appResponse.isBlank()) {
                log.error("SUNAT respondio HTTP 200 sin applicationResponse. Body: {}", body);
                throw new RuntimeException(
                        "SUNAT respondio sin applicationResponse. Verifique credenciales, nombre del ZIP y estructura del XML.");
            }
            byte[] cdrZip = Base64.getMimeDecoder().decode(appResponse.trim());
            log.info("SUNAT CDR recibido: {} bytes", cdrZip.length);
            return new SendBillResponse(cdrZip, "R-" + zipFileName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("No se pudo interpretar la respuesta de SUNAT: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo interpretar la respuesta de SUNAT: " + e.getMessage());
        }
    }

    private SunatSoapFaultException parseFault(String body, int httpStatus) {
        try {
            Document document = parseXml(body);
            String faultText = firstTagText(document, "faultstring");
            if (faultText == null || faultText.isBlank()) {
                faultText = "SUNAT devolvio error HTTP " + httpStatus;
            }
            return toFault(faultText.trim());
        } catch (RuntimeException e) {
            return new SunatSoapFaultException("HTTP_" + httpStatus, e.getMessage());
        } catch (Exception e) {
            return new SunatSoapFaultException("HTTP_" + httpStatus, "SUNAT devolvio error HTTP " + httpStatus);
        }
    }

    private SunatSoapFaultException toFault(String faultText) {
        Matcher matcher = CODE_PATTERN.matcher(faultText);
        if (matcher.matches()) {
            String code = matcher.group(1);
            String message = matcher.group(2);
            return new SunatSoapFaultException(code, message == null || message.isBlank() ? faultText : message.trim());
        }
        return new SunatSoapFaultException("SOAP_FAULT", faultText);
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String firstTagText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0 || nodes.item(0) == null || nodes.item(0).getTextContent() == null) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private SendSummaryResponse parseSendSummaryResponse(String body) {
        try {
            Document document = parseXml(body);

            String faultText = firstTagText(document, "faultstring");
            if (faultText != null && !faultText.isBlank()) {
                log.warn("SUNAT sendSummary devolvió SOAP Fault con HTTP 200: {}", faultText.trim());
                throw toFault(faultText.trim());
            }

            String ticket = firstTagText(document, "ticket");
            if (ticket == null || ticket.isBlank()) {
                log.error("SUNAT sendSummary respondió HTTP 200 sin ticket. Body: {}", body);
                throw new RuntimeException(
                        "SUNAT no devolvió ticket para la comunicación de baja. Verifique credenciales y estructura del XML.");
            }

            log.info("SUNAT ticket recibido: {}", ticket.trim());
            return new SendSummaryResponse(ticket.trim());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("No se pudo interpretar la respuesta sendSummary de SUNAT: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo interpretar la respuesta de sendSummary de SUNAT");
        }
    }

    private GetStatusResponse parseGetStatusResponse(String body, String ticket) {
        try {
            Document document = parseXml(body);

            String faultText = firstTagText(document, "faultstring");
            if (faultText != null && !faultText.isBlank()) {
                log.warn("SUNAT getStatus devolvió SOAP Fault con HTTP 200: {}", faultText.trim());
                throw toFault(faultText.trim());
            }

            String statusCode = firstTagText(document, "statusCode");
            String content = firstTagText(document, "content");

            // statusCode: 0 = procesado OK, 98 = en proceso, 99 = procesado con error
            if ("98".equals(statusCode)) {
                log.info("SUNAT getStatus ticket={}: aún en proceso", ticket);
                return new GetStatusResponse(ticket, "98", null, null);
            }

            byte[] cdrZip = null;
            String cdrFileName = null;
            if (content != null && !content.isBlank()) {
                cdrZip = Base64.getMimeDecoder().decode(content.trim());
                cdrFileName = "R-" + ticket + ".zip";
                log.info("SUNAT getStatus ticket={}: CDR recibido, {} bytes", ticket, cdrZip.length);
            }

            return new GetStatusResponse(ticket, statusCode, cdrZip, cdrFileName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("No se pudo interpretar la respuesta getStatus de SUNAT: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo interpretar la respuesta de getStatus de SUNAT");
        }
    }

    private String buildSendBillEnvelope(String username, String password, String fileName, String contentFileBase64) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ser="http://service.sunat.gob.pe"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    <ser:sendBill>
                      <fileName>%s</fileName>
                      <contentFile>%s</contentFile>
                    </ser:sendBill>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                xmlEscape(username),
                xmlEscape(password),
                xmlEscape(fileName),
                contentFileBase64);
    }

    private String buildSendSummaryEnvelope(String username, String password, String fileName, String contentFileBase64) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ser="http://service.sunat.gob.pe"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    <ser:sendSummary>
                      <fileName>%s</fileName>
                      <contentFile>%s</contentFile>
                    </ser:sendSummary>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                xmlEscape(username),
                xmlEscape(password),
                xmlEscape(fileName),
                contentFileBase64);
    }

    private String buildGetStatusEnvelope(String username, String password, String ticket) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ser="http://service.sunat.gob.pe"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    <ser:getStatus>
                      <ticket>%s</ticket>
                    </ser:getStatus>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                xmlEscape(username),
                xmlEscape(password),
                xmlEscape(ticket));
    }

    private String resolveEndpoint(String value) {
        String url = requireText(value, "La configuracion SUNAT no tiene urlBillService");
        if (url.toLowerCase().endsWith("?wsdl")) {
            return url.substring(0, url.length() - 5);
        }
        return url;
    }

    private String resolveEndpointConsultaTicket(SunatConfig config) {
        // Si url_consulta_ticket está configurada, usarla. Si no, usar url_bill_service.
        String url = config.getUrlConsultaTicket();
        if (url != null && !url.isBlank()) {
            return url.toLowerCase().endsWith("?wsdl") ? url.substring(0, url.length() - 5) : url.trim();
        }
        return resolveEndpoint(config.getUrlBillService());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String xmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public record SendBillResponse(
            byte[] cdrZipBytes,
            String cdrZipFileName) {
    }

    public record SendSummaryResponse(
            String ticket) {
    }

    public record GetStatusResponse(
            String ticket,
            String statusCode,
            byte[] cdrZipBytes,
            String cdrZipFileName) {

        public boolean isEnProceso() {
            return "98".equals(statusCode);
        }

        public boolean hasCdr() {
            return cdrZipBytes != null && cdrZipBytes.length > 0;
        }
    }
}
