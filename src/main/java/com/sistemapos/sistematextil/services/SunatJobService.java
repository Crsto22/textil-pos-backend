package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.SunatJob;
import com.sistemapos.sistematextil.repositories.SunatJobRepository;
import com.sistemapos.sistematextil.util.sunat.SunatJobEstado;
import com.sistemapos.sistematextil.util.sunat.SunatJobTipoDocumento;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatJobService {

    private final SunatJobRepository sunatJobRepository;

    @Value("${sunat.job.max-retries:10}")
    private int maxRetries;

    @Transactional
    public void enqueueVenta(Integer idVenta) {
        upsert(SunatJobTipoDocumento.VENTA, idVenta, SunatJobEstado.PENDIENTE_ENVIO, null);
    }

    @Transactional
    public void enqueueNotaCredito(Integer idNotaCredito) {
        upsert(SunatJobTipoDocumento.NOTA_CREDITO, idNotaCredito, SunatJobEstado.PENDIENTE_ENVIO, null);
    }

    @Transactional
    public void enqueueGuiaRemision(Integer idGuiaRemision) {
        upsert(SunatJobTipoDocumento.GUIA_REMISION, idGuiaRemision, SunatJobEstado.PENDIENTE_ENVIO, null);
    }

    @Transactional
    public void enqueueBajaLote(Integer idSunatBajaLote) {
        upsert(SunatJobTipoDocumento.BAJA_LOTE, idSunatBajaLote, SunatJobEstado.PENDIENTE_ENVIO, null);
    }

    @Transactional
    public void enqueueConsultaTicketGuia(Integer idGuiaRemision, String ticket) {
        upsert(SunatJobTipoDocumento.GUIA_REMISION, idGuiaRemision, SunatJobEstado.PENDIENTE_CDR, ticket);
    }

    @Transactional
    public SunatJob save(SunatJob sunatJob) {
        return sunatJobRepository.save(sunatJob);
    }

    private void upsert(
            SunatJobTipoDocumento tipoDocumento,
            Integer documentoId,
            SunatJobEstado estado,
            String ticketSunat) {
        LocalDateTime now = LocalDateTime.now();
        SunatJob job = sunatJobRepository.findByTipoDocumentoAndDocumentoId(tipoDocumento, documentoId)
                .orElseGet(SunatJob::new);

        job.setTipoDocumento(tipoDocumento);
        job.setDocumentoId(documentoId);
        job.setEstado(estado);
        job.setIntentos(0);
        job.setMaxIntentos(maxRetries);
        job.setUltimoError(null);
        job.setUltimoCodigo(null);
        job.setTicketSunat(ticketSunat);
        job.setLockedAt(null);
        job.setLastAttemptAt(null);
        job.setProcessedAt(null);
        job.setNextRetryAt(now);
        sunatJobRepository.save(job);
    }
}
