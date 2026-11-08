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

export async function loadAllCardPages(query) {
  let result = query;

  if (!result.data) {
    result = await result.refetch({ cancelRefetch: false, throwOnError: true });
  }
  if (result.isError || !result.data) {
    throw result.error ?? new Error('The first Card page could not be loaded.');
  }

  while (result.hasNextPage) {
    result = await result.fetchNextPage({ cancelRefetch: false, throwOnError: true });
    if (result.isFetchNextPageError || result.isError || !result.data) {
      throw result.error ?? new Error('A Card page could not be loaded.');
    }
  }

  const pages = result.data.pages ?? [];
  if (hasDuplicateCardPages(pages)) {
    throw new Error('Card pages overlap. Reload this Column before moving a Card.');
  }

  return flattenCardPages(pages);
}
