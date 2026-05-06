package com.sistemapos.sistematextil.services;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.NodeList;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.model.GuiaRemisionConductor;
import com.sistemapos.sistematextil.model.GuiaRemisionDetalle;
import com.sistemapos.sistematextil.model.GuiaRemisionDocumentoRelacionado;
import com.sistemapos.sistematextil.model.GuiaRemisionTransportista;
import com.sistemapos.sistematextil.model.GuiaRemisionVehiculo;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.GuiaRemisionConductorRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionDetalleRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionDocumentoRelacionadoRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionTransportistaRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionVehiculoRepository;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuiaRemisionPdfService {

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color COLOR_PRIMARIO = new Color(60, 76, 102);
    private static final Color COLOR_FONDO_SECCION = new Color(245, 247, 250);
    private static final Color COLOR_FILA_PAR = new Color(247, 249, 252);
    private static final Color COLOR_BORDE = new Color(225, 230, 236);

    private final GuiaRemisionRepository guiaRemisionRepository;
    private final GuiaRemisionDetalleRepository detalleRepository;
    private final GuiaRemisionDocumentoRelacionadoRepository documentoRelacionadoRepository;
    private final GuiaRemisionConductorRepository conductorRepository;
    private final GuiaRemisionTransportistaRepository transportistaRepository;
    private final GuiaRemisionVehiculoRepository vehiculoRepository;
    private final S3StorageService s3StorageService;
    private final SunatDocumentStorageService sunatDocumentStorageService;

    @Transactional(readOnly = true)
    public byte[] generarPdfA4(Integer idGuiaRemision) {
        GuiaRemision guia = guiaRemisionRepository
                .findByIdGuiaRemisionAndDeletedAtIsNull(idGuiaRemision)
                .orElseThrow(() -> new RuntimeException("Guia de remision no encontrada: " + idGuiaRemision));

        return generarPdfA4(guia);
    }

    @Transactional(readOnly = true)
    public byte[] generarPdfA4(GuiaRemision guia) {
        if (guia == null || guia.getIdGuiaRemision() == null) {
            throw new RuntimeException("Guia de remision no encontrada");
        }
        Integer idGuiaRemision = guia.getIdGuiaRemision();

        List<GuiaRemisionDetalle> detalles = detalleRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaRemisionDetalleAsc(idGuiaRemision);
        List<GuiaRemisionDocumentoRelacionado> documentosRelacionados = documentoRelacionadoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaDocumentoRelacionadoAsc(
                        idGuiaRemision);
        List<GuiaRemisionConductor> conductores = conductorRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(idGuiaRemision);
        List<GuiaRemisionTransportista> transportistas = transportistaRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(idGuiaRemision);
        List<GuiaRemisionVehiculo> vehiculos = vehiculoRepository
                .findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByEsPrincipalDesc(idGuiaRemision);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40f, 40f, 28f, 28f);
            PdfWriter.getInstance(document, output);
            document.open();

            agregarCabecera(document, guia);
            document.add(crearEspaciador(4f));
            agregarDestinatario(document, guia);
            document.add(crearEspaciador(4f));
            agregarDatosTraslado(document, guia);
            document.add(crearEspaciador(4f));
            agregarOrigenDestino(document, guia);
            if (!documentosRelacionados.isEmpty()) {
                document.add(crearEspaciador(4f));
                agregarDocumentosRelacionados(document, documentosRelacionados);
            }
            document.add(crearEspaciador(4f));
            agregarTransporte(document, guia, transportistas, conductores, vehiculos);
            document.add(crearEspaciador(4f));
            agregarTablaBienes(document, detalles);
            document.add(crearEspaciador(5f));
            agregarPie(document, guia);

            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("No se pudo generar el PDF de la guia de remision");
        }
    }

    private void agregarCabecera(Document document, GuiaRemision guia) throws DocumentException {
        Sucursal sucursal = guia.getSucursal();
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        String nombre = empresa != null ? v(empresa.getNombre()) : "";
        String razonSocial = empresa != null ? v(empresa.getRazonSocial()) : "";
        String ruc = empresa != null ? v(empresa.getRuc()) : "";
        String direccion = empresa != null ? v(empresa.getDireccion()) : "";
        String ubicacion = ubicacion(empresa);
        String dirCompleta = direccion + (!direccion.isBlank() && !ubicacion.isBlank() ? " - " + ubicacion : ubicacion);
        String telefono = empresa != null ? v(empresa.getTelefono()) : "";
        String correo = empresa != null ? v(empresa.getCorreo()) : "";

        PdfPTable header = new PdfPTable(new float[] { 6.4f, 3.6f });
        header.setWidthPercentage(100);

        PdfPCell empresaCell = celda(Rectangle.NO_BORDER, 0f);
        empresaCell.setPaddingRight(18f);
        Image logo = cargarLogo(guia);
        if (logo != null) {
            PdfPTable wrap = new PdfPTable(new float[] { 2.2f, 4.2f });
            wrap.setWidthPercentage(100);
            PdfPCell logoCell = celda(Rectangle.NO_BORDER, 0f);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setPaddingRight(8f);
            logo.scaleToFit(120f, 60f);
            logo.setAlignment(Element.ALIGN_CENTER);
            logoCell.addElement(logo);
            wrap.addCell(logoCell);
            PdfPCell datosCell = celda(Rectangle.NO_BORDER, 0f);
            datosCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            datosCell.setPaddingTop(4f);
            rellenarDatosEmpresa(datosCell, nombre, razonSocial, ruc, dirCompleta, telefono, correo);
            wrap.addCell(datosCell);
            empresaCell.addElement(wrap);
        } else {
            rellenarDatosEmpresa(empresaCell, nombre, razonSocial, ruc, dirCompleta, telefono, correo);
        }
        header.addCell(empresaCell);

        PdfPCell tipoCell = celda(Rectangle.BOX, 12f);
        tipoCell.setBorderColor(COLOR_PRIMARIO);
        tipoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tipoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (!ruc.isBlank()) {
            Paragraph pRuc = new Paragraph("RUC: " + ruc, fuente(true, 9.5f, COLOR_PRIMARIO));
            pRuc.setAlignment(Element.ALIGN_CENTER);
            pRuc.setSpacingAfter(6f);
            tipoCell.addElement(pRuc);
        }
        Paragraph pTipo = new Paragraph("GUIA DE REMISION\nREMITENTE ELECTRONICA",
                fuente(true, 11f, COLOR_PRIMARIO));
        pTipo.setAlignment(Element.ALIGN_CENTER);
        tipoCell.addElement(pTipo);
        Paragraph pNum = new Paragraph(numeroGuia(guia), fuente(true, 13f));
        pNum.setAlignment(Element.ALIGN_CENTER);
        pNum.setSpacingBefore(6f);
        tipoCell.addElement(pNum);
        header.addCell(tipoCell);

        document.add(header);
    }

    private void rellenarDatosEmpresa(PdfPCell cell, String nombre, String razonSocial, String ruc,
            String direccion, String telefono, String correo) {
        String nombreMostrar = !nombre.isBlank() ? nombre : razonSocial;
        if (!nombreMostrar.isBlank()) {
            Paragraph p = new Paragraph(nombreMostrar, fuente(true, 11f, COLOR_PRIMARIO));
            p.setSpacingAfter(2f);
            cell.addElement(p);
        }
        if (!razonSocial.isBlank() && !razonSocial.equalsIgnoreCase(nombre)) {
            cell.addElement(new Paragraph(razonSocial, fuente(false, 8f)));
        }
        if (!direccion.isBlank()) {
            cell.addElement(new Paragraph(direccion, fuente(false, 8f)));
        }
        if (!telefono.isBlank()) {
            cell.addElement(new Paragraph("Tel: " + telefono, fuente(false, 8f)));
        }
        if (!correo.isBlank()) {
            cell.addElement(new Paragraph(correo, fuente(false, 8f)));
        }
    }

    private void agregarDatosTraslado(Document document, GuiaRemision guia) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 2.2f, 2.8f, 2.2f, 2.8f });
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(0f);
        tabla.setSpacingAfter(0f);

        String fechaTraslado = guia.getFechaInicioTraslado() != null ? guia.getFechaInicioTraslado().format(FMT_FECHA)
                : "-";
        String motivo = describirMotivo(v(guia.getMotivoTraslado()));
        String modalidad = describirModalidad(v(guia.getModalidadTransporte()));
        String peso = guia.getPesoBrutoTotal() != null
                ? guia.getPesoBrutoTotal().stripTrailingZeros().toPlainString() + " " + valorGuia(v(guia.getUnidadPeso()))
                : "-";
        String bultos = guia.getNumeroBultos() != null ? String.valueOf(guia.getNumeroBultos()) : "-";

        agregarFilaDatoDoble(tabla, "MOTIVO DE TRASLADO:", motivo, "FECHA DE INICIO:", fechaTraslado);
        agregarFilaDatoDoble(tabla, "MODALIDAD:", modalidad, "NRO. BULTOS:", bultos);
        agregarFilaDatoSimple(tabla, "PESO BRUTO TOTAL:", peso);
        if (!v(guia.getDescripcionMotivo()).isBlank()) {
            agregarFilaDatoSimple(tabla, "DESCRIPCION MOTIVO:", v(guia.getDescripcionMotivo()));
        }

        agregarSeccion(document, null, tabla);
    }

    private void agregarOrigenDestino(Document document, GuiaRemision guia) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 5f, 5f });
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(0f);

        PdfPCell origenCell = celda(Rectangle.BOX, 8f);
        origenCell.setBorderColor(COLOR_BORDE);
        origenCell.setBackgroundColor(COLOR_FONDO_SECCION);
        Paragraph pOTitle = new Paragraph("PUNTO DE PARTIDA", fuente(true, 8f, COLOR_PRIMARIO));
        pOTitle.setSpacingAfter(4f);
        origenCell.addElement(pOTitle);
        origenCell.addElement(new Paragraph(v(guia.getDireccionPartida()), fuente(false, 9f)));
        if (!v(guia.getUbigeoPartida()).isBlank()) {
            origenCell.addElement(new Paragraph("Ubigeo: " + v(guia.getUbigeoPartida()), fuente(false, 8f)));
        }
        tabla.addCell(origenCell);

        PdfPCell destinoCell = celda(Rectangle.BOX, 8f);
        destinoCell.setBorderColor(COLOR_BORDE);
        destinoCell.setBackgroundColor(COLOR_FONDO_SECCION);
        destinoCell.setPaddingLeft(10f);
        Paragraph pDTitle = new Paragraph("PUNTO DE LLEGADA", fuente(true, 8f, COLOR_PRIMARIO));
        pDTitle.setSpacingAfter(4f);
        destinoCell.addElement(pDTitle);
        destinoCell.addElement(new Paragraph(v(guia.getDireccionLlegada()), fuente(false, 9f)));
        if (!v(guia.getUbigeoLlegada()).isBlank()) {
            destinoCell.addElement(new Paragraph("Ubigeo: " + v(guia.getUbigeoLlegada()), fuente(false, 8f)));
        }
        tabla.addCell(destinoCell);

        document.add(tabla);
    }

    private void agregarDestinatario(Document document, GuiaRemision guia) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 2.2f, 2.8f, 2.2f, 2.8f });
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(0f);
        tabla.setSpacingAfter(0f);

        Sucursal sucursal = guia.getSucursal();
        String razonSocialEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? (!v(sucursal.getEmpresa().getRazonSocial()).isBlank()
                        ? v(sucursal.getEmpresa().getRazonSocial())
                        : v(sucursal.getEmpresa().getNombre()))
                : "";
        String rucEmpresa = sucursal != null && sucursal.getEmpresa() != null
                ? v(sucursal.getEmpresa().getRuc())
                : "";
        String fechaEmision = guia.getFechaEmision() != null ? guia.getFechaEmision().format(FMT_FECHA_HORA) : "-";
        String razonSocial = !v(guia.getDestinatarioRazonSocial()).isBlank()
                ? v(guia.getDestinatarioRazonSocial())
                : valorGuia(razonSocialEmpresa);
        String ruc = !v(guia.getDestinatarioNroDoc()).isBlank()
                ? v(guia.getDestinatarioNroDoc())
                : valorGuia(rucEmpresa);
        String tipoDocDestinatario = !v(guia.getDestinatarioTipoDoc()).isBlank()
                ? describirTipoDoc(v(guia.getDestinatarioTipoDoc()))
                : (!ruc.equals("-") ? "RUC" : "-");

        agregarFilaDatoDoble(tabla, "FECHA DE EMISION:", fechaEmision, "TIPO DOC.:", tipoDocDestinatario);
        agregarFilaDatoDoble(tabla, "DESTINATARIO:", razonSocial, "NRO. DOC.:", ruc);

        agregarSeccion(document, null, tabla);
    }

    private void agregarTransporte(Document document,
            GuiaRemision guia,
            List<GuiaRemisionTransportista> transportistas,
            List<GuiaRemisionConductor> conductores,
            List<GuiaRemisionVehiculo> vehiculos) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 2.2f, 2.8f, 2.2f, 2.8f });
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(0f);
        tabla.setSpacingAfter(0f);

        GuiaRemisionTransportista transportista = transportistas.isEmpty() ? null : transportistas.get(0);
        GuiaRemisionConductor conductor = conductores.isEmpty() ? null : conductores.get(0);
        GuiaRemisionVehiculo vehiculo = vehiculos.isEmpty() ? null : vehiculos.get(0);
        String modalidad = v(guia.getModalidadTransporte());
        String tipoTransporte = describirModalidad(modalidad);
        String placa = vehiculo != null ? valorGuia(v(vehiculo.getPlaca())) : "-";

        agregarFilaDatoSimple(tabla, "TIPO DE TRANSPORTE:", tipoTransporte);
        if ("01".equals(modalidad)) {
            String razonSocialTransportista = transportista != null
                    ? valorGuia(v(transportista.getTransportistaRazonSocial()))
                    : "-";
            String rucTransportista = transportista != null
                    ? valorGuia(v(transportista.getTransportistaNroDoc()))
                    : "-";
            String registroMtc = transportista != null
                    ? valorGuia(v(transportista.getTransportistaRegistroMtc()))
                    : "-";
            agregarFilaDatoDoble(tabla, "TRANSPORTISTA:", razonSocialTransportista, "RUC:", rucTransportista);
            agregarFilaDatoSimple(tabla, "REGISTRO MTC:", registroMtc);
        } else {
            String nombreConductor = conductor != null
                    ? (v(conductor.getNombres()) + " " + v(conductor.getApellidos())).trim()
                    : "-";
            String documentoConductor = conductor != null ? valorGuia(v(conductor.getNroDocumento())) : "-";
            String licencia = conductor != null ? valorGuia(v(conductor.getLicencia())) : "-";
            agregarFilaDatoDoble(tabla, "CONDUCTOR:", nombreConductor, "DOC.:", documentoConductor);
            agregarFilaDatoDoble(tabla, "LICENCIA:", licencia, "PLACA:", placa);
        }

        agregarSeccion(document, null, tabla);
    }

    private void agregarDocumentosRelacionados(Document document,
            List<GuiaRemisionDocumentoRelacionado> documentosRelacionados) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 2.2f, 2f, 2.4f, 3.4f });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        tabla.setSpacingBefore(0f);

        headerBienes(tabla, "TIPO");
        headerBienes(tabla, "SERIE");
        headerBienes(tabla, "NUMERO");
        headerBienes(tabla, "DOCUMENTO RELACIONADO");

        for (GuiaRemisionDocumentoRelacionado documento : documentosRelacionados) {
            Color fondo = (documento.getIdGuiaDocumentoRelacionado() != null
                    && documento.getIdGuiaDocumentoRelacionado() % 2 == 0)
                    ? COLOR_FILA_PAR
                    : Color.WHITE;
            tabla.addCell(celdaBien(describirTipoDocumentoRelacionado(v(documento.getTipoDocumento())),
                    Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaBien(valorGuia(v(documento.getSerie())), Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaBien(valorGuia(v(documento.getNumero())), Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaBien(numeroDocumentoRelacionado(documento), Element.ALIGN_LEFT, fondo));
        }

        agregarSeccion(document, "DOCUMENTOS RELACIONADOS", tabla);
    }

    private void agregarTablaBienes(Document document, List<GuiaRemisionDetalle> detalles) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[] { 1.1f, 1.3f, 1.9f, 5.7f });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        tabla.setSpacingBefore(1f);

        headerBienes(tabla, "CANT");
        headerBienes(tabla, "UNIDAD");
        headerBienes(tabla, "CODIGO");
        headerBienes(tabla, "DESCRIPCION");

        for (GuiaRemisionDetalle d : detalles) {
            Color fondo = (d.getIdGuiaRemisionDetalle() != null && d.getIdGuiaRemisionDetalle() % 2 == 0)
                    ? COLOR_FILA_PAR
                    : Color.WHITE;
            tabla.addCell(celdaBien(cantidadItemGuiaPdf(d), Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaBien(unidadItemGuiaPdf(d), Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaBien(codigoItemGuiaPdf(d), Element.ALIGN_CENTER, fondo));
            tabla.addCell(celdaBien(descripcionItemGuiaPdf(d), Element.ALIGN_LEFT, fondo));
        }

        if (detalles.isEmpty()) {
            PdfPCell vacioCell = celda(Rectangle.BOX, 6f);
            vacioCell.setColspan(4);
            vacioCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            vacioCell.addElement(new Paragraph("Sin items registrados", fuente(false, 8f, Color.GRAY)));
            tabla.addCell(vacioCell);
        }

        document.add(tabla);
    }

    private void agregarPie(Document document, GuiaRemision guia) throws DocumentException {
        Image qr = generarQrSunatDesdeCdr(guia);
        if (qr == null) {
            Paragraph leyenda = new Paragraph(
                    leyendaSunat(guia)
                            + " Representacion impresa de la Guia de Remision Remitente Electronica.",
                    fuente(false, 8.5f, new Color(80, 80, 80)));
            leyenda.setAlignment(Element.ALIGN_CENTER);
            leyenda.setSpacingBefore(2f);
            document.add(leyenda);
            return;
        }

        PdfPTable pie = new PdfPTable(new float[] { 1.4f, 5.6f });
        pie.setWidthPercentage(100);
        pie.setSpacingBefore(2f);

        PdfPCell qrCell = celda(Rectangle.NO_BORDER, 0f);
        qr.scaleToFit(96f, 96f);
        qr.setAlignment(Element.ALIGN_CENTER);
        qrCell.addElement(qr);
        pie.addCell(qrCell);

        PdfPCell textoCell = celda(Rectangle.NO_BORDER, 3f);
        textoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        textoCell.addElement(new Paragraph(
                leyendaSunat(guia)
                        + " Representacion impresa de la Guia de Remision Remitente Electronica.",
                fuente(false, 8.5f, new Color(80, 80, 80))));
        textoCell.addElement(new Paragraph(
                "QR generado desde la constancia de recepcion SUNAT.",
                fuente(false, 7.5f, Color.GRAY)));
        pie.addCell(textoCell);

        document.add(pie);
    }

    private boolean tieneCdrDisponible(GuiaRemision guia) {
        return guia != null
                && ((guia.getSunatCdrKey() != null && !guia.getSunatCdrKey().isBlank())
                        || (guia.getSunatCdrNombre() != null && !guia.getSunatCdrNombre().isBlank()));
    }

    private Image generarQrSunatDesdeCdr(GuiaRemision guia) {
        if (!tieneCdrDisponible(guia) || !esGuiaAceptadaPorSunat(guia)) {
            return null;
        }
        String contenido = extraerContenidoQrDesdeCdr(guia);
        if (contenido == null || contenido.isBlank()) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(contenido.trim(), BarcodeFormat.QR_CODE, 220, 220, hints);
            BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(buffered, "png", out);
            return Image.getInstance(out.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean esGuiaAceptadaPorSunat(GuiaRemision guia) {
        SunatEstado estado = guia != null ? guia.getSunatEstado() : null;
        return estado == SunatEstado.ACEPTADO || estado == SunatEstado.OBSERVADO;
    }

    private String extraerContenidoQrDesdeCdr(GuiaRemision guia) {
        try {
            byte[] cdrBytes = sunatDocumentStorageService.download(guia.getSunatCdrKey());
            byte[] xmlBytes = extraerXmlDesdeZip(cdrBytes);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            org.w3c.dom.Document cdr = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xmlBytes));
            NodeList nodes = cdr.getElementsByTagNameNS("*", "DocumentDescription");
            if (nodes.getLength() == 0 || nodes.item(0) == null) {
                return null;
            }
            return nodes.item(0).getTextContent();
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] extraerXmlDesdeZip(byte[] zipBytes) throws IOException {
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = zipInput.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }
        throw new IOException("El CDR no contiene XML");
    }

    private void tituloSeccion(Document document, String texto) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(2f);
        PdfPCell c = celda(Rectangle.BOX, 5f);
        c.setBackgroundColor(COLOR_PRIMARIO);
        Paragraph p = new Paragraph(texto, fuente(true, 8f, Color.WHITE));
        p.setAlignment(Element.ALIGN_LEFT);
        c.addElement(p);
        t.addCell(c);
        document.add(t);
    }

    private void agregarFilaDatoDoble(PdfPTable tabla,
            String label1, String valor1, String label2, String valor2) {
        PdfPCell lc1 = celda(Rectangle.NO_BORDER, 2f);
        lc1.addElement(new Paragraph(label1, fuente(true, 8.5f)));
        tabla.addCell(lc1);

        PdfPCell vc1 = celda(Rectangle.NO_BORDER, 2f);
        vc1.addElement(new Paragraph(valorGuia(valor1), fuente(false, 8.5f)));
        tabla.addCell(vc1);

        PdfPCell lc2 = celda(Rectangle.NO_BORDER, 2f);
        lc2.addElement(new Paragraph(label2, fuente(true, 8.5f)));
        tabla.addCell(lc2);

        PdfPCell vc2 = celda(Rectangle.NO_BORDER, 2f);
        vc2.addElement(new Paragraph(valorGuia(valor2), fuente(false, 8.5f)));
        tabla.addCell(vc2);
    }

    private void agregarFilaDatoSimple(PdfPTable tabla, String label, String valor) {
        PdfPCell lc = celda(Rectangle.NO_BORDER, 2f);
        lc.addElement(new Paragraph(label, fuente(true, 8.5f)));
        tabla.addCell(lc);

        PdfPCell vc = celda(Rectangle.NO_BORDER, 2f);
        vc.setColspan(Math.max(1, tabla.getNumberOfColumns() - 1));
        vc.addElement(new Paragraph(valorGuia(valor), fuente(false, 8.5f)));
        tabla.addCell(vc);
    }

    private void agregarFilaDatoLinea(PdfPTable tabla, String label, String valor) {
        PdfPCell lc = celda(Rectangle.NO_BORDER, 2f);
        lc.addElement(new Paragraph(label, fuente(true, 8.5f)));
        tabla.addCell(lc);

        PdfPCell vc = celda(Rectangle.NO_BORDER, 2f);
        vc.addElement(new Paragraph(valorGuia(valor), fuente(false, 8.5f)));
        tabla.addCell(vc);
    }

    private void headerBienes(PdfPTable tabla, String texto) {
        PdfPCell c = celda(Rectangle.BOX, 6f);
        c.setBackgroundColor(COLOR_PRIMARIO);
        c.setBorderColor(Color.WHITE);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph p = new Paragraph(texto, fuente(true, 9f, Color.WHITE));
        p.setAlignment(Element.ALIGN_CENTER);
        c.addElement(p);
        tabla.addCell(c);
    }

    private PdfPCell celdaBien(String texto, int alineacion, Color fondo) {
        PdfPCell c = celda(Rectangle.BOX, 5f);
        c.setBorderColor(COLOR_BORDE);
        c.setBackgroundColor(fondo);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        c.setUseAscender(true);
        Paragraph p = new Paragraph(texto, fuente(false, 9f));
        p.setAlignment(alineacion);
        c.addElement(p);
        return c;
    }

    private void agregarSeccion(Document document, String titulo, PdfPTable contenido) throws DocumentException {
        PdfPTable contenedor = new PdfPTable(1);
        contenedor.setWidthPercentage(100);
        contenedor.setSpacingBefore(0f);
        contenedor.setSpacingAfter(0f);

        PdfPCell caja = celda(Rectangle.NO_BORDER, 0f);
        caja.setPadding(8f);
        caja.setCellEvent(new RoundedBorderCellEvent(COLOR_BORDE, 0.9f, 8f));

        if (titulo != null && !titulo.isBlank()) {
            Paragraph tituloP = new Paragraph(titulo, fuente(true, 9f));
            tituloP.setSpacingAfter(5f);
            caja.addElement(tituloP);
        }
        caja.addElement(contenido);

        contenedor.addCell(caja);
        document.add(contenedor);
    }

    private PdfPCell celda(int border, float padding) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        c.setPadding(padding);
        c.setBorderWidth(0.8f);
        return c;
    }

    private com.lowagie.text.Font fuente(boolean bold, float size) {
        return fuente(bold, size, Color.BLACK);
    }

    private com.lowagie.text.Font fuente(boolean bold, float size, Color color) {
        return FontFactory.getFont(
                FontFactory.HELVETICA,
                size,
                bold ? com.lowagie.text.Font.BOLD : com.lowagie.text.Font.NORMAL,
                color);
    }

    private String v(Object val) {
        return val == null ? "" : String.valueOf(val).trim();
    }

    private String ubicacion(Empresa empresa) {
        if (empresa == null) return "";
        String d = v(empresa.getDistrito()), p = v(empresa.getProvincia()), dep = v(empresa.getDepartamento());
        return (!d.isBlank() ? d : "")
                + (!d.isBlank() && !p.isBlank() ? " - " + p : (!p.isBlank() ? p : ""))
                + ((!d.isBlank() || !p.isBlank()) && !dep.isBlank() ? " - " + dep : (!dep.isBlank() ? dep : ""));
    }

    private String numeroGuia(GuiaRemision guia) {
        String serie = v(guia.getSerie());
        String corr = guia.getCorrelativo() != null
                ? String.format("%08d", guia.getCorrelativo()) : "00000000";
        return serie + "-" + corr;
    }

    private Paragraph crearEspaciador(float leading) {
        Paragraph spacer = new Paragraph(" ", fuente(false, 4f));
        spacer.setLeading(leading);
        spacer.setSpacingBefore(0f);
        spacer.setSpacingAfter(0f);
        return spacer;
    }

    private String valorGuia(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private String motivoTrasladoPdf(String codigo) {
        return switch (codigo) {
            case "01" -> "Venta";
            case "02" -> "Compra";
            case "03" -> "Venta con entrega a terceros";
            case "04" -> "Traslado entre establecimientos de la misma empresa";
            case "05" -> "Consignacion";
            case "06" -> "Devolucion";
            case "07" -> "Recojo de bienes transformados";
            case "13" -> "Otros";
            case "14" -> "Venta sujeta a confirmacion del comprador";
            case "17" -> "Traslado de bienes para transformacion";
            default -> valorGuia(codigo);
        };
    }

    private String tipoTransportePdf(String codigo) {
        return switch (codigo) {
            case "01" -> "Transporte publico";
            case "02" -> "Transporte privado";
            default -> valorGuia(codigo);
        };
    }

    private String describirTipoDocumentoRelacionado(String codigo) {
        return switch (codigo) {
            case "01" -> "01 - Factura";
            case "03" -> "03 - Boleta";
            case "04" -> "04 - Liquidacion de compra";
            default -> codigo.isBlank() ? "-" : codigo;
        };
    }

    private String numeroDocumentoRelacionado(GuiaRemisionDocumentoRelacionado documento) {
        String serie = v(documento.getSerie());
        String numero = v(documento.getNumero());
        if (serie.isBlank() && numero.isBlank()) {
            return "-";
        }
        if (serie.isBlank()) {
            return numero;
        }
        if (numero.isBlank()) {
            return serie;
        }
        return serie + "-" + numero;
    }

    private String descripcionItemGuiaPdf(GuiaRemisionDetalle detalle) {
        if (!v(detalle.getDescripcion()).isBlank()) {
            return v(detalle.getDescripcion());
        }
        if (detalle.getProductoVariante() != null && detalle.getProductoVariante().getProducto() != null) {
            StringBuilder sb = new StringBuilder(v(detalle.getProductoVariante().getProducto().getNombre()));
            if (detalle.getProductoVariante().getColor() != null
                    && !v(detalle.getProductoVariante().getColor().getNombre()).isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" - ");
                }
                sb.append(v(detalle.getProductoVariante().getColor().getNombre()));
            }
            if (detalle.getProductoVariante().getTalla() != null
                    && !v(detalle.getProductoVariante().getTalla().getNombre()).isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" - ");
                }
                sb.append(v(detalle.getProductoVariante().getTalla().getNombre()));
            }
            return valorGuia(sb.toString());
        }
        return "-";
    }

    private String codigoItemGuiaPdf(GuiaRemisionDetalle detalle) {
        if (!v(detalle.getCodigoProducto()).isBlank()) {
            return v(detalle.getCodigoProducto());
        }
        if (detalle.getProductoVariante() != null && !v(detalle.getProductoVariante().getSku()).isBlank()) {
            return v(detalle.getProductoVariante().getSku());
        }
        if (detalle.getProductoVariante() != null && !v(detalle.getProductoVariante().getCodigoBarras()).isBlank()) {
            return v(detalle.getProductoVariante().getCodigoBarras());
        }
        return "-";
    }

    private String unidadItemGuiaPdf(GuiaRemisionDetalle detalle) {
        String unidad = v(detalle.getUnidadMedida());
        return unidad.isBlank() ? "NIU" : unidad.toUpperCase();
    }

    private String cantidadItemGuiaPdf(GuiaRemisionDetalle detalle) {
        if (detalle.getCantidad() == null) {
            return "-";
        }
        return detalle.getCantidad().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static class RoundedBorderCellEvent implements PdfPCellEvent {
        private final Color color;
        private final float lineWidth;
        private final float radius;

        private RoundedBorderCellEvent(Color color, float lineWidth, float radius) {
            this.color = color;
            this.lineWidth = lineWidth;
            this.radius = radius;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte canvas = canvases[PdfPTable.LINECANVAS];
            canvas.saveState();
            canvas.setColorStroke(color);
            canvas.setLineWidth(lineWidth);
            float inset = lineWidth / 2f;
            canvas.roundRectangle(
                    position.getLeft() + inset,
                    position.getBottom() + inset,
                    position.getWidth() - lineWidth,
                    position.getHeight() - lineWidth,
                    radius);
            canvas.stroke();
            canvas.restoreState();
        }
    }

    private String describirMotivo(String codigo) {
        return switch (codigo) {
            case "01" -> "01 - Venta";
            case "02" -> "02 - Compra";
            case "03" -> "03 - Venta con entrega a terceros";
            case "04" -> "04 - Traslado entre establecimientos de la misma empresa";
            case "05" -> "05 - Consignacion";
            case "06" -> "06 - Devolucion";
            case "07" -> "07 - Recojo de bienes transformados";
            case "13" -> "13 - Otros";
            case "14" -> "14 - Venta sujeta a confirmacion del comprador";
            case "17" -> "17 - Traslado de bienes para transformacion";
            default -> codigo.isBlank() ? "-" : codigo;
        };
    }

    private String describirModalidad(String codigo) {
        return switch (codigo) {
            case "01" -> "01 - Transporte publico";
            case "02" -> "02 - Transporte privado";
            default -> codigo.isBlank() ? "-" : codigo;
        };
    }

    private String describirTipoDoc(String codigo) {
        return switch (codigo) {
            case "1" -> "DNI";
            case "4" -> "Carnet de Extranjeria";
            case "6" -> "RUC";
            case "7" -> "Pasaporte";
            case "A" -> "Cedula Diplomatica";
            default -> codigo.isBlank() ? "-" : codigo;
        };
    }

    private String leyendaSunat(GuiaRemision guia) {
        SunatEstado estado = guia.getSunatEstado();
        if (estado == null) return "Documento generado por el establecimiento.";
        return switch (estado) {
            case ACEPTADO -> "Guia de Remision Electronica ACEPTADA por SUNAT.";
            case OBSERVADO -> "Guia de Remision Electronica ACEPTADA con observaciones por SUNAT.";
            case RECHAZADO -> "Guia de Remision Electronica RECHAZADA por SUNAT.";
            case PENDIENTE_ENVIO, ENVIANDO -> "Guia de Remision Electronica registrada y pendiente de envio a SUNAT.";
            case PENDIENTE_CDR, PENDIENTE -> "Guia de Remision Electronica enviada y pendiente de constancia SUNAT.";
            case ERROR_TRANSITORIO, ERROR -> "Guia generada con incidencia temporal en el envio a SUNAT.";
            case ERROR_DEFINITIVO -> "Guia generada con error definitivo en la emision electronica.";
            default -> "Guia de Remision Electronica pendiente de validacion por SUNAT.";
        };
    }

    private Image cargarLogo(GuiaRemision guia) {
        String logoUrl = guia.getSucursal() != null && guia.getSucursal().getEmpresa() != null
                ? guia.getSucursal().getEmpresa().getLogoUrl() : null;
        if (logoUrl == null || logoUrl.isBlank()) return null;
        ImageIO.scanForPlugins();
        try (InputStream stream = s3StorageService.openStream(logoUrl)) {
            BufferedImage buffered = ImageIO.read(stream);
            if (buffered != null) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(buffered, "png", png);
                Image image = Image.getInstance(png.toByteArray());
                image.setAlignment(Element.ALIGN_LEFT);
                return image;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
