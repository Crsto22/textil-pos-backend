package com.sistemapos.sistematextil.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.BrevoProperties;

import brevo.ApiClient;
import brevo.ApiException;
import brevo.Configuration;
import brevo.auth.ApiKeyAuth;
import brevoApi.TransactionalEmailsApi;
import brevoModel.SendSmtpEmail;
import brevoModel.SendSmtpEmailAttachment;
import brevoModel.SendSmtpEmailReplyTo;
import brevoModel.SendSmtpEmailSender;
import brevoModel.SendSmtpEmailTo;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrevoEmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final BrevoProperties properties;

    public void enviarHtml(String destinatario, String nombre, String asunto, String html, List<Adjunto> adjuntos) {
        if (!habilitado()) {
            log.warn("Brevo no configurado; correo omitido para {}", destinatario);
            return;
        }
        try {
            ApiClient client = Configuration.getDefaultApiClient();
            ((ApiKeyAuth) client.getAuthentication("api-key")).setApiKey(properties.apiKey());

            SendSmtpEmail email = new SendSmtpEmail()
                    .sender(new SendSmtpEmailSender()
                            .email(properties.senderEmail())
                            .name(valor(properties.senderName(), "KIMENTS")))
                    .addToItem(new SendSmtpEmailTo()
                            .email(destinatario)
                            .name(nombre))
                    .subject(asunto)
                    .htmlContent(html)
                    .addTagsItem("ecommerce");

            if (texto(properties.replyToEmail()) != null) {
                email.replyTo(new SendSmtpEmailReplyTo().email(properties.replyToEmail()));
            }
            if (adjuntos != null) {
                for (Adjunto adjunto : adjuntos) {
                    email.addAttachmentItem(new SendSmtpEmailAttachment()
                            .name(adjunto.nombre())
                            .content(adjunto.contenido()));
                }
            }

            new TransactionalEmailsApi().sendTransacEmail(email);
        } catch (ApiException e) {
            log.error("Brevo rechazo el correo para {}: {}", destinatario, e.getResponseBody(), e);
        } catch (Exception e) {
            log.error("No se pudo enviar correo Brevo para {}", destinatario, e);
        }
    }

    private boolean habilitado() {
        return properties.enabled()
                && texto(properties.apiKey()) != null
                && texto(properties.senderEmail()) != null;
    }

    private String valor(String value, String fallback) {
        String text = texto(value);
        return text == null ? fallback : text;
    }

    private String texto(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Adjunto(String nombre, byte[] contenido) {
    }
}
