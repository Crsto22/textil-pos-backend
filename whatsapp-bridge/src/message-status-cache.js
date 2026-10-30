const STATUS_RANK = Object.freeze({ failed: 0, sent: 1, delivered: 2, read: 3 });

export function moreAdvancedStatus(current, candidate) {
  const currentRank = STATUS_RANK[current] ?? -1;
  const candidateRank = STATUS_RANK[candidate] ?? -1;
  return candidateRank > currentRank ? candidate : currentRank >= 0 ? current : null;
}

export class MessageStatusCache {
  constructor({ ttlMs = 10 * 60 * 1000, maxEntries = 5000, now = () => Date.now() } = {}) {
    this.ttlMs = ttlMs;
    this.maxEntries = maxEntries;
    this.now = now;
    this.entries = new Map();
  }

  remember(messageId, status) {
    if (!messageId || !(status in STATUS_RANK)) return;
    this.prune();
    const existing = this.entries.get(messageId);
    this.entries.set(messageId, {
      status: moreAdvancedStatus(existing?.status, status),
      expiresAt: this.now() + this.ttlMs,
    });
    while (this.entries.size > this.maxEntries) {
      this.entries.delete(this.entries.keys().next().value);
    }
  }

  consume(messageId, embeddedStatus) {
    this.prune();
    const cached = this.entries.get(messageId)?.status;
    this.entries.delete(messageId);
    return moreAdvancedStatus(embeddedStatus, cached);
  }

  prune() {
    const timestamp = this.now();
    for (const [messageId, entry] of this.entries) {
      if (entry.expiresAt <= timestamp) this.entries.delete(messageId);
    }
  }
}
