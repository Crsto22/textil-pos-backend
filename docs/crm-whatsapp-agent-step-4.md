# Paso 4: contexto conversacional reproducible

`AgentContextFactory` construye una fotografía inmutable de una sesión del agente. Todavía no ejecuta Gemini ni responde mensajes.

## Contenido

- Identidad de sesión, conversación, conexión y sucursal.
- Mensaje ancla, estado, estado de retorno, último evento y resumen anterior.
- Datos conocidos del cliente.
- Hasta 30 mensajes recientes, en orden cronológico.
- Carrito activo, artículos, promociones y totales.
- Datos faltantes correspondientes al estado actual.
- Solicitud de pago, reserva y última evidencia.
- Método y datos de entrega o recojo.
- Hasta 12 resultados recientes de herramientas.
- Huella SHA-256 del contenido para comprobar reproducibilidad.

## Aislamiento de mensajes

Se incluyen:

- Mensajes entrantes de la clienta.
- Mensajes salientes cuyo origen sea `AGENT`.
- Orígenes antiguos `AI` y `AI_KIMENTS` por compatibilidad histórica.

Se excluyen:

- Avisos operativos `CRM_SYSTEM`.
- Mensajes internos con dirección `SYSTEM`.
- Mensajes escritos por asesoras con origen `HUMAN`.
- Mensajes eliminados.
- Mensajes posteriores al mensaje ancla de la ejecución.

El filtro se aplica en la consulta SQL y nuevamente en la política de ensamblado.

## Reproducibilidad

El job futuro deberá llamar:

```java
agentContextFactory.build(sessionId, anchorMessageId);
```

El `anchorMessageId` impide que mensajes recibidos mientras Gemini está trabajando alteren el contexto de esa ejecución. Dos ensamblados con los mismos datos y el mismo ancla producen el mismo objeto y la misma huella.
