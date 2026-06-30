package com.sistemapos.sistematextil.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.sistemapos.sistematextil.model.EcommercePedido;

@Service
public class EcommercePedidoSseService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter conectar() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        enviar(emitter, "ready", Map.of("at", LocalDateTime.now().toString()));
        return emitter;
    }

    public void pedidoCambiado(EcommercePedido pedido) {
        if (pedido == null) {
            return;
        }
        Map<String, String> payload = Map.of(
                "codigo", pedido.getCodigo(),
                "estado", pedido.getEstado(),
                "at", LocalDateTime.now().toString());
        for (SseEmitter emitter : emitters) {
            enviar(emitter, "pedidos.changed", payload);
        }
    }

    private void enviar(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }
}
