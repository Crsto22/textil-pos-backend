package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.NotaCreditoDetalle;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatNotaCreditoXmlBuilderService {

    private static final String NS_CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final DateTimeFormatter ISSUE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISSUE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SunatMontoTextoService sunatMontoTextoService;

    public Document build(NotaCredito notaCredito, List<NotaCreditoDetalle> detalles) {
        validarEntrada(notaCredito, detalles);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element root = document.createElementNS(NS_CREDIT_NOTE, "CreditNote");
            root.setAttribute("xmlns", NS_CREDIT_NOTE);
            root.setAttribute("xmlns:cac", NS_CAC);
            root.setAttribute("xmlns:cbc", NS_CBC);
            root.setAttribute("xmlns:ext", NS_EXT);
            root.setAttribute("xmlns:ds", NS_DS);
            root.setAttribute("xmlns:qdt", "urn:oasis:names:specification:ubl:schema:xsd:QualifiedDatatypes-2");
            root.setAttribute("xmlns:sac", "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1");
            root.setAttribute("xmlns:udt", "urn:un:unece:uncefact:data:specification:UnqualifiedDataTypesSchemaModule:2");
            root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            document.appendChild(root);

            appendExtensions(document, root);
            appendText(document, root, NS_CBC, "cbc:UBLVersionID", "2.1");
            appendText(document, root, NS_CBC, "cbc:CustomizationID", "2.0");
            appendText(document, root, NS_CBC, "cbc:ID", SunatComprobanteHelper.numeroComprobante(notaCredito));
            appendText(document, root, NS_CBC, "cbc:IssueDate", notaCredito.getFecha().toLocalDate().format(ISSUE_DATE));
            appendText(document, root, NS_CBC, "cbc:IssueTime", notaCredito.getFecha().toLocalTime().format(ISSUE_TIME));
            appendText(document, root, NS_CBC, "cbc:Note",
                    sunatMontoTextoService.montoEnLetras(notaCredito.getTotal(), notaCredito.getMoneda()),
                    "languageLocaleID", "1000");
            appendText(document, root, NS_CBC, "cbc:DocumentCurrencyCode",
                    normalizarMoneda(notaCredito.getMoneda()),
                    "listID", "ISO 4217 Alpha",
                    "listName", "Currency",
                    "listAgencyName", "United Nations Economic Commission for Europe");
            appendText(document, root, NS_CBC, "cbc:LineCountNumeric", String.valueOf(detalles.size()));

            appendDiscrepancyResponse(document, root, notaCredito);
            appendBillingReference(document, root, notaCredito);
            appendSignatureBlock(document, root, notaCredito);
            appendSupplier(document, root, notaCredito);
            appendCustomer(document, root, notaCredito);
            appendDocumentTaxTotal(document, root, notaCredito, detalles);
            appendMonetaryTotal(document, root, notaCredito, detalles);

            int lineNumber = 1;
            for (NotaCreditoDetalle detalle : detalles) {
                appendCreditNoteLine(document, root, notaCredito, detalle, lineNumber++);
            }

            return document;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir el XML de nota de credito para SUNAT");
        }
    }

    private void validarEntrada(NotaCredito notaCredito, List<NotaCreditoDetalle> detalles) {
        if (notaCredito == null) {
            throw new RuntimeException("No hay nota de credito para construir XML SUNAT");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("La nota de credito no tiene detalles para construir XML SUNAT");
        }
        if (notaCredito.getFecha() == null) {
            throw new RuntimeException("La nota de credito no tiene fecha de emision");
        }
        if (isBlank(notaCredito.getCodigoMotivo())) {
            throw new RuntimeException("La nota de credito no tiene codigo de motivo");
        }
        if (isBlank(notaCredito.getDescripcionMotivo())) {
            throw new RuntimeException("La nota de credito no tiene descripcion de motivo");
        }
        if (isBlank(notaCredito.getTipoDocumentoRef())) {
            throw new RuntimeException("La nota de credito no tiene tipo de documento de referencia");
        }

        Sucursal sucursal = notaCredito.getSucursal();
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        if (empresa == null) {
            throw new RuntimeException("La nota de credito no tiene empresa emisora");
        }
        if (isBlank(empresa.getRuc()) || empresa.getRuc().trim().length() != 11) {
            throw new RuntimeException("La empresa emisora debe tener RUC de 11 digitos");
        }
        if (isBlank(empresa.getRazonSocial())) {
            throw new RuntimeException("La empresa emisora debe tener razon social");
        }
        if (sucursal == null || isBlank(sucursal.getDireccion())) {
            throw new RuntimeException("La sucursal emisora debe tener direccion");
        }
        if (isBlank(sucursal.getUbigeo()) || sucursal.getUbigeo().trim().length() != 6) {
            throw new RuntimeException("La sucursal emisora debe tener ubigeo de 6 digitos");
        }
        if (isBlank(sucursal.getDepartamento()) || isBlank(sucursal.getProvincia()) || isBlank(sucursal.getDistrito())) {
            throw new RuntimeException("La sucursal emisora debe tener departamento, provincia y distrito");
        }

        Cliente cliente = notaCredito.getCliente();
        if (cliente == null) {
            throw new RuntimeException("La nota de credito no tiene cliente");
        }
        if (isBlank(cliente.getNombres())) {
            throw new RuntimeException("La nota de credito debe tener cliente con nombre o razon social");
        }
    }

    private void appendExtensions(Document document, Element root) {
        Element extensions = document.createElementNS(NS_EXT, "ext:UBLExtensions");
        Element extension = document.createElementNS(NS_EXT, "ext:UBLExtension");
        Element extensionContent = document.createElementNS(NS_EXT, "ext:ExtensionContent");
        extension.appendChild(extensionContent);
        extensions.appendChild(extension);
        root.appendChild(extensions);
    }

    private void appendDiscrepancyResponse(Document document, Element root, NotaCredito notaCredito) {
        Element discrepancy = appendElement(document, root, NS_CAC, "cac:DiscrepancyResponse");
        appendText(document, discrepancy, NS_CBC, "cbc:ReferenceID",
                notaCredito.getSerieRef() + "-" + String.format(Locale.ROOT, "%08d", notaCredito.getCorrelativoRef()));
        appendText(document, discrepancy, NS_CBC, "cbc:ResponseCode", notaCredito.getCodigoMotivo(),
                "listAgencyName", "PE:SUNAT",
                "listName", "Tipo de nota de credito",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo09");
        appendText(document, discrepancy, NS_CBC, "cbc:Description", notaCredito.getDescripcionMotivo());
    }

    private void appendBillingReference(Document document, Element root, NotaCredito notaCredito) {
        Element billingReference = appendElement(document, root, NS_CAC, "cac:BillingReference");
        Element invoiceDocumentReference = appendElement(document, billingReference, NS_CAC, "cac:InvoiceDocumentReference");
        appendText(document, invoiceDocumentReference, NS_CBC, "cbc:ID",
                notaCredito.getSerieRef() + "-" + String.format(Locale.ROOT, "%08d", notaCredito.getCorrelativoRef()));
        appendText(document, invoiceDocumentReference, NS_CBC, "cbc:DocumentTypeCode", notaCredito.getTipoDocumentoRef(),
                "listAgencyName", "PE:SUNAT",
                "listName", "Tipo de Documento",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
    }

    private void appendSignatureBlock(Document document, Element root, NotaCredito notaCredito) {
        String signatureId = signatureId(notaCredito);
        Empresa empresa = notaCredito.getSucursal().getEmpresa();

        Element signature = appendElement(document, root, NS_CAC, "cac:Signature");
        appendText(document, signature, NS_CBC, "cbc:ID", signatureId);

        Element signatoryParty = appendElement(document, signature, NS_CAC, "cac:SignatoryParty");
        Element partyIdentification = appendElement(document, signatoryParty, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", empresa.getRuc());

        Element partyName = appendElement(document, signatoryParty, NS_CAC, "cac:PartyName");
        appendText(document, partyName, NS_CBC, "cbc:Name", textoEmpresaVisible(empresa));

        Element attachment = appendElement(document, signature, NS_CAC, "cac:DigitalSignatureAttachment");
        Element externalReference = appendElement(document, attachment, NS_CAC, "cac:ExternalReference");
        appendText(document, externalReference, NS_CBC, "cbc:URI", "#" + signatureId);
    }

    private void appendSupplier(Document document, Element root, NotaCredito notaCredito) {
        Sucursal sucursal = notaCredito.getSucursal();
        Empresa empresa = sucursal.getEmpresa();

        Element supplier = appendElement(document, root, NS_CAC, "cac:AccountingSupplierParty");
        Element party = appendElement(document, supplier, NS_CAC, "cac:Party");

        Element partyIdentification = appendElement(document, party, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", empresa.getRuc().trim(),
                "schemeID", "6",
                "schemeName", "Documento de Identidad",
                "schemeAgencyName", "PE:SUNAT",
                "schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");

        if (!isBlank(empresa.getNombreComercial())) {
            Element partyName = appendElement(document, party, NS_CAC, "cac:PartyName");
            appendText(document, partyName, NS_CBC, "cbc:Name", empresa.getNombreComercial().trim());
        }

        Element partyLegalEntity = appendElement(document, party, NS_CAC, "cac:PartyLegalEntity");
        appendText(document, partyLegalEntity, NS_CBC, "cbc:RegistrationName", empresa.getRazonSocial().trim());

        Element registrationAddress = appendElement(document, partyLegalEntity, NS_CAC, "cac:RegistrationAddress");
        appendText(document, registrationAddress, NS_CBC, "cbc:ID", sucursal.getUbigeo().trim(),
                "schemeName", "Ubigeos",
                "schemeAgencyName", "PE:INEI");
        appendText(document, registrationAddress, NS_CBC, "cbc:AddressTypeCode", codigoEstablecimiento(sucursal),
                "listName", "Establecimientos anexos");
        appendText(document, registrationAddress, NS_CBC, "cbc:CitySubdivisionName", sucursal.getDistrito().trim());
        appendText(document, registrationAddress, NS_CBC, "cbc:CityName", sucursal.getProvincia().trim());
        appendText(document, registrationAddress, NS_CBC, "cbc:CountrySubentity", sucursal.getDepartamento().trim());
        appendText(document, registrationAddress, NS_CBC, "cbc:District", sucursal.getDistrito().trim());
        Element addressLine = appendElement(document, registrationAddress, NS_CAC, "cac:AddressLine");
        appendText(document, addressLine, NS_CBC, "cbc:Line", sucursal.getDireccion().trim());
        Element country = appendElement(document, registrationAddress, NS_CAC, "cac:Country");
        appendText(document, country, NS_CBC, "cbc:IdentificationCode", "PE",
                "listID", "ISO 3166-1",
                "listAgencyName", "United Nations Economic Commission for Europe",
                "listName", "Country");
    }

    private void appendCustomer(Document document, Element root, NotaCredito notaCredito) {
        Cliente cliente = notaCredito.getCliente();
        String docCode = SunatComprobanteHelper.codigoTipoDocumento(cliente.getTipoDocumento());
        String nroDocumento = isBlank(cliente.getNroDocumento()) ? "-" : cliente.getNroDocumento().trim();

        Element customer = appendElement(document, root, NS_CAC, "cac:AccountingCustomerParty");
        Element party = appendElement(document, customer, NS_CAC, "cac:Party");
        Element partyIdentification = appendElement(document, party, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", nroDocumento,
                "schemeID", docCode,
                "schemeName", "Documento de Identidad",
                "schemeAgencyName", "PE:SUNAT",
                "schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");
        Element partyLegalEntity = appendElement(document, party, NS_CAC, "cac:PartyLegalEntity");
        appendText(document, partyLegalEntity, NS_CBC, "cbc:RegistrationName", cliente.getNombres().trim());
    }

    private void appendDocumentTaxTotal(Document document, Element root, NotaCredito notaCredito, List<NotaCreditoDetalle> detalles) {
        BigDecimal taxAmount = detalles.stream()
                .map(NotaCreditoDetalle::getIgvDetalle)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = detalles.stream()
                .map(NotaCreditoDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        TaxSchemeData taxSchemeData = taxSchemeData(detalles.get(0).getCodigoTipoAfectacionIgv());

        Element taxTotal = appendElement(document, root, NS_CAC, "cac:TaxTotal");
        appendCurrencyAmount(document, taxTotal, "cbc:TaxAmount", taxAmount, notaCredito.getMoneda());

        Element taxSubtotal = appendElement(document, taxTotal, NS_CAC, "cac:TaxSubtotal");
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxableAmount", taxableAmount, notaCredito.getMoneda());
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxAmount", taxAmount, notaCredito.getMoneda());
        Element taxCategory = appendElement(document, taxSubtotal, NS_CAC, "cac:TaxCategory");
        appendText(document, taxCategory, NS_CBC, "cbc:ID", taxSchemeData.categoryCode(),
                "schemeID", "UN/ECE 5305",
                "schemeName", "Tax Category Identifier",
                "schemeAgencyName", "United Nations Economic Commission for Europe");
        if (taxSchemeData.percentRequired()) {
            appendText(document, taxCategory, NS_CBC, "cbc:Percent", formatearDecimal(notaCredito.getIgvPorcentaje(), 2));
        }
        appendText(document, taxCategory, NS_CBC, "cbc:TaxExemptionReasonCode",
                detalles.get(0).getCodigoTipoAfectacionIgv(),
                "listAgencyName", "PE:SUNAT",
                "listName", "Afectacion del IGV",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07");
        Element taxScheme = appendElement(document, taxCategory, NS_CAC, "cac:TaxScheme");
        appendText(document, taxScheme, NS_CBC, "cbc:ID", taxSchemeData.id(),
                "schemeID", "UN/ECE 5153",
                "schemeName", "Codigo de tributos",
                "schemeAgencyName", "PE:SUNAT");
        appendText(document, taxScheme, NS_CBC, "cbc:Name", taxSchemeData.name());
        appendText(document, taxScheme, NS_CBC, "cbc:TaxTypeCode", taxSchemeData.typeCode());
    }

    private void appendMonetaryTotal(Document document, Element root, NotaCredito notaCredito, List<NotaCreditoDetalle> detalles) {
        BigDecimal lineExtensionAmount = detalles.stream()
                .map(NotaCreditoDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = detalles.stream()
                .map(NotaCreditoDetalle::getIgvDetalle)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxInclusiveAmount = lineExtensionAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        Element monetaryTotal = appendElement(document, root, NS_CAC, "cac:LegalMonetaryTotal");
        appendCurrencyAmount(document, monetaryTotal, "cbc:LineExtensionAmount", lineExtensionAmount, notaCredito.getMoneda());
        appendCurrencyAmount(document, monetaryTotal, "cbc:TaxInclusiveAmount", taxInclusiveAmount, notaCredito.getMoneda());
        if (notaCredito.getDescuentoTotal() != null && notaCredito.getDescuentoTotal().compareTo(BigDecimal.ZERO) > 0) {
            appendCurrencyAmount(document, monetaryTotal, "cbc:AllowanceTotalAmount", notaCredito.getDescuentoTotal(), notaCredito.getMoneda());
        }
        appendCurrencyAmount(document, monetaryTotal, "cbc:PayableAmount", notaCredito.getTotal(), notaCredito.getMoneda());
    }

    private void appendCreditNoteLine(
            Document document,
            Element root,
            NotaCredito notaCredito,
            NotaCreditoDetalle detalle,
            int lineNumber) {
        ProductoVariante variante = detalle.getProductoVariante();
        BigDecimal cantidad = BigDecimal.valueOf(detalle.getCantidad());
        BigDecimal valorVentaBrutoConIgv = detalle.getPrecioUnitario()
                .multiply(cantidad)
                .setScale(2, RoundingMode.HALF_UP);
        TaxSchemeData taxSchemeData = taxSchemeData(detalle.getCodigoTipoAfectacionIgv());

        BigDecimal lineExtensionAmount = detalle.getSubtotal() == null
                ? BigDecimal.ZERO
                : detalle.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorUnitarioSinIgv = detalle.getCantidad() > 0
                ? lineExtensionAmount.divide(cantidad, 10, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Element line = appendElement(document, root, NS_CAC, "cac:CreditNoteLine");
        appendText(document, line, NS_CBC, "cbc:ID", String.valueOf(lineNumber));
        appendText(document, line, NS_CBC, "cbc:CreditedQuantity", String.valueOf(detalle.getCantidad()),
                "unitCode", detalle.getUnidadMedida());
        appendCurrencyAmount(document, line, "cbc:LineExtensionAmount", lineExtensionAmount, notaCredito.getMoneda());

        BigDecimal precioEfectivo = (detalle.getTotalDetalle() != null && detalle.getCantidad() > 0)
                ? detalle.getTotalDetalle().divide(cantidad, 10, RoundingMode.HALF_UP)
                : detalle.getPrecioUnitario();

        Element pricingReference = appendElement(document, line, NS_CAC, "cac:PricingReference");
        Element alternativePrice = appendElement(document, pricingReference, NS_CAC, "cac:AlternativeConditionPrice");
        appendCurrencyAmount(document, alternativePrice, "cbc:PriceAmount", precioEfectivo, notaCredito.getMoneda(), 10);
        appendText(document, alternativePrice, NS_CBC, "cbc:PriceTypeCode", "01",
                "listName", "Tipo de Precio",
                "listAgencyName", "PE:SUNAT",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16");

        Element taxTotal = appendElement(document, line, NS_CAC, "cac:TaxTotal");
        appendCurrencyAmount(document, taxTotal, "cbc:TaxAmount", detalle.getIgvDetalle(), notaCredito.getMoneda());
        Element taxSubtotal = appendElement(document, taxTotal, NS_CAC, "cac:TaxSubtotal");
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxableAmount", detalle.getSubtotal(), notaCredito.getMoneda());
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxAmount", detalle.getIgvDetalle(), notaCredito.getMoneda());
        Element taxCategory = appendElement(document, taxSubtotal, NS_CAC, "cac:TaxCategory");
        appendText(document, taxCategory, NS_CBC, "cbc:ID", taxSchemeData.categoryCode(),
                "schemeID", "UN/ECE 5305",
                "schemeName", "Tax Category Identifier",
                "schemeAgencyName", "United Nations Economic Commission for Europe");
        if (taxSchemeData.percentRequired()) {
            appendText(document, taxCategory, NS_CBC, "cbc:Percent", formatearDecimal(notaCredito.getIgvPorcentaje(), 2));
        }
        appendText(document, taxCategory, NS_CBC, "cbc:TaxExemptionReasonCode", detalle.getCodigoTipoAfectacionIgv(),
                "listAgencyName", "PE:SUNAT",
                "listName", "Afectacion del IGV",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07");
        Element taxScheme = appendElement(document, taxCategory, NS_CAC, "cac:TaxScheme");
        appendText(document, taxScheme, NS_CBC, "cbc:ID", taxSchemeData.id(),
                "schemeID", "UN/ECE 5153",
                "schemeName", "Codigo de tributos",
                "schemeAgencyName", "PE:SUNAT");
        appendText(document, taxScheme, NS_CBC, "cbc:Name", taxSchemeData.name());
        appendText(document, taxScheme, NS_CBC, "cbc:TaxTypeCode", taxSchemeData.typeCode());

        Element item = appendElement(document, line, NS_CAC, "cac:Item");
        appendText(document, item, NS_CBC, "cbc:Description", descripcionDetalle(detalle));
        if (variante != null && !isBlank(variante.getSku())) {
            Element sellersItem = appendElement(document, item, NS_CAC, "cac:SellersItemIdentification");
            appendText(document, sellersItem, NS_CBC, "cbc:ID", variante.getSku().trim());
        }

        Element price = appendElement(document, line, NS_CAC, "cac:Price");
        appendCurrencyAmount(document, price, "cbc:PriceAmount", valorUnitarioSinIgv, notaCredito.getMoneda(), 10);
    }

    private Element appendElement(Document document, Element parent, String namespace, String qualifiedName) {
        Element element = document.createElementNS(namespace, qualifiedName);
        parent.appendChild(element);
        return element;
    }

    private Element appendText(Document document, Element parent, String namespace, String qualifiedName, String value, String... attributes) {
        Element element = document.createElementNS(namespace, qualifiedName);
        element.setTextContent(value == null ? "" : value);
        for (int i = 0; i + 1 < attributes.length; i += 2) {
            element.setAttribute(attributes[i], attributes[i + 1]);
        }
        parent.appendChild(element);
        return element;
    }

    private void appendCurrencyAmount(Document document, Element parent, String qualifiedName, BigDecimal amount, String moneda) {
        appendText(document, parent, NS_CBC, qualifiedName, formatearDecimal(amount, 2), "currencyID", normalizarMoneda(moneda));
    }

    private void appendCurrencyAmount(Document document, Element parent, String qualifiedName, BigDecimal amount, String moneda, int scale) {
        appendText(document, parent, NS_CBC, qualifiedName, formatearDecimal(amount, scale), "currencyID", normalizarMoneda(moneda));
    }

    private String formatearDecimal(BigDecimal value, int scale) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.setScale(scale, RoundingMode.HALF_UP);
        return normalized.toPlainString();
    }

    private String normalizarMoneda(String moneda) {
        if (moneda == null || moneda.isBlank()) {
            return "PEN";
        }
        return moneda.trim().toUpperCase(Locale.ROOT);
    }

    private String descripcionDetalle(NotaCreditoDetalle detalle) {
        if (detalle.getDescripcion() != null && !detalle.getDescripcion().isBlank()) {
            return detalle.getDescripcion().trim();
        }
        ProductoVariante variante = detalle.getProductoVariante();
        if (variante != null && variante.getProducto() != null && variante.getProducto().getNombre() != null) {
            return variante.getProducto().getNombre().trim();
        }
        return "ITEM";
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

    private String codigoEstablecimiento(Sucursal sucursal) {
        if (sucursal == null || isBlank(sucursal.getCodigoEstablecimientoSunat())) {
            return "0000";
        }
        return sucursal.getCodigoEstablecimientoSunat().trim();
    }

    private String signatureId(NotaCredito notaCredito) {
        return "SIGN-" + notaCredito.getSucursal().getEmpresa().getRuc().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BigDecimal calcularBaseSinIgv(BigDecimal totalConIgv, BigDecimal igvPorcentaje, String codigoTipoAfectacionIgv) {
        BigDecimal total = totalConIgv == null ? BigDecimal.ZERO : totalConIgv.setScale(2, RoundingMode.HALF_UP);
        if (!afectaIgv(codigoTipoAfectacionIgv) || total.compareTo(BigDecimal.ZERO) == 0) {
            return total;
        }
        BigDecimal porcentaje = igvPorcentaje == null ? BigDecimal.valueOf(18) : igvPorcentaje;
        BigDecimal factor = BigDecimal.ONE.add(porcentaje.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        return total.divide(factor, 10, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private boolean afectaIgv(String codigoTipoAfectacionIgv) {
        if (codigoTipoAfectacionIgv == null || codigoTipoAfectacionIgv.isBlank()) {
            return true;
        }
        return codigoTipoAfectacionIgv.startsWith("1");
    }

    private TaxSchemeData taxSchemeData(String afectacion) {
        String code = afectacion == null ? "10" : afectacion.trim();
        return switch (code) {
            case "20", "21" -> new TaxSchemeData("E", "9997", "EXO", "VAT", false);
            case "30", "31", "32", "33", "34", "35", "36" -> new TaxSchemeData("O", "9998", "INA", "FRE", false);
            case "40" -> new TaxSchemeData("G", "9995", "EXP", "FRE", false);
            default -> new TaxSchemeData("S", "1000", "IGV", "VAT", true);
        };
    }

    private record TaxSchemeData(
            String categoryCode,
            String id,
            String name,
            String typeCode,
            boolean percentRequired) {
    }
}
