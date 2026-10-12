# Paso 16 - RAG informativo

## Alcance permitido

La herramienta `consultar_informacion_negocio` recupera exclusivamente
articulos de la conexion WhatsApp en estas categorias:

- `POLITICAS`
- `HORARIOS`
- `UBICACION`
- `ENVIOS`
- `CUIDADOS`
- `FAQ`

No consulta articulos eliminados ni versiones antiguas. Mientras un articulo
se reindexa o tiene un error de reindexacion, su ultima version activa sigue
disponible.

## Recuperacion

La consulta se resuelve con un ranking hibrido:

1. Gemini genera un embedding `RETRIEVAL_QUERY` de 256 dimensiones.
2. Se compara con los embeddings conservados en los chunks activos.
3. Se combinan similitud semantica, coincidencias textuales y categoria.
4. Se devuelven como maximo tres fragmentos de una unica categoria y 3500
   caracteres de contexto.
5. Si el proveedor de embeddings falla, se usa recuperacion textual sin
   detener la conversacion.

El modelo recibe fuentes verificadas y la instruccion de redactar una respuesta
breve y natural, sin inventar datos.

## Separacion de datos comerciales

El backend bloquea el RAG para consultas sobre precios, stock, productos,
modelos, tallas, colores, promociones, carrito, pedidos, pagos, comprobantes y
ventas. Esos datos deben obtenerse exclusivamente mediante las herramientas
transaccionales de catalogo, promociones, carrito, pagos y ventas.

Adicionalmente, cualquier importe monetario presente por error en un articulo
informativo se elimina antes de entregar el fragmento al modelo.

## Seguridad de alcance

La herramienta recibe `agentSessionId` mediante `ToolContext`; no acepta un ID
de conexion proporcionado por Gemini. El backend deriva la conexion desde la
sesion activa, por lo que una conversacion no puede consultar conocimiento de
otra conexion.
