# Paso 8 - Herramientas de promociones

El agente dispone de tres herramientas Spring AI de solo lectura:

- `buscar_promociones`: interpreta consultas libres, nombres de productos y referencias comerciales como `combo 42`.
- `obtener_promocion`: devuelve la regla, productos, cantidades, precio y ahorro verificados.
- `validar_promocion_carrito`: compara una promocion o todas las promociones con el carrito activo sin modificarlo.

## Reglas soportadas

Cada combo vigente contiene exactamente dos unidades. La capa distingue:

- `MIXED_PRODUCTS`: un producto más otro producto.
- `TWO_SAME_PRODUCT`: dos unidades del mismo producto.

Por ello, una consulta como `si llevo dos Emma` prioriza la promoción de dos EMMA sobre los combos que solamente
incluyen una EMMA junto con otro modelo. `Quiero el combo 42` resuelve primero el nombre comercial `combo 42`; no
asume que 42 sea necesariamente el identificador interno de la base de datos.

## Seguridad y sucursal

Gemini no proporciona el identificador de sucursal ni accede a SQL. El backend obtiene `agentSessionId` desde el
`ToolContext`, resuelve la conexión WhatsApp y limita stock, productos, variantes y promociones a su sucursal.

Las promociones vencidas, inactivas, eliminadas, con productos no publicados o sin stock suficiente en esa sucursal
no se entregan al modelo. La validación del carrito informa si aplica, qué producto falta o si cambió el stock, pero
no reserva, agrega, elimina ni recalcula persistentemente el carrito.
