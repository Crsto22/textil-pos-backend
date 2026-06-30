# API Publico Ecommerce

Este documento describe el funcionamiento de los endpoints publicos creados para el catalogo ecommerce. Estos APIs no requieren token JWT y solo exponen productos publicados para ecommerce.

## Reglas Generales

- El producto debe tener `publicarEcommerce = true`.
- Debe existir una sola sucursal ecommerce activa:
  - `publicarEcommerce = true`
  - `estado = ACTIVO`
  - `tipo = VENTA`
  - `deletedAt = null`
- El stock se calcula por variante real:
  - producto
  - color
  - talla
  - sucursal ecommerce
- El ecommerce lista por grupo `producto-color`, no por talla individual.
- Cada producto publicado devuelve `slug` para usar URLs web como `/productos/conjunto-michell`.
- La compra futura debe usar `idProductoVariante`, porque ahi esta la combinacion real de color y talla.
- Los productos agotados pueden mostrarse como catalogo.
- Si se usa `soloDisponibles=true`, se ocultan los grupos sin stock.

## Regla De Precios Y Ofertas

El precio publico usa la misma logica interna del backend mediante `PrecioOfertaService`.

Prioridad:

1. Oferta por sucursal ecommerce.
2. Oferta global de la variante.
3. Precio regular de la variante.

Cada variante devuelve:

- `precioRegular`
- `precioMayor`
- `precioOfertaAplicada`
- `precioVigente`
- `tipoOfertaAplicada`
- `sucursalOfertaId`
- `ofertaInicio`
- `ofertaFin`

Valores posibles de `tipoOfertaAplicada`:

- `SUCURSAL`
- `GLOBAL`
- `NINGUNA`

## Regla De Imagenes

Para la imagen principal del item:

1. Primero se usa la imagen del color desde `producto_color_imagen`.
2. Si ese color no tiene imagen, se usa la imagen global del producto.
3. Si no existe ninguna imagen, se devuelve `null`.

La imagen global no se duplica dentro de la lista de imagenes por color.

El campo `origen` puede ser:

- `COLOR`
- `GLOBAL`

## Estados De Stock

El estado se calcula por las tallas/variantes del color:

- `DISPONIBLE`: todas las tallas activas tienen stock mayor a 0.
- `PARCIAL`: algunas tallas tienen stock y otras no.
- `AGOTADO`: ninguna talla tiene stock.

## GET /api/public/ecommerce/inicio

Pagina de inicio del ecommerce. Devuelve portadas, imagenes destacadas de productos y dos secciones de 4 productos cada una:

- `portadas` - portadas activas del ecommerce. Cada item trae imagen desktop y mobile.
- `imagenesProductos` - productos publicados con `imagenGlobalUrl` o `imagenGlobalThumbUrl` y `slug`. Sirve para enlazar a `/productos/{slug}`.
- `aleatorios` — 4 productos aleatorios con stock disponible.
- `masVendidos` — 4 productos mas vendidos (por cantidad) que tengan stock.

Cada item tiene la misma estructura que el listado de productos (`producto`, `color`, `imagenPrincipal`, `precioMinimo`, `precioMaximo`, `estadoStock`, `stockTotalColor`, `variantes`).

### Ejemplo Request

```http
GET /api/public/ecommerce/inicio
```

### Ejemplo Response

