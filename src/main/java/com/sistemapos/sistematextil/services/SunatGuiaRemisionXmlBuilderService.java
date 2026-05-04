package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.model.GuiaRemisionConductor;
import com.sistemapos.sistematextil.model.GuiaRemisionDetalle;
import com.sistemapos.sistematextil.model.GuiaRemisionDocumentoRelacionado;
import com.sistemapos.sistematextil.model.GuiaRemisionTransportista;
import com.sistemapos.sistematextil.model.GuiaRemisionVehiculo;
import com.sistemapos.sistematextil.model.Sucursal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatGuiaRemisionXmlBuilderService {

    private static final String NS_DESPATCH = "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2";
    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final DateTimeFormatter ISSUE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISSUE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public Document build(
            GuiaRemision guia,
            List<GuiaRemisionDetalle> detalles,
            List<GuiaRemisionDocumentoRelacionado> documentosRelacionados,
            List<GuiaRemisionConductor> conductores,
            List<GuiaRemisionTransportista> transportistas,
            List<GuiaRemisionVehiculo> vehiculos) {

        validarEntrada(guia, detalles);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element root = document.createElementNS(NS_DESPATCH, "DespatchAdvice");
            root.setAttribute("xmlns", NS_DESPATCH);
            root.setAttribute("xmlns:cac", NS_CAC);
            root.setAttribute("xmlns:cbc", NS_CBC);
            root.setAttribute("xmlns:ext", NS_EXT);
            root.setAttribute("xmlns:ds", NS_DS);
            document.appendChild(root);

            appendExtensions(document, root);
            appendText(document, root, NS_CBC, "cbc:UBLVersionID", "2.1");
            appendText(document, root, NS_CBC, "cbc:CustomizationID", "2.0");
            appendText(document, root, NS_CBC, "cbc:ID", numeroGuia(guia));
            appendText(document, root, NS_CBC, "cbc:IssueDate",
                    guia.getFechaEmision().toLocalDate().format(ISSUE_DATE));
            appendText(document, root, NS_CBC, "cbc:IssueTime",
                    guia.getFechaEmision().toLocalTime().format(ISSUE_TIME));
            appendText(document, root, NS_CBC, "cbc:DespatchAdviceTypeCode", "09",
                    "listAgencyName", "PE:SUNAT",
                    "listName", "Tipo de Documento",
                    "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");

            if (guia.getObservaciones() != null && !guia.getObservaciones().isBlank()) {
                appendText(document, root, NS_CBC, "cbc:Note", guia.getObservaciones().trim());
            }

            appendDocumentosRelacionados(document, root, guia, documentosRelacionados);
            appendSignatureBlock(document, root, guia);
            appendDespatchSupplier(document, root, guia);
            appendDeliveryCustomer(document, root, guia);
            appendShipment(document, root, guia, conductores, transportistas, vehiculos);

            int lineNumber = 1;
            for (GuiaRemisionDetalle detalle : detalles) {
                appendDespatchLine(document, root, detalle, lineNumber++);
            }

            return document;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir el XML UBL de la guia de remision");
        }
    }

    private void validarEntrada(GuiaRemision guia, List<GuiaRemisionDetalle> detalles) {
        if (guia == null) {
            throw new RuntimeException("No hay guia de remision para construir XML SUNAT");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new RuntimeException("La guia de remision no tiene detalles para construir XML SUNAT");
        }
        if (guia.getFechaEmision() == null) {
            throw new RuntimeException("La guia de remision no tiene fecha de emision");
        }
        if (isBlank(guia.getMotivoTraslado())) {
            throw new RuntimeException("La guia de remision no tiene motivo de traslado");
        }
        if (!List.of("01", "02", "03", "04", "05", "06", "07", "13", "14", "17")
                .contains(guia.getMotivoTraslado().trim())) {
            throw new RuntimeException("Motivo de traslado no permitido para este modulo");
        }
        Sucursal sucursal = guia.getSucursal();
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        if (empresa == null) {
            throw new RuntimeException("La guia de remision no tiene empresa emisora");
        }
        if (isBlank(empresa.getRuc()) || empresa.getRuc().trim().length() != 11) {
            throw new RuntimeException("La empresa emisora debe tener RUC de 11 digitos");
        }
        if (isBlank(empresa.getRazonSocial())) {
            throw new RuntimeException("La empresa emisora debe tener razon social");
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

    private void appendSignatureBlock(Document document, Element root, GuiaRemision guia) {
        Empresa empresa = guia.getSucursal().getEmpresa();
        String signatureId = "SIGN-" + empresa.getRuc().trim();

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

    private void appendDespatchSupplier(Document document, Element root, GuiaRemision guia) {
        Empresa empresa = guia.getSucursal().getEmpresa();

        Element supplier = appendElement(document, root, NS_CAC, "cac:DespatchSupplierParty");
        Element party = appendElement(document, supplier, NS_CAC, "cac:Party");

        Element partyIdentification = appendElement(document, party, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", empresa.getRuc().trim(),
                "schemeID", "6",
                "schemeName", "Documento de Identidad",
                "schemeAgencyName", "PE:SUNAT",
                "schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");

        Element partyLegalEntity = appendElement(document, party, NS_CAC, "cac:PartyLegalEntity");
        appendText(document, partyLegalEntity, NS_CBC, "cbc:RegistrationName", empresa.getRazonSocial().trim());
    }

    private void appendDocumentosRelacionados(
            Document document,
            Element root,
            GuiaRemision guia,
            List<GuiaRemisionDocumentoRelacionado> documentosRelacionados) {
        if (documentosRelacionados == null || documentosRelacionados.isEmpty()) {
            return;
        }
        Empresa empresa = guia.getSucursal().getEmpresa();
        for (GuiaRemisionDocumentoRelacionado relacionado : documentosRelacionados) {
            if (relacionado == null || isBlank(relacionado.getTipoDocumento())
                    || isBlank(relacionado.getSerie()) || isBlank(relacionado.getNumero())) {
                continue;
            }
            Element reference = appendElement(document, root, NS_CAC, "cac:AdditionalDocumentReference");
            appendText(document, reference, NS_CBC, "cbc:ID",
                    relacionado.getSerie().trim() + "-" + relacionado.getNumero().trim());
            appendText(document, reference, NS_CBC, "cbc:DocumentTypeCode",
                    relacionado.getTipoDocumento().trim(),
                    "listAgencyName", "PE:SUNAT",
                    "listName", "Documento relacionado al transporte",
                    "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo61");
            appendText(document, reference, NS_CBC, "cbc:DocumentType",
                    descripcionDocumentoRelacionado(relacionado.getTipoDocumento()));
            appendDocumentoRelacionadoIssuerParty(document, reference, empresa);
        }
    }

    private String descripcionDocumentoRelacionado(String tipoDocumento) {
        return switch (tipoDocumento == null ? "" : tipoDocumento.trim()) {
            case "01" -> "Factura";
            case "03" -> "Boleta de venta";
            case "04" -> "Liquidacion de compra";
            default -> "Documento relacionado";
        };
    }

    private void appendDocumentoRelacionadoIssuerParty(Document document, Element reference, Empresa empresa) {
        Element issuerParty = appendElement(document, reference, NS_CAC, "cac:IssuerParty");
        Element partyIdentification = appendElement(document, issuerParty, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", empresa.getRuc().trim(),
                "schemeID", "6",
                "schemeName", "Documento de Identidad",
                "schemeAgencyName", "PE:SUNAT",
                "schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");
    }

    private void appendDeliveryCustomer(Document document, Element root, GuiaRemision guia) {
        Element customer = appendElement(document, root, NS_CAC, "cac:DeliveryCustomerParty");
        Element party = appendElement(document, customer, NS_CAC, "cac:Party");

        Element partyIdentification = appendElement(document, party, NS_CAC, "cac:PartyIdentification");
        appendText(document, partyIdentification, NS_CBC, "cbc:ID", guia.getDestinatarioNroDoc().trim(),
                "schemeID", guia.getDestinatarioTipoDoc().trim(),
                "schemeName", "Documento de Identidad",
                "schemeAgencyName", "PE:SUNAT",
                "schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");

        Element partyLegalEntity = appendElement(document, party, NS_CAC, "cac:PartyLegalEntity");
        appendText(document, partyLegalEntity, NS_CBC, "cbc:RegistrationName",
                guia.getDestinatarioRazonSocial().trim());
    }

    private void appendShipment(Document document, Element root, GuiaRemision guia,
            List<GuiaRemisionConductor> conductores,
            List<GuiaRemisionTransportista> transportistas,
            List<GuiaRemisionVehiculo> vehiculos) {

        Element shipment = appendElement(document, root, NS_CAC, "cac:Shipment");
        appendText(document, shipment, NS_CBC, "cbc:ID", "1");
        appendText(document, shipment, NS_CBC, "cbc:HandlingCode", guia.getMotivoTraslado().trim(),
                "listAgencyName", "PE:SUNAT",
                "listName", "Motivo de traslado",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo20");
        if (!isBlank(guia.getDescripcionMotivo())) {
            appendText(document, shipment, NS_CBC, "cbc:HandlingInstructions",
                    guia.getDescripcionMotivo().trim());
        }

        appendText(document, shipment, NS_CBC, "cbc:GrossWeightMeasure",
                formatearDecimal(guia.getPesoBrutoTotal(), 3),
                "unitCode", normalizeUnitCode(guia.getUnidadPeso(), "KGM"));

        if (guia.getNumeroBultos() != null && guia.getNumeroBultos() > 0) {
            appendText(document, shipment, NS_CBC, "cbc:TotalTransportHandlingUnitQuantity",
                    String.valueOf(guia.getNumeroBultos()));
        }

        // ShipmentStage - modalidad and transport data
        Element shipmentStage = appendElement(document, shipment, NS_CAC, "cac:ShipmentStage");
        appendText(document, shipmentStage, NS_CBC, "cbc:TransportModeCode",
                guia.getModalidadTransporte().trim(),
                "listName", "Modalidad de traslado",
                "listAgencyName", "PE:SUNAT",
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo18");

        Element transitPeriod = appendElement(document, shipmentStage, NS_CAC, "cac:TransitPeriod");
        appendText(document, transitPeriod, NS_CBC, "cbc:StartDate",
                guia.getFechaInicioTraslado().format(ISSUE_DATE));

        // Carrier (transportista) for modalidad 01 (public)
        if ("01".equals(guia.getModalidadTransporte().trim()) && transportistas != null) {
            for (GuiaRemisionTransportista t : transportistas) {
                Element carrierParty = appendElement(document, shipmentStage, NS_CAC, "cac:CarrierParty");
                Element partyIdent = appendElement(document, carrierParty, NS_CAC, "cac:PartyIdentification");
                appendText(document, partyIdent, NS_CBC, "cbc:ID", t.getTransportistaNroDoc().trim(),
                        "schemeID", t.getTransportistaTipoDoc().trim());
                Element partyLegal = appendElement(document, carrierParty, NS_CAC, "cac:PartyLegalEntity");
                appendText(document, partyLegal, NS_CBC, "cbc:RegistrationName",
                        t.getTransportistaRazonSocial().trim());
                if (!isBlank(t.getTransportistaRegistroMtc())) {
                    appendText(document, partyLegal, NS_CBC, "cbc:CompanyID",
                            t.getTransportistaRegistroMtc().trim());
                }
            }
        }

        // Driver (conductor) for modalidad 02 (private)
        if ("02".equals(guia.getModalidadTransporte().trim()) && conductores != null) {
            for (GuiaRemisionConductor c : conductores) {
                Element driverPerson = appendElement(document, shipmentStage, NS_CAC, "cac:DriverPerson");
                appendText(document, driverPerson, NS_CBC, "cbc:ID", c.getNroDocumento().trim(),
                        "schemeID", c.getTipoDocumento().trim(),
                        "schemeName", "Documento de Identidad",
                        "schemeAgencyName", "PE:SUNAT",
                        "schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");
                appendText(document, driverPerson, NS_CBC, "cbc:FirstName", c.getNombres().trim());
                appendText(document, driverPerson, NS_CBC, "cbc:FamilyName", c.getApellidos().trim());
                appendText(document, driverPerson, NS_CBC, "cbc:JobTitle",
                        Boolean.TRUE.equals(c.getEsPrincipal()) ? "Principal" : "Secundario");
                Element driverIdent = appendElement(document, driverPerson, NS_CAC, "cac:IdentityDocumentReference");
                appendText(document, driverIdent, NS_CBC, "cbc:ID", c.getLicencia().trim());
            }
        }

        // Delivery address
        Element delivery = appendElement(document, shipment, NS_CAC, "cac:Delivery");
        Element deliveryAddress = appendElement(document, delivery, NS_CAC, "cac:DeliveryAddress");
        appendText(document, deliveryAddress, NS_CBC, "cbc:ID", guia.getUbigeoLlegada().trim(),
                "schemeName", "Ubigeos",
                "schemeAgencyName", "PE:INEI");
        appendAddressTypeCode(document, deliveryAddress, guia.getSucursalLlegada(), guia);
        Element addressLineLlegada = appendElement(document, deliveryAddress, NS_CAC, "cac:AddressLine");
        appendText(document, addressLineLlegada, NS_CBC, "cbc:Line", guia.getDireccionLlegada().trim());

        // Despatch address (origin/partida) according to SUNAT GRE structure
        Element despatch = appendElement(document, delivery, NS_CAC, "cac:Despatch");
        Element despatchAddress = appendElement(document, despatch, NS_CAC, "cac:DespatchAddress");
        appendText(document, despatchAddress, NS_CBC, "cbc:ID", guia.getUbigeoPartida().trim(),
                "schemeName", "Ubigeos",
                "schemeAgencyName", "PE:INEI");
        appendAddressTypeCode(document, despatchAddress, guia.getSucursalPartida(), guia);
        Element addressLinePartida = appendElement(document, despatchAddress, NS_CAC, "cac:AddressLine");
        appendText(document, addressLinePartida, NS_CBC, "cbc:Line", guia.getDireccionPartida().trim());

        // Vehicles for modalidad 02 (private)
        if ("02".equals(guia.getModalidadTransporte().trim()) && vehiculos != null) {
            for (GuiaRemisionVehiculo v : vehiculos) {
                Element transportHandlingUnit = appendElement(document, shipment, NS_CAC,
                        "cac:TransportHandlingUnit");
                Element transportEquipment = appendElement(document, transportHandlingUnit, NS_CAC,
                        "cac:TransportEquipment");
                appendText(document, transportEquipment, NS_CBC, "cbc:ID", v.getPlaca().trim());
            }
        }
    }

    private void appendAddressTypeCode(Document document, Element address, Sucursal sucursal, GuiaRemision guia) {
        String codigoEstablecimiento = sucursal != null ? sucursal.getCodigoEstablecimientoSunat() : null;
        String ruc = guia.getSucursal() != null && guia.getSucursal().getEmpresa() != null
                ? guia.getSucursal().getEmpresa().getRuc()
                : null;
        if (!isBlank(codigoEstablecimiento) && !isBlank(ruc)) {
            appendText(document, address, NS_CBC, "cbc:AddressTypeCode", codigoEstablecimiento.trim(),
                    "listAgencyName", "PE:SUNAT",
                    "listName", "Establecimientos anexos",
                    "listID", ruc.trim());
        }
    }

    private void appendDespatchLine(Document document, Element root, GuiaRemisionDetalle detalle, int lineNumber) {
        Element line = appendElement(document, root, NS_CAC, "cac:DespatchLine");
        appendText(document, line, NS_CBC, "cbc:ID", String.valueOf(lineNumber));
        appendText(document, line, NS_CBC, "cbc:DeliveredQuantity",
                formatearDecimal(detalle.getCantidad(), 3),
                "unitCode", normalizeUnitCode(detalle.getUnidadMedida(), "NIU"));

        Element orderLineReference = appendElement(document, line, NS_CAC, "cac:OrderLineReference");
        appendText(document, orderLineReference, NS_CBC, "cbc:LineID", String.valueOf(lineNumber));

        Element item = appendElement(document, line, NS_CAC, "cac:Item");
        appendText(document, item, NS_CBC, "cbc:Description",
                detalle.getDescripcion() != null ? detalle.getDescripcion().trim() : "ITEM");

        if (!isBlank(detalle.getCodigoProducto())) {
            Element sellersItem = appendElement(document, item, NS_CAC, "cac:SellersItemIdentification");
            appendText(document, sellersItem, NS_CBC, "cbc:ID", detalle.getCodigoProducto().trim());
        } else if (detalle.getProductoVariante() != null && !isBlank(detalle.getProductoVariante().getSku())) {
            Element sellersItem = appendElement(document, item, NS_CAC, "cac:SellersItemIdentification");
            appendText(document, sellersItem, NS_CBC, "cbc:ID", detalle.getProductoVariante().getSku().trim());
        }
    }

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

    private String formatearDecimal(BigDecimal value, int scale) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO
                : value.setScale(scale, java.math.RoundingMode.HALF_UP);
        return normalized.toPlainString();
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

    public static String numeroGuia(GuiaRemision guia) {
        String serie = guia.getSerie() == null ? "" : guia.getSerie().trim();
        String correlativo = guia.getCorrelativo() == null
                ? ""
                : String.format(java.util.Locale.ROOT, "%08d", guia.getCorrelativo());
        if (serie.isBlank()) return correlativo;
        if (correlativo.isBlank()) return serie;
        return serie + "-" + correlativo;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeUnitCode(String value, String defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
