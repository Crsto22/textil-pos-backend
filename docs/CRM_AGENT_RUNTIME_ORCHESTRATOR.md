# Orquestador conversacional del agente CRM

El worker `AgentConversationRunner` consulta jobs listos y entrega cada lease a
`AgentConversationOrchestrator`. El recorrido de un turno es:

1. confirmar que el job sigue siendo el ultimo de la conversacion;
2. reconstruir contexto y lote agrupado desde la base de datos;
3. crear el prompt principal y un contexto de herramientas ligado al job;
4. llamar a Gemini con herramientas reales y salida estructurada;
5. auditar cada herramienta y reutilizar su resultado ante reintentos;
6. guardar texto, resumen, metadatos y huella del contexto en el job;
7. comprobar otra vez que no haya llegado un mensaje nuevo;
8. guardar resumen y salida idempotente en el outbox;
9. completar el job para habilitar su entrega por WhatsApp.

El outbox solo entrega respuestas cuyo job ya esta `COMPLETED`. Un mensaje nuevo
vuelve obsoleto el trabajo anterior y cancela sus salidas no enviadas.

Si el backend reinicia despues de consultar Gemini, el worker reutiliza la respuesta
guardada. No vuelve a consumir el modelo ni repite herramientas. Los errores
transitorios usan los reintentos configurados; un error no recuperable o el ultimo
intento abre un caso de soporte y mueve el chat a espera.

La herramienta `generar_nota_venta` solo se expone a Gemini durante la fase
`AUTOMATIC_SALES`. En las fases anteriores la Nota de Venta permanece bloqueada en
backend aunque el modelo intentara solicitarla.