```json
{
  "tiendaConfigurada": true,
  "portadas": [
    {
      "idEcommercePortada": 1,
      "desktopUrl": "/storage/ecommerce/portadas/desktop/portada.webp",
      "desktopThumbUrl": "/storage/ecommerce/portadas/desktop/portada-thumb.webp",
      "mobileUrl": "/storage/ecommerce/portadas/mobile/portada.webp",
      "mobileThumbUrl": "/storage/ecommerce/portadas/mobile/portada-thumb.webp",
      "orden": 1,
      "estado": "ACTIVO"
    }
  ],
  "imagenesProductos": [
    {
      "idProducto": 12,
      "nombre": "Conjunto Michell",
      "slug": "conjunto-michell",
      "imagenUrl": "/storage/productos/producto-12/global/conjunto.webp",
      "imagenThumbUrl": "/storage/productos/producto-12/global/conjunto-thumb.webp"
    }
  ],
  "aleatorios": [
    {
      "producto": {
        "idProducto": 12,
        "nombre": "Conjunto Michell",
        "slug": "conjunto-michell",
        "descripcion": "Conjunto dos piezas",
        "estado": "ACTIVO",
        "fechaCreacion": "2026-05-10T10:00:00",
        "categoria": { "idCategoria": 2, "nombre": "Conjuntos" },
        "imagenGlobalUrl": "/storage/productos/global/conjunto-michell.jpg",
        "imagenGlobalThumbUrl": "/storage/productos/global/thumb_conjunto-michell.jpg"
      },
      "color": { "idColor": 3, "nombre": "Negro", "hex": "#000000" },
      "imagenPrincipal": {
        "idColorImagen": 45,
        "url": "/storage/productos/12/negro/conjunto-michell-negro.jpg",
        "urlThumb": "/storage/productos/12/negro/thumb_conjunto-michell-negro.jpg",
        "orden": 1, "esPrincipal": true, "estado": "ACTIVO", "origen": "COLOR"
      },
      "precioMinimo": 89.0,
      "precioMaximo": 129.0,
      "estadoStock": "DISPONIBLE",
      "stockTotalColor": 12,
      "variantes": [
        {
          "idProductoVariante": 201, "sku": "CON-NEG-S", "codigoBarras": "7750000000201",
          "talla": { "idTalla": 1, "nombre": "S" },
          "precioRegular": 129.0, "precioMayor": 110.0, "precioOfertaAplicada": 89.0,
          "precioVigente": 89.0, "tipoOfertaAplicada": "SUCURSAL", "sucursalOfertaId": 15,
          "ofertaInicio": "2026-06-01T00:00:00", "ofertaFin": "2026-06-30T23:59:59",
          "stock": 5, "disponible": true, "estado": "ACTIVO"
        }
      ]
    }
  ],
  "masVendidos": [
    {
      "producto": {
        "idProducto": 8, "nombre": "Blazer Ejecutivo", "slug": "blazer-ejecutivo",
        "descripcion": "Blazer para dama con corte moderno", "estado": "ACTIVO",
        "fechaCreacion": "2026-04-15T10:00:00",
        "categoria": { "idCategoria": 3, "nombre": "Blazers" },
        "imagenGlobalUrl": "/storage/productos/global/blazer-ejecutivo.jpg",
        "imagenGlobalThumbUrl": "/storage/productos/global/thumb_blazer-ejecutivo.jpg"
      },
      "color": { "idColor": 1, "nombre": "Azul", "hex": "#0000FF" },
      "imagenPrincipal": {
        "idColorImagen": 30,
        "url": "/storage/productos/8/azul/blazer-ejecutivo-azul.jpg",
        "urlThumb": "/storage/productos/8/azul/thumb_blazer-ejecutivo-azul.jpg",
        "orden": 1, "esPrincipal": true, "estado": "ACTIVO", "origen": "COLOR"
      },
      "precioMinimo": 99.0, "precioMaximo": 99.0,
      "estadoStock": "PARCIAL", "stockTotalColor": 3,
      "variantes": [
        {
          "idProductoVariante": 101, "sku": "BLA-AZU-S", "codigoBarras": "7750000000101",
          "talla": { "idTalla": 1, "nombre": "S" },
          "precioRegular": 99.0, "precioMayor": 85.0, "precioOfertaAplicada": null,
          "precioVigente": 99.0, "tipoOfertaAplicada": "NINGUNA", "sucursalOfertaId": null,
          "ofertaInicio": null, "ofertaFin": null,
          "stock": 3, "disponible": true, "estado": "ACTIVO"
        }
      ]
    }
  ]
}
```

### Sin Sucursal Ecommerce

```http
HTTP/1.1 200 OK
```

```json
{
  "tiendaConfigurada": false,
  "portadas": [],
  "imagenesProductos": [],
  "aleatorios": [],
  "masVendidos": []
}
```


## GET /api/public/ecommerce/productos

Lista productos publicados agrupados por `producto-color`.

### Query Params

| Parametro | Tipo | Default | Descripcion |
| --- | --- | --- | --- |
| `page` | number | `0` | Pagina, base 0. |
| `size` | number | `10` | Cantidad por pagina. Maximo 20. |
| `q` | string | null | Busca por producto, color, talla, SKU o codigo de barras. Si se envian varias palabras, todas deben existir en la combinacion producto-color-variante. |
| `idCategoria` | number | null | Filtra por categoria. |
| `idColor` | number | null | Filtra por color. |
| `tallas` | string | null | Filtra por tallas separadas por coma, por ejemplo `XS,S,M,L`. |
| `precioMax` | number | null | Filtra variantes cuyo precio vigente sea menor o igual al monto enviado. |
| `soloDisponibles` | boolean | `false` | Si es `true`, oculta grupos sin stock. |

