import test from "node:test";
import assert from "node:assert/strict";
import { numericTimestamp, sortHistoryMessages } from "../src/history-sync.js";

test("sorts history chronologically without mutating the Baileys batch", () => {
  const messages = [
    { key: { id: "new" }, messageTimestamp: 30 },
    { key: { id: "old" }, messageTimestamp: { toNumber: () => 10 } },
    { key: { id: "middle" }, messageTimestamp: "20" },
  ];

  const sorted = sortHistoryMessages(messages);

  assert.deepEqual(sorted.map((message) => message.key.id), ["old", "middle", "new"]);
  assert.deepEqual(messages.map((message) => message.key.id), ["new", "old", "middle"]);
  assert.equal(numericTimestamp(undefined), 0);
});
