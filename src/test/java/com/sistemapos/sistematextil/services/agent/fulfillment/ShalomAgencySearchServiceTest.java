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

    @Test
    void resolvesAnOfferedAgencyRegardlessOfGenericWordsOrTokenOrder() {
        ShalomAgencyRepository repository = mock(ShalomAgencyRepository.class);
        ShalomAgency liverpool = agency(22L,
                "LA LIBERTAD / TRUJILLO / TRUJILLO / CALLE LIVERPOOL",
                "LA LIBERTAD", "TRUJILLO", "TRUJILLO", "CALLE LIVERPOOL",
                "CALLE LIVERPOOL 241, TRUJILLO");
        when(repository.findById(22L)).thenReturn(java.util.Optional.of(liverpool));

        ShalomAgencySearchService service = new ShalomAgencySearchService(repository);

        assertEquals(22L, service.resolveReference("Trujillo - sede Liverpool", List.of(22L)).agencyId());
        assertEquals(22L, service.resolveReference("Shalom de Liverpool en Trujillo", List.of(22L)).agencyId());
        assertEquals(22L, service.resolveReference("Liverpool Trujillo", List.of(22L)).agencyId());
    }

    @Test
    void doesNotChooseWhenTheReferenceMatchesSeveralOfferedAgencies() {
        ShalomAgencyRepository repository = mock(ShalomAgencyRepository.class);
        ShalomAgency first = agency(22L, "TRUJILLO / CALLE LIVERPOOL",
                "LA LIBERTAD", "TRUJILLO", "TRUJILLO", "LIVERPOOL", "CALLE LIVERPOOL 241");
        ShalomAgency second = agency(23L, "TRUJILLO / AV ESPAÑA",
                "LA LIBERTAD", "TRUJILLO", "TRUJILLO", "AV ESPAÑA", "AV ESPAÑA 100");
        when(repository.findById(22L)).thenReturn(java.util.Optional.of(first));
        when(repository.findById(23L)).thenReturn(java.util.Optional.of(second));

        var result = new ShalomAgencySearchService(repository)
                .resolveReference("agencia de Trujillo", List.of(22L, 23L));

        assertEquals("AMBIGUOUS", result.status());
        assertEquals(null, result.agencyId());
        assertEquals(2, result.candidates().size());
    }

    @Test
    void resolvesANumericReplyUsingTheOfferedOrder() {
        ShalomAgencyRepository repository = mock(ShalomAgencyRepository.class);
        ShalomAgency first = agency(80L, "CHICLAYO / CALLE TACNA",
                "LAMBAYEQUE", "CHICLAYO", "CHICLAYO", "CALLE TACNA", "CALLE TACNA 1095");
        ShalomAgency second = agency(81L, "CHICLAYO / TAHUANTINSUYO",
                "LAMBAYEQUE", "CHICLAYO", "JOSE LEONARDO ORTIZ", "TAHUANTINSUYO", "CALLE TAHUANTINSUYO 995");
        when(repository.findById(80L)).thenReturn(java.util.Optional.of(first));
        when(repository.findById(81L)).thenReturn(java.util.Optional.of(second));

        ShalomAgencySearchService service = new ShalomAgencySearchService(repository);

        assertEquals(80L, service.resolveReference("1", List.of(80L, 81L)).agencyId());
        assertEquals(81L, service.resolveReference("2", List.of(80L, 81L)).agencyId());
        assertEquals("NO_MATCH", service.resolveReference("3", List.of(80L, 81L)).status());
    }

    @Test
    void returnsUpToFiveAgenciesForAnAmbiguousSearch() {
        ShalomAgencyRepository repository = mock(ShalomAgencyRepository.class);
        List<ShalomAgency> agencies = java.util.stream.LongStream.rangeClosed(1, 6)
                .mapToObj(id -> agency(id, "CHICLAYO / SEDE " + id,
                        "LAMBAYEQUE", "CHICLAYO", "CHICLAYO", "SEDE " + id,
                        "CALLE CHICLAYO " + id))
                .toList();
        when(repository.findByActiveTrueAndDestinationEnabledTrueOrderByNameAsc()).thenReturn(agencies);

        var result = new ShalomAgencySearchService(repository).search("Chiclayo");

        assertEquals("AMBIGUOUS", result.status());
        assertEquals(5, result.candidates().size());
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