### Ejemplo Request

```http
GET /api/public/ecommerce/productos?page=0&size=10&tallas=XS,S,M&precioMax=150&soloDisponibles=false
```

### Ejemplo Response

```json
{
  "tiendaConfigurada": true,
  "message": null,
  "content": [
    {
      "producto": {
        "idProducto": 45,
        "nombre": "Blazer Ejecutivo",
        "slug": "blazer-ejecutivo",
        "descripcion": "Blazer para dama con corte moderno",
        "estado": "ACTIVO",
        "fechaCreacion": "2026-06-10T15:25:40",
        "categoria": {
          "idCategoria": 3,
          "nombre": "Blazers"
        },
        "imagenGlobalUrl": "/storage/productos/global/blazer-ejecutivo.jpg",
        "imagenGlobalThumbUrl": "/storage/productos/global/thumb_blazer-ejecutivo.jpg"
      },
      "color": {
        "idColor": 8,
        "nombre": "Vino",
        "hex": "#7A1E35"
      },
      "imagenPrincipal": {
        "idColorImagen": 120,
        "url": "/storage/productos/45/vino/blazer-vino.jpg",
        "urlThumb": "/storage/productos/45/vino/thumb_blazer-vino.jpg",
        "orden": 1,
        "esPrincipal": true,
        "estado": "ACTIVO",
        "origen": "COLOR"
      },
      "precioMinimo": 79.0,
      "precioMaximo": 89.0,
      "estadoStock": "PARCIAL",
      "stockTotalColor": 4,
      "variantes": [
        {
          "idProductoVariante": 301,
          "sku": "BLA-VIN-S",
          "codigoBarras": "7750000000301",
          "talla": {
            "idTalla": 1,
            "nombre": "S"
          },
          "precioRegular": 99.0,
          "precioMayor": 85.0,
          "precioOfertaAplicada": 79.0,
          "precioVigente": 79.0,
          "tipoOfertaAplicada": "SUCURSAL",
          "sucursalOfertaId": 15,
          "ofertaInicio": "2026-06-01T00:00:00",
          "ofertaFin": "2026-06-30T23:59:59",
          "stock": 2,
          "disponible": true,
          "estado": "ACTIVO"
        },
        {
          "idProductoVariante": 302,
          "sku": "BLA-VIN-M",
          "codigoBarras": "7750000000302",
          "talla": {
            "idTalla": 2,
            "nombre": "M"
          },
          "precioRegular": 99.0,
          "precioMayor": 85.0,
          "precioOfertaAplicada": null,
          "precioVigente": 99.0,
          "tipoOfertaAplicada": "NINGUNA",
          "sucursalOfertaId": null,
          "ofertaInicio": null,
          "ofertaFin": null,
          "stock": 0,
          "disponible": false,
          "estado": "ACTIVO"
        }
      ]
    }
  ],
  "page": 0,
  "size": 10,
  "totalPages": 1,
  "totalElements": 1,
  "numberOfElements": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

### Response Sin Sucursal Ecommerce

Si no existe sucursal ecommerce activa, el listado no falla. Devuelve catalogo vacio:

```json
{
  "tiendaConfigurada": false,
  "message": "Tienda ecommerce no configurada",
  "content": [],
  "page": 0,
  "size": 10,
  "totalPages": 0,
  "totalElements": 0,
  "numberOfElements": 0,
  "first": true,
  "last": true,
  "empty": true
}
```

## GET /api/public/ecommerce/productos/{slug}

Muestra el detalle completo de un producto usando su slug publico. Devuelve todos los colores, todas las tallas, todas las imagenes, precios y ofertas del producto, sin filtrar por stock. Tambien devuelve hasta 5 productos/color recomendados de otros productos con stock para mostrar debajo del detalle.

### Ejemplo Request

```http
GET /api/public/ecommerce/productos/michell
```

### Ejemplo Response

```json
{
  "tiendaConfigurada": true,
  "producto": {
    "idProducto": 45,
    "nombre": "Michell",
    "slug": "michell",
    "descripcion": "Vestido Michell corte moderno",
    "estado": "ACTIVO",
    "fechaCreacion": "2026-06-10T15:25:40",
    "categoria": { "idCategoria": 3, "nombre": "Vestidos" },
    "imagenGlobalUrl": "/storage/productos/global/michell.jpg",
    "imagenGlobalThumbUrl": "/storage/productos/global/thumb_michell.jpg"
  },
  "colores": [
    {
      "color": { "idColor": 8, "nombre": "Vino", "hex": "#7A1E35" },
      "imagenPrincipal": {
        "idColorImagen": 120,
        "url": "/storage/productos/45/vino/michell-vino.jpg",
        "urlThumb": "/storage/productos/45/vino/thumb_michell-vino.jpg",
        "orden": 1,
        "esPrincipal": true,
        "estado": "ACTIVO",
        "origen": "COLOR"
      },
      "imagenes": [
        {
          "idColorImagen": 120,
          "url": "/storage/productos/45/vino/michell-vino.jpg",
          "urlThumb": "/storage/productos/45/vino/thumb_michell-vino.jpg",
          "orden": 1, "esPrincipal": true, "estado": "ACTIVO", "origen": "COLOR"
        },
        {
          "idColorImagen": 121,
          "url": "/storage/productos/45/vino/michell-vino-lateral.jpg",
          "urlThumb": "/storage/productos/45/vino/thumb_michell-vino-lateral.jpg",
          "orden": 2, "esPrincipal": false, "estado": "ACTIVO", "origen": "COLOR"
        }
      ],
      "precioMinimo": 79.0,
      "precioMaximo": 99.0,
      "estadoStock": "PARCIAL",
      "stockTotalColor": 4,
      "variantes": [
        {
          "idProductoVariante": 301,
          "sku": "MIC-VIN-S",
          "codigoBarras": "7750000000301",
          "talla": { "idTalla": 1, "nombre": "S" },
          "precioRegular": 99.0,
          "precioMayor": 85.0,
          "precioOfertaAplicada": 79.0,
          "precioVigente": 79.0,
          "tipoOfertaAplicada": "SUCURSAL",
          "sucursalOfertaId": 15,
          "ofertaInicio": "2026-06-01T00:00:00",
          "ofertaFin": "2026-06-30T23:59:59",
          "stock": 2,
          "disponible": true,
          "estado": "ACTIVO"
        },
        {
          "idProductoVariante": 302,
          "sku": "MIC-VIN-M",
          "codigoBarras": "7750000000302",
          "talla": { "idTalla": 2, "nombre": "M" },
          "precioRegular": 99.0,
          "precioMayor": 85.0,
          "precioOfertaAplicada": null,
          "precioVigente": 99.0,
          "tipoOfertaAplicada": "NINGUNA",
          "sucursalOfertaId": null,
          "ofertaInicio": null,
          "ofertaFin": null,
          "stock": 2,
          "disponible": true,
          "estado": "ACTIVO"
        }
      ]
    },
    {
      "color": { "idColor": 9, "nombre": "Blanco", "hex": "#FFFFFF" },
      "imagenPrincipal": {
        "idColorImagen": null,
        "url": "/storage/productos/global/michell.jpg",
        "urlThumb": "/storage/productos/global/thumb_michell.jpg",
        "orden": null, "esPrincipal": true, "estado": "ACTIVO", "origen": "GLOBAL"
      },
      "imagenes": [],
      "precioMinimo": 99.0,
      "precioMaximo": 99.0,
      "estadoStock": "AGOTADO",
      "stockTotalColor": 0,
      "variantes": [
        {
          "idProductoVariante": 303,
          "sku": "MIC-BLA-S",
          "codigoBarras": "7750000000303",
          "talla": { "idTalla": 1, "nombre": "S" },
          "precioRegular": 99.0,
          "precioMayor": 85.0,
          "precioOfertaAplicada": null,
          "precioVigente": 99.0,
          "tipoOfertaAplicada": "NINGUNA",
          "sucursalOfertaId": null,
          "ofertaInicio": null,
          "ofertaFin": null,
          "stock": 0,
          "disponible": false,
          "estado": "ACTIVO"
        }
      ]
    }
  ],
  "recomendados": [
    {
      "producto": {
        "idProducto": 52,
        "nombre": "Ashley",
        "slug": "ashley",
        "descripcion": "Vestido Ashley",
        "estado": "ACTIVO",
        "fechaCreacion": "2026-06-12T10:15:00",
        "categoria": { "idCategoria": 3, "nombre": "Vestidos" },
        "imagenGlobalUrl": null,
        "imagenGlobalThumbUrl": null,
        "guiaTallasUrl": null,
        "guiaTallasThumbUrl": null
      },
      "color": { "idColor": 11, "nombre": "Negro", "hex": "#000000" },
      "imagenPrincipal": {
        "idColorImagen": 150,
        "url": "/storage/productos/52/negro/ashley-negro.jpg",
        "urlThumb": "/storage/productos/52/negro/thumb_ashley-negro.jpg",
        "orden": 1,
        "esPrincipal": true,
        "estado": "ACTIVO",
        "origen": "COLOR"
      },
      "precioMinimo": 89.0,
      "precioMaximo": 89.0,
      "estadoStock": "DISPONIBLE",
      "stockTotalColor": 6,
      "variantes": []
    }
  ]
}
```

### Error Sin Sucursal Ecommerce

```http
HTTP/1.1 409 Conflict
```

```json
{ "message": "Tienda ecommerce no configurada", "code": "TIENDA_NO_CONFIGURADA" }
```

### Error Producto No Encontrado

Aplica cuando el slug no existe, el producto no esta publicado en ecommerce, o el producto esta eliminado/inactivo.

```http
HTTP/1.1 404 Not Found
```

```json
{ "message": "Producto no encontrado", "code": "Producto no encontrado" }
```


## Uso Recomendado En Frontend Ecommerce

1. Cargar pagina de inicio con `GET /api/public/ecommerce/inicio`.
2. Mostrar `portadas` en el hero e `imagenesProductos` debajo del hero enlazando a `/productos/{slug}`.
3. Cada item se renderiza igual que en el listado de productos.
4. Cargar listado completo con `GET /api/public/ecommerce/productos?page=0&size=10`.
5. Mostrar una tarjeta por cada item de `content`.
6. Usar `imagenPrincipal.urlThumb || imagenPrincipal.url`.
7. Mostrar precio:
   - Si `precioMinimo == precioMaximo`, mostrar un solo precio.
   - Si son diferentes, mostrar rango: `S/ precioMinimo - S/ precioMaximo`.
8. Si `estadoStock = AGOTADO`, mostrar el producto como catalogo sin boton de compra.
9. Al entrar al detalle, usar `GET /api/public/ecommerce/productos/{slug}` con el slug del producto.
10. Para seleccionar talla, usar el item de `variantes` dentro del color elegido.
11. Para comprar o reservar, enviar `idProductoVariante` al flujo futuro de pedido.


## Consultas SQL De Validacion

Estas consultas ayudan a validar los datos desde MySQL o MCP MySQL cuando este disponible.

### Sucursal ecommerce activa

```sql
SELECT id_sucursal, nombre, tipo, estado, publicar_ecommerce, deleted_at
FROM sucursal
WHERE publicar_ecommerce = 1
  AND estado = 'ACTIVO'
  AND tipo = 'VENTA'
  AND deleted_at IS NULL;
