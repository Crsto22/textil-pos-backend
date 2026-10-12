import assert from "node:assert/strict";
import test from "node:test";
import { deterministicMessageId, RequestIdempotencyRegistry } from "../src/request-idempotency.js";

test("reuses one result for concurrent calls with the same key", async () => {
  const registry = new RequestIdempotencyRegistry({ ttlMs: 60_000 });
  let sends = 0;
  const operation = async () => ({ messageId: `m-${++sends}` });
  const [first, second] = await Promise.all([
    registry.execute("order-1", "same", operation),
    registry.execute("order-1", "same", operation),
  ]);
  assert.equal(sends, 1);
  assert.deepEqual(first, second);
});

test("rejects a reused key with different payload", async () => {
  const registry = new RequestIdempotencyRegistry({ ttlMs: 60_000 });
  await registry.execute("order-2", "first", async () => ({ ok: true }));
  await assert.rejects(() => registry.execute("order-2", "other", async () => ({ ok: false })),
    error => error.code === "IDEMPOTENCY_CONFLICT");
});

test("deterministic WhatsApp id is stable", () => {
  assert.equal(deterministicMessageId("same-key"), deterministicMessageId("same-key"));
  assert.notEqual(deterministicMessageId("same-key"), deterministicMessageId("other-key"));
});
