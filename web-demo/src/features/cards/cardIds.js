export function normalizeCardId(cardId) {
  if (cardId == null) return cardId;
  if (typeof cardId === 'number') {
    return Number.isSafeInteger(cardId) && cardId > 0 ? cardId : cardId;
  }
  if (typeof cardId !== 'string' || !/^\d+$/.test(cardId)) return cardId;

  const numericId = Number(cardId);
  return Number.isSafeInteger(numericId) && numericId > 0 ? numericId : cardId;
}