```

### Productos publicados

```sql
SELECT producto_id, nombre, publicar_ecommerce, estado, activo, deleted_at
FROM producto
WHERE publicar_ecommerce = 1
  AND estado = 'ACTIVO'
  AND activo = 1
  AND deleted_at IS NULL;
```

### Variantes con stock en la sucursal ecommerce

```sql
SELECT
  p.producto_id,
  p.nombre AS producto,
  c.nombre AS color,
  t.nombre AS talla,
  v.id_producto_variante,
  v.precio,
  v.precio_oferta,
  ss.cantidad
FROM producto_variante v
JOIN producto p ON p.producto_id = v.producto_id
JOIN colores c ON c.color_id = v.color_id
JOIN tallas t ON t.talla_id = v.talla_id
LEFT JOIN sucursal_stock ss ON ss.id_producto_variante = v.id_producto_variante
WHERE p.publicar_ecommerce = 1
  AND ss.id_sucursal = :idSucursalEcommerce
ORDER BY p.nombre, c.nombre, t.nombre;
```

### Ofertas por sucursal ecommerce

```sql
SELECT
  vos.id_producto_variante_oferta_sucursal,
  vos.id_producto_variante,
  vos.id_sucursal,
  vos.precio_oferta,
  vos.oferta_inicio,
  vos.oferta_fin,
  vos.deleted_at
FROM producto_variante_oferta_sucursal vos
WHERE vos.id_sucursal = :idSucursalEcommerce
  AND vos.deleted_at IS NULL;
```
