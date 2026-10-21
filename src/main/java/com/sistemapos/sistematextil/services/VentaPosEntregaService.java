package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.CrmWhatsappFilmAddon;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.model.Pago;
import com.sistemapos.sistematextil.model.ShalomAgency;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.ShalomAgencyRepository;
import com.sistemapos.sistematextil.util.venta.VentaEntregaCreateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VentaPosEntregaService {
    private static final BigDecimal FILM_AMOUNT = new BigDecimal("2.00");

    private final CrmWhatsappFulfillmentRepository fulfillmentRepository;
    private final CrmWhatsappFilmAddonRepository filmAddonRepository;
    private final ShalomAgencyRepository shalomAgencyRepository;
    private final JdbcTemplate jdbcTemplate;

    public PreparedDelivery prepare(VentaEntregaCreateRequest request, Sucursal saleBranch) {
        if (request == null || blank(request.metodo())) return null;
        String method = request.metodo().trim().toUpperCase(Locale.ROOT);
        if ("PICKUP".equals(method)) {
            if (saleBranch == null || saleBranch.getIdSucursal() == null) {
                throw new IllegalArgumentException("La sucursal de recojo no es valida");
            }
            return new PreparedDelivery(CrmWhatsappFulfillmentMethod.PICKUP, saleBranch, null,
                    null, null, null, null);
        }
        if (!"SHALOM".equals(method)) {
            throw new IllegalArgumentException("La modalidad de entrega no es valida");
        }

        String recipient = required(request.destinatario(), "Ingresa los nombres y apellidos del destinatario", 180);
        if (recipient.split("\\s+").length < 2) {
            throw new IllegalArgumentException("Ingresa los nombres y apellidos del destinatario");
        }
        String document = digits(request.documento());
        if (!document.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI del destinatario debe tener exactamente 8 digitos");
        }
        String phone = digits(request.celular());
        if (!phone.matches("9\\d{8}")) {
            throw new IllegalArgumentException("El celular del destinatario debe ser un numero peruano valido de 9 digitos");
        }
        if (request.idAgenciaShalom() == null) {
            throw new IllegalArgumentException("Selecciona una agencia de Shalom");
        }
        ShalomAgency agency = shalomAgencyRepository.findById(request.idAgenciaShalom())
                .filter(ShalomAgency::isActive)
                .filter(ShalomAgency::isDestinationEnabled)
                .orElseThrow(() -> new IllegalArgumentException("La agencia de Shalom ya no esta disponible"));
        String filmStatus = required(request.estadoFilm(), "Selecciona el estado del film protector", 20)
                .toUpperCase(Locale.ROOT);
        if (!filmStatus.equals("PAID") && !filmStatus.equals("PENDING")) {
            throw new IllegalArgumentException("El estado del film protector no es valido");
        }
        return new PreparedDelivery(CrmWhatsappFulfillmentMethod.SHALOM, null, agency,
                recipient, document, phone, filmStatus);
    }

    public void save(PreparedDelivery prepared, Venta sale, Pago mainPayment, Usuario actor) {
        if (prepared == null) return;
        CrmWhatsappFulfillment fulfillment = new CrmWhatsappFulfillment();
        fulfillment.setVenta(sale);
        fulfillment.setIdempotencyKey("pos-fulfillment:" + sale.getIdVenta());
        fulfillment.setMethod(prepared.method());
        fulfillment.setConfirmedAt(LocalDateTime.now());

        if (prepared.method() == CrmWhatsappFulfillmentMethod.PICKUP) {
            fulfillment.setPickupBranch(prepared.pickupBranch());
            fulfillment.setStatus(CrmWhatsappFulfillmentStatus.READY);
            fulfillmentRepository.save(fulfillment);
            return;
        }

        ShalomAgency agency = prepared.agency();
        fulfillment.setRecipientName(prepared.recipient());
        fulfillment.setDocumentType("DNI");
        fulfillment.setDocumentNumber(prepared.document());
        fulfillment.setPhone(prepared.phone());
        fulfillment.setShalomAgencyReference(agency);
        fulfillment.setShalomAgency(agency.getName());
        fulfillment.setShalomAgencyAddress(agency.getAddress());
        fulfillment.setShalomDestination(location(agency));
        boolean paid = "PAID".equals(prepared.filmStatus());
        fulfillment.setStatus(paid
                ? CrmWhatsappFulfillmentStatus.READY
                : CrmWhatsappFulfillmentStatus.FILM_PAYMENT_PENDING);
        fulfillment = fulfillmentRepository.save(fulfillment);

        CrmWhatsappFilmAddon film = new CrmWhatsappFilmAddon();
        film.setFulfillment(fulfillment);
        film.setVenta(sale);
        film.setIdempotencyKey("pos-film:" + sale.getIdVenta());
        film.setAmount(FILM_AMOUNT);
        film.setStatus(paid ? CrmWhatsappFilmStatus.CONFIRMED : CrmWhatsappFilmStatus.AWAITING_PAYMENT);
        film.setResolutionSource(paid ? "POS_PAID" : "POS_PENDING");
        if (paid) {
            LocalDateTime paidAt = mainPayment != null && mainPayment.getFecha() != null
                    ? mainPayment.getFecha() : LocalDateTime.now();
            film.setPaidAt(paidAt);
            film.setResolvedAt(LocalDateTime.now());
        } else {
            film.setPaymentStartedAt(LocalDateTime.now());
        }
        film = filmAddonRepository.save(film);

        if (paid) saveFilmPayment(film, sale, mainPayment, actor);
    }

    private void saveFilmPayment(CrmWhatsappFilmAddon film, Venta sale, Pago mainPayment, Usuario actor) {
        if (mainPayment == null || mainPayment.getMetodoPago() == null) {
            throw new IllegalArgumentException("No existe un pago principal para registrar el film");
        }
        String provider = mainPayment.getMetodoPago().getNombre();
        if (blank(provider)) provider = "POS";
        if (provider.length() > 40) provider = provider.substring(0, 40);
        jdbcTemplate.update("""
                INSERT INTO crm_whatsapp_film_payment
                  (id_film_addon,id_venta,id_film_evidence,id_payment_evidence,
                   id_metodo_pago,id_metodo_pago_cuenta,confirmed_by,confirmation_source,
                   amount,currency,provider,operation_code,paid_at)
                VALUES (?,?,NULL,NULL,?,NULL,?,'POS',?,'PEN',?,?,?)
                """,
                film.getIdFilmAddon(), sale.getIdVenta(),
                mainPayment.getMetodoPago().getIdMetodoPago(),
                actor == null ? null : actor.getIdUsuario(), FILM_AMOUNT, provider,
                blank(mainPayment.getCodigoOperacion()) ? null : mainPayment.getCodigoOperacion().trim(),
                mainPayment.getFecha() == null ? LocalDateTime.now() : mainPayment.getFecha());
    }

    private String location(ShalomAgency agency) {
        return java.util.stream.Stream.of(agency.getDepartment(), agency.getProvince(), agency.getPlace())
                .filter(value -> !blank(value)).map(String::trim).distinct()
                .reduce((left, right) -> left + " / " + right).orElse(null);
    }

    private String required(String value, String message, int maxLength) {
        if (blank(value)) throw new IllegalArgumentException(message);
        String clean = value.trim();
        if (clean.length() > maxLength) throw new IllegalArgumentException(message);
        return clean;
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record PreparedDelivery(
            CrmWhatsappFulfillmentMethod method,
            Sucursal pickupBranch,
            ShalomAgency agency,
            String recipient,
            String document,
            String phone,
            String filmStatus) {
    }
}
