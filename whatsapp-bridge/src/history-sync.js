export function numericTimestamp(value) {
  if (value == null) return 0;
  if (typeof value === "number") return value;
  if (typeof value?.toNumber === "function") return value.toNumber();
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function sortHistoryMessages(messages = []) {
  return [...messages].sort((left, right) =>
    numericTimestamp(left?.messageTimestamp) - numericTimestamp(right?.messageTimestamp));
}
