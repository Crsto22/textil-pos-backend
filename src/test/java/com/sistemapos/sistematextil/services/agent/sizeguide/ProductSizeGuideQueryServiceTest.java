package com.sistemapos.sistematextil.services.agent.sizeguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.BodyMeasurement;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.MeasurementRow;

class ProductSizeGuideQueryServiceTest {

    @Test
    void productNameAsComponentMeansWholeSetAndTiePrefersLargerSize() {
        var service = new ProductSizeGuideQueryService(null, null, null);
        List<MeasurementRow> rows = List.of(
                row(1L, "BLAZER", "XS", "CONTORNO_DE_CINTURA", 76),
                row(1L, "BLAZER", "S", "CONTORNO_DE_CINTURA", 80),
                row(1L, "BLAZER", "M", "CONTORNO_DE_CINTURA", 84),
                row(2L, "PANTALON", "XS", "CONTORNO_DE_CINTURA", 68),
                row(2L, "PANTALON", "XS", "CONTORNO_DE_CADERA", 92),
                row(2L, "PANTALON", "S", "CONTORNO_DE_CINTURA", 72),
                row(2L, "PANTALON", "S", "CONTORNO_DE_CADERA", 96),
                row(2L, "PANTALON", "M", "CONTORNO_DE_CINTURA", 76),
                row(2L, "PANTALON", "M", "CONTORNO_DE_CADERA", 100));

        var result = service.calculate(45, "BELEN", "PRENDA", "BELEN",
                List.of(new BodyMeasurement("cintura", BigDecimal.valueOf(70), "CM"),
                        new BodyMeasurement("cadera", BigDecimal.valueOf(100), "CM")),
                "", rows, Map.of("XS", 10, "S", 10, "L", 10));

        assertEquals(2, result.components().size());
        assertTrue(result.components().stream().allMatch(item -> item.recommendedSize().equals("M")));
        assertTrue(result.components().stream().noneMatch(item -> item.available()));
        assertEquals(false, result.componentConflict());
    }

    private MeasurementRow row(Long component, String name, String size, String measure, int value) {
        return new MeasurementRow(component, name, "", size, measure, measure,
                BigDecimal.valueOf(value), null, null);
    }
}
