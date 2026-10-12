package com.sistemapos.sistematextil.services.agent.sizeguide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.agent.sizeguide.SpringAiGeminiProductSizeGuideVisionGateway.RawComponent;
import com.sistemapos.sistematextil.services.agent.sizeguide.SpringAiGeminiProductSizeGuideVisionGateway.RawExtraction;
import com.sistemapos.sistematextil.services.agent.sizeguide.SpringAiGeminiProductSizeGuideVisionGateway.RawMeasurement;

class SpringAiGeminiProductSizeGuideVisionGatewayTest {

    @Test
    void convertsEmptyStructuredFieldsToNullWithoutRequiringJsonNulls() {
        var gateway = new SpringAiGeminiProductSizeGuideVisionGateway(null, 60_000, 5_000);
        RawExtraction raw = new RawExtraction(true, " cuerpo ", "cm", "", List.of(
                new RawComponent("PANTALON", "", List.of(
                        new RawMeasurement("S", "Cintura", "76 cm", "", "", "", "76"),
                        new RawMeasurement("M", "Cadera", "", "98,5", "102,5", "1", "98.5-102.5")))), 98);

        var normalized = gateway.normalize(raw);

        assertThat(normalized.measurementType()).isEqualTo("CUERPO");
        assertThat(normalized.components().getFirst().measurements().getFirst().referenceValue())
                .isEqualByComparingTo("76");
        assertThat(normalized.components().getFirst().measurements().getFirst().minimumValue()).isNull();
        assertThat(normalized.components().getFirst().measurements().get(1).minimumValue())
                .isEqualByComparingTo("98.5");
        assertThat(normalized.components().getFirst().measurements().get(1).toleranceValue())
                .isEqualByComparingTo("1");
    }
}
