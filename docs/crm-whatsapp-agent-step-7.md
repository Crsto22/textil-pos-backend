# Paso 7 - Herramientas de catalogo de solo lectura

El agente dispone de cinco herramientas Spring AI:

- `buscar_productos`: busqueda tolerante a lenguaje libre y errores leves.
- `obtener_producto`: detalle verificado, descripcion, preventa, guia, colores, tallas, precio y stock.
- `consultar_variantes`: variantes por producto con filtros opcionales de color y talla.
- `consultar_stock`: existencia y suficiencia para una cantidad concreta.
- `obtener_imagen_producto`: imagen global e imagenes disponibles por color.

## Limite de seguridad

Gemini no recibe acceso SQL ni puede indicar una sucursal. El unico dato de alcance inyectado por el backend es
`agentSessionId` mediante `ToolContext`; este dato no forma parte del esquema visible de las herramientas. En cada
llamada, el backend resuelve la cadena `sesion -> conexion WhatsApp -> sucursal` y todas las consultas quedan
restringidas a esa sucursal.

Los resultados son DTOs inmutables. Nunca se entregan entidades JPA al modelo y estas herramientas no contienen
operaciones de escritura, reserva o modificacion de stock.

## Flujo de uso previsto

1. Gemini interpreta la expresion libre y llama `buscar_productos`.
2. Usa solamente los identificadores devueltos para consultar detalle o variantes.
3. Consulta una variante concreta antes de afirmar disponibilidad o cantidad.
4. Solicita la imagen global o la del color cuando la conversacion lo requiere.

El procesador conversacional que ejecutara estos callbacks se conectara en el paso de orquestacion. Este paso deja
cerrado y probado el limite de lectura del catalogo sin activar respuestas reales de WhatsApp.
