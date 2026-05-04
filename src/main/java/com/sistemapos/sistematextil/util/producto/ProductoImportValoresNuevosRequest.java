package com.sistemapos.sistematextil.util.producto;

import java.util.List;
import java.util.Map;

public record ProductoImportValoresNuevosRequest(
        List<String> missingCategorias,
        List<String> missingColores,
        List<String> missingTallas,
        Map<String, String> savedColorHexes
) {
}
