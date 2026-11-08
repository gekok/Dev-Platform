import assert from 'node:assert/strict';
import test from 'node:test';
import api, { configureApiBaseUrl } from '../../../shared/services/axios.js';
import {
  createProjectBoard,
  listProjectBoards,
  reorderColumns,
} from './boardApi.js';

function response(config, data) {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

test('normalizes Board envelopes and sends the Board Service create/reorder contracts', async () => {
  const previousAdapter = api.defaults.adapter;
  const requests = [];
  configureApiBaseUrl('/api/v1');
  api.defaults.adapter = async (config) => {
    requests.push({
      url: config.url,
      method: config.method,
      params: config.params,
      data: config.data,
    });

    if (config.url === '/projects/4/boards' && config.method === 'get') {
      return response(config, {
        data: {
          items: [{
            id: 12,
            projectId: 4,
            name: 'Roadmap',
            boardKey: 'road-map',
            myRole: 'OWNER',
            columnCount: 3,
          }],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        },
        meta: { correlationId: 'test' },
      });
    }

    if (config.url === '/projects/4/boards' && config.method === 'post') {
      return response(config, {
        data: {
          id: 13,
          projectId: 4,
          name: 'Planning',
          boardKey: 'planning-board',
          myRole: 'OWNER',
          columns: [],
        },
        meta: {},
      });
    }

    return response(config, { data: [{ id: 31, boardId: 13, name: 'To Do', position: 0 }], meta: {} });
  };

  try {
    const boards = await listProjectBoards(4);
    const created = await createProjectBoard(4, {
      name: 'Planning',
      key: 'planning-board',
      createDefaultColumns: true,
    });
    const columns = await reorderColumns(13, [31]);

    assert.equal(boards.items[0].key, 'road-map');
    assert.equal(boards.items[0].role, 'OWNER');
    assert.equal(boards.pagination.totalElements, 1);
    assert.equal(created.boardKey, 'planning-board');
    assert.equal(created.id, 13);
    assert.deepEqual(columns, [{ id: 31, boardId: 13, name: 'To Do', position: 0 }]);

    assert.deepEqual(requests[0].params, {
      sort: 'createdAt,desc',
      page: 0,
      size: 100,
    });
    assert.equal(requests[1].url, '/projects/4/boards');
    assert.equal(requests[1].method, 'post');
    assert.deepEqual(JSON.parse(requests[1].data), {
      name: 'Planning',
      createDefaultColumns: true,
      boardKey: 'planning-board',
    });
    assert.equal(requests[2].url, '/boards/13/columns/order');
    assert.equal(requests[2].method, 'put');
    assert.deepEqual(JSON.parse(requests[2].data), { columnIds: [31] });
  } finally {
    api.defaults.adapter = previousAdapter;
  }
});
