package com.sistemapos.sistematextil.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

@Service
public class SunatCdrParserService {

    public SunatCdrResult parse(byte[] cdrZipBytes) {
        return parseApplicationResponse(extractXml(cdrZipBytes).bytes());
    }

    public ExtractedXml extractXml(byte[] cdrZipBytes) {
        if (cdrZipBytes == null || cdrZipBytes.length == 0) {
            throw new RuntimeException("SUNAT no devolvio un CDR valido");
        }

        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(cdrZipBytes))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                return new ExtractedXml(entry.getName(), readAll(zipInput));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el CDR devuelto por SUNAT");
        }

        throw new RuntimeException("El CDR devuelto por SUNAT no contiene XML");
    }

    public byte[] wrapXmlAsZip(String xmlFileName, byte[] xmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            throw new RuntimeException("No hay XML CDR para comprimir");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry(xmlFileName));
            zipOutput.write(xmlBytes);
            zipOutput.closeEntry();
            zipOutput.finish();
            return output.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo comprimir el XML del CDR");
        }
    }

    public boolean isZip(byte[] bytes) {
        return bytes != null
                && bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K';
    }

    private SunatCdrResult parseApplicationResponse(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xmlBytes));

            String code = firstTagText(document, "ResponseCode");
            String description = firstTagText(document, "Description");
            List<String> notes = allTagText(document, "Note");

            StringBuilder mensaje = new StringBuilder(description == null || description.isBlank()
                    ? "SUNAT respondio sin descripcion"
                    : description.trim());
            if (!notes.isEmpty()) {
                mensaje.append(" | ");
                mensaje.append(String.join(" | ", notes));
            }

            return new SunatCdrResult(resolveEstado(code), code, mensaje.toString());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo interpretar el XML del CDR");
        }
    }

    private SunatEstado resolveEstado(String code) {
        if (code == null || code.isBlank()) {
            return SunatEstado.ERROR;
        }
        if ("0".equals(code.trim())) {
            return SunatEstado.ACEPTADO;
        }
        return SunatEstado.OBSERVADO;
    }

    private byte[] readAll(ZipInputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String firstTagText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0 || nodes.item(0) == null || nodes.item(0).getTextContent() == null) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private List<String> allTagText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) != null && nodes.item(i).getTextContent() != null) {
                String value = nodes.item(i).getTextContent().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    public record ExtractedXml(
            String fileName,
            byte[] bytes) {
    }
}
