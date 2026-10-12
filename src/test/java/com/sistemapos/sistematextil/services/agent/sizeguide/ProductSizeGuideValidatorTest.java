package com.sistemapos.sistematextil.services.agent.sizeguide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.Component;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.Extraction;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.Measurement;

class ProductSizeGuideValidatorTest {

    private final ProductSizeGuideValidator validator = new ProductSizeGuideValidator(85);

    @Test
    void acceptsDifferentComponentsDynamicMeasurementsAndUnknownCommercialSizes() {
        Extraction lyana = new Extraction(true, "INDETERMINADO", "CM", "Guia de LYANA", List.of(
                component("CHALECO",
                        measure("XS", "Contorno de busto", "92"),
                        measure("S", "Contorno de busto", "96"),
                        measure("XXL", "Ancho de hombro", "40")),
                component("PANTALON",
                        measure("XS", "Contorno de cintura", "72"),
                        measure("S", "Contorno de cintura", "76"),
                        measure("XXL", "Tiro posterior", "44"))), 97);

        var result = validator.validate(lyana);

        assertThat(result.measurementType()).isEqualTo("INDETERMINADO");
        assertThat(result.sourceUnit()).isEqualTo("CM");
        assertThat(result.extraction().components()).hasSize(2);
        assertThat(ProductSizeGuideValidator.normalizedCode("Tiro posterior"))
                .isEqualTo("TIRO_POSTERIOR");
    }

    @Test
    void rejectsLowConfidenceSoItCannotBeUsedForRecommendations() {
        Extraction uncertain = new Extraction(true, "CUERPO", "CM", "", List.of(
                component("PRENDA", measure("S", "Cintura", "76"), measure("M", "Cintura", "80"))), 74);

        assertThatThrownBy(() -> validator.validate(uncertain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confianza insuficiente");
    }

    @Test
    void rejectsDuplicateCells() {
        Extraction duplicate = new Extraction(true, "PRENDA", "CM", "", List.of(
                component("BLAZER", measure("S", "Cintura", "80"),
                        measure("S", "Cintura", "81"))), 99);

        assertThatThrownBy(() -> validator.validate(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicadas");
    }

    private Component component(String name, Measurement... measurements) {
        return new Component(name, "", List.of(measurements));
    }

    private Measurement measure(String size, String name, String value) {
        return new Measurement(size, name, new BigDecimal(value), null, null, null, value);
    }
}
