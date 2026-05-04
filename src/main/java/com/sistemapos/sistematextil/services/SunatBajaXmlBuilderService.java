package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.SunatBajaItem;
import com.sistemapos.sistematextil.model.SunatBajaLote;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.util.sunat.SunatBajaTipo;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;

@Service
public class SunatBajaXmlBuilderService {

    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String NS_SAC = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS_VOIDED = "urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
    private static final String NS_SUMMARY = "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Document build(SunatBajaLote lote, List<SunatBajaItem> items) {
        if (lote == null) {
            throw new RuntimeException("No hay lote SUNAT para construir XML");
        }
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("El lote de baja no tiene items");
        }
        if (lote.getTipoEnvio() == null) {
            throw new RuntimeException("El lote de baja no tiene tipo de envio");
        }
        validateItemsForTipo(lote, items);
        return switch (lote.getTipoEnvio()) {
            case RA -> buildVoidedDocuments(lote, items);
            case RC -> buildSummaryDocuments(lote, items);
        };
    }

    private Document buildVoidedDocuments(SunatBajaLote lote, List<SunatBajaItem> items) {
        try {
            Document document = newDocument();
            Element root = document.createElementNS(NS_VOIDED, "VoidedDocuments");
            initRoot(document, root, NS_VOIDED);
            document.appendChild(root);

            appendExtensions(document, root);
            appendText(document, root, NS_CBC, "cbc:UBLVersionID", "2.0");
            appendText(document, root, NS_CBC, "cbc:CustomizationID", "1.0");
            appendText(document, root, NS_CBC, "cbc:ID", SunatComprobanteHelper.numeroLoteSunat(lote));
            appendText(document, root, NS_CBC, "cbc:ReferenceDate", lote.getFechaDocumento().format(DATE_FORMAT));
            appendText(document, root, NS_CBC, "cbc:IssueDate", lote.getFechaGeneracion().format(DATE_FORMAT));

            appendSignature(document, root, lote);
            appendSupplier(document, root, lote.getEmpresa());

            int line = 1;
            for (SunatBajaItem item : items) {
                Element voidedLine = appendElement(document, root, NS_SAC, "sac:VoidedDocumentsLine");
                appendText(document, voidedLine, NS_CBC, "cbc:LineID", String.valueOf(line++));
                appendText(document, voidedLine, NS_CBC, "cbc:DocumentTypeCode",
                        SunatComprobanteHelper.codigoTipoComprobante(item.getTipoComprobante()));
                appendText(document, voidedLine, NS_SAC, "sac:DocumentSerialID", item.getSerie());
                appendText(document, voidedLine, NS_SAC, "sac:DocumentNumberID", String.valueOf(item.getCorrelativo()));
                appendText(document, voidedLine, NS_SAC, "sac:VoidReasonDescription", item.getMotivo());
            }

            return document;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir el XML de comunicacion de baja");
        }
    }

    private Document buildSummaryDocuments(SunatBajaLote lote, List<SunatBajaItem> items) {
        try {
            Document document = newDocument();
            Element root = document.createElementNS(NS_SUMMARY, "SummaryDocuments");
            initRoot(document, root, NS_SUMMARY);
            document.appendChild(root);

            appendExtensions(document, root);
            appendText(document, root, NS_CBC, "cbc:UBLVersionID", "2.0");
            appendText(document, root, NS_CBC, "cbc:CustomizationID", "1.1");
            appendText(document, root, NS_CBC, "cbc:ID", SunatComprobanteHelper.numeroLoteSunat(lote));
            appendText(document, root, NS_CBC, "cbc:ReferenceDate", lote.getFechaDocumento().format(DATE_FORMAT));
            appendText(document, root, NS_CBC, "cbc:IssueDate", lote.getFechaGeneracion().format(DATE_FORMAT));

            appendSignature(document, root, lote);
            appendSupplier(document, root, lote.getEmpresa());

            int line = 1;
            for (SunatBajaItem item : items) {
                Element summaryLine = appendElement(document, root, NS_SAC, "sac:SummaryDocumentsLine");
                appendText(document, summaryLine, NS_CBC, "cbc:LineID", String.valueOf(line++));
                appendText(document, summaryLine, NS_CBC, "cbc:DocumentTypeCode",
                        SunatComprobanteHelper.codigoTipoComprobante(item.getTipoComprobante()));
                appendText(document, summaryLine, NS_CBC, "cbc:ID",
                        item.getSerie() + "-" + String.format("%08d", item.getCorrelativo()));
                appendBillingReferenceIfNeeded(document, summaryLine, item);

                Element status = appendElement(document, summaryLine, NS_CAC, "cac:Status");
                appendText(document, status, NS_CBC, "cbc:ConditionCode", "3");

                appendCurrencyAmount(document, summaryLine, "sac:TotalAmount", totalItem(item), monedaItem(item));

                Element billingPayment = appendElement(document, summaryLine, NS_SAC, "sac:BillingPayment");
                appendCurrencyAmount(document, billingPayment, "cbc:PaidAmount", subtotalItem(item), monedaItem(item));
                appendText(document, billingPayment, NS_CBC, "cbc:InstructionID", "01");

                Element taxTotal = appendElement(document, summaryLine, NS_CAC, "cac:TaxTotal");
                appendCurrencyAmount(document, taxTotal, "cbc:TaxAmount", igvItem(item), monedaItem(item));
                Element taxSubtotal = appendElement(document, taxTotal, NS_CAC, "cac:TaxSubtotal");
                appendCurrencyAmount(document, taxSubtotal, "cbc:TaxAmount", igvItem(item), monedaItem(item));
                Element taxCategory = appendElement(document, taxSubtotal, NS_CAC, "cac:TaxCategory");
                appendText(
                        document,
                        taxCategory,
                        NS_CBC,
                        "cbc:Percent",
                        normalizedPercent(igvPorcentajeItem(item)));
                Element taxScheme = appendElement(document, taxCategory, NS_CAC, "cac:TaxScheme");
                appendText(document, taxScheme, NS_CBC, "cbc:ID", "1000");
                appendText(document, taxScheme, NS_CBC, "cbc:Name", "IGV");
                appendText(document, taxScheme, NS_CBC, "cbc:TaxTypeCode", "VAT");
            }

            return document;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir el XML de resumen diario de bajas");
        }
    }

    private Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private void initRoot(Document document, Element root, String defaultNamespace) {
        root.setAttribute("xmlns", defaultNamespace);
        root.setAttribute("xmlns:cac", NS_CAC);
        root.setAttribute("xmlns:cbc", NS_CBC);
        root.setAttribute("xmlns:ext", NS_EXT);
        root.setAttribute("xmlns:sac", NS_SAC);
        root.setAttribute("xmlns:ds", NS_DS);
    }

    private void appendExtensions(Document document, Element root) {
        Element extensions = document.createElementNS(NS_EXT, "ext:UBLExtensions");
        Element extension = document.createElementNS(NS_EXT, "ext:UBLExtension");
        Element extensionContent = document.createElementNS(NS_EXT, "ext:ExtensionContent");
        extension.appendChild(extensionContent);
        extensions.appendChild(extension);
        root.appendChild(extensions);
    }

    private void appendSignature(Document document, Element root, SunatBajaLote lote) {
        String signatureId = "SIGN-" + lote.getEmpresa().getRuc().trim();
        Element signature = appendElement(document, root, NS_CAC, "cac:Signature");
        appendText(document, signature, NS_CBC, "cbc:ID", signatureId);

        Element signatoryParty = appendElement(document, signature, NS_CAC, "cac:SignatoryParty");
        Element partyIdentification = appendElement(document, signatoryParty, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", lote.getEmpresa().getRuc().trim());

        Element partyName = appendElement(document, signatoryParty, NS_CAC, "cac:PartyName");
        appendText(document, partyName, NS_CBC, "cbc:Name", visibleCompanyName(lote.getEmpresa()));

        Element attachment = appendElement(document, signature, NS_CAC, "cac:DigitalSignatureAttachment");
        Element externalReference = appendElement(document, attachment, NS_CAC, "cac:ExternalReference");
        appendText(document, externalReference, NS_CBC, "cbc:URI", "#" + signatureId);
    }

    private void appendSupplier(Document document, Element root, Empresa empresa) {
        validateCompany(empresa);

        Element supplier = appendElement(document, root, NS_CAC, "cac:AccountingSupplierParty");
        appendText(document, supplier, NS_CBC, "cbc:CustomerAssignedAccountID", empresa.getRuc().trim());
        appendText(document, supplier, NS_CBC, "cbc:AdditionalAccountID", "6");
        Element party = appendElement(document, supplier, NS_CAC, "cac:Party");
        Element legalEntity = appendElement(document, party, NS_CAC, "cac:PartyLegalEntity");
        appendText(document, legalEntity, NS_CBC, "cbc:RegistrationName", empresa.getRazonSocial().trim());
    }

    private void validateCompany(Empresa empresa) {
        if (empresa == null) {
            throw new RuntimeException("El lote de baja no tiene empresa asociada");
        }
        if (empresa.getRuc() == null || empresa.getRuc().isBlank()) {
            throw new RuntimeException("La empresa no tiene RUC para emitir la baja SUNAT");
        }
        if (empresa.getRazonSocial() == null || empresa.getRazonSocial().isBlank()) {
            throw new RuntimeException("La empresa no tiene razon social para emitir la baja SUNAT");
        }
    }

    private void validateItemsForTipo(SunatBajaLote lote, List<SunatBajaItem> items) {
        for (SunatBajaItem item : items) {
            String tipoComprobante = SunatComprobanteHelper.codigoTipoComprobante(item.getTipoComprobante());
            if (lote.getTipoEnvio() == SunatBajaTipo.RA && !("01".equals(tipoComprobante) || "07".equals(tipoComprobante))) {
                throw new RuntimeException("El lote RA solo permite facturas o notas de credito vinculadas a factura.");
            }
            if (lote.getTipoEnvio() == SunatBajaTipo.RC && !("03".equals(tipoComprobante) || "07".equals(tipoComprobante))) {
                throw new RuntimeException("El lote RC solo permite boletas o notas de credito vinculadas a boleta con ConditionCode 3.");
            }
        }
    }

    private void appendBillingReferenceIfNeeded(Document document, Element summaryLine, SunatBajaItem item) {
        NotaCredito notaCredito = item.getNotaCredito();
        if (notaCredito == null) {
            return;
        }
        Element billingReference = appendElement(document, summaryLine, NS_CAC, "cac:BillingReference");
        Element invoiceDocumentReference = appendElement(document, billingReference, NS_CAC, "cac:InvoiceDocumentReference");
        appendText(
                document,
                invoiceDocumentReference,
                NS_CBC,
                "cbc:ID",
                notaCredito.getSerieRef() + "-" + String.format("%08d", notaCredito.getCorrelativoRef()));
        appendText(
                document,
                invoiceDocumentReference,
                NS_CBC,
                "cbc:DocumentTypeCode",
                notaCredito.getTipoDocumentoRef());
    }

    private String monedaItem(SunatBajaItem item) {
        NotaCredito notaCredito = item.getNotaCredito();
        if (notaCredito != null) {
            return notaCredito.getMoneda();
        }
        Venta venta = item.getVenta();
        return venta == null ? "PEN" : venta.getMoneda();
    }

    private BigDecimal totalItem(SunatBajaItem item) {
        NotaCredito notaCredito = item.getNotaCredito();
        if (notaCredito != null) {
            return notaCredito.getTotal();
        }
        Venta venta = item.getVenta();
        return venta == null ? BigDecimal.ZERO : venta.getTotal();
    }

    private BigDecimal subtotalItem(SunatBajaItem item) {
        NotaCredito notaCredito = item.getNotaCredito();
        if (notaCredito != null) {
            return notaCredito.getSubtotal();
        }
        Venta venta = item.getVenta();
        return venta == null ? BigDecimal.ZERO : venta.getSubtotal();
    }

    private BigDecimal igvItem(SunatBajaItem item) {
        NotaCredito notaCredito = item.getNotaCredito();
        if (notaCredito != null) {
            return notaCredito.getIgv();
        }
        Venta venta = item.getVenta();
        return venta == null ? BigDecimal.ZERO : venta.getIgv();
    }

    private BigDecimal igvPorcentajeItem(SunatBajaItem item) {
        NotaCredito notaCredito = item.getNotaCredito();
        if (notaCredito != null) {
            return notaCredito.getIgvPorcentaje();
        }
        Venta venta = item.getVenta();
        return venta == null ? BigDecimal.ZERO : venta.getIgvPorcentaje();
    }

    private String visibleCompanyName(Empresa empresa) {
        if (empresa.getNombreComercial() != null && !empresa.getNombreComercial().isBlank()) {
            return empresa.getNombreComercial().trim();
        }
        return empresa.getRazonSocial().trim();
    }

    private Element appendElement(Document document, Element parent, String namespace, String qName) {
        Element child = document.createElementNS(namespace, qName);
        parent.appendChild(child);
        return child;
    }

    private void appendText(Document document, Element parent, String namespace, String qName, String value) {
        Element element = appendElement(document, parent, namespace, qName);
        element.setTextContent(value == null ? "" : value);
    }

    private void appendCurrencyAmount(
            Document document,
            Element parent,
            String qName,
            BigDecimal amount,
            String currencyCode) {
        Element element = appendElement(document, parent, qName.startsWith("sac:") ? NS_SAC : NS_CBC, qName);
        element.setAttribute("currencyID", currencyCode == null || currencyCode.isBlank() ? "PEN" : currencyCode.trim());
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        element.setTextContent(normalized.toPlainString());
    }

    private String normalizedPercent(BigDecimal percent) {
        BigDecimal normalized = percent == null ? BigDecimal.valueOf(18) : percent;
        return normalized.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
