import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  MEMBER_ID,
  MEMBER_TOKEN,
  OUTSIDER_ID,
  OUTSIDER_TOKEN,
  OWNER_TOKEN,
  apiUrl,
  bearer,
  withMockServer,
} from '../testUtils.js';

async function requestJson(method, path, token, payload) {
  const headers = {
    ...(token == null ? {} : bearer(token)),
    ...(payload === undefined ? {} : { 'Content-Type': 'application/json' }),
  };

  return fetch(apiUrl(path), {
    method,
    headers,
    ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
  });
}

function getJson(path, token) {
  return requestJson('GET', path, token);
}

function postJson(path, token, payload) {
  return requestJson('POST', path, token, payload);
}

function patchJson(path, token, payload) {
  return requestJson('PATCH', path, token, payload);
}

function cardSummaryKeys(card) {
  return Object.keys(card).sort();
}

const CARD_SUMMARY_KEYS = [
  'assigneeId',
  'boardId',
  'columnId',
  'id',
  'position',
  'status',
  'title',
].sort();

test('Project Members can list, create, and read Card details through Board-scoped routes', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards.find((item) => item.key === 'PLAT');
    const column = repository.read().columns.find((item) => item.boardId === board.id);
    const payload = {
      columnId: column.id,
      title: 'Write Card API adapter',
      description: 'Keep the feature behind its API module.',
      assigneeId: MEMBER_ID,
      dueDate: '2026-11-02T17:00:00.000Z',
    };

    const createResponse = await postJson(
      `/boards/${board.id}/cards`,
      MEMBER_TOKEN,
      payload,
    );
    const created = await createResponse.json();
    assert.equal(createResponse.status, 201);
    assert.deepEqual(cardSummaryKeys(created), CARD_SUMMARY_KEYS);
    assert.equal(created.boardId, board.id);
    assert.equal(created.columnId, column.id);
    assert.equal(created.title, payload.title);
    assert.equal(created.status, 'TODO');
    assert.equal(created.position, 1);
    assert.equal(created.assigneeId, MEMBER_ID);

    const listResponse = await getJson(
      `/boards/${board.id}/cards?columnId=${column.id}`,
      MEMBER_TOKEN,
    );
    const listed = await listResponse.json();
    assert.equal(listResponse.status, 200);
    assert.deepEqual(listed.map((card) => card.id), [201, created.id]);
    assert.ok(listed.every((card) => cardSummaryKeys(card).join() === CARD_SUMMARY_KEYS.join()));

    const detailResponse = await getJson(`/cards/${created.id}`, MEMBER_TOKEN);
    const detail = await detailResponse.json();
    assert.equal(detailResponse.status, 200);
    assert.deepEqual(detail, { ...created, ...payload });
    assert.equal(
      repository.read().cards.find((card) => card.id === created.id).dueDate,
      payload.dueDate,
    );
  });
});

test('Card creation rejects date-only due dates that the backend OffsetDateTime contract cannot parse', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards.find((item) => item.key === 'PLAT');
    const column = repository.read().columns.find((item) => item.boardId === board.id);
    const initialState = repository.read();
    const response = await postJson(`/boards/${board.id}/cards`, OWNER_TOKEN, {
      columnId: column.id,
      title: 'Due date needs an explicit offset',
      dueDate: '2026-10-31',
    });

    assert.equal(response.status, 400);
    assert.equal((await response.json()).code, 'BAD_REQUEST');
    assert.deepEqual(repository.read(), initialState);
  });
});

test('Card creation rejects invalid calendar timestamps and offsets outside Java OffsetDateTime limits', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards.find((item) => item.key === 'PLAT');
    const column = repository.read().columns.find((item) => item.boardId === board.id);
    const invalidDueDates = [
      '2026-02-30T12:00:00Z',
      '2026-10-31T24:00:00Z',
      '2026-10-31T00:00:00+18:01',
      '2026-10-31T00:00:00-18:01',
      '2026-10-31T00:00:00+20:00',
    ];

    for (const dueDate of invalidDueDates) {
      const initialState = repository.read();
      const response = await postJson(`/boards/${board.id}/cards`, OWNER_TOKEN, {
        columnId: column.id,
        title: `Invalid due date ${dueDate}`,
        dueDate,
      });

      assert.equal(response.status, 400, dueDate);
      assert.equal((await response.json()).code, 'BAD_REQUEST', dueDate);
      assert.deepEqual(repository.read(), initialState, dueDate);
    }
  });
});

