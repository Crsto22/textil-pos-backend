import assert from "node:assert/strict";
import test from "node:test";
import { SendDeduplicator } from "../src/send-deduplicator.js";

test("suppresses the webhook race without losing concurrent identical sends", () => {
  const deduplicator = new SendDeduplicator({ pendingTtlMs: 60_000, sentTtlMs: 60_000 });
  const signature = "51999999999@s.whatsapp.net|MEDIA|Ficha EMMA";
  const first = deduplicator.register(signature);
  deduplicator.register(signature);

  assert.equal(deduplicator.consumePending(signature), true);
  deduplicator.unregister(signature, first);
  assert.equal(deduplicator.consumePending(signature), true);
  assert.equal(deduplicator.consumePending(signature), false);

  deduplicator.rememberSent("wamid-1");
  assert.equal(deduplicator.consumeSent("wamid-1"), true);
  assert.equal(deduplicator.consumeSent("wamid-1"), false);
  deduplicator.clear();
});
