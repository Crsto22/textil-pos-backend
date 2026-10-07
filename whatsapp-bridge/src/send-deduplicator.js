export class SendDeduplicator {
  constructor({ pendingTtlMs = 30_000, sentTtlMs = 60_000 } = {}) {
    this.pendingTtlMs = pendingTtlMs;
    this.sentTtlMs = sentTtlMs;
    this.pending = new Map();
    this.sentMessageIds = new Set();
  }

  register(signature) {
    const token = Symbol(signature);
    const tokens = this.pending.get(signature) || new Set();
    tokens.add(token);
    this.pending.set(signature, tokens);
    this.schedule(() => this.unregister(signature, token), this.pendingTtlMs);
    return token;
  }

  unregister(signature, token) {
    const tokens = this.pending.get(signature);
    if (!tokens) return;
    tokens.delete(token);
    if (tokens.size === 0) this.pending.delete(signature);
  }

  consumePending(signature) {
    const tokens = this.pending.get(signature);
    if (!tokens?.size) return false;
    const token = tokens.values().next().value;
    this.unregister(signature, token);
    return true;
  }

  rememberSent(messageId) {
    if (!messageId) return;
    this.sentMessageIds.add(messageId);
    this.schedule(() => this.sentMessageIds.delete(messageId), this.sentTtlMs);
  }

  consumeSent(messageId) {
    return Boolean(messageId) && this.sentMessageIds.delete(messageId);
  }

  clear() {
    this.pending.clear();
    this.sentMessageIds.clear();
  }

  schedule(callback, delay) {
    const timer = setTimeout(callback, delay);
    timer.unref?.();
  }
}
