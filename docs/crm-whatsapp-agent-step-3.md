# Paso 3: integración aislada de Spring AI con Gemini

## Alcance

Este paso conecta Spring AI con Gemini únicamente desde la prueba de credenciales existente:

`POST /api/crm/whatsapp/connection/ai-credentials/test`

No está conectado al webhook de WhatsApp, a los jobs del agente, al outbox ni a las sesiones de venta. Por tanto, todavía no lee ni responde mensajes reales.

## Flujo de la prueba

1. Obtiene la API key cifrada y el modelo configurado para la conexión.
2. Construye un `GoogleGenAiChatModel` y un `ChatClient` solo para esa ejecución.
3. Entrega una conversación fija de compra.
4. Gemini llama a la herramienta de solo lectura `consultar_capacidades_agente`.
5. Gemini devuelve un `AgentConversationDecision` estructurado.
6. Se valida el esquema, el rango de confianza y que la herramienta haya sido invocada.
7. La respuesta del endpoint incluye latencia, modelo, cantidad de tool calls y la decisión.

## Garantías de aislamiento

- La herramienta declara que no permite mutaciones ni mensajes reales.
- No existen llamadas desde el webhook al nuevo gateway.
- No se crean sesiones, carritos, pagos, ventas ni mensajes.
- La API key nunca se incorpora a logs o respuestas HTTP.
- El timeout se aplica al cliente HTTP de Gemini.

## Ejemplo de respuesta

```json
{
  "ok": true,
  "provider": "GEMINI",
  "model": "gemini-3.5-flash-lite",
  "latencyMs": 1250,
  "toolCalls": 1,
  "decision": {
    "intent": "CART_CHANGE",
    "customerGoal": "Comprar dos unidades de Emma",
    "productReference": "Emma",
    "colorReference": "camel",
    "sizeReference": "L",
    "quantity": 2,
    "promotionReference": null,
    "nextAction": "Consultar catalogo y stock",
    "confidence": 98,
    "requiresHumanHelp": false
  }
}
```
