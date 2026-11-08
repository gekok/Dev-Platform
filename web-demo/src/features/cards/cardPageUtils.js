export function flattenCardPages(pages = []) {
  const cardsById = new Map();
  pages.flatMap((page) => page.items ?? []).forEach((card) => {
    const cardId = String(card.id);
    if (!cardsById.has(cardId)) cardsById.set(cardId, card);
  });

  return [...cardsById.values()].sort((left, right) =>
    (right.position - left.position) || (Number(right.id) - Number(left.id)),
  );
}

export function hasDuplicateCardPages(pages = []) {
  const cardIds = pages.flatMap((page) => page.items ?? []).map((card) => String(card.id));
  return new Set(cardIds).size !== cardIds.length;
}
