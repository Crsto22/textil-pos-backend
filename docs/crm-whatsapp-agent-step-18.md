# Paso 18: outbox confiable de WhatsApp

## Objetivo

Las salidas del agente no se envían directamente. Primero se insertan en
`crm_whatsapp_agent_outbox` y un worker las entrega respetando el orden de cada
conversación.

## Flujo

1. El orquestador llama `AgentOutboxService` con sesión, job y un ordinal lógico.
2. `JdbcAgentOutboxStore` bloquea la sesión, valida el job y genera una clave
   idempotente independiente del texto redactado.
3. Solo la primera salida abierta de cada conversación puede obtener un lease.
4. `AgentWhatsappOutboxRunner` envía texto, imagen o documento al bridge.
5. El bridge usa `X-Idempotency-Key`, comparte el resultado de solicitudes
   concurrentes y asigna un ID de WhatsApp determinista.
6. En una sola transacción local se registra el mensaje `AI_KIMENTS` y se marca
   el outbox como `SENT`.

## Estados

- `PENDING`: pendiente de primer intento.
- `PROCESSING`: lease exclusivo de un worker.
- `RETRY`: error temporal con backoff exponencial.
- `SENT`: confirmado por el bridge y registrado en el chat.
- `FAILED`: error definitivo o límite de intentos alcanzado.
- `CANCELLED`: salida invalidada porque llegó un mensaje más reciente.

## Garantías

- Clave idempotente única y hash separado del contenido.
- Orden estricto por conversación; un mensaje posterior no adelanta a otro abierto.
- Cinco intentos por defecto, lease de 90 segundos y recuperación tras reinicio.
- El mismo reintento conserva el ID de WhatsApp, incluso si el backend perdió la
  confirmación anterior.
- Los ecos salientes del bridge se suprimen y las salidas se guardan con origen
  `AI_KIMENTS`, por lo que no vuelven a disparar el procesamiento de entrada.

## Configuración

Las propiedades `crm.whatsapp.agent.outbox.*` permiten ajustar intentos, lease,
tamaño de lote y frecuencia del worker sin cambiar el código.
