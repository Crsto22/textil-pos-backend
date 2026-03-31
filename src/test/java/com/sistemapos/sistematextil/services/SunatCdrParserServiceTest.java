package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.util.sunat.SunatCdrResult;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

class SunatCdrParserServiceTest {

    private final SunatCdrParserService service = new SunatCdrParserService();

    @Test
    void extraeYParseaCdrZip() {
        byte[] xmlBytes = """
                <ApplicationResponse>
                  <DocumentResponse>
                    <Response>
                      <ResponseCode>0</ResponseCode>
                      <Description>Comprobante aceptado</Description>
                    </Response>
                  </DocumentResponse>
                </ApplicationResponse>
                """.getBytes(StandardCharsets.UTF_8);

        byte[] zipBytes = service.wrapXmlAsZip("R-10454562467-01-F001-00000001.xml", xmlBytes);

        assertTrue(service.isZip(zipBytes));

        SunatCdrParserService.ExtractedXml extractedXml = service.extractXml(zipBytes);
        assertEquals("R-10454562467-01-F001-00000001.xml", extractedXml.fileName());
        assertArrayEquals(xmlBytes, extractedXml.bytes());

        SunatCdrResult result = service.parse(zipBytes);
        assertEquals(SunatEstado.ACEPTADO, result.estado());
        assertEquals("0", result.codigo());
        assertTrue(result.mensaje().contains("Comprobante aceptado"));
    }
}
