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
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatXmlBuilderService {

    private static final String NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final DateTimeFormatter ISSUE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISSUE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SunatMontoTextoService sunatMontoTextoService;

    public Document build(Venta venta, List<VentaDetalle> detalles) {
        validarEntrada(venta, detalles);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element root = document.createElementNS(NS_INVOICE, "Invoice");
            root.setAttribute("xmlns", NS_INVOICE);
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
            appendText(document, root, NS_CBC, "cbc:ProfileID", "0101",
                    "schemeName", "Tipo de Operacion",
                    "schemeAgencyName", "PE:SUNAT",
                    "schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo51");
            appendText(document, root, NS_CBC, "cbc:ID", SunatComprobanteHelper.numeroComprobante(venta));
            appendText(document, root, NS_CBC, "cbc:IssueDate", venta.getFecha().toLocalDate().format(ISSUE_DATE));
            appendText(document, root, NS_CBC, "cbc:IssueTime", venta.getFecha().toLocalTime().format(ISSUE_TIME));
            appendText(document, root, NS_CBC, "cbc:DueDate", venta.getFecha().toLocalDate().format(ISSUE_DATE));
            appendText(document, root, NS_CBC, "cbc:InvoiceTypeCode",
                    SunatComprobanteHelper.codigoTipoComprobante(venta.getTipoComprobante()),
                    "listAgencyName", "PE:SUNAT",
                    "listName", "Tipo de Documento",
                    "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01",
                    "listID", "0101",
                    "listSchemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo51");
            appendText(document, root, NS_CBC, "cbc:Note",
                    sunatMontoTextoService.montoEnLetras(venta.getTotal(), venta.getMoneda()),
                    "languageLocaleID", "1000");
            appendText(document, root, NS_CBC, "cbc:DocumentCurrencyCode", normalizarMoneda(venta.getMoneda()),
                    "listID", "ISO 4217 Alpha",
                    "listName", "Currency",
                    "listAgencyName", "United Nations Economic Commission for Europe");
            appendText(document, root, NS_CBC, "cbc:LineCountNumeric", String.valueOf(detalles.size()));

            appendSignatureBlock(document, root, venta);
            appendSupplier(document, root, venta);
            appendCustomer(document, root, venta);
            appendPaymentTerms(document, root, venta);
            appendDocumentTaxTotal(document, root, venta, detalles);
            appendMonetaryTotal(document, root, venta, detalles);

            int lineNumber = 1;
            for (VentaDetalle detalle : detalles) {
                appendInvoiceLine(document, root, venta, detalle, lineNumber++);
            }

            return document;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir el XML UBL para SUNAT");
        }
    }

    private void validarEntrada(Venta venta, List<VentaDetalle> detalles) {
        if (venta == null) {
            throw new RuntimeException("No hay venta para construir XML SUNAT");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("La venta no tiene detalles para construir XML SUNAT");
        }
        if (venta.getFecha() == null) {
            throw new RuntimeException("La venta no tiene fecha de emision");
        }

        Sucursal sucursal = venta.getSucursal();
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        if (empresa == null) {
            throw new RuntimeException("La venta no tiene empresa emisora");
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

        Cliente cliente = venta.getCliente();
        if (cliente == null) {
            throw new RuntimeException("La venta no tiene cliente para emitir comprobante electronico");
        }
        if (isBlank(cliente.getNombres())) {
            throw new RuntimeException("El cliente debe tener nombre o razon social");
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

    private void appendSignatureBlock(Document document, Element root, Venta venta) {
        String signatureId = signatureId(venta);
        Empresa empresa = venta.getSucursal().getEmpresa();

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

    private void appendSupplier(Document document, Element root, Venta venta) {
        Sucursal sucursal = venta.getSucursal();
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

    private void appendCustomer(Document document, Element root, Venta venta) {
        Cliente cliente = venta.getCliente();
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

    private void appendPaymentTerms(Document document, Element root, Venta venta) {
        String tipoComprobante = venta.getTipoComprobante() == null ? "" : venta.getTipoComprobante().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"FACTURA".equals(tipoComprobante)) {
            return;
        }

        String formaPago = venta.getFormaPago() != null ? venta.getFormaPago().trim().toUpperCase(java.util.Locale.ROOT) : "CONTADO";
        String paymentMeansID = "CREDITO".equals(formaPago) ? "Credito" : "Contado";

        Element paymentTerms = appendElement(document, root, NS_CAC, "cac:PaymentTerms");
        appendText(document, paymentTerms, NS_CBC, "cbc:ID", "FormaPago");
        appendText(document, paymentTerms, NS_CBC, "cbc:PaymentMeansID", paymentMeansID);
    }

    private void appendDocumentTaxTotal(Document document, Element root, Venta venta, List<VentaDetalle> detalles) {
        BigDecimal taxAmount = detalles.stream()
            .map(VentaDetalle::getIgvDetalle)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = detalles.stream()
            .map(VentaDetalle::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        TaxSchemeData taxSchemeData = taxSchemeData(detalles.get(0).getCodigoTipoAfectacionIgv());

        Element taxTotal = appendElement(document, root, NS_CAC, "cac:TaxTotal");
        appendCurrencyAmount(document, taxTotal, "cbc:TaxAmount", taxAmount, venta.getMoneda());

        Element taxSubtotal = appendElement(document, taxTotal, NS_CAC, "cac:TaxSubtotal");
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxableAmount", taxableAmount, venta.getMoneda());
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxAmount", taxAmount, venta.getMoneda());
        Element taxCategory = appendElement(document, taxSubtotal, NS_CAC, "cac:TaxCategory");
        appendText(document, taxCategory, NS_CBC, "cbc:ID", taxSchemeData.categoryCode(),
                "schemeID", "UN/ECE 5305",
                "schemeName", "Tax Category Identifier",
                "schemeAgencyName", "United Nations Economic Commission for Europe");
        if (taxSchemeData.percentRequired()) {
            appendText(document, taxCategory, NS_CBC, "cbc:Percent", formatearDecimal(venta.getIgvPorcentaje(), 2));
        }
        appendText(document, taxCategory, NS_CBC, "cbc:TaxExemptionReasonCode", detalles.get(0).getCodigoTipoAfectacionIgv(),
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

    private void appendMonetaryTotal(Document document, Element root, Venta venta, List<VentaDetalle> detalles) {
        BigDecimal lineExtensionAmount = detalles.stream()
                .map(VentaDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = detalles.stream()
            .map(VentaDetalle::getIgvDetalle)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxInclusiveAmount = lineExtensionAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        Element monetaryTotal = appendElement(document, root, NS_CAC, "cac:LegalMonetaryTotal");
        appendCurrencyAmount(document, monetaryTotal, "cbc:LineExtensionAmount", lineExtensionAmount, venta.getMoneda());
        appendCurrencyAmount(document, monetaryTotal, "cbc:TaxInclusiveAmount", taxInclusiveAmount, venta.getMoneda());
        appendCurrencyAmount(document, monetaryTotal, "cbc:PayableAmount", venta.getTotal(), venta.getMoneda());
    }

    private void appendInvoiceLine(Document document, Element root, Venta venta, VentaDetalle detalle, int lineNumber) {
        ProductoVariante variante = detalle.getProductoVariante();
        BigDecimal cantidad = BigDecimal.valueOf(detalle.getCantidad());
        BigDecimal valorVentaBrutoConIgv = detalle.getPrecioUnitario()
                .multiply(cantidad)
                .setScale(2, RoundingMode.HALF_UP);
        TaxSchemeData taxSchemeData = taxSchemeData(detalle.getCodigoTipoAfectacionIgv());

        BigDecimal lineExtensionAmount = detalle.getSubtotal() == null
            ? BigDecimal.ZERO
            : detalle.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseSinIgvAntesDescuento = calcularBaseSinIgv(
            valorVentaBrutoConIgv,
            venta.getIgvPorcentaje(),
            detalle.getCodigoTipoAfectacionIgv());
        if (baseSinIgvAntesDescuento.compareTo(lineExtensionAmount) < 0) {
            baseSinIgvAntesDescuento = lineExtensionAmount;
        }

        BigDecimal descuentoSinIgv = baseSinIgvAntesDescuento
            .subtract(lineExtensionAmount)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal factorDescuento = baseSinIgvAntesDescuento.compareTo(BigDecimal.ZERO) > 0
            ? descuentoSinIgv.divide(baseSinIgvAntesDescuento, 5, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal valorUnitarioSinIgvAntesDescuento = detalle.getCantidad() > 0
            ? baseSinIgvAntesDescuento.divide(cantidad, 10, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        Element line = appendElement(document, root, NS_CAC, "cac:InvoiceLine");
        appendText(document, line, NS_CBC, "cbc:ID", String.valueOf(lineNumber));
        appendText(document, line, NS_CBC, "cbc:InvoicedQuantity", String.valueOf(detalle.getCantidad()),
                "unitCode", detalle.getUnidadMedida());
        appendCurrencyAmount(document, line, "cbc:LineExtensionAmount", lineExtensionAmount, venta.getMoneda());

        // Precio de venta unitario con IGV despues del descuento (cat.16 tipo 01)
        BigDecimal precioEfectivo = (detalle.getTotalDetalle() != null && detalle.getCantidad() > 0)
                ? detalle.getTotalDetalle().divide(cantidad, 10, RoundingMode.HALF_UP)
                : detalle.getPrecioUnitario();

        Element pricingReference = appendElement(document, line, NS_CAC, "cac:PricingReference");
        Element alternativePrice = appendElement(document, pricingReference, NS_CAC, "cac:AlternativeConditionPrice");
        appendCurrencyAmount(document, alternativePrice, "cbc:PriceAmount", precioEfectivo, venta.getMoneda(), 10);
        appendText(document, alternativePrice, NS_CBC, "cbc:PriceTypeCode", "01",
                "listName", "Tipo de Precio",
                "listAgencyName", "PE:SUNAT",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16");

        if (descuentoSinIgv.compareTo(BigDecimal.ZERO) > 0) {
            Element allowance = appendElement(document, line, NS_CAC, "cac:AllowanceCharge");
            appendText(document, allowance, NS_CBC, "cbc:ChargeIndicator", "false");
            appendText(document, allowance, NS_CBC, "cbc:AllowanceChargeReasonCode", "00",
                "listAgencyName", "PE:SUNAT",
                "listName", "Cargo/descuento",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53");
            appendText(document, allowance, NS_CBC, "cbc:MultiplierFactorNumeric", formatearDecimal(factorDescuento, 5));
            appendCurrencyAmount(document, allowance, "cbc:Amount", descuentoSinIgv, venta.getMoneda());
            appendCurrencyAmount(document, allowance, "cbc:BaseAmount", baseSinIgvAntesDescuento, venta.getMoneda());
        }

        Element taxTotal = appendElement(document, line, NS_CAC, "cac:TaxTotal");
        appendCurrencyAmount(document, taxTotal, "cbc:TaxAmount", detalle.getIgvDetalle(), venta.getMoneda());
        Element taxSubtotal = appendElement(document, taxTotal, NS_CAC, "cac:TaxSubtotal");
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxableAmount", detalle.getSubtotal(), venta.getMoneda());
        appendCurrencyAmount(document, taxSubtotal, "cbc:TaxAmount", detalle.getIgvDetalle(), venta.getMoneda());
        Element taxCategory = appendElement(document, taxSubtotal, NS_CAC, "cac:TaxCategory");
        appendText(document, taxCategory, NS_CBC, "cbc:ID", taxSchemeData.categoryCode(),
                "schemeID", "UN/ECE 5305",
                "schemeName", "Tax Category Identifier",
                "schemeAgencyName", "United Nations Economic Commission for Europe");
        if (taxSchemeData.percentRequired()) {
            appendText(document, taxCategory, NS_CBC, "cbc:Percent", formatearDecimal(venta.getIgvPorcentaje(), 2));
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
        appendCurrencyAmount(document, price, "cbc:PriceAmount", valorUnitarioSinIgvAntesDescuento, venta.getMoneda(), 10);
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

    private String descripcionDetalle(VentaDetalle detalle) {
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

    private String signatureId(Venta venta) {
        return "SIGN-" + venta.getSucursal().getEmpresa().getRuc().trim();
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
