package com.sistemapos.sistematextil.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.model.ShalomAgency;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.ShalomAgencyRepository;
import com.sistemapos.sistematextil.util.venta.VentaEntregaCreateRequest;

class VentaPosEntregaServiceTest {
    private ShalomAgencyRepository agencies;
    private VentaPosEntregaService service;

    @BeforeEach
    void setUp() {
        agencies = mock(ShalomAgencyRepository.class);
        service = new VentaPosEntregaService(
                mock(CrmWhatsappFulfillmentRepository.class),
                mock(CrmWhatsappFilmAddonRepository.class),
                agencies,
                mock(JdbcTemplate.class));
    }

    @Test
    void permitsSaleWithoutDelivery() {
        assertThat(service.prepare(null, branch())).isNull();
        assertThat(service.prepare(new VentaEntregaCreateRequest(null, null, null, null, null, null), branch()))
                .isNull();
    }

    @Test
    void pickupUsesSaleBranch() {
        Sucursal branch = branch();
        var prepared = service.prepare(
                new VentaEntregaCreateRequest("PICKUP", null, null, null, null, null), branch);

        assertThat(prepared.method()).isEqualTo(CrmWhatsappFulfillmentMethod.PICKUP);
        assertThat(prepared.pickupBranch()).isSameAs(branch);
    }

    @Test
    void shalomRequiresValidPersonalDataAndActiveAgency() {
        ShalomAgency agency = agency(true, true);
        when(agencies.findById(12L)).thenReturn(Optional.of(agency));

        var prepared = service.prepare(
                new VentaEntregaCreateRequest(
                        "SHALOM", "Ana Torres", "12345678", "987654321", 12L, "PAID"),
                branch());

        assertThat(prepared.method()).isEqualTo(CrmWhatsappFulfillmentMethod.SHALOM);
        assertThat(prepared.agency()).isSameAs(agency);
        assertThat(prepared.filmStatus()).isEqualTo("PAID");
    }

    @Test
    void shalomRejectsInvalidDniPhoneAndUnavailableAgency() {
        assertThatThrownBy(() -> service.prepare(
                new VentaEntregaCreateRequest(
                        "SHALOM", "Ana Torres", "123", "987654321", 12L, "PENDING"), branch()))
                .hasMessageContaining("DNI");

        assertThatThrownBy(() -> service.prepare(
                new VentaEntregaCreateRequest(
                        "SHALOM", "Ana Torres", "12345678", "123456789", 12L, "PENDING"), branch()))
                .hasMessageContaining("celular");

        when(agencies.findById(any())).thenReturn(Optional.of(agency(false, true)));
        assertThatThrownBy(() -> service.prepare(
                new VentaEntregaCreateRequest(
                        "SHALOM", "Ana Torres", "12345678", "987654321", 12L, "PENDING"), branch()))
                .hasMessageContaining("no esta disponible");
    }

    private Sucursal branch() {
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        branch.setNombre("San Carlos");
        return branch;
    }

    private ShalomAgency agency(boolean active, boolean destinationEnabled) {
        ShalomAgency agency = new ShalomAgency();
        agency.setIdShalomAgency(12L);
        agency.setName("LIMA / LIMA / AGENCIA CENTRO");
        agency.setAddress("Av. Principal 123");
        agency.setActive(active);
        agency.setDestinationEnabled(destinationEnabled);
        return agency;
    }
}
