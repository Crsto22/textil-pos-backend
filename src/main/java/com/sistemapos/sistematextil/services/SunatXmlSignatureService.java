package com.sistemapos.sistematextil.services;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.sistemapos.sistematextil.model.SunatConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatXmlSignatureService {

    private final SunatCertificateStorageService sunatCertificateStorageService;
    private final SunatSecretCryptoService sunatSecretCryptoService;

    public SignedXml sign(Document document, SunatConfig config, String signatureId) {
        if (document == null) {
            throw new RuntimeException("No hay XML para firmar");
        }
        if (config == null) {
            throw new RuntimeException("No hay configuracion SUNAT registrada");
        }

        String password = sunatSecretCryptoService.decrypt(config.getCertificadoPassword());
        if (password == null || password.isBlank()) {
            throw new RuntimeException("La configuracion SUNAT no tiene certificadoPassword");
        }

        try (FileInputStream input = new FileInputStream(
                sunatCertificateStorageService.resolveStoredPath(config.getCertificadoUrl()).toFile())) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, password.toCharArray());

            String alias = null;
            for (String currentAlias : Collections.list(keyStore.aliases())) {
                if (keyStore.isKeyEntry(currentAlias)) {
                    alias = currentAlias;
                    break;
                }
            }
            if (alias == null) {
                throw new RuntimeException("El certificado digital no contiene una clave privada valida");
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            Document documentToSign = parse(toBytes(document));
            Element extensionContent = resolveExtensionContent(documentToSign);

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            Reference reference = factory.newReference(
                    "",
                    factory.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(
                            factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
                    null,
                    null);

            SignedInfo signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(
                            CanonicalizationMethod.INCLUSIVE,
                            (C14NMethodParameterSpec) null),
                    factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(reference));

            KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
            X509Data x509Data = keyInfoFactory.newX509Data(List.of(certificate));
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

            DOMSignContext signContext = new DOMSignContext(privateKey, extensionContent);
            signContext.setDefaultNamespacePrefix("ds");
            signContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);

            XMLSignature signature = factory.newXMLSignature(signedInfo, keyInfo, null, signatureId, null);
            signature.sign(signContext);

            byte[] bytes = toBytes(documentToSign);
            validateSignedXml(bytes, certificate);
            return new SignedXml(bytes, extractDigestValue(documentToSign));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo firmar el XML SUNAT");
        }
    }

    private Element resolveExtensionContent(Document document) {
        NodeList nodes = document.getElementsByTagNameNS("*", "ExtensionContent");
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element)) {
            throw new RuntimeException("El XML SUNAT no tiene ext:ExtensionContent para insertar la firma");
        }
        return element;
    }

    private byte[] toBytes(Document document) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toByteArray();
    }

    private void validateSignedXml(byte[] xmlBytes, X509Certificate certificate) {
        try {
            Document document = parse(xmlBytes);
            Element signatureElement = resolveSignatureElement(document);

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            DOMValidateContext validateContext = new DOMValidateContext(certificate.getPublicKey(), signatureElement);
            validateContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);

            XMLSignature signature = factory.unmarshalXMLSignature(validateContext);
            boolean valid = signature.validate(validateContext);
            if (!valid) {
                boolean signatureValueValid = signature.getSignatureValue().validate(validateContext);
                boolean referencesValid = true;
                for (Object referenceObject : signature.getSignedInfo().getReferences()) {
                    Reference reference = (Reference) referenceObject;
                    referencesValid = referencesValid && reference.validate(validateContext);
                }
                throw new RuntimeException(
                        "La firma XML generada no pudo validarse localmente (signatureValue="
                                + signatureValueValid + ", references=" + referencesValid + ")");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo validar localmente la firma XML generada");
        }
    }

    private Document parse(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
    }

    private Element resolveSignatureElement(Document document) {
        NodeList nodes = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element)) {
            throw new RuntimeException("El XML firmado no contiene ds:Signature");
        }
        return element;
    }

    private String extractDigestValue(Document document) {
        NodeList digestNodes = document.getElementsByTagNameNS("*", "DigestValue");
        if (digestNodes.getLength() == 0 || digestNodes.item(0) == null || digestNodes.item(0).getTextContent() == null) {
            throw new RuntimeException("No se pudo obtener el hash del XML firmado");
        }
        return digestNodes.item(0).getTextContent().trim();
    }

    public record SignedXml(
            byte[] bytes,
            String digestValue) {
    }
}
