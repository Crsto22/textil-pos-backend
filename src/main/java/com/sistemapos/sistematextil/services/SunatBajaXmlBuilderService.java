package com.sistemapos.sistematextil.services;

import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sistemapos.sistematextil.model.ComunicacionBaja;
import com.sistemapos.sistematextil.model.ComunicacionBajaDetalle;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;

/**
 * Construye el XML VoidedDocuments (Comunicación de Baja) para SUNAT.
 * Estructura: sac:VoidedDocuments → Header + VoidedDocumentsLine(s)
 * Formato nombre archivo: {RUC}-RA-{YYYYMMDD}-{NNNNN}.xml
 */
@Service
public class SunatBajaXmlBuilderService {

    private static final String NS_VOIDED = "urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS_SAC = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
    private static final DateTimeFormatter ISSUE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Document build(ComunicacionBaja baja, List<ComunicacionBajaDetalle> detalles) {
        validarEntrada(baja, detalles);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element root = document.createElementNS(NS_VOIDED, "VoidedDocuments");
            root.setAttribute("xmlns", NS_VOIDED);
            root.setAttribute("xmlns:cac", NS_CAC);
            root.setAttribute("xmlns:cbc", NS_CBC);
            root.setAttribute("xmlns:ext", NS_EXT);
            root.setAttribute("xmlns:ds", NS_DS);
            root.setAttribute("xmlns:sac", NS_SAC);
            root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            document.appendChild(root);

            // UBL Extensions (contenedor de firma)
            appendExtensions(document, root);

            // Header
            appendText(document, root, NS_CBC, "cbc:UBLVersionID", "2.0");
            appendText(document, root, NS_CBC, "cbc:CustomizationID", "1.0");
            appendText(document, root, NS_CBC, "cbc:ID", baja.getIdentificadorBaja());
            appendText(document, root, NS_CBC, "cbc:ReferenceDate",
                    baja.getFechaEmisionOriginal().format(ISSUE_DATE));
            appendText(document, root, NS_CBC, "cbc:IssueDate",
                    baja.getFechaGeneracionBaja().toLocalDate().format(ISSUE_DATE));

            // Signature block
            Venta venta = baja.getVenta();
            Empresa empresa = venta.getSucursal().getEmpresa();
            appendSignatureBlock(document, root, empresa);

            // Supplier (AccountingSupplierParty)
            appendSupplier(document, root, venta.getSucursal());

            // VoidedDocumentsLine (una por cada detalle)
            int lineNumber = 1;
            for (ComunicacionBajaDetalle detalle : detalles) {
                appendVoidedLine(document, root, detalle, lineNumber++);
            }

            return document;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir el XML de Comunicación de Baja para SUNAT");
        }
    }

    /**
     * Construye el nombre del archivo XML: {RUC}-RA-{YYYYMMDD}-{NNNNN}.xml
     */
    public String construirNombreArchivoXml(ComunicacionBaja baja) {
        return construirBaseNombre(baja) + ".xml";
    }

    public String construirNombreArchivoZip(ComunicacionBaja baja) {
        return construirBaseNombre(baja) + ".zip";
    }

    private String construirBaseNombre(ComunicacionBaja baja) {
        String ruc = baja.getVenta().getSucursal().getEmpresa().getRuc().trim();
        return ruc + "-" + baja.getIdentificadorBaja();
    }

    // ─── VALIDACIÓN ──────────────────────────────────────────────────

    private void validarEntrada(ComunicacionBaja baja, List<ComunicacionBajaDetalle> detalles) {
        if (baja == null) {
            throw new RuntimeException("No hay comunicación de baja para construir XML");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("La comunicación de baja no tiene detalles");
        }
        if (baja.getIdentificadorBaja() == null || baja.getIdentificadorBaja().isBlank()) {
            throw new RuntimeException("La comunicación de baja no tiene identificador");
        }
        if (baja.getFechaEmisionOriginal() == null) {
            throw new RuntimeException("La comunicación de baja no tiene fecha de emisión original");
        }
        if (baja.getFechaGeneracionBaja() == null) {
            throw new RuntimeException("La comunicación de baja no tiene fecha de generación");
        }

        Venta venta = baja.getVenta();
        if (venta == null || venta.getSucursal() == null || venta.getSucursal().getEmpresa() == null) {
            throw new RuntimeException("La comunicación de baja no tiene empresa emisora");
        }
        Empresa empresa = venta.getSucursal().getEmpresa();
        if (isBlank(empresa.getRuc()) || empresa.getRuc().trim().length() != 11) {
            throw new RuntimeException("La empresa emisora debe tener RUC de 11 dígitos");
        }
        if (isBlank(empresa.getRazonSocial())) {
            throw new RuntimeException("La empresa emisora debe tener razón social");
        }
    }

    // ─── SECCIONES XML ───────────────────────────────────────────────

    private void appendExtensions(Document document, Element root) {
        Element extensions = document.createElementNS(NS_EXT, "ext:UBLExtensions");
        Element extension = document.createElementNS(NS_EXT, "ext:UBLExtension");
        Element extensionContent = document.createElementNS(NS_EXT, "ext:ExtensionContent");
        extension.appendChild(extensionContent);
        extensions.appendChild(extension);
        root.appendChild(extensions);
    }

    private void appendSignatureBlock(Document document, Element root, Empresa empresa) {
        String signatureId = "SIGN-" + empresa.getRuc().trim();

        Element signature = appendElement(document, root, NS_CAC, "cac:Signature");
        appendText(document, signature, NS_CBC, "cbc:ID", signatureId);

        Element signatoryParty = appendElement(document, signature, NS_CAC, "cac:SignatoryParty");
        Element partyIdentification = appendElement(document, signatoryParty, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", empresa.getRuc().trim());

        Element partyName = appendElement(document, signatoryParty, NS_CAC, "cac:PartyName");
        appendText(document, partyName, NS_CBC, "cbc:Name", textoEmpresaVisible(empresa));

        Element attachment = appendElement(document, signature, NS_CAC, "cac:DigitalSignatureAttachment");
        Element externalReference = appendElement(document, attachment, NS_CAC, "cac:ExternalReference");
        appendText(document, externalReference, NS_CBC, "cbc:URI", "#" + signatureId);
    }

    private void appendSupplier(Document document, Element root, Sucursal sucursal) {
        Empresa empresa = sucursal.getEmpresa();

        Element supplier = appendElement(document, root, NS_CAC, "cac:AccountingSupplierParty");
        appendText(document, supplier, NS_CBC,
            "cbc:CustomerAssignedAccountID", empresa.getRuc().trim());
        appendText(document, supplier, NS_CBC,
            "cbc:AdditionalAccountID", "6"); // RUC

        Element party = appendElement(document, supplier, NS_CAC, "cac:Party");
        Element partyLegalEntity = appendElement(document, party, NS_CAC, "cac:PartyLegalEntity");
        appendText(document, partyLegalEntity, NS_CBC, "cbc:RegistrationName", empresa.getRazonSocial().trim());
    }

    private void appendVoidedLine(Document document, Element root, ComunicacionBajaDetalle detalle, int lineNumber) {
        Element line = appendElement(document, root, NS_SAC, "sac:VoidedDocumentsLine");
        appendText(document, line, NS_CBC, "cbc:LineID", String.valueOf(lineNumber));

        String codigoTipo = SunatComprobanteHelper.codigoTipoComprobante(detalle.getTipoComprobante());
        appendText(document, line, NS_CBC, "cbc:DocumentTypeCode", codigoTipo);
        appendText(document, line, NS_SAC, "sac:DocumentSerialID", detalle.getSerie());
        appendText(document, line, NS_SAC, "sac:DocumentNumberID",
                String.valueOf(detalle.getCorrelativo()));
        appendText(document, line, NS_SAC, "sac:VoidReasonDescription", detalle.getMotivo());
    }

    // ─── HELPERS ─────────────────────────────────────────────────────

    private Element appendElement(Document document, Element parent, String namespace, String qualifiedName) {
        Element element = document.createElementNS(namespace, qualifiedName);
        parent.appendChild(element);
        return element;
    }

    private Element appendText(Document document, Element parent, String namespace, String qualifiedName,
            String value, String... attributes) {
        Element element = document.createElementNS(namespace, qualifiedName);
        element.setTextContent(value == null ? "" : value);
        for (int i = 0; i + 1 < attributes.length; i += 2) {
            element.setAttribute(attributes[i], attributes[i + 1]);
        }
        parent.appendChild(element);
        return element;
    }

    private String textoEmpresaVisible(Empresa empresa) {
        if (empresa.getNombreComercial() != null && !empresa.getNombreComercial().isBlank()) {
            return empresa.getNombreComercial().trim();
        }
        if (empresa.getNombre() != null && !empresa.getNombre().isBlank()) {
            return empresa.getNombre().trim();
        }
        return empresa.getRazonSocial().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
