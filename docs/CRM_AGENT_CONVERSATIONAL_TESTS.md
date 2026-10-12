# Pruebas conversacionales del agente CRM

Esta batería separa la comprensión probabilística de Gemini de las reglas deterministas que protegen stock, pagos y ventas.

## Cobertura automática

| Riesgo | Prueba principal | Garantía |
|---|---|---|
| Mensajes fragmentados y simultáneos | `AgentMessageBatchFactoryTest`, `AgentMessageBatchSchedulerTest`, `AgentJobCoordinatorTest` | Un lote cronológico y un solo job ejecutable |
| Errores ortográficos | `AgentCatalogQueryServiceTest` | Busca `alicee a rayas` sin exigir coincidencia exacta |
| Producto inexistente | `AgentCatalogQueryServiceTest` | No inventa un producto para `Brenda` |
| Nombres parecidos | `AgentCatalogQueryServiceTest` | Devuelve ambas Alessia para que el agente aclare |
| Combos distintos y dos iguales | `AgentPromotionQueryServiceTest`, `AgentCartServiceTest` | Valida composición, precio y cantidades reales |
| Falta de stock | `AgentCartServiceTest` | Rechaza antes de escribir el carrito |
| Cambio de talla o color | `AgentCartServiceTest`, `AgentCartToolsTest` | Reemplazo atómico, mismo producto y cantidad |
| Pago vencido | `AgentPaymentExpiryServiceTest` | Vence a los cuatro minutos y libera reserva |
| Comprobante incorrecto o duplicado | `AgentPaymentEvidenceAnalysisStoreTest` | Valida monto, hash y número de operación |
| Shalom y recojo | `AgentFulfillmentServiceTest` | Estados, datos y captura correctos |
| Reinicio del backend | `AgentJobCoordinatorTest` | Recupera leases vencidos sin duplicar trabajo |
| Corpus de conversaciones reales | `AgentConversationScenarioCorpusTest` | Mantiene escenarios versionados y completos |

La suite normal es determinista y no consume Gemini:

```powershell
mvn test
```

## Evaluación semántica real con Gemini

`SpringAiGeminiConversationLiveTest` ejecuta el corpus contra Gemini y valida la decisión estructurada. Está deshabilitada de forma predeterminada para evitar costos y fallos por red.

Para ejecutarla expresamente:

```powershell
$env:GEMINI_API_KEY="..."
mvn -Dcrm.agent.live-eval=true -Dcrm.agent.live-eval.model=gemini-2.5-flash -Dtest=SpringAiGeminiConversationLiveTest test
```

Esta evaluación comprueba comprensión semántica. Las mutaciones y valores comerciales continúan validados exclusivamente por Java y MySQL.
