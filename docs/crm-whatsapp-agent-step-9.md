# Paso 9 - Carrito persistente

El agente dispone de siete herramientas de carrito:

- `agregar_producto_carrito`
- `agregar_combo_carrito`
- `cambiar_cantidad_carrito`
- `eliminar_producto_carrito`
- `vaciar_carrito`
- `recalcular_carrito`
- `confirmar_carrito`

## Fuente de verdad

Gemini solo puede proporcionar identificadores de variante/promocion y cantidades. Los esquemas de las herramientas
no aceptan precio, descuento, total, sucursal, sesion ni clave de operacion. Estos datos son resueltos por el backend
desde el contexto seguro y MySQL.

Antes de cada respuesta del carrito, el backend vuelve a consultar:

1. Sucursal asociada a la conexion WhatsApp.
2. Variante activa y stock de esa sucursal.
3. Precio regular y precio vigente global o por sucursal.
4. Promociones activas, sus reglas y stock agregado por producto.
5. Mejor combinacion de promociones sin usar dos veces la misma unidad.

## Persistencia, concurrencia e idempotencia

El carrito, sus lineas, promociones aplicadas, snapshots y totales se guardan en las tablas creadas en el paso 2.
Cada mutacion bloquea la sesion y el carrito dentro de una transaccion. `agentOperationId`, inyectado mediante
`ToolContext` y oculto a Gemini, se registra en `crm_whatsapp_agent_tool_call`: repetir la misma llamada devuelve la
respuesta almacenada y no vuelve a sumar unidades.

Al confirmar se recalculan por ultima vez precio, stock y promociones, el carrito queda activo para las etapas de
datos, pago y venta pero bloqueado para edición (`CONFIRMED`), y la maquina de estados avanza de
`ARMANDO_CARRITO` a `ESPERANDO_DATOS_CLIENTE`.
