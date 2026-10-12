# Paso 15 - Aprobacion y rechazo humano

## Cola de revision

Cuando la Nota de Venta automatica queda en `VENTA_PENDIENTE_REVISION`, la
conversacion pasa a `ESPERA` con el motivo `PAYMENT_VERIFICATION`. Desde
`/chat`, una asesora autorizada puede revisar en un solo modal:

- El archivo original del comprobante.
- Monto, fecha, destinatario, operacion, proveedor, confianza y texto extraido.
- La Nota de Venta, sus productos, pago, total y modalidad de entrega.
- El PDF de la Nota de Venta mediante una ruta protegida por la conversacion.

## Aceptacion

La aceptacion exige confirmacion humana del abono. El backend bloquea la
evidencia, solicitud y sesion, valida que la Nota de Venta siga emitida y que
el monto coincida, y despues marca:

- Evidencia: `VALIDADO`.
- Solicitud: `COMPLETED`.
- Sesion: `COMPLETADO` e inactiva.

## Rechazo

El rechazo exige un motivo. En una misma transaccion se ejecuta la anulacion
oficial de la Nota de Venta, se devuelve el stock por sus detalles, la reserva
consumida pasa a `REVERSED`, la solicitud queda `CANCELLED` y la sesion queda
`CANCELADO` e inactiva.

## Auditoria e idempotencia

Cada evidencia admite una unica decision final en
`crm_whatsapp_payment_review_audit`. Se registran asesor, accion, motivo,
montos, operacion, estados de venta y estados de sesion antes y despues. Los
bloqueos transaccionales y la restriccion unica impiden aprobar, rechazar o
reponer stock dos veces.
