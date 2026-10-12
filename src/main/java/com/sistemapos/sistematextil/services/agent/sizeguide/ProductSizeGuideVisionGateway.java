package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.math.BigDecimal;
import java.util.List;

public interface ProductSizeGuideVisionGateway {

    VisionResult extract(Long connectionId, byte[] content, String mimeType);

    record VisionResult(String provider, String model, String credentialSource, long latencyMs,
            Extraction extraction) {}

    record Extraction(boolean sizeGuide, String measurementType, String unit, String description,
            List<Component> components, int confidence) {}

    record Component(String name, String description, List<Measurement> measurements) {}

    record Measurement(String sizeLabel, String measurementName, BigDecimal referenceValue,
            BigDecimal minimumValue, BigDecimal maximumValue, BigDecimal toleranceValue,
            String originalValue) {}
}
