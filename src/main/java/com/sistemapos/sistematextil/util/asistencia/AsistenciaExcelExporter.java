package com.sistemapos.sistematextil.util.asistencia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.ResumenResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.SesionAsistenciaResponse;

public final class AsistenciaExcelExporter {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] DIAS = { "LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM" };

    private AsistenciaExcelExporter() {
    }

    public static byte[] exportar(
            List<ResumenResponse> resumen,
            LocalDate desde,
            LocalDate hasta,
            String filtros,
            Usuario usuario,
            Sucursal sucursalFiltro) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(workbook);
            Map<Integer, List<ResumenResponse>> porTrabajador = agrupar(resumen);

            crearHojaAsistencia(workbook, estilos, porTrabajador, desde, hasta, filtros, usuario, sucursalFiltro);
            crearHojaHoras(workbook, estilos, porTrabajador, desde, hasta, filtros, usuario, sucursalFiltro);
            crearHojaDetalle(workbook, estilos, resumen, desde, hasta, filtros, usuario, sucursalFiltro);
            crearHojaSesiones(workbook, estilos, resumen, desde, hasta, filtros, usuario, sucursalFiltro);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el reporte Excel de asistencias", ex);
        }
    }

    private static void crearHojaAsistencia(
            Workbook workbook,
            Estilos estilos,
            Map<Integer, List<ResumenResponse>> trabajadores,
            LocalDate desde,
            LocalDate hasta,
            String filtros,
            Usuario usuario,
            Sucursal sucursalFiltro) {
        Sheet sheet = workbook.createSheet("Asistencia");
        int rowIndex = cabecera(sheet, "REPORTE DE ASISTENCIA", desde, hasta, filtros, usuario, sucursalFiltro);
        rowIndex = filaInfo(sheet, rowIndex, "Leyenda",
                "P: Presente | T: Tardanza | SA: Salida anticipada | F: Falta | I: Incompleto | RR: Requiere revision | EC: En curso | PD: Pendiente | RU: Registro unico | D: Descanso | TD: Trabajo en descanso | SR: Sin registro | -: Dia futuro");
        rowIndex++;

        if (trabajadores.isEmpty()) {
            sinResultados(sheet, rowIndex, estilos);
        } else {
            for (LocalDate lunes : semanas(desde, hasta)) {
                rowIndex = bloqueSemanal(sheet, estilos, trabajadores, lunes, desde, hasta, rowIndex, false);
                rowIndex++;
            }
        }
        ajustarColumnas(sheet, 9);
        sheet.createFreezePane(2, 8);
    }

    private static void crearHojaHoras(
            Workbook workbook,
            Estilos estilos,
            Map<Integer, List<ResumenResponse>> trabajadores,
            LocalDate desde,
            LocalDate hasta,
            String filtros,
            Usuario usuario,
            Sucursal sucursalFiltro) {
        Sheet sheet = workbook.createSheet("Horas trabajadas");
        int rowIndex = cabecera(sheet, "HORAS TRABAJADAS", desde, hasta, filtros, usuario, sucursalFiltro) + 1;
        if (trabajadores.isEmpty()) {
            sinResultados(sheet, rowIndex, estilos);
        } else {
            for (LocalDate lunes : semanas(desde, hasta)) {
                rowIndex = bloqueSemanal(sheet, estilos, trabajadores, lunes, desde, hasta, rowIndex, true);
                rowIndex++;
            }
        }
        ajustarColumnas(sheet, 10);
        sheet.createFreezePane(2, 7);
    }

    private static int bloqueSemanal(
            Sheet sheet,
            Estilos estilos,
            Map<Integer, List<ResumenResponse>> trabajadores,
            LocalDate lunes,
            LocalDate desde,
            LocalDate hasta,
            int rowIndex,
            boolean horas) {
        LocalDate domingo = lunes.plusDays(6);
        Row titulo = sheet.createRow(rowIndex++);
        Cell tituloCell = titulo.createCell(0);
        tituloCell.setCellValue("Semana del " + max(lunes, desde).format(FECHA) + " al " + min(domingo, hasta).format(FECHA));
        tituloCell.setCellStyle(estilos.seccion);
        sheet.addMergedRegion(new CellRangeAddress(titulo.getRowNum(), titulo.getRowNum(), 0, horas ? 9 : 8));

        Row header = sheet.createRow(rowIndex++);
        String[] fijos = horas
                ? new String[] { "CODIGO", "TRABAJADOR", "LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM", "TOTAL SEMANA" }
                : new String[] { "CODIGO", "TRABAJADOR", "LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM" };
        for (int column = 0; column < fijos.length; column++) {
            Cell cell = header.createCell(column);
            String value = fijos[column];
            if (column >= 2 && column <= 8) {
                LocalDate fecha = lunes.plusDays(column - 2L);
                value = DIAS[column - 2] + " " + fecha.format(DateTimeFormatter.ofPattern("dd/MM"));
            }
            cell.setCellValue(value);
            cell.setCellStyle(estilos.header);
        }

        for (List<ResumenResponse> dias : trabajadores.values()) {
            ResumenResponse trabajador = dias.getFirst();
            Map<LocalDate, ResumenResponse> porFecha = new LinkedHashMap<>();
            dias.forEach(dia -> porFecha.put(dia.fecha(), dia));
            Row row = sheet.createRow(rowIndex++);
            celda(row, 0, trabajador.codigoZkteco(), estilos.texto);
            celda(row, 1, trabajador.trabajador(), estilos.texto);
            for (int day = 0; day < 7; day++) {
                LocalDate fecha = lunes.plusDays(day);
                ResumenResponse dia = fecha.isBefore(desde) || fecha.isAfter(hasta) ? null : porFecha.get(fecha);
                Cell cell = row.createCell(day + 2);
                if (horas) {
                    escribirHoras(cell, dia, estilos);
                } else {
                    escribirEstado(cell, dia, estilos);
                }
            }
            if (horas) {
                long total = dias.stream().filter(dia -> !dia.fecha().isBefore(lunes)
                        && !dia.fecha().isAfter(domingo)).filter(AsistenciaExcelExporter::tieneHoras)
                        .mapToLong(ResumenResponse::segundosTrabajados).sum();
                Cell totalCell = row.createCell(9);
                totalCell.setCellValue(total / 86400d);
                totalCell.setCellStyle(estilos.totalHoras);
            }
        }
        return rowIndex;
    }

    private static void crearHojaDetalle(
            Workbook workbook,
            Estilos estilos,
            List<ResumenResponse> resumen,
            LocalDate desde,
            LocalDate hasta,
            String filtros,
            Usuario usuario,
            Sucursal sucursalFiltro) {
        Sheet sheet = workbook.createSheet("Detalle diario");
        int rowIndex = cabecera(sheet, "DETALLE DIARIO", desde, hasta, filtros, usuario, sucursalFiltro) + 1;
        String[] headers = { "FECHA", "CODIGO", "TRABAJADOR", "MODALIDAD", "SUCURSAL BASE",
                "SUCURSALES VISITADAS", "TURNO", "PRIMERA MARCA", "ULTIMA MARCA", "ESTADO",
                "TARDANZA", "SALIDA ANTICIPADA", "HORAS", "MARCACIONES" };
        int headerRow = rowIndex;
        rowIndex = header(sheet, rowIndex, headers, estilos);
        if (resumen.isEmpty()) {
            sinResultados(sheet, rowIndex, estilos);
        } else {
            for (ResumenResponse item : resumen) {
                Row row = sheet.createRow(rowIndex++);
                celda(row, 0, item.fecha().format(FECHA), estilos.texto);
                celda(row, 1, item.codigoZkteco(), estilos.texto);
                celda(row, 2, item.trabajador(), estilos.texto);
                celda(row, 3, item.rotativo() ? "ROTATIVO" : "FIJO", estilos.texto);
                celda(row, 4, valor(item.sucursal(), "Sin sucursal base"), estilos.texto);
                celda(row, 5, item.sucursalesMarcacion().stream().map(s -> s.sucursal()).distinct()
                        .reduce((a, b) -> a + ", " + b).orElse("-"), estilos.texto);
                celda(row, 6, valor(item.turno(), "Sin turno"), estilos.texto);
                celda(row, 7, fechaHora(item.primeraMarcacion()), estilos.texto);
                celda(row, 8, fechaHora(item.ultimaMarcacion()), estilos.texto);
                celda(row, 9, estadoVisible(item), esDiaFuturo(item) ? estilos.gris : estilos.estado(item));
                celdaNumero(row, 10, item.minutosTardanza(), estilos.numero);
                celdaNumero(row, 11, item.minutosSalidaAnticipada(), estilos.numero);
                Cell horas = row.createCell(12);
                if (tieneHoras(item)) {
                    horas.setCellValue(item.segundosTrabajados() / 86400d);
                    horas.setCellStyle(estilos.horas);
                } else {
                    horas.setCellValue("-");
                    horas.setCellStyle(estilos.centrada);
                }
                celdaNumero(row, 13, item.cantidadMarcaciones(), estilos.numero);
            }
            sheet.setAutoFilter(new CellRangeAddress(headerRow, rowIndex - 1, 0, headers.length - 1));
        }
        ajustarColumnas(sheet, headers.length);
        sheet.createFreezePane(0, headerRow + 1);
    }

    private static void crearHojaSesiones(
            Workbook workbook,
            Estilos estilos,
            List<ResumenResponse> resumen,
            LocalDate desde,
            LocalDate hasta,
            String filtros,
            Usuario usuario,
            Sucursal sucursalFiltro) {
        Sheet sheet = workbook.createSheet("Sesiones por sucursal");
        int rowIndex = cabecera(sheet, "SESIONES POR SUCURSAL", desde, hasta, filtros, usuario, sucursalFiltro) + 1;
        String[] headers = { "FECHA", "CODIGO", "TRABAJADOR", "SUCURSAL ENTRADA", "SUCURSAL SALIDA", "ENTRADA", "SALIDA",
                "DISPOSITIVO ENTRADA", "DISPOSITIVO SALIDA", "DURACION", "ESTADO" };
        int headerRow = rowIndex;
        rowIndex = header(sheet, rowIndex, headers, estilos);
        int dataStart = rowIndex;
        for (ResumenResponse item : resumen) {
            for (SesionAsistenciaResponse sesion : item.sesiones()) {
                Row row = sheet.createRow(rowIndex++);
                celda(row, 0, item.fecha().format(FECHA), estilos.texto);
                celda(row, 1, item.codigoZkteco(), estilos.texto);
                celda(row, 2, item.trabajador(), estilos.texto);
                celda(row, 3, sesion.sucursal(), estilos.texto);
                celda(row, 4, valor(sesion.sucursalSalida(), "-"), estilos.texto);
                celda(row, 5, fechaHora(sesion.entrada()), estilos.texto);
                celda(row, 6, fechaHora(sesion.salida()), estilos.texto);
                celda(row, 7, valor(sesion.dispositivoEntrada(), "-"), estilos.texto);
                celda(row, 8, valor(sesion.dispositivoSalida(), "-"), estilos.texto);
                Cell duracion = row.createCell(9);
                if (sesion.completa()) {
                    duracion.setCellValue(sesion.segundosTrabajados() / 86400d);
                    duracion.setCellStyle(estilos.horas);
                } else {
                    duracion.setCellValue("-");
                    duracion.setCellStyle(estilos.centrada);
                }
                celda(row, 10, sesion.completa() ? "COMPLETA" : "INCOMPLETA",
                        sesion.completa() ? estilos.verde : estilos.rojo);
            }
        }
        if (rowIndex == dataStart) {
            sinResultados(sheet, rowIndex, estilos);
        } else {
            sheet.setAutoFilter(new CellRangeAddress(headerRow, rowIndex - 1, 0, headers.length - 1));
        }
        ajustarColumnas(sheet, headers.length);
        sheet.createFreezePane(0, headerRow + 1);
    }

    private static int cabecera(
            Sheet sheet,
            String titulo,
            LocalDate desde,
            LocalDate hasta,
            String filtros,
            Usuario usuario,
            Sucursal sucursalFiltro) {
        Sucursal sucursalUsuario = usuario != null ? usuario.getSucursal() : null;
        Empresa empresa = sucursalUsuario != null ? sucursalUsuario.getEmpresa()
                : sucursalFiltro != null ? sucursalFiltro.getEmpresa() : null;
        int row = 0;
        row = filaInfo(sheet, row, titulo, "");
        row = filaInfo(sheet, row, "Empresa", empresa == null ? "Sistema Textil"
                : valor(empresa.getNombreComercial(), empresa.getNombre()));
        row = filaInfo(sheet, row, "RUC", empresa == null ? "-" : valor(empresa.getRuc(), "-"));
        row = filaInfo(sheet, row, "Periodo", desde.format(FECHA) + " al " + hasta.format(FECHA));
        row = filaInfo(sheet, row, "Filtros", filtros);
        row = filaInfo(sheet, row, "Generado", LocalDateTime.now(ZONA_LIMA).format(FECHA_HORA));
        row = filaInfo(sheet, row, "Usuario", usuario == null ? "-"
                : valor(usuario.getNombre(), "") + " " + valor(usuario.getApellido(), ""));
        return row;
    }

    private static int filaInfo(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value == null ? "" : value);
        return rowIndex;
    }

    private static int header(Sheet sheet, int rowIndex, String[] headers, Estilos estilos) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(estilos.header);
        }
        return rowIndex;
    }

    private static void escribirEstado(Cell cell, ResumenResponse dia, Estilos estilos) {
        if (dia == null) {
            cell.setCellValue("-");
            cell.setCellStyle(estilos.gris);
            return;
        }
        if (esDiaFuturo(dia)) {
            cell.setCellValue("-");
            cell.setCellStyle(estilos.gris);
            return;
        }
        cell.setCellValue(codigoEstado(dia));
        cell.setCellStyle(estilos.estado(dia));
    }

    private static boolean esDiaFuturo(ResumenResponse dia) {
        return dia.fecha().isAfter(LocalDate.now(ZONA_LIMA));
    }

    private static String estadoVisible(ResumenResponse dia) {
        if (esDiaFuturo(dia)) {
            return "DIA FUTURO";
        }
        if (dia.salidaAnticipada()) {
            return "TARDANZA".equals(dia.estado()) ? "TARDANZA + SALIDA ANTICIPADA" : "SALIDA ANTICIPADA";
        }
        return dia.estado();
    }

    private static void escribirHoras(Cell cell, ResumenResponse dia, Estilos estilos) {
        if (!tieneHoras(dia)) {
            cell.setCellValue("-");
            cell.setCellStyle(estilos.centrada);
            return;
        }
        cell.setCellValue(dia.segundosTrabajados() / 86400d);
        cell.setCellStyle(estilos.horas);
    }

    private static String codigoEstado(ResumenResponse dia) {
        if (dia.salidaAnticipada()) {
            return "TARDANZA".equals(dia.estado()) ? "T + SA" : "SA";
        }
        return switch (dia.estado()) {
            case "PRESENTE" -> "P";
            case "TARDANZA" -> "T";
            case "FALTA" -> "F";
            case "INCOMPLETA", "REGISTRO_INCOMPLETO" -> "I";
            case "REQUIERE_REVISION" -> "RR";
            case "EN_CURSO" -> "EC";
            case "REGISTRO_UNICO" -> "RU";
            case "PENDIENTE" -> "PD";
            case "DESCANSO" -> "D";
            case "TRABAJO_EN_DESCANSO" -> "TD";
            case "SIN_REGISTRO" -> "SR";
            default -> dia.estado();
        };
    }

    private static boolean tieneHoras(ResumenResponse dia) {
        return dia != null && dia.segundosTrabajados() > 0;
    }

    private static Map<Integer, List<ResumenResponse>> agrupar(List<ResumenResponse> resumen) {
        Map<Integer, List<ResumenResponse>> result = new LinkedHashMap<>();
        for (ResumenResponse item : resumen) {
            result.computeIfAbsent(item.idTrabajador(), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static List<LocalDate> semanas(LocalDate desde, LocalDate hasta) {
        List<LocalDate> semanas = new ArrayList<>();
        LocalDate lunes = desde.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!lunes.isAfter(hasta)) {
            semanas.add(lunes);
            lunes = lunes.plusWeeks(1);
        }
        return semanas;
    }

    private static void sinResultados(Sheet sheet, int rowIndex, Estilos estilos) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue("Sin resultados");
        cell.setCellStyle(estilos.centrada);
    }

    private static void celda(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void celdaNumero(Row row, int column, long value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String fechaHora(LocalDateTime value) {
        return value == null ? "-" : value.format(FECHA_HORA);
    }

    private static String valor(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static void ajustarColumnas(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 768, 50 * 256));
        }
    }

    private static final class Estilos {
        private final CellStyle texto;
        private final CellStyle centrada;
        private final CellStyle numero;
        private final CellStyle horas;
        private final CellStyle totalHoras;
        private final CellStyle header;
        private final CellStyle seccion;
        private final CellStyle verde;
        private final CellStyle amarillo;
        private final CellStyle naranja;
        private final CellStyle rojo;
        private final CellStyle azul;
        private final CellStyle gris;

        private Estilos(Workbook workbook) {
            texto = base(workbook, HorizontalAlignment.LEFT);
            centrada = base(workbook, HorizontalAlignment.CENTER);
            numero = base(workbook, HorizontalAlignment.RIGHT);
            horas = base(workbook, HorizontalAlignment.CENTER);
            horas.setDataFormat(workbook.createDataFormat().getFormat("[h]:mm:ss"));
            totalHoras = workbook.createCellStyle();
            totalHoras.cloneStyleFrom(horas);
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalHoras.setFont(totalFont);
            totalHoras.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            totalHoras.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            header = coloreado(workbook, IndexedColors.DARK_BLUE, IndexedColors.WHITE, true);
            seccion = coloreado(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE, IndexedColors.DARK_BLUE, true);
            verde = coloreado(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN, true);
            amarillo = coloreado(workbook, IndexedColors.LIGHT_YELLOW, IndexedColors.DARK_YELLOW, true);
            naranja = coloreado(workbook, IndexedColors.LIGHT_ORANGE, IndexedColors.BROWN, true);
            rojo = coloreado(workbook, IndexedColors.ROSE, IndexedColors.DARK_RED, true);
            azul = coloreado(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE, IndexedColors.DARK_BLUE, true);
            gris = coloreado(workbook, IndexedColors.GREY_25_PERCENT, IndexedColors.GREY_80_PERCENT, false);
        }

        private CellStyle estado(ResumenResponse dia) {
            if (dia.salidaAnticipada()) {
                return naranja;
            }
            return switch (dia.estado()) {
                case "PRESENTE" -> verde;
                case "TARDANZA", "REGISTRO_UNICO" -> amarillo;
                case "FALTA", "INCOMPLETA", "REGISTRO_INCOMPLETO", "REQUIERE_REVISION" -> rojo;
                case "EN_CURSO" -> azul;
                case "TRABAJO_EN_DESCANSO" -> verde;
                default -> gris;
            };
        }

        private static CellStyle base(Workbook workbook, HorizontalAlignment alignment) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            return style;
        }

        private static CellStyle coloreado(
                Workbook workbook, IndexedColors background, IndexedColors foreground, boolean bold) {
            CellStyle style = base(workbook, HorizontalAlignment.CENTER);
            style.setFillForegroundColor(background.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setColor(foreground.getIndex());
            font.setBold(bold);
            style.setFont(font);
            return style;
        }
    }
}
