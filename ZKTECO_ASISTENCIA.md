# ZKTeco M2F-LR - asistencia

## Orden de configuracion

1. Iniciar el backend para que cree las tablas de asistencia.
2. Obtener el numero de serie desde la informacion del M2F-LR.
3. Crear el dispositivo con `POST /api/dispositivos-asistencia` usando un JWT de administrador.
4. Crear los trabajadores con `POST /api/trabajadores`. `codigoZkteco` debe ser el mismo `User ID` usado en el reloj.
5. En el reloj configurar fecha, hora y zona horaria de Peru (`America/Lima`).
6. En `COMM > Cloud Server/ADMS`, configurar el dominio HTTPS del backend y puerto `443`.
7. Hacer dos marcaciones de prueba y consultar `GET /api/asistencia/marcaciones`.

## Ejemplos administrativos

```http
POST /api/dispositivos-asistencia
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "numeroSerie": "SERIAL_DEL_RELOJ",
  "nombre": "Reloj tienda principal",
  "idSucursal": 1
}
```

```http
POST /api/trabajadores
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "codigoZkteco": "1001",
  "dni": "12345678",
  "nombres": "Ana",
  "apellidos": "Perez",
  "idSucursal": 1,
  "idTurno": 1
}
```

```http
GET /api/asistencia/resumen?desde=2026-07-01&hasta=2026-07-31&idSucursal=1&page=0
Authorization: Bearer <jwt>
```

## Rutas ADMS

- `GET /iclock/cdata`: handshake y opciones.
- `POST /iclock/cdata`: recepcion idempotente de `ATTLOG`.
- `GET /iclock/getrequest`: consulta de comandos; responde `OK` en esta version.
- `POST /iclock/devicecmd`: confirmacion de comandos.

Estas rutas no usan JWT porque el reloj no lo soporta. Solo aceptan un numero de serie registrado y activo. En produccion deben publicarse exclusivamente por HTTPS y tener limite de frecuencia y tamano en el proxy o Cloudflare.

El fixture de pruebas es representativo. Antes de produccion, reemplazarlo o ampliarlo con una exportacion real del M2F-LR y confirmar el payload recibido en `ATTLOG`.

## Proteccion Nginx en produccion

El reloj debe usar `api.kiments.tech`, HTTPS habilitado y puerto `443`. El puerto `8080` del backend debe aceptar conexiones solo desde localhost o desde la red interna de Docker; nunca debe quedar publicado directamente en Internet.

Agregar las zonas una sola vez dentro del bloque `http` principal de Nginx:

```nginx
limit_req_zone $binary_remote_addr zone=adms_por_ip:10m rate=60r/m;
limit_conn_zone $binary_remote_addr zone=adms_conexiones:10m;
```

Agregar esta ubicacion dentro del bloque HTTPS de `api.kiments.tech`. El `proxy_pass` debe apuntar al backend interno real y conservar la ruta `/iclock/`:

```nginx
location ^~ /iclock/ {
    client_max_body_size 256k;
    client_body_timeout 15s;
    proxy_request_buffering on;

    limit_req zone=adms_por_ip burst=20 nodelay;
    limit_req_status 429;
    limit_conn adms_conexiones 5;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_pass http://127.0.0.1:8080;
}
```

Si Nginx se ejecuta dentro de la misma red Docker, reemplazar `127.0.0.1:8080` por el nombre y puerto interno del servicio backend. No agregar `/api` al `proxy_pass`: los controladores ADMS estan publicados en `/iclock/*`.

Validar y recargar sin interrumpir conexiones:

```bash
sudo nginx -t
sudo systemctl reload nginx
curl -I https://api.kiments.tech/iclock/cdata
```

El `curl` puede responder `400` por no incluir `SN`; eso confirma que HTTPS y la ruta alcanzan el backend. Una carga mayor de 256 KB debe responder `413` y el exceso de solicitudes debe responder `429`.
