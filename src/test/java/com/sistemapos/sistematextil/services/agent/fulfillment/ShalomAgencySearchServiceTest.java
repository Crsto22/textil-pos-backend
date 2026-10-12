package com.sistemapos.sistematextil.services.agent.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.ShalomAgency;
import com.sistemapos.sistematextil.repositories.ShalomAgencyRepository;

class ShalomAgencySearchServiceTest {

    @Test
    void resolvesLaVictoriaInsteadOfAnAddressContainingVictor() {
        ShalomAgencyRepository repository = mock(ShalomAgencyRepository.class);
        ShalomAgency laVictoria = agency(3L,
                "LIMA / LIMA / LA VICTORIA / GAMARRA",
                "LIMA", "LIMA", "LA VICTORIA", "GAMARRA",
                "JR. HUANUCO 1705, LA VICTORIA");
        ShalomAgency canete = agency(2L,
                "LIMA / CAÑETE / SAN VICENTE DE CAÑETE",
                "LIMA", "CAÑETE", "SAN VICENTE", "CAÑETE",
                "AA.HH. VICTOR ANDRES BELAUNDE, SAN VICENTE DE CAÑETE");
        when(repository.findByActiveTrueAndDestinationEnabledTrueOrderByNameAsc())
                .thenReturn(List.of(canete, laVictoria));

        var result = new ShalomAgencySearchService(repository).search("Agencia Lima la Victor");

        assertEquals("AMBIGUOUS", result.status());
        assertEquals(3L, result.candidates().getFirst().agencyId());
    }

    @Test
    void prioritizesRequestedDistrictOverIncidentalAddressWords() {
        ShalomAgencyRepository repository = mock(ShalomAgencyRepository.class);
        ShalomAgency sjl = agency(1L,
                "LIMA / LIMA / SAN JUAN DE LURIGANCHO / CANTO GRANDE",
                "LIMA", "LIMA", "SAN JUAN DE LURIGANCHO", "CANTO GRANDE",
                "AV. CANTO GRANDE 123, SAN JUAN DE LURIGANCHO");
        ShalomAgency canete = agency(2L,
                "LIMA / CAÑETE / SAN VICENTE DE CAÑETE",
                "LIMA", "CAÑETE", "SAN VICENTE", "CAÑETE",
                "AA.HH. VICTOR ANDRES BELAUNDE, SAN VICENTE DE CAÑETE");
        when(repository.findByActiveTrueAndDestinationEnabledTrueOrderByNameAsc())
                .thenReturn(List.of(canete, sjl));

        var result = new ShalomAgencySearchService(repository).search("San Juan de Lurigancho");

        assertEquals("MATCH", result.status());
        assertEquals(1, result.candidates().size());
        assertEquals(1L, result.candidates().getFirst().agencyId());
    }

    private ShalomAgency agency(Long id, String name, String department, String province,
            String zone, String place, String address) {
        ShalomAgency value = new ShalomAgency();
        value.setIdShalomAgency(id);
        value.setExternalId(id.intValue());
        value.setName(name);
        value.setDepartment(department);
        value.setProvince(province);
        value.setZone(zone);
        value.setPlace(place);
        value.setAddress(address);
        value.setActive(true);
        value.setDestinationEnabled(true);
        value.setNormalizedSearch(ShalomAgencySyncService.normalize(
                name + " " + department + " " + province + " " + zone + " " + place + " " + address));
        return value;
    }
}