test('Card creation accepts Java OffsetDateTime maximum offsets', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards.find((item) => item.key === 'PLAT');
    const column = repository.read().columns.find((item) => item.boardId === board.id);

    for (const [index, dueDate] of [
      '2026-10-31T00:00:00+18:00',
      '2026-10-31T00:00:00-18:00',
    ].entries()) {
      const response = await postJson(`/boards/${board.id}/cards`, OWNER_TOKEN, {
        columnId: column.id,
        title: `Valid boundary due date ${index}`,
        dueDate,
      });

      assert.equal(response.status, 201, dueDate);
      assert.equal((await response.json()).title, `Valid boundary due date ${index}`);
    }
  });
});

test('Project Members can move Cards across Columns with contiguous positions and unchanged status', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards.find((item) => item.key === 'PLAT');
    const card = repository.read().cards.find((item) => item.title === 'Define project-first boundaries');
    const destination = repository.read().columns.find(
      (column) => column.boardId === board.id && column.name === 'In Progress',
    );
    const projectMemberIds = repository
      .read()
      .projectMembers.filter((membership) => membership.projectId === board.projectId)
      .map((membership) => membership.userId);

    const response = await patchJson(`/cards/${card.id}/move`, MEMBER_TOKEN, {
      toColumnId: destination.id,
      toPosition: 0,
    });
    const moved = await response.json();
    assert.equal(response.status, 200);
    assert.equal(moved.columnId, destination.id);
    assert.equal(moved.position, 0);
    assert.equal(moved.status, 'TODO');

    const cards = repository.read().cards;
    const sourcePositions = cards
      .filter((item) => item.boardId === board.id && item.columnId === card.columnId)
      .map((item) => item.position);
    const destinationCards = cards
      .filter((item) => item.boardId === board.id && item.columnId === destination.id)
      .sort((left, right) => left.position - right.position);
    assert.deepEqual(sourcePositions, []);
    assert.deepEqual(destinationCards.map((item) => item.position), [0, 1]);
    assert.equal(destinationCards[0].id, card.id);
    assert.equal(destinationCards.find((item) => item.id === card.id).status, 'TODO');

    const movedNotifications = repository
      .read()
      .notifications.filter((notification) => notification.type === 'MOVED' && notification.cardId === card.id);
    assert.deepEqual(
      movedNotifications.map((notification) => notification.recipientId).sort(),
      projectMemberIds.filter((userId) => userId !== MEMBER_ID).sort(),
    );
  });
});

test('Project Members can reorder Cards inside one Column without leaving position gaps', async () => {
  await withMockServer(async ({ repository }) => {
    const board = repository.read().boards.find((item) => item.key === 'PLAT');
    const column = repository.read().columns.find((item) => item.boardId === board.id);
    const createResponse = await postJson(`/boards/${board.id}/cards`, OWNER_TOKEN, {
      columnId: column.id,
      title: 'Second card in To Do',
      description: null,
      assigneeId: null,
      dueDate: null,
    });
    const created = await createResponse.json();

    const response = await patchJson(`/cards/${created.id}/move`, MEMBER_TOKEN, {
      toColumnId: column.id,
      toPosition: 0,
    });
    const moved = await response.json();
    const cards = repository
      .read()
      .cards.filter((item) => item.boardId === board.id && item.columnId === column.id)
      .sort((left, right) => left.position - right.position);

    assert.equal(response.status, 200);
    assert.equal(moved.status, 'TODO');
    assert.deepEqual(cards.map((item) => item.id), [created.id, 201]);
    assert.deepEqual(cards.map((item) => item.position), [0, 1]);
  });
});

