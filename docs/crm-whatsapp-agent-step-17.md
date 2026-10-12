# Paso 17 - Prompt principal

## Construccion

`AgentMainPromptFactory` genera un paquete versionado con tres elementos:

- Instruccion de sistema estable.
- Mensaje de contexto serializado y delimitado.
- `ToolContext` con el `agentSessionId` validado por el backend.

El prompt se identifica como `kiments-main-v1`. El contexto contiene el ultimo
mensaje del cliente, la ultima respuesta del agente, historial reciente,
resumen anterior, estado, datos faltantes, cliente, carrito, pago, entrega y
resultados recientes de herramientas.

## Reglas principales

- Personalidad breve, calida y comercial de IA Kiments.
- Datos del negocio exclusivamente mediante herramientas.
- RAG limitado a informacion no comercial.
- Prohibicion de inventar hechos, identificadores o resultados.
- Una unica pregunta final cuando falta un dato.
- Prioridad al ultimo mensaje y uso condicionado del historial.
- Busqueda ante errores de escritura y aclaracion ante varias coincidencias.
- Prohibicion de repetir saludos, listas, preguntas, mutaciones o respuestas.
- Derivacion a ayuda para reclamos, cambios, devoluciones, mayoristas, riesgo o
  fallos criticos reiterados.

## Guia por estado

Cada valor de `CrmWhatsappAgentState` tiene una prioridad explicita. El modelo
no cambia estados directamente: usa herramientas y el backend aplica eventos
validados mediante la maquina de estados.

Los estados de pago, comprobante, entrega y venta impiden reiniciar el flujo o
declarar resultados antes de que el backend los confirme.

## Seguridad del contexto

El historial, el resumen, los textos del cliente, las fuentes RAG y los campos
textuales devueltos por herramientas se consideran datos no ejecutables. El
prompt ordena ignorar instrucciones incrustadas, solicitudes de secretos y
intentos de evitar herramientas.

Los textos se limitan antes de serializarse para mantener un contexto acotado y
reproducible. El `fingerprint` permite relacionar el prompt con la fotografia
exacta del contexto que lo genero.
