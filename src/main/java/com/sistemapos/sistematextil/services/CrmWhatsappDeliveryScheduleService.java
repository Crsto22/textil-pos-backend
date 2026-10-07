package com.sistemapos.sistematextil.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappDeliveryDateMode;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappDeliveryScheduleService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Lima");
    private static final Locale SPANISH = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", SPANISH);

    private final CrmWhatsappAiConfigRepository configRepository;

    @Transactional(readOnly = true)
    public ScheduleResult resolve(CrmWhatsappConversation conversation) {
        if (conversation == null || conversation.getConnection() == null) {
            throw new IllegalStateException("La conversacion no tiene una conexion configurada");
        }
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(conversation.getConnection().getIdConnection())
                .orElse(null);
        ZoneId zone = resolveZone(config == null ? null : config.getZonaHoraria());
        return resolveAt(config, ZonedDateTime.now(zone));
    }

    ScheduleResult resolveAt(CrmWhatsappAiConfig config, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();
        LocalTime shippingCutoff = value(config == null ? null : config.getSameDayShippingCutoff(), LocalTime.of(15, 0));
        LocalTime pickupOpens = value(config == null ? null : config.getPickupOpensAt(), LocalTime.of(10, 0));
        LocalTime pickupCloses = value(config == null ? null : config.getPickupClosesAt(), LocalTime.of(18, 0));

        CrmWhatsappDeliveryDateMode shippingMode = mode(config == null ? null : config.getShippingDateMode());
        LocalDate shippingDate = shippingMode == CrmWhatsappDeliveryDateMode.AUTOMATICA
                ? (currentTime.isAfter(shippingCutoff) ? today.plusDays(1) : today)
                : validSpecificDate(config == null ? null : config.getShippingSpecificDate(), today,
                        currentTime, shippingCutoff);

        CrmWhatsappDeliveryDateMode pickupMode = mode(config == null ? null : config.getPickupDateMode());
        LocalDate pickupDate = pickupMode == CrmWhatsappDeliveryDateMode.AUTOMATICA
                ? (currentTime.isBefore(pickupCloses) ? today : today.plusDays(1))
                : validSpecificDate(config == null ? null : config.getPickupSpecificDate(), today,
                        currentTime, pickupCloses);

        return new ScheduleResult(
                shippingDate, shippingCutoff, shippingMode,
                pickupDate, pickupOpens, pickupCloses, pickupMode,
                shippingDate == null || pickupDate == null);
    }

    public Map<String, Object> modelResult(ScheduleResult schedule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "consultar_programacion_entregas");
        result.put("shippingMethod", "SHALOM");
        result.put("shippingDate", dateValue(schedule.shippingDate()));
        result.put("shippingDateText", dateText(schedule.shippingDate()));
        result.put("sameDayCutoff", schedule.sameDayCutoff().toString());
        result.put("sameDayCutoffText", timeText(schedule.sameDayCutoff()));
        result.put("pickupLocation", "La Victoria");
        result.put("pickupDate", dateValue(schedule.pickupDate()));
        result.put("pickupDateText", dateText(schedule.pickupDate()));
        result.put("pickupOpensAt", schedule.pickupOpensAt().toString());
        result.put("pickupOpensAtText", timeText(schedule.pickupOpensAt()));
        result.put("pickupClosesAt", schedule.pickupClosesAt().toString());
        result.put("pickupClosesAtText", timeText(schedule.pickupClosesAt()));
        result.put("preorderUsesProductDate", true);
        result.put("requiresConfigurationUpdate", schedule.requiresConfigurationUpdate());
        return result;
    }

    public String customerResponse(ScheduleResult schedule) {
        String shipping = schedule.shippingDate() == null
                ? "📦 La próxima fecha de despacho por Shalom aún debe ser actualizada."
                : "📦 Los productos listos para entrega se despachan por Shalom "
                        + dateText(schedule.shippingDate()) + ", confirmando el pedido hasta las "
                        + timeText(schedule.sameDayCutoff()) + ".";
        String pickup = schedule.pickupDate() == null
                ? "🏬 La próxima fecha de recojo en tienda aún debe ser actualizada."
                : "🏬 También puedes recoger en nuestra tienda de La Victoria "
                        + dateText(schedule.pickupDate()) + ", de "
                        + timeText(schedule.pickupOpensAt()) + " a "
                        + timeText(schedule.pickupClosesAt()) + ".";
        return shipping + "\n\n" + pickup
                + "\n\nLos productos en preventa conservan la fecha de envío indicada en cada modelo.";
    }

    private LocalDate validSpecificDate(LocalDate configured, LocalDate today, LocalTime now, LocalTime limit) {
        if (configured == null || configured.isBefore(today)) return null;
        if (configured.equals(today) && now.isAfter(limit)) return null;
        return configured;
    }

    private CrmWhatsappDeliveryDateMode mode(CrmWhatsappDeliveryDateMode value) {
        return value == null ? CrmWhatsappDeliveryDateMode.AUTOMATICA : value;
    }

    private LocalTime value(LocalTime configured, LocalTime fallback) {
        return configured == null ? fallback : configured;
    }

    private ZoneId resolveZone(String value) {
        try {
            return value == null || value.isBlank() ? DEFAULT_ZONE : ZoneId.of(value);
        } catch (RuntimeException ignored) {
            return DEFAULT_ZONE;
        }
    }

    private String dateValue(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private String dateText(LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMAT);
    }

    private String timeText(LocalTime time) {
        return time.format(TIME_FORMAT).toLowerCase(SPANISH);
    }

    public record ScheduleResult(
            LocalDate shippingDate,
            LocalTime sameDayCutoff,
            CrmWhatsappDeliveryDateMode shippingDateMode,
            LocalDate pickupDate,
            LocalTime pickupOpensAt,
            LocalTime pickupClosesAt,
            CrmWhatsappDeliveryDateMode pickupDateMode,
            boolean requiresConfigurationUpdate) {}
}
