import test from "node:test";
import assert from "node:assert/strict";

import { MessageStatusCache, moreAdvancedStatus } from "../src/message-status-cache.js";

test("keeps the most advanced status when updates are duplicated or out of order", () => {
  assert.equal(moreAdvancedStatus("sent", "delivered"), "delivered");
  assert.equal(moreAdvancedStatus("read", "sent"), "read");
  assert.equal(moreAdvancedStatus("delivered", "delivered"), "delivered");
});

test("applies a status received before its message", () => {
  const cache = new MessageStatusCache();
  cache.remember("message-1", "read");
  assert.equal(cache.consume("message-1", "sent"), "read");
  assert.equal(cache.consume("message-1", "sent"), "sent");
});

test("expires pending statuses and bounds memory", () => {
  let now = 100;
  const cache = new MessageStatusCache({ ttlMs: 10, maxEntries: 1, now: () => now });
  cache.remember("old", "delivered");
  cache.remember("new", "read");
  assert.equal(cache.consume("old", "sent"), "sent");
  now = 111;
  assert.equal(cache.consume("new", "sent"), "sent");
});
