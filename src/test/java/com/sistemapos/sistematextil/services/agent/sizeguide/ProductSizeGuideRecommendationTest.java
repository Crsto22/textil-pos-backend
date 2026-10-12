package com.sistemapos.sistematextil.services.agent.sizeguide;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.BodyMeasurement;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.MeasurementRow;

class ProductSizeGuideRecommendationTest {

    private final ProductSizeGuideQueryService service = new ProductSizeGuideQueryService(null, null, null);

    @Test
    void recommendsLyanaSizeSForSeventyFiveCentimeterWaistAndChecksStockSeparately() {
        List<MeasurementRow> rows = List.of(
                row(1L, "CHALECO", "XS", "CONTORNO_DE_CINTURA", 72),
                row(1L, "CHALECO", "S", "CONTORNO_DE_CINTURA", 76),
                row(1L, "CHALECO", "M", "CONTORNO_DE_CINTURA", 80),
                row(1L, "CHALECO", "L", "CONTORNO_DE_CINTURA", 84));

        var result = service.calculate(60, "LYANA", "INDETERMINADO", "",
                List.of(new BodyMeasurement("cintura", new BigDecimal("75"), "CM")), "NORMAL",
                rows, Map.of("S", 0, "M", 3));

        assertThat(result.components()).singleElement().satisfies(component -> {
            assertThat(component.recommendedSize()).isEqualTo("S");
            assertThat(component.available()).isFalse();
            assertThat(component.availableGuideSizes()).containsExactly("M");
        });
        assertThat(result.orientationOnly()).isTrue();
    }

    @Test
    void choosesOneCommercialSizeForEveryPieceOfASet() {
        List<MeasurementRow> rows = List.of(
                row(1L, "BLAZER", "XS", "CONTORNO_DE_CINTURA", 76),
                row(1L, "BLAZER", "S", "CONTORNO_DE_CINTURA", 80),
                row(2L, "PANTALON", "S", "CONTORNO_DE_CINTURA", 72),
                row(2L, "PANTALON", "M", "CONTORNO_DE_CINTURA", 76));

        var result = service.calculate(45, "BELEN", "PRENDA", "",
                List.of(new BodyMeasurement("cintura", new BigDecimal("75"), "CM")), "NORMAL",
                rows, Map.of("XS", 2, "S", 2, "M", 2));

        assertThat(result.componentConflict()).isFalse();
        assertThat(result.components()).extracting(component -> component.component() + ":" + component.recommendedSize())
                .containsExactly("BLAZER:S", "PANTALON:S");
    }

    @Test
    void convertsInchesAndChoosesTheNextSizeWhenTheClosestWouldBeSmaller() {
        List<MeasurementRow> rows = List.of(
                row(1L, "PANTALON", "S", "CONTORNO_DE_CINTURA", 76),
                row(1L, "PANTALON", "M", "CONTORNO_DE_CINTURA", 80));

        var result = service.calculate(60, "LYANA", "CUERPO", "",
                List.of(new BodyMeasurement("cintura", new BigDecimal("30"), "IN")), "NORMAL",
                rows, Map.of("S", 1, "M", 1));

        assertThat(result.components()).singleElement()
                .extracting(component -> component.recommendedSize()).isEqualTo("M");
    }

    private MeasurementRow row(long componentId, String component, String size, String measure, int value) {
        return new MeasurementRow(componentId, component, "", size, measure,
                measure.replace('_', ' '), new BigDecimal(value), null, null);
    }
}
