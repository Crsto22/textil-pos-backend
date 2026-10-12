import { createHash } from "node:crypto";

export function deterministicMessageId(key) {
  if (!key) return undefined;
  return createHash("sha256").update(String(key)).digest("hex").toUpperCase().slice(0, 32);
}

export class RequestIdempotencyRegistry {
  constructor({ ttlMs = 24 * 60 * 60 * 1000 } = {}) {
    this.ttlMs = ttlMs;
    this.entries = new Map();
  }

  async execute(key, signature, operation) {
    if (!key) return operation();
    const existing = this.entries.get(key);
    if (existing) {
      if (existing.signature !== signature) {
        const error = new Error("La clave idempotente ya fue usada con otro contenido");
        error.code = "IDEMPOTENCY_CONFLICT";
        throw error;
      }
      return existing.promise;
    }
    const promise = Promise.resolve().then(operation);
    this.entries.set(key, { signature, promise });
    try {
      const result = await promise;
      const timer = setTimeout(() => this.entries.delete(key), this.ttlMs);
      timer.unref?.();
      return result;
    } catch (error) {
      this.entries.delete(key);
      throw error;
    }
  }
}
