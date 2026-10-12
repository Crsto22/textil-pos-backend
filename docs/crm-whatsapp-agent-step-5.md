# Paso 5: agrupación móvil de mensajes

## Flujo

1. El webhook guarda el mensaje entrante y publica un evento después del commit.
2. Se crea o reutiliza la sesión activa de esa conversación.
3. Los jobs y las entregas pendientes anteriores se marcan como obsoletos/cancelados.
4. Se crea un único job nuevo anclado al último mensaje.
5. Su `available_at` queda configurado a `ahora + ventana completa`.
6. El job futuro construirá el lote recorriendo hacia atrás desde su ancla.

Con cuatro mensajes consecutivos se conservan cuatro jobs auditables: tres quedan `OBSOLETE` y solamente el cuarto queda `PENDING`. Por ello habrá una sola ejecución válida.

## Reglas del lote

- Ventana predeterminada: 5 segundos.
- Máximo predeterminado: 15 mensajes.
- Ambos valores se configuran mediante variables de entorno.
- El resultado se entrega en orden cronológico.
- Un espacio superior a la ventana inicia otro lote.
- Una respuesta saliente de la IA o una persona corta el lote.
- Los avisos `CRM_SYSTEM` se ignoran y no cortan el lote.
- Los mensajes eliminados no participan.

## Contenido multimodal

- Texto: cuerpo normalizado.
- Audio transcrito: se usa la transcripción como contenido.
- Audio aún no transcrito: `[AUDIO PENDIENTE DE TRANSCRIPCION]`.
- Imagen: conserva su texto y agrega `[IMAGEN ADJUNTA]`.
- Sticker: agrega `[STICKER ADJUNTO]`.
- Otros archivos: agregan un indicador con su tipo.

## Protección contra carreras

Cada job queda unido a su mensaje ancla. Antes de cualquier entrega deberá ejecutarse:

```java
agentMessageBatchScheduler.isCurrentBeforeDelivery(jobId);
```

La validación exige que el job no sea obsoleto y que su ancla siga siendo el último mensaje de la sesión. Un mensaje nuevo también cancela cualquier fila no enviada del outbox de esa sesión.
