package com.sistemapos.sistematextil.util.asistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.ResumenResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.SesionAsistenciaResponse;

class AsistenciaExcelExporterTest {

    @Test
    void generaHojasConEstadoHorasYRotativos() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        ResumenResponse dia = new ResumenResponse(
                1, "1001", "Ana Perez", 1, "Central", null, null, fecha, null, null,
                LocalDateTime.of(2026, 7, 13, 8, 0), LocalDateTime.of(2026, 7, 13, 17, 0),
                "PRESENTE", 0, 9, 595, 4, false, 0, false, List.of(), List.of());
        ResumenResponse rotativo = new ResumenResponse(
                2, "1002", "Rosa Modelo", null, null, null, null, fecha, null, null,
                LocalDateTime.of(2026, 7, 13, 9, 0), LocalDateTime.of(2026, 7, 13, 13, 0),
                "PRESENTE", 0, 240, 14400, 2, false, 0, true, List.of(),
                List.of(new SesionAsistenciaResponse(1, "Central", LocalDateTime.of(2026, 7, 13, 9, 0),
                        LocalDateTime.of(2026, 7, 13, 13, 0), "Reloj Central", "Reloj Central",
                        1, "Central", 240, 14400, true)));

        byte[] archivo = AsistenciaExcelExporter.exportar(
                List.of(dia, rotativo), fecha, fecha.plusDays(6), "TODOS", null, null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertEquals("Asistencia", workbook.getSheetAt(0).getSheetName());
            assertEquals(595 / 86400d,
                    workbook.getSheet("Horas trabajadas").getRow(10).getCell(2).getNumericCellValue(), 0.0000001);
            assertEquals("[h]:mm:ss",
                    workbook.getSheet("Horas trabajadas").getRow(10).getCell(2).getCellStyle().getDataFormatString());
            assertEquals("P", workbook.getSheet("Asistencia").getRow(11).getCell(2).getStringCellValue());
            assertEquals(IndexedColors.LIGHT_GREEN.getIndex(),
                    workbook.getSheet("Asistencia").getRow(11).getCell(2).getCellStyle().getFillForegroundColor());
            assertEquals("Rotativos por sucursal", workbook.getSheetAt(2).getSheetName());
            assertEquals("Rosa Modelo", workbook.getSheet("Rotativos por sucursal").getRow(9).getCell(2).getStringCellValue());
        }
    }

    @Test
    void separaTotalesPorSemanaYExplicaElEstadoPendiente() throws Exception {
        LocalDate lunes = LocalDate.of(2026, 7, 13);
        ResumenResponse primeraSemana = new ResumenResponse(
                1, "1001", "Ana Perez", 1, "Central", null, null, lunes, null, null,
                lunes.atTime(8, 0), lunes.atTime(17, 0), "PRESENTE", 0, 540, 32400, 2,
                false, 0, false, List.of(), List.of());
        ResumenResponse segundaSemana = new ResumenResponse(
                1, "1001", "Ana Perez", 1, "Central", null, null, lunes.plusWeeks(1), null, null,
                lunes.plusWeeks(1).atTime(8, 0), lunes.plusWeeks(1).atTime(16, 0), "PENDIENTE", 0, 480, 28800, 2,
                false, 0, false, List.of(), List.of());

        byte[] archivo = AsistenciaExcelExporter.exportar(
                List.of(primeraSemana, segundaSemana), lunes, lunes.plusDays(13), "TODOS", null, null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            assertEquals("TOTAL SEMANA", workbook.getSheet("Horas trabajadas").getRow(9).getCell(9).getStringCellValue());
            assertEquals(32400 / 86400d,
                    workbook.getSheet("Horas trabajadas").getRow(10).getCell(9).getNumericCellValue(), 0.0000001);
            assertEquals(28800 / 86400d,
                    workbook.getSheet("Horas trabajadas").getRow(14).getCell(9).getNumericCellValue(), 0.0000001);
            assertTrue(workbook.getSheet("Asistencia").getRow(7).getCell(1).getStringCellValue().contains("PD: Pendiente"));
        }
    }

    @Test
    void muestraGuionParaDiasFuturos() throws Exception {
        LocalDate fecha = LocalDate.now(ZoneId.of("America/Lima")).plusDays(1);
        ResumenResponse dia = new ResumenResponse(
                1, "1001", "Ana Perez", 1, "Central", null, null, fecha, null, null,
                null, null, "PENDIENTE", 0, 0, 0, 0, false, 0, false, List.of(), List.of());

        byte[] archivo = AsistenciaExcelExporter.exportar(
                List.of(dia), fecha, fecha.plusDays(6), "TODOS", null, null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            int column = fecha.getDayOfWeek().getValue() + 1;
            assertEquals("-", workbook.getSheet("Asistencia").getRow(11).getCell(column).getStringCellValue());
            assertEquals("Sin resultados", workbook.getSheet("Rotativos por sucursal").getRow(9).getCell(0).getStringCellValue());
        }
    }
}
