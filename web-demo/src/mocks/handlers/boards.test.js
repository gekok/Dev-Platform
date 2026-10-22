import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  MEMBER_TOKEN,
  OUTSIDER_TOKEN,
  OWNER_TOKEN,
  apiUrl,
  bearer,
  withMockServer,
} from '../testUtils.js';

async function postJson(path, token, payload) {
  return fetch(apiUrl(path), {
    method: 'POST',
    headers: {
      ...bearer(token),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

test('Project Owners and Members can list Boards and read a Board with ordered Columns', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards[0];

    for (const token of [OWNER_TOKEN, MEMBER_TOKEN]) {
      const listResponse = await fetch(
        apiUrl(`/projects/${board.projectId}/boards`),
        { headers: bearer(token) },
      );
      const boards = await listResponse.json();
      assert.equal(listResponse.status, 200);
      assert.equal(boards.length, 2);
      assert.ok(boards.every((item) => item.projectId === board.projectId));

      const boardResponse = await fetch(apiUrl(`/boards/${board.id}`), {
        headers: bearer(token),
      });
      const detail = await boardResponse.json();
      assert.equal(boardResponse.status, 200);
      assert.equal(detail.id, board.id);
      assert.equal(detail.projectId, board.projectId);
      assert.equal(detail.key, board.key);
      assert.deepEqual(
        detail.columns.map((column) => [column.name, column.position]),
        [['To Do', 0], ['In Progress', 1], ['Done', 2]],
      );
    }
  });
});

test('non-members cannot read nested Board lists or direct Board details', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards[0];

    for (const path of [
      `/projects/${board.projectId}/boards`,
      `/boards/${board.id}`,
    ]) {
      const response = await fetch(apiUrl(path), {
        headers: bearer(OUTSIDER_TOKEN),
      });
      assert.equal(response.status, 403);
      assert.equal((await response.json()).code, 'FORBIDDEN');
    }
  });
});

test('Board and Column routes require a valid bearer token', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards[0];
    const projectListResponse = await fetch(
      apiUrl(`/projects/${board.projectId}/boards`),
    );
    const boardReadResponse = await fetch(apiUrl(`/boards/${board.id}`));
    const boardCreateResponse = await postJson(
      `/projects/${board.projectId}/boards`,
      'invalid-token',
      { name: 'Unauthenticated Board', key: 'UNAU' },
    );
    const columnCreateResponse = await postJson(
      `/boards/${board.id}/columns`,
      'invalid-token',
      { name: 'Review' },
    );
    const columnReorderResponse = await fetch(
      apiUrl(`/boards/${board.id}/columns/order`),
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderedColumnIds: [103, 102, 101] }),
      },
    );

    for (const response of [
      projectListResponse,
      boardReadResponse,
      boardCreateResponse,
      columnCreateResponse,
      columnReorderResponse,
    ]) {
      assert.equal(response.status, 401);
      assert.equal((await response.json()).code, 'UNAUTHORIZED');
    }
  });
});

test('Owner can create a Board with default Columns under a Project', async () => {
  await withMockServer(async ({ repository }) => {
    const projectId = repository.read().projects[0].id;
    const response = await postJson(
      `/projects/${projectId}/boards`,
      OWNER_TOKEN,
      { name: 'Sprint Planning', key: 'SPR' },
    );
    const created = await response.json();

    assert.equal(response.status, 201);
    assert.equal(created.projectId, projectId);
    assert.equal(created.name, 'Sprint Planning');
    assert.deepEqual(
      created.columns.map((column) => [column.name, column.position]),
      [['To Do', 0], ['In Progress', 1], ['Done', 2]],
    );
    assert.equal(created.columns.every((column) => column.boardId === created.id), true);
  });
});

test('Board creation rejects invalid and duplicate keys', async () => {
  await withMockServer(async ({ repository }) => {
    const projectId = repository.read().projects[0].id;
    const invalidResponse = await postJson(
      `/projects/${projectId}/boards`,
      OWNER_TOKEN,
      { name: 'Invalid key', key: 'lower' },
    );
    assert.equal(invalidResponse.status, 400);
    assert.equal((await invalidResponse.json()).code, 'BAD_REQUEST');

    const duplicateResponse = await postJson(
      `/projects/${projectId}/boards`,
      OWNER_TOKEN,
      { name: 'Duplicate key', key: 'PLAT' },
    );
    assert.equal(duplicateResponse.status, 409);
    assert.equal((await duplicateResponse.json()).code, 'CONFLICT');

    const createProjectResponse = await postJson('/projects', OWNER_TOKEN, {
      name: 'Second Project',
    });
    assert.equal(createProjectResponse.status, 201);
    const secondProject = await createProjectResponse.json();
    assert.ok(Number.isSafeInteger(secondProject.id) && secondProject.id > 0);
    assert.notEqual(secondProject.id, projectId);
    const crossProjectDuplicateResponse = await postJson(
      `/projects/${secondProject.id}/boards`,
      OWNER_TOKEN,
      { name: 'Cross-project duplicate', key: 'PLAT' },
    );
    assert.equal(crossProjectDuplicateResponse.status, 409);
    assert.equal((await crossProjectDuplicateResponse.json()).code, 'CONFLICT');
  });
});

