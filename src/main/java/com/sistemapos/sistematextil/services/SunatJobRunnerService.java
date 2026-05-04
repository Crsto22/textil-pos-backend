package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.SunatBajaLote;
import com.sistemapos.sistematextil.model.SunatJob;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.SunatBajaLoteRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionRepository;
import com.sistemapos.sistematextil.repositories.NotaCreditoRepository;
import com.sistemapos.sistematextil.repositories.SunatJobRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.sunat.SunatBajaEstado;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;
import com.sistemapos.sistematextil.util.sunat.SunatJobEstado;
import com.sistemapos.sistematextil.util.sunat.SunatJobTipoDocumento;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatJobRunnerService {

    private static final Logger log = LoggerFactory.getLogger(SunatJobRunnerService.class);
    private static final EnumSet<SunatEstado> ESTADOS_PENDIENTES_ENVIO = EnumSet.of(
            SunatEstado.PENDIENTE_ENVIO,
            SunatEstado.ENVIANDO,
            SunatEstado.ERROR_TRANSITORIO,
            SunatEstado.PENDIENTE);
    private static final EnumSet<SunatEstado> ESTADOS_PENDIENTES_CDR = EnumSet.of(
            SunatEstado.PENDIENTE_CDR);
    private static final EnumSet<SunatBajaEstado> ESTADOS_BAJA_PENDIENTES = EnumSet.of(
            SunatBajaEstado.PENDIENTE_ENVIO,
            SunatBajaEstado.ENVIANDO,
            SunatBajaEstado.ERROR_TRANSITORIO,
            SunatBajaEstado.PENDIENTE,
            SunatBajaEstado.PENDIENTE_CDR);

    private final SunatJobRepository sunatJobRepository;
    private final VentaRepository ventaRepository;
    private final NotaCreditoRepository notaCreditoRepository;
    private final GuiaRemisionRepository guiaRemisionRepository;
    private final SunatBajaLoteRepository sunatBajaLoteRepository;
    private final VentaService ventaService;
    private final NotaCreditoService notaCreditoService;
    private final GuiaRemisionService guiaRemisionService;
    private final SunatBajaService sunatBajaService;
    private final SunatJobService sunatJobService;
    private final SunatRetryPolicyService retryPolicyService;
    private final TransactionTemplate transactionTemplate;

    @Value("${sunat.job.batch-size:10}")
    private int batchSize;

    @Value("${sunat.job.lock-minutes:10}")
    private int lockMinutes;

    @Scheduled(fixedDelayString = "${sunat.job.fixed-delay-ms:15000}")
    public void processQueue() {
        LocalDateTime now = LocalDateTime.now();
        List<SunatJob> readyJobs;
        try {
            reconcileMissingJobs();
            readyJobs = sunatJobRepository.findReadyJobs(
                    EnumSet.of(SunatJobEstado.PENDIENTE_ENVIO, SunatJobEstado.PENDIENTE_CDR),
                    now,
                    now.minusMinutes(lockMinutes),
                    PageRequest.of(0, batchSize));
        } catch (RuntimeException e) {
            if (isMissingSunatJobTable(e)) {
                log.warn("La tabla sunat_job aun no esta disponible. El worker SUNAT reintentara en el siguiente ciclo.");
                return;
            }
            throw e;
        }

        for (SunatJob job : readyJobs) {
            try {
                transactionTemplate.executeWithoutResult(
                        status -> processIfClaimed(job.getIdSunatJob(), job.getEstado()));
            } catch (RuntimeException e) {
                log.error("Error procesando job SUNAT {}: {}", job.getIdSunatJob(), e.getMessage(), e);
            }
        }
    }

    private void reconcileMissingJobs() {
        Pageable page = PageRequest.of(0, batchSize);

        ventaRepository.findPendingSunatIdsWithoutJob(ESTADOS_PENDIENTES_ENVIO, page)
                .forEach(sunatJobService::enqueueVenta);

        notaCreditoRepository.findPendingSunatIdsWithoutJob(ESTADOS_PENDIENTES_ENVIO, page)
                .forEach(sunatJobService::enqueueNotaCredito);

        guiaRemisionRepository.findPendingSunatIdsWithoutJob(ESTADOS_PENDIENTES_ENVIO, page)
                .forEach(sunatJobService::enqueueGuiaRemision);

        sunatBajaLoteRepository.findPendingSunatIdsWithoutJob(ESTADOS_BAJA_PENDIENTES, page)
                .forEach(sunatJobService::enqueueBajaLote);

        guiaRemisionRepository.findPendingSunatIdsWithoutJob(ESTADOS_PENDIENTES_CDR, page)
                .forEach(this::enqueueMissingGuideTicketJob);
    }

    private void enqueueMissingGuideTicketJob(Integer idGuiaRemision) {
        guiaRemisionRepository
                .findByIdGuiaRemisionAndDeletedAtIsNull(idGuiaRemision)
                .ifPresent(guia -> sunatJobService.enqueueConsultaTicketGuia(
                        guia.getIdGuiaRemision(),
                        guia.getSunatTicket()));
    }

    @Transactional
    public void processIfClaimed(Long idSunatJob, SunatJobEstado estadoPrevio) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = sunatJobRepository.claimJob(
                idSunatJob,
                EnumSet.of(estadoPrevio),
                SunatJobEstado.PROCESANDO,
                now,
                now.minusMinutes(lockMinutes));
        if (claimed == 0) {
            return;
        }

        SunatJob job = sunatJobRepository.findById(idSunatJob)
                .orElseThrow(() -> new RuntimeException("Job SUNAT no encontrado"));

        switch (job.getTipoDocumento()) {
            case VENTA -> processVenta(job, now);
            case NOTA_CREDITO -> processNotaCredito(job, now);
            case GUIA_REMISION -> processGuiaRemision(job, estadoPrevio, now);
            case BAJA_LOTE -> processBajaLote(job, estadoPrevio, now);
            default -> finalizeAsDefinitiveError(job, "TIPO", "Tipo de documento SUNAT no soportado", now);
        }
    }

    private void processVenta(SunatJob job, LocalDateTime now) {
        Venta venta = ventaRepository.findByIdVentaAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(null);
        if (venta == null) {
            finalizeAsDefinitiveError(job, "NOT_FOUND", "La venta asociada ya no existe", now);
            return;
        }

        ventaService.procesarEmisionElectronica(venta.getIdVenta());
        Venta actualizada = ventaRepository.findByIdVentaAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(venta);
        syncJobWithState(job, actualizada.getSunatEstado(), actualizada.getSunatCodigo(), actualizada.getSunatMensaje(),
                actualizada.getSunatTicket(), now);
    }

    private void processNotaCredito(SunatJob job, LocalDateTime now) {
        NotaCredito notaCredito = notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(null);
        if (notaCredito == null) {
            finalizeAsDefinitiveError(job, "NOT_FOUND", "La nota de credito asociada ya no existe", now);
            return;
        }

        notaCreditoService.procesarEmisionEnCola(notaCredito.getIdNotaCredito());
        NotaCredito actualizada = notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(notaCredito);
        syncJobWithState(job, actualizada.getSunatEstado(), actualizada.getSunatCodigo(),
                actualizada.getSunatMensaje(), actualizada.getSunatTicket(), now);
    }

    private void processGuiaRemision(SunatJob job, SunatJobEstado estadoPrevio, LocalDateTime now) {
        GuiaRemision guia = guiaRemisionRepository
                .findByIdGuiaRemisionAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(null);
        if (guia == null) {
            finalizeAsDefinitiveError(job, "NOT_FOUND", "La guia asociada ya no existe", now);
            return;
        }

        if (estadoPrevio == SunatJobEstado.PENDIENTE_CDR) {
            guiaRemisionService.consultarCdrPendiente(job.getDocumentoId());
        } else {
            guiaRemisionService.procesarEmisionElectronica(job.getDocumentoId());
        }

        GuiaRemision actualizada = guiaRemisionRepository
                .findByIdGuiaRemisionAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(guia);
        syncJobWithState(job, actualizada.getSunatEstado(), actualizada.getSunatCodigo(),
                actualizada.getSunatMensaje(), actualizada.getSunatTicket(), now);
    }

    private void processBajaLote(SunatJob job, SunatJobEstado estadoPrevio, LocalDateTime now) {
        SunatBajaLote lote = sunatBajaLoteRepository.findByIdSunatBajaLoteAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(null);
        if (lote == null) {
            finalizeAsDefinitiveError(job, "NOT_FOUND", "El lote de baja asociado ya no existe", now);
            return;
        }

        if (estadoPrevio == SunatJobEstado.PENDIENTE_CDR) {
            sunatBajaService.consultarTicketPendiente(job.getDocumentoId());
        } else {
            sunatBajaService.procesarLoteEnCola(job.getDocumentoId());
        }

        SunatBajaLote actualizado = sunatBajaLoteRepository.findByIdSunatBajaLoteAndDeletedAtIsNull(job.getDocumentoId())
                .orElse(lote);
        syncJobWithBajaState(job, actualizado.getEstado(), actualizado.getCodigo(),
                actualizado.getMensaje(), actualizado.getTicketSunat(), now);
    }

    private void syncJobWithState(
            SunatJob job,
            SunatEstado estado,
            String codigo,
            String mensaje,
            String ticket,
            LocalDateTime now) {
        job.setUltimoCodigo(trim(codigo, 40));
        job.setUltimoError(trim(mensaje, 1000));
        job.setTicketSunat(trim(ticket, 120));
        job.setLockedAt(null);

        if (estado == SunatEstado.NO_APLICA) {
            job.setEstado(SunatJobEstado.FINALIZADO);
            job.setProcessedAt(now);
            job.setNextRetryAt(null);
            sunatJobService.save(job);
            return;
        }

        if (estado == SunatEstado.ACEPTADO || estado == SunatEstado.OBSERVADO || estado == SunatEstado.RECHAZADO) {
            job.setEstado(SunatJobEstado.FINALIZADO);
            job.setProcessedAt(now);
            job.setNextRetryAt(null);
            sunatJobService.save(job);
            return;
        }

        if (estado == SunatEstado.PENDIENTE_CDR) {
            job.setEstado(SunatJobEstado.PENDIENTE_CDR);
            job.setNextRetryAt(retryPolicyService.nextRetryAt(job.getIntentos(), now));
            job.setProcessedAt(null);
            sunatJobService.save(job);
            return;
        }

        if (estado == SunatEstado.ERROR_TRANSITORIO
                || estado == SunatEstado.PENDIENTE_ENVIO
                || estado == SunatEstado.ENVIANDO
                || estado == SunatEstado.PENDIENTE) {
            if (job.getIntentos() >= job.getMaxIntentos()) {
                markDocumentAsDefinitiveError(job, codigo, mensaje, now);
                finalizeAsDefinitiveError(job, codigo, mensaje == null
                        ? "Se agotaron los reintentos de envio a SUNAT"
                        : mensaje, now);
                return;
            }
            job.setEstado(SunatJobEstado.PENDIENTE_ENVIO);
            job.setNextRetryAt(retryPolicyService.nextRetryAt(job.getIntentos(), now));
            job.setProcessedAt(null);
            sunatJobService.save(job);
            return;
        }

        finalizeAsDefinitiveError(job, codigo, mensaje, now);
    }

    private void syncJobWithBajaState(
            SunatJob job,
            SunatBajaEstado estado,
            String codigo,
            String mensaje,
            String ticket,
            LocalDateTime now) {
        job.setUltimoCodigo(trim(codigo, 40));
        job.setUltimoError(trim(mensaje, 1000));
        job.setTicketSunat(trim(ticket, 120));
        job.setLockedAt(null);

        if (estado == null || estado == SunatBajaEstado.ERROR) {
            finalizeAsDefinitiveError(job, codigo, mensaje, now);
            return;
        }
        if (estado == SunatBajaEstado.NO_APLICA) {
            job.setEstado(SunatJobEstado.FINALIZADO);
            job.setProcessedAt(now);
            job.setNextRetryAt(null);
            sunatJobService.save(job);
            return;
        }
        if (estado == SunatBajaEstado.ACEPTADO || estado == SunatBajaEstado.OBSERVADO || estado == SunatBajaEstado.RECHAZADO) {
            job.setEstado(SunatJobEstado.FINALIZADO);
            job.setProcessedAt(now);
            job.setNextRetryAt(null);
            sunatJobService.save(job);
            return;
        }
        if (estado == SunatBajaEstado.PENDIENTE_CDR) {
            job.setEstado(SunatJobEstado.PENDIENTE_CDR);
            job.setNextRetryAt(retryPolicyService.nextRetryAt(job.getIntentos(), now));
            job.setProcessedAt(null);
            sunatJobService.save(job);
            return;
        }
        if (estado == SunatBajaEstado.ERROR_TRANSITORIO
                || estado == SunatBajaEstado.PENDIENTE_ENVIO
                || estado == SunatBajaEstado.ENVIANDO
                || estado == SunatBajaEstado.PENDIENTE) {
            if (job.getIntentos() >= job.getMaxIntentos()) {
                sunatBajaService.marcarErrorDefinitivo(job.getDocumentoId(), codigo, mensaje, now);
                finalizeAsDefinitiveError(job, codigo, mensaje == null
                        ? "Se agotaron los reintentos de envio de baja a SUNAT"
                        : mensaje, now);
                return;
            }
            job.setEstado(SunatJobEstado.PENDIENTE_ENVIO);
            job.setNextRetryAt(retryPolicyService.nextRetryAt(job.getIntentos(), now));
            job.setProcessedAt(null);
            sunatJobService.save(job);
            return;
        }
        finalizeAsDefinitiveError(job, codigo, mensaje, now);
    }

    private void finalizeAsDefinitiveError(
            SunatJob job,
            String codigo,
            String mensaje,
            LocalDateTime now) {
        job.setEstado(SunatJobEstado.ERROR_DEFINITIVO);
        job.setUltimoCodigo(trim(codigo, 40));
        job.setUltimoError(trim(mensaje, 1000));
        job.setLockedAt(null);
        job.setProcessedAt(now);
        job.setNextRetryAt(null);
        sunatJobService.save(job);
    }

    private void markDocumentAsDefinitiveError(
            SunatJob job,
            String codigo,
            String mensaje,
            LocalDateTime now) {
        String normalizedMessage = trim(mensaje == null || mensaje.isBlank()
                ? "Se agotaron los reintentos de envio a SUNAT"
                : mensaje, 500);

        switch (job.getTipoDocumento()) {
            case VENTA -> ventaRepository.findByIdVentaAndDeletedAtIsNull(job.getDocumentoId()).ifPresent(venta -> {
                venta.setSunatEstado(SunatEstado.ERROR_DEFINITIVO);
                venta.setSunatCodigo(trim(codigo, 20));
                venta.setSunatMensaje(normalizedMessage);
                venta.setSunatRespondidoAt(now);
                ventaRepository.save(venta);
            });
            case NOTA_CREDITO -> notaCreditoRepository.findByIdNotaCreditoAndDeletedAtIsNull(job.getDocumentoId())
                    .ifPresent(nota -> {
                        nota.setSunatEstado(SunatEstado.ERROR_DEFINITIVO);
                        nota.setSunatCodigo(trim(codigo, 20));
                        nota.setSunatMensaje(normalizedMessage);
                        nota.setSunatRespondidoAt(now);
                        notaCreditoRepository.save(nota);
                    });
            case GUIA_REMISION -> guiaRemisionRepository
                    .findByIdGuiaRemisionAndDeletedAtIsNull(job.getDocumentoId())
                    .ifPresent(guia -> {
                        guia.setSunatEstado(SunatEstado.ERROR_DEFINITIVO);
                        guia.setSunatCodigo(trim(codigo, 20));
                        guia.setSunatMensaje(normalizedMessage);
                        guia.setSunatRespondidoAt(now);
                        guiaRemisionRepository.save(guia);
                    });
            case BAJA_LOTE -> sunatBajaService.marcarErrorDefinitivo(job.getDocumentoId(), codigo, normalizedMessage, now);
            default -> {
            }
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private boolean isMissingSunatJobTable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase().contains("sunat_job")
                    && message.toLowerCase().contains("doesn't exist")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
