package com.sistemapos.sistematextil.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappDeliveryDateMode;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;

class CrmWhatsappDeliveryScheduleServiceTest {

    private final CrmWhatsappDeliveryScheduleService service = new CrmWhatsappDeliveryScheduleService(
            mock(CrmWhatsappAiConfigRepository.class));

    @Test
    void automaticoUsaHoyAntesDelLimite() {
        CrmWhatsappAiConfig config = config();
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 7, 14, 30, 0, 0, ZoneId.of("America/Lima"));

        var result = service.resolveAt(config, now);

        assertThat(result.shippingDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(result.pickupDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(service.customerResponse(result)).contains("Shalom", "La Victoria");
    }

    @Test
    void automaticoAvanzaAMananaCuandoTerminaElHorario() {
        CrmWhatsappAiConfig config = config();
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 7, 19, 0, 0, 0, ZoneId.of("America/Lima"));

        var result = service.resolveAt(config, now);

        assertThat(result.shippingDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(result.pickupDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void fechaEspecificaVencidaNoSePrometeAlCliente() {
        CrmWhatsappAiConfig config = config();
        config.setShippingDateMode(CrmWhatsappDeliveryDateMode.FECHA_ESPECIFICA);
        config.setShippingSpecificDate(LocalDate.of(2026, 10, 6));
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneId.of("America/Lima"));

        var result = service.resolveAt(config, now);

        assertThat(result.shippingDate()).isNull();
        assertThat(result.requiresConfigurationUpdate()).isTrue();
    }

    private CrmWhatsappAiConfig config() {
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setShippingDateMode(CrmWhatsappDeliveryDateMode.AUTOMATICA);
        config.setSameDayShippingCutoff(LocalTime.of(15, 0));
        config.setPickupDateMode(CrmWhatsappDeliveryDateMode.AUTOMATICA);
        config.setPickupOpensAt(LocalTime.of(10, 0));
        config.setPickupClosesAt(LocalTime.of(18, 0));
        return config;
    }
}
