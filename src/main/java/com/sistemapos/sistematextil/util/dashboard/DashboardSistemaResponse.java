package com.sistemapos.sistematextil.util.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardSistemaResponse(
        String dashboard,
        LocalDateTime generadoEn,
        StorageResumen storage,
        DatabaseResumen database,
        RuntimeResumen runtime,
        DiskResumen disk,
        SunatResumen sunat,
        UsuariosResumen usuarios,
        List<AlertaItem> alertas) {

    public record StorageResumen(
            String basePath,
            boolean existe,
            long totalBytes,
            String totalLegible,
            long totalArchivos,
            List<StorageCarpetaItem> carpetas) {
    }

    public record StorageCarpetaItem(
            String carpeta,
            boolean existe,
            long bytes,
            String bytesLegible,
            long archivos) {
    }

    public record DatabaseResumen(
            BigDecimal sizeMb,
            long tablesCount,
            List<DatabaseTableItem> tablasMasPesadas) {
    }

    public record DatabaseTableItem(
            String tableName,
            BigDecimal sizeMb,
            long rows) {
    }

    public record RuntimeResumen(
            String applicationName,
            String javaVersion,
            long uptimeMs,
            String uptimeLegible,
            int processors,
            long memoryUsedBytes,
            String memoryUsedLegible,
            long memoryFreeBytes,
            String memoryFreeLegible,
            long memoryMaxBytes,
            String memoryMaxLegible,
            BigDecimal memoryUsedPercent) {
    }

    public record DiskResumen(
            String path,
            long totalBytes,
            String totalLegible,
            long usedBytes,
            String usedLegible,
            long freeBytes,
            String freeLegible,
            BigDecimal freePercent) {
    }

    public record SunatResumen(
            long totalJobs,
            long jobsNoFinalizados,
            List<SunatEstadoItem> jobsPorEstado,
            SunatUltimoJob ultimoJob,
            SunatServicioEstado servicio) {
    }

    public record SunatEstadoItem(
            String estado,
            long total) {
    }

    public record SunatUltimoJob(
            Integer idSunatJob,
            String estado,
            String tipoDocumento,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion) {
    }

    public record SunatServicioEstado(
            String estado,
            boolean disponible,
            String ambiente,
            String endpoint,
            Integer httpStatus,
            Long latenciaMs,
            String mensaje,
            LocalDateTime verificadoEn) {
    }

    public record UsuariosResumen(
            long activos,
            long eliminados,
            List<UsuarioRolItem> activosPorRol) {
    }

    public record UsuarioRolItem(
            String rol,
            long total) {
    }

    public record AlertaItem(
            String componente,
            String estado,
            String mensaje) {
    }
}
