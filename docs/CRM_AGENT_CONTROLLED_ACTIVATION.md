# Activacion controlada del agente CRM

La activacion se guarda por conexion de WhatsApp y debe avanzar una fase por vez.

1. `LOCAL_TEST`: el agente puede probarse de forma aislada, pero ningun mensaje entrante real crea jobs.
2. `WHATSAPP_TEST`: solo los celulares autorizados pueden iniciar o continuar ejecuciones.
3. `LIMITED`: se admiten conversaciones reales hasta el limite configurado por hora. Las conversaciones ya iniciadas pueden continuar. La generacion automatica de Notas de Venta permanece bloqueada.
4. `AUTOMATIC_SALES`: habilita el flujo completo y permite al runner crear una Nota de Venta cuando pago y entrega estan listos.

Se puede retroceder a cualquier fase. Al retroceder o apagar el agente se cancelan jobs y salidas pendientes de esa conexion para evitar que una respuesta anterior se entregue despues del cambio.

## Monitoreo

`GET /api/crm/whatsapp/connection/agent-config` devuelve la fotografia de las ultimas 24 horas:

- sesiones y jobs completados;
- fallos de jobs, herramientas y outbox;
- respuestas obsoletas que fueron suprimidas;
- salidas enviadas y pendientes;
- comprobantes duplicados;
- ventas automaticas creadas.

La pantalla `/conexiones` presenta estos contadores y permite actualizarlos manualmente. Un fallo de job o de envio marca el estado `ALERT`; fallos de herramientas, duplicados o una cola elevada marcan `WATCH`.

## Regla de seguridad

No debe habilitarse `AUTOMATIC_SALES` hasta completar satisfactoriamente las tres fases anteriores. Las restricciones se validan en backend; modificar el frontend no permite saltarlas.