test('assignee updates notify only a new assignee and never notify the actor', async () => {
  await withMockServer(async ({ repository }) => {
    const card = repository.read().cards.find((item) => item.id === 201);
    const otherMemberId = repository
      .read()
      .projectMembers.find(
        (membership) => membership.projectId === 1 && membership.userId !== MEMBER_ID && membership.role === 'MEMBER',
      ).userId;
    const existingNotificationIds = new Set(
      repository.read().notifications.map((notification) => notification.id),
    );

    const assignmentResponse = await patchJson(
      `/cards/${card.id}/assignee`,
      MEMBER_TOKEN,
      { assigneeId: otherMemberId },
    );
    const assigned = await assignmentResponse.json();
    assert.equal(assignmentResponse.status, 200);
    assert.equal(assigned.assigneeId, otherMemberId);

    const repeatedResponse = await patchJson(
      `/cards/${card.id}/assignee`,
      MEMBER_TOKEN,
      { assigneeId: otherMemberId },
    );
    assert.equal(repeatedResponse.status, 200);

    const selfAssignmentResponse = await patchJson(
      `/cards/${card.id}/assignee`,
      MEMBER_TOKEN,
      { assigneeId: MEMBER_ID },
    );
    assert.equal(selfAssignmentResponse.status, 200);

    const unassignResponse = await patchJson(
      `/cards/${card.id}/assignee`,
      MEMBER_TOKEN,
      { assigneeId: null },
    );
    assert.equal(unassignResponse.status, 200);
    assert.equal((await unassignResponse.json()).assigneeId, null);

    const newNotifications = repository
      .read()
      .notifications.filter((notification) => !existingNotificationIds.has(notification.id));
    assert.equal(newNotifications.length, 1);
    assert.equal(newNotifications[0].type, 'ASSIGNED');
    assert.equal(newNotifications[0].cardId, card.id);
    assert.equal(newNotifications[0].recipientId, otherMemberId);
    assert.equal(newNotifications[0].read, false);
  });
});

test('comments return API fields, store unique mentions, and notify each mentioned member once', async () => {
  await withMockServer(async ({ repository }) => {
    const card = repository.read().cards.find((item) => item.id === 201);
    const otherMemberId = repository
      .read()
      .projectMembers.find(
        (membership) => membership.projectId === 1 && membership.userId !== MEMBER_ID && membership.role === 'MEMBER',
      ).userId;
    const existingNotificationIds = new Set(
      repository.read().notifications.map((notification) => notification.id),
    );
    const listResponse = await getJson(`/cards/${card.id}/comments`, MEMBER_TOKEN);
    const seededComments = await listResponse.json();
    assert.equal(listResponse.status, 200);
    assert.deepEqual(Object.keys(seededComments[0]).sort(), [
      'authorId',
      'cardId',
      'content',
      'createdAt',
      'id',
    ]);

    const response = await postJson(`/cards/${card.id}/comments`, MEMBER_TOKEN, {
      content: 'Please check the API and notify the team.',
      mentionedUserIds: [otherMemberId, otherMemberId, MEMBER_ID],
    });
    const comment = await response.json();
    assert.equal(response.status, 201);
    assert.deepEqual(Object.keys(comment).sort(), [
      'authorId',
      'cardId',
      'content',
      'createdAt',
      'id',
    ]);
    assert.equal(comment.authorId, MEMBER_ID);

    const storedComment = repository.read().comments.find((item) => item.id === comment.id);
    assert.deepEqual(storedComment.mentionedUserIds, [otherMemberId, MEMBER_ID]);
    const mentionNotifications = repository
      .read()
      .notifications.filter(
        (notification) =>
          notification.type === 'MENTIONED' &&
          notification.cardId === card.id &&
          !existingNotificationIds.has(notification.id),
      );
    assert.deepEqual(
      mentionNotifications.map((notification) => notification.recipientId).sort(),
      [otherMemberId].sort(),
    );

    const invalidMentionResponse = await postJson(
      `/cards/${card.id}/comments`,
      MEMBER_TOKEN,
      { content: 'This member is outside the Project.', mentionedUserIds: [OUTSIDER_ID] },
    );
    assert.equal(invalidMentionResponse.status, 400);
    assert.equal((await invalidMentionResponse.json()).code, 'BAD_REQUEST');
    assert.equal(repository.read().comments.length, 2);
    assert.equal(
      repository
        .read()
        .notifications.filter((notification) => notification.type === 'MENTIONED' && notification.cardId === card.id)
        .length,
      2,
    );
  });
});

