import assert from "node:assert/strict";
import test from "node:test";

import { postJsonWithRetry } from "../src/backend-webhook-retry.js";

test("retries temporary backend failures and preserves the same payload", async () => {
  const calls = [];
  const delays = [];
  const payload = { event: "message.received", messageId: "wamid-1", body: "Confirmar" };
  const responses = [
    () => { throw new TypeError("fetch failed"); },
    () => response(503, "starting"),
    () => response(200, "ok"),
  ];

  await postJsonWithRetry("http://localhost/webhook", payload, {
    attempts: 5,
    initialDelayMs: 10,
    maxDelayMs: 20,
    fetchImpl: async (_url, request) => {
      calls.push(JSON.parse(request.body));
      return responses.shift()();
    },
    sleep: async (delay) => delays.push(delay),
  });

  assert.equal(calls.length, 3);
  assert.deepEqual(calls, [payload, payload, payload]);
  assert.deepEqual(delays, [10, 20]);
});

test("does not retry permanent authentication or validation errors", async () => {
  let calls = 0;
  await assert.rejects(() => postJsonWithRetry("http://localhost/webhook", { messageId: "wamid-2" }, {
    attempts: 5,
    fetchImpl: async () => {
      calls += 1;
      return response(401, "invalid token");
    },
    sleep: async () => assert.fail("must not sleep"),
  }), /401/);
  assert.equal(calls, 1);
});

function response(status, text) {
  return { ok: status >= 200 && status < 300, status, text: async () => text };
}
