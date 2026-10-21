import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  MEMBER_TOKEN,
  OUTSIDER_ID,
  OUTSIDER_TOKEN,
  OWNER_TOKEN,
  apiUrl,
  bearer,
  withMockServer,
} from '../testUtils.js';

async function requestJson(path, method, token, payload) {
  const headers = token ? bearer(token) : {};
  if (payload !== undefined) headers['Content-Type'] = 'application/json';

  return fetch(apiUrl(path), {
    method,
    headers,
    ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
  });
}

async function createPrivateProjectAndBoard() {
  const projectResponse = await requestJson('/projects', 'POST', OWNER_TOKEN, {
    name: 'Private project',
  });
  assert.equal(projectResponse.status, 201);
  const project = await projectResponse.json();

  const boardResponse = await requestJson(
    `/projects/${project.id}/boards`,
    'POST',
    OWNER_TOKEN,
    { name: 'Private board', key: 'PRIV' },
  );
  assert.equal(boardResponse.status, 201);
  const board = await boardResponse.json();

  return { project, board };
}

async function setDefaultBoard(projectId, boardId, token = OWNER_TOKEN, extra = {}) {
  return requestJson(
    `/users/me/preferences/default-boards/${projectId}`,
    'PUT',
    token,
    { boardId, ...extra },
  );
}

test('lists only the authenticated user\'s preferences for joined projects', async () => {
  await withMockServer(async ({ repository }) => {
    const initial = repository.read();
    const project = initial.projects[0];
    const [ownerBoard, memberBoard] = initial.boards;
    const privateResources = await createPrivateProjectAndBoard();

    const ownerSet = await setDefaultBoard(project.id, ownerBoard.id);
    const ownerPrivateSet = await setDefaultBoard(
      privateResources.project.id,
      privateResources.board.id,
    );
    const memberSet = await setDefaultBoard(
      project.id,
      memberBoard.id,
      MEMBER_TOKEN,
    );

    assert.equal(ownerSet.status, 200);
    assert.equal(ownerPrivateSet.status, 200);
    assert.equal(memberSet.status, 200);

    const ownerResponse = await requestJson(
      '/users/me/preferences/default-boards',
      'GET',
      OWNER_TOKEN,
    );
    const memberResponse = await requestJson(
      '/users/me/preferences/default-boards',
      'GET',
      MEMBER_TOKEN,
    );
    const ownerPreferences = await ownerResponse.json();
    const memberPreferences = await memberResponse.json();

    assert.equal(ownerResponse.status, 200);
    assert.equal(memberResponse.status, 200);
    assert.deepEqual(
      ownerPreferences.defaultBoards.sort((left, right) => left.projectId - right.projectId),
      [
        { projectId: project.id, boardId: ownerBoard.id },
        {
          projectId: privateResources.project.id,
          boardId: privateResources.board.id,
        },
      ],
    );
    assert.deepEqual(memberPreferences.defaultBoards, [
      { projectId: project.id, boardId: memberBoard.id },
    ]);
  });
});

test('requires a valid bearer token and rejects non-members', async () => {
  await withMockServer(async ({ repository }) => {
    const { projects, boards } = repository.read();
    const project = projects[0];
    const board = boards[0];
    const existingPreference = await setDefaultBoard(project.id, board.id);
    assert.equal(existingPreference.status, 200);
    const before = repository.read().defaultBoardPreferences;

    const unauthenticatedResponse = await requestJson(
      '/users/me/preferences/default-boards',
      'GET',
    );
    const forbiddenResponse = await setDefaultBoard(
      project.id,
      boards[1].id,
      OUTSIDER_TOKEN,
    );

    assert.equal(unauthenticatedResponse.status, 401);
    assert.equal((await unauthenticatedResponse.json()).code, 'UNAUTHORIZED');
    assert.equal(forbiddenResponse.status, 403);
    assert.equal((await forbiddenResponse.json()).code, 'FORBIDDEN');
    assert.deepEqual(repository.read().defaultBoardPreferences, before);
  });
});

test('sets and replaces a default only when the Board belongs to the Project', async () => {
  await withMockServer(async ({ repository }) => {
    const { projects, boards } = repository.read();
    const project = projects[0];
    const [firstBoard, secondBoard] = boards;

    const firstResponse = await setDefaultBoard(project.id, firstBoard.id, OWNER_TOKEN, {
      userId: OUTSIDER_ID,
    });
    const firstBody = await firstResponse.json();
    assert.equal(firstResponse.status, 200);
    assert.deepEqual(firstBody, {
      projectId: project.id,
      boardId: firstBoard.id,
    });

    const replacementResponse = await setDefaultBoard(project.id, secondBoard.id);
    const replacementBody = await replacementResponse.json();
    assert.equal(replacementResponse.status, 200);
    assert.deepEqual(replacementBody, {
      projectId: project.id,
      boardId: secondBoard.id,
    });
    assert.deepEqual(repository.read().defaultBoardPreferences, [
      {
        userId: project.ownerId,
        projectId: project.id,
        boardId: secondBoard.id,
      },
    ]);
    assert.equal(
      repository.read().defaultBoardPreferences.some(
        (preference) => preference.userId === OUTSIDER_ID,
      ),
      false,
    );
  });
});

test('returns 404 for missing projects or boards and 400 for malformed or cross-project Board IDs', async () => {
  await withMockServer(async ({ repository }) => {
    const { projects, boards } = repository.read();
    const project = projects[0];
    const existingPreference = await setDefaultBoard(project.id, boards[0].id);
    assert.equal(existingPreference.status, 200);
    const privateResources = await createPrivateProjectAndBoard();

    const missingProjectResponse = await setDefaultBoard(9999, boards[0].id);
    const missingBoardResponse = await setDefaultBoard(project.id, 9999);
    const malformedBoardResponse = await setDefaultBoard(project.id, 'not-a-board-id');
    const crossProjectBoardResponse = await setDefaultBoard(
      project.id,
      privateResources.board.id,
    );

    assert.equal(missingProjectResponse.status, 404);
    assert.equal((await missingProjectResponse.json()).code, 'NOT_FOUND');
    assert.equal(missingBoardResponse.status, 404);
    assert.equal((await missingBoardResponse.json()).code, 'NOT_FOUND');
    assert.equal(malformedBoardResponse.status, 400);
    assert.equal((await malformedBoardResponse.json()).code, 'BAD_REQUEST');
    assert.equal(crossProjectBoardResponse.status, 400);
    assert.equal((await crossProjectBoardResponse.json()).code, 'BAD_REQUEST');
    assert.deepEqual(repository.read().defaultBoardPreferences, [
      {
        userId: project.ownerId,
        projectId: project.id,
        boardId: boards[0].id,
      },
    ]);
  });
});

test('clears an existing preference and returns 204 when no preference exists', async () => {
  await withMockServer(async ({ repository }) => {
    const { projects, boards } = repository.read();
    const project = projects[0];
    const saveResponse = await setDefaultBoard(project.id, boards[0].id);
    assert.equal(saveResponse.status, 200);

    for (let index = 0; index < 2; index += 1) {
      const clearResponse = await requestJson(
        `/users/me/preferences/default-boards/${project.id}`,
        'DELETE',
        OWNER_TOKEN,
      );
      assert.equal(clearResponse.status, 204);
      assert.equal(await clearResponse.text(), '');
    }

    assert.deepEqual(repository.read().defaultBoardPreferences, []);
  });
});
