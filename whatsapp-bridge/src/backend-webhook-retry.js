const DEFAULT_ATTEMPTS = 20;
const DEFAULT_INITIAL_DELAY_MS = 500;
const DEFAULT_MAX_DELAY_MS = 15_000;

export async function postJsonWithRetry(url, payload, options = {}) {
  const attempts = positiveInteger(options.attempts, DEFAULT_ATTEMPTS);
  const initialDelayMs = positiveInteger(options.initialDelayMs, DEFAULT_INITIAL_DELAY_MS);
  const maxDelayMs = positiveInteger(options.maxDelayMs, DEFAULT_MAX_DELAY_MS);
  const fetchImpl = options.fetchImpl || fetch;
  const sleep = options.sleep || ((milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)));
  const onRetry = options.onRetry || (() => {});

  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetchImpl(url, {
        method: "POST",
        headers: options.headers || { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (response.ok) {
        return;
      }
      const detail = await response.text().catch(() => "");
      const error = new Error(`Backend webhook respondio ${response.status}${detail ? `: ${detail}` : ""}`);
      if (!isRetryableStatus(response.status)) {
        throw Object.assign(error, { nonRetryable: true });
      }
      lastError = error;
    } catch (error) {
      if (error?.nonRetryable) {
        throw error;
      }
      lastError = error;
    }

    if (attempt === attempts) {
      break;
    }
    const delayMs = Math.min(maxDelayMs, initialDelayMs * (2 ** (attempt - 1)));
    onRetry({ attempt, nextAttempt: attempt + 1, delayMs, error: lastError });
    await sleep(delayMs);
  }
  throw lastError || new Error("No se pudo entregar el webhook al backend");
}

function isRetryableStatus(status) {
  return status === 408 || status === 425 || status === 429 || status >= 500;
}

function positiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}
