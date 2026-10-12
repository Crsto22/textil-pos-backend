# Paso 6: jobs exclusivos y recuperables

## Garantía de exclusividad

La tabla `crm_whatsapp_agent_job` incorpora `active_slot` y el índice único:

```text
(id_agent_session, active_slot)
```

Un job abierto usa `active_slot = 1`; al finalizar, fallar o quedar obsoleto se libera con `NULL`. Como una conversación solamente puede tener una sesión activa, no puede mantener dos jobs ejecutables al mismo tiempo.

El scheduler del paso 5 bloquea la conversación, invalida el trabajo anterior y crea el job nuevo en una sola transacción.

## Claim transaccional

El worker futuro obtendrá trabajo con `AgentJobCoordinator.claimNext()`:

- Selección con `FOR UPDATE SKIP LOCKED`.
- Solo jobs `PENDING`, disponibles y cuyo mensaje ancla continúa vigente.
- Cambio atómico a `RUNNING`.
- Incremento del intento.
- Lease con token único para cada claim.

El token único impide que un proceso lento complete accidentalmente un job que venció y fue reclamado nuevamente.

## Reintentos y recuperación

- Máximo predeterminado: 3 intentos.
- Espera exponencial: 2, 4 y 8 segundos.
- Lease predeterminada: 90 segundos.
- Un error devuelve el job a `PENDING` mientras queden intentos.
- Cuando se agota el límite queda `FAILED` y libera la ranura activa.
- Cada 10 segundos se recuperan leases vencidas.
- Después de reiniciar el backend, los jobs `PENDING` permanecen disponibles y los `RUNNING` vencidos se recuperan.

## Idempotencia y carreras

- `idempotency_key` es única.
- Un mensaje ancla solo puede tener un job.
- Antes de completar se verifica atómicamente que el ancla siga siendo el último mensaje de la sesión.
- Un mensaje nuevo marca el job anterior `OBSOLETE`, libera su ranura y cancela su outbox pendiente.
- Aunque el resultado antiguo termine después, no puede pasar a `COMPLETED` ni entregarse.

Este paso todavía no ejecuta Gemini. Deja preparada la cola segura para conectar posteriormente el procesador real.