test('concurrent Board creation with one key returns one conflict and persists one Board', async () => {
  await withMockServer(async ({ repository }) => {
    const projectId = repository.read().projects[0].id;
    const responses = await Promise.all([
      postJson(`/projects/${projectId}/boards`, OWNER_TOKEN, {
        name: 'Concurrent Board A',
        key: 'RACE',
      }),
      postJson(`/projects/${projectId}/boards`, OWNER_TOKEN, {
        name: 'Concurrent Board B',
        key: 'RACE',
      }),
    ]);
    const responseBodies = await Promise.all(responses.map((response) => response.json()));
    const statuses = responses.map((response) => response.status).sort();

    assert.deepEqual(statuses, [201, 409]);
    const conflictIndex = responses.findIndex((response) => response.status === 409);
    assert.equal(responseBodies[conflictIndex].code, 'CONFLICT');
    assert.equal(
      repository.read().boards.filter((board) => board.key === 'RACE').length,
      1,
    );
  });
});

test('concurrent Column creation with one name returns one conflict and persists one Column', async () => {
  await withMockServer(async ({ repository }) => {
    const boardId = repository.read().boards[0].id;
    const responses = await Promise.all([
      postJson(`/boards/${boardId}/columns`, OWNER_TOKEN, { name: 'Race Column' }),
      postJson(`/boards/${boardId}/columns`, OWNER_TOKEN, { name: 'Race Column' }),
    ]);
    const responseBodies = await Promise.all(responses.map((response) => response.json()));
    const statuses = responses.map((response) => response.status).sort();

    assert.deepEqual(statuses, [201, 409]);
    const conflictIndex = responses.findIndex((response) => response.status === 409);
    assert.equal(responseBodies[conflictIndex].code, 'CONFLICT');
    assert.equal(
      repository.read().columns.filter(
        (column) => column.boardId === boardId && column.name === 'Race Column',
      ).length,
      1,
    );
  });
});

test('Members cannot create Boards or manage Columns', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards[0];
    const createBoardResponse = await postJson(
      `/projects/${board.projectId}/boards`,
      MEMBER_TOKEN,
      { name: 'Member Board', key: 'MEMB' },
    );
    assert.equal(createBoardResponse.status, 403);

    const addColumnResponse = await postJson(
      `/boards/${board.id}/columns`,
      MEMBER_TOKEN,
      { name: 'Review' },
    );
    assert.equal(addColumnResponse.status, 403);

    const reorderResponse = await fetch(
      apiUrl(`/boards/${board.id}/columns/order`),
      {
        method: 'PATCH',
        headers: {
          ...bearer(MEMBER_TOKEN),
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ orderedColumnIds: [103, 102, 101] }),
      },
    );
    assert.equal(reorderResponse.status, 403);
  });
});

test('Owner can add and reorder Columns; invalid cross-Board order is rejected', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards[0];
    const addResponse = await postJson(
      `/boards/${board.id}/columns`,
      OWNER_TOKEN,
      { name: 'Review' },
    );
    const added = await addResponse.json();
    assert.equal(addResponse.status, 201);
    assert.equal(added.name, 'Review');
    assert.equal(added.position, 3);

    const beforeInvalidOrder = repository
      .read()
      .columns.filter((column) => column.boardId === board.id)
      .map((column) => [column.id, column.position]);
    const otherBoardColumnId = repository
      .read()
      .columns.find((column) => column.boardId !== board.id).id;
    const crossBoardColumnIds = beforeInvalidOrder.map(([columnId], index) =>
      index === 0 ? otherBoardColumnId : columnId,
    );
    const invalidOrderResponse = await fetch(
      apiUrl(`/boards/${board.id}/columns/order`),
      {
        method: 'PATCH',
        headers: {
          ...bearer(OWNER_TOKEN),
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ orderedColumnIds: crossBoardColumnIds }),
      },
    );
    assert.equal(invalidOrderResponse.status, 400);
    assert.equal((await invalidOrderResponse.json()).code, 'BAD_REQUEST');
    assert.deepEqual(
      repository
        .read()
        .columns.filter((column) => column.boardId === board.id)
        .map((column) => [column.id, column.position]),
      beforeInvalidOrder,
    );

    const currentIds = repository
      .read()
      .columns.filter((column) => column.boardId === board.id)
      .map((column) => column.id);
    const reorderResponse = await fetch(
      apiUrl(`/boards/${board.id}/columns/order`),
      {
        method: 'PATCH',
        headers: {
          ...bearer(OWNER_TOKEN),
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ orderedColumnIds: [...currentIds].reverse() }),
      },
    );
    const reordered = await reorderResponse.json();
    assert.equal(reorderResponse.status, 200);
    assert.deepEqual(
      reordered.columns.map((column) => column.id),
      [...currentIds].reverse(),
    );
    assert.deepEqual(
      reordered.columns.map((column) => column.position),
      [0, 1, 2, 3],
    );
  });
});

test('Column reorder rejects an order made stale by a concurrent Column addition', async () => {
  await withMockServer(async ({ repository }) => {
    const boardId = repository.read().boards[0].id;
    const orderedColumnIds = repository
      .read()
      .columns.filter((column) => column.boardId === boardId)
      .map((column) => column.id);
    const transact = repository.transact;
    let addColumnBeforeReorder = true;

    repository.transact = (mutator) => {
      if (addColumnBeforeReorder) {
        addColumnBeforeReorder = false;
        transact((draft, allocateId) => {
          draft.columns.push({
            id: allocateId('column'),
            boardId,
            name: 'Concurrent Column',
            position: draft.columns.filter((column) => column.boardId === boardId).length,
          });
        });
      }

      return transact(mutator);
    };

    const response = await fetch(apiUrl(`/boards/${boardId}/columns/order`), {
      method: 'PATCH',
      headers: {
        ...bearer(OWNER_TOKEN),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ orderedColumnIds }),
    });

    assert.equal(response.status, 400);
    assert.equal((await response.json()).code, 'BAD_REQUEST');
    const columns = repository.read().columns.filter((column) => column.boardId === boardId);
    assert.deepEqual(columns.map((column) => column.position), [0, 1, 2, 3]);
  });
});