test('Cards reject Columns and assignees outside their Project and Board boundaries', async () => {
  await withMockServer(async ({ repository }) => {
    const boards = repository.read().boards;
    const card = repository.read().cards.find((item) => item.id === 201);
    const otherBoardColumn = repository.read().columns.find(
      (column) => column.boardId !== card.boardId,
    );
    const crossBoardCreate = await postJson(`/boards/${card.boardId}/cards`, OWNER_TOKEN, {
      columnId: otherBoardColumn.id,
      title: 'Invalid cross-board card',
    });
    assert.equal(crossBoardCreate.status, 400);
    assert.equal((await crossBoardCreate.json()).code, 'BAD_REQUEST');

    const invalidAssignee = await patchJson(
      `/cards/${card.id}/assignee`,
      OWNER_TOKEN,
      { assigneeId: OUTSIDER_ID },
    );
    assert.equal(invalidAssignee.status, 400);
    assert.equal((await invalidAssignee.json()).code, 'BAD_REQUEST');

    const invalidMove = await patchJson(`/cards/${card.id}/move`, OWNER_TOKEN, {
      toColumnId: otherBoardColumn.id,
      toPosition: 0,
    });
    assert.equal(invalidMove.status, 400);
    assert.equal((await invalidMove.json()).code, 'BAD_REQUEST');
    assert.deepEqual(
      repository.read().cards.find((item) => item.id === card.id),
      card,
    );
    assert.equal(repository.read().boards.length, boards.length);
  });
});

test('non-members cannot list, read, create, move, assign, or comment on Project Cards', async () => {
  await withMockServer(async ({ repository }) => {
    const card = repository.read().cards.find((item) => item.id === 201);
    const requests = [
      getJson(`/boards/${card.boardId}/cards`, OUTSIDER_TOKEN),
      getJson(`/cards/${card.id}`, OUTSIDER_TOKEN),
      postJson(`/boards/${card.boardId}/cards`, OUTSIDER_TOKEN, {
        columnId: card.columnId,
        title: 'Outsider Card',
      }),
      patchJson(`/cards/${card.id}/move`, OUTSIDER_TOKEN, {
        toColumnId: card.columnId,
        toPosition: 0,
      }),
      patchJson(`/cards/${card.id}/assignee`, OUTSIDER_TOKEN, {
        assigneeId: MEMBER_ID,
      }),
      getJson(`/cards/${card.id}/comments`, OUTSIDER_TOKEN),
      postJson(`/cards/${card.id}/comments`, OUTSIDER_TOKEN, {
        content: 'Outsider comment',
      }),
    ];

    for (const response of await Promise.all(requests)) {
      assert.equal(response.status, 403);
      assert.equal((await response.json()).code, 'FORBIDDEN');
    }

    assert.equal(repository.read().cards.length, 4);
    assert.equal(repository.read().comments.length, 1);
  });
});

test('Card routes require a bearer token and return 404 for unknown Cards', async () => {
  await withMockServer(async ({ repository }) => {
    const card = repository.read().cards.find((item) => item.id === 201);
    const missingTokenResponse = await requestJson('GET', `/boards/${card.boardId}/cards`);
    assert.equal(missingTokenResponse.status, 401);

    const unknownCardResponse = await getJson('/cards/999999', OWNER_TOKEN);
    assert.equal(unknownCardResponse.status, 404);
    assert.equal((await unknownCardResponse.json()).code, 'NOT_FOUND');
  });
});
