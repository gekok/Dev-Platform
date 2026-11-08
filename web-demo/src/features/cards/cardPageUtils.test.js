import assert from 'node:assert/strict';
import test from 'node:test';
import {
  flattenCardPages,
  hasDuplicateCardPages,
  loadAllCardPages,
} from './cardPageUtils.js';

test('flattens cursor pages in stable position and ID descending order without duplicates', () => {
  const pages = [
    { items: [{ id: 7, position: 2 }, { id: 6, position: 2 }] },
    { items: [{ id: 6, position: 2 }, { id: 5, position: 1 }] },
  ];

  assert.deepEqual(flattenCardPages(pages).map((card) => card.id), [7, 6, 5]);
  assert.equal(hasDuplicateCardPages(pages), true);
  assert.equal(hasDuplicateCardPages([{ items: [{ id: 7 }] }, { items: [{ id: 6 }] }]), false);
});

test('loads the pending first page before fetching all remaining pages', async () => {
  const pageOne = {
    data: { pages: [{ items: [{ id: 9, position: 2 }] }] },
    hasNextPage: true,
    isError: false,
    fetchNextPage: async (options) => {
      assert.deepEqual(options, { cancelRefetch: false, throwOnError: true });
      return {
        data: {
          pages: [
            { items: [{ id: 9, position: 2 }] },
            { items: [{ id: 8, position: 1 }] },
          ],
        },
        hasNextPage: false,
        isError: false,
        isFetchNextPageError: false,
      };
    },
  };
  const query = {
    data: undefined,
    refetch: async (options) => {
      assert.deepEqual(options, { cancelRefetch: false, throwOnError: true });
      return pageOne;
    },
  };

  assert.deepEqual((await loadAllCardPages(query)).map((card) => card.id), [9, 8]);
});

test('does not treat a failed initial page as an empty destination Column', async () => {
  const error = new Error('Column request failed');
  const query = {
    data: undefined,
    refetch: async () => ({ data: undefined, error, isError: true }),
  };

  await assert.rejects(loadAllCardPages(query), error);
});
