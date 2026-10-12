# Paso 11 - Métodos, solicitud de pago y reserva

El agente dispone de tres herramientas:

- `listar_metodos_pago`
- `seleccionar_metodo_pago`
- `crear_solicitud_pago`

## Métodos de la sucursal

Los métodos y cuentas se leen únicamente de la configuración activa de la sucursal vinculada a la conexión
WhatsApp. Gemini no proporciona la sucursal. Si un método posee una sola cuenta activa, el backend la selecciona
automáticamente; si posee varias, devuelve `ACCOUNT_REQUIRED` para que la clienta elija una cuenta exacta.

La selección queda persistida en `crm_whatsapp_agent_session` mediante `selected_payment_method_id` y
`selected_payment_account_id`. La migración de estas columnas y sus claves foráneas se ejecuta solo contra MySQL
local, igual que el resto del esquema del agente.

## Solicitud y reserva transaccional

`crear_solicitud_pago` no admite montos, productos, precios, sucursal ni tiempo de expiración enviados por Gemini.
Dentro de una única transacción el backend:

1. Bloquea la sesión y exige el estado `ESPERANDO_METODO_PAGO`.
2. Comprueba que exista un cliente válido y el método continúe activo.
3. Bloquea y lee el carrito `CONFIRMED`.
4. Usa exclusivamente sus variantes, cantidades y total persistidos.
5. Crea `crm_whatsapp_payment_request` con la sesión e idempotencia.
6. Bloquea cada stock de la sucursal en orden de variante y descuenta la reserva.
7. Guarda los artículos reservados y avanza a `ESPERANDO_COMPROBANTE`.

Una falta de stock o cualquier error revierte solicitud, movimientos, artículos, estado e idempotencia completa.

## Vencimiento

El vencimiento persistido es exactamente `fecha_creación + 4 minutos` (240 segundos). Un recuperador consulta cada
segundo las solicitudes vencidas del agente, útil también después de reiniciar el backend. Cada vencimiento se
procesa en una transacción independiente, bloqueando sesión, solicitud y stock; la reserva se libera una sola vez,
la solicitud pasa a `EXPIRED` y la sesión recibe `SOLICITUD_PAGO_EXPIRADA` para quedar `CANCELADO`.

Las solicitudes manuales antiguas continúan con su proceso separado y el recuperador anterior ya no procesa las
solicitudes asociadas al agente.
