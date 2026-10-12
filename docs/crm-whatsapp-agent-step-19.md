# Paso 19: configuración operativa en Conexiones

## Configuración persistente

Cada conexión de WhatsApp conserva en `crm_whatsapp_ai_config`:

- activación del agente (apagado por defecto);
- modelo Gemini;
- ventana móvil de agrupación;
- reserva de pago fija de 240 segundos;
- confianza mínima del comprobante;
- habilitación futura de atención por asesoras;
- reintentos de interpretación y envío;
- límites de mensajes por lote, herramientas y salidas.

Los endpoints administrativos son:

- `GET /api/crm/whatsapp/connection/agent-config`
- `PUT /api/crm/whatsapp/connection/agent-config`

El frontend los expone en `/conexiones` mediante el proxy autenticado de Next.js.

## Aplicación en caliente

Los mensajes nuevos consultan `agent_enabled` antes de crear una sesión o job.
Los workers de jobs y outbox también verifican la activación en sus consultas de
claim. La ventana, confianza y límites se consultan desde MySQL durante cada
operación, por lo que no requieren reiniciar el backend.

Al detener el agente o eliminar la credencial Gemini se invalidan jobs y salidas
pendientes. Esto evita que una respuesta antigua sea enviada al reactivarlo.

## Validaciones

- No se puede activar sin credencial Gemini utilizable ni sucursal vinculada.
- La reserva de pago se mantiene exactamente en cuatro minutos.
- Los rangos se validan tanto en frontend como en backend.
- El modelo solo acepta caracteres seguros y un máximo de 100 caracteres.
