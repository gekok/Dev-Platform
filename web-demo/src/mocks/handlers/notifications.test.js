import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  MEMBER_ID,
  MEMBER_TOKEN,
  OWNER_TOKEN,
  apiUrl,
  bearer,
  withMockServer,
} from '../testUtils.js';
import { DEMO_OWNER_ID } from '../data/seed.js';

test('Owner sees only their Notifications with the agreed response fields', async () => {
  await withMockServer(async ({ repository }) => {
    const response = await fetch(apiUrl('/notifications/me'), {
      headers: bearer(OWNER_TOKEN),
    });
    const notifications = await response.json();

    assert.equal(response.status, 200);
    assert.equal(notifications.length, 1);
    assert.deepEqual(Object.keys(notifications[0]).sort(), [
      'boardId',
      'cardId',
      'createdAt',
      'id',
      'read',
      'title',
      'type',
    ]);
    assert.equal(notifications[0].id, 401);
    assert.equal(notifications[0].type, 'ASSIGNED');
    assert.equal(notifications[0].read, false);
    assert.equal(repository.read().notifications.find((item) => item.id === 401).recipientId, DEMO_OWNER_ID);
  });
});

test('unreadOnly=true excludes read Notifications', async () => {
  await withMockServer(async ({ repository }) => {
    repository.transact((draft, allocateId) => {
      draft.notifications.push({
        id: allocateId('notification'),
        recipientId: DEMO_OWNER_ID,
        type: 'MOVED',
        title: 'A previously read Notification',
        cardId: 201,
        boardId: 11,
        read: true,
        createdAt: '2026-10-21T08:40:00.000Z',
      });
    });

    const allResponse = await fetch(apiUrl('/notifications/me'), {
      headers: bearer(OWNER_TOKEN),
    });
    const unreadResponse = await fetch(apiUrl('/notifications/me?unreadOnly=true'), {
      headers: bearer(OWNER_TOKEN),
    });

    assert.equal(allResponse.status, 200);
    assert.equal(unreadResponse.status, 200);
    assert.equal((await allResponse.json()).length, 2);
    const unread = await unreadResponse.json();
    assert.deepEqual(unread.map((item) => item.id), [401]);
    assert.ok(unread.every((item) => item.read === false));
  });
});

test('recipient can mark a Notification read without changing another recipient item', async () => {
  await withMockServer(async ({ repository }) => {
    const response = await fetch(apiUrl('/notifications/401/read'), {
      method: 'PATCH',
      headers: bearer(OWNER_TOKEN),
    });
    const notification = await response.json();
    const storedNotifications = repository.read().notifications;

    assert.equal(response.status, 200);
    assert.equal(notification.id, 401);
    assert.equal(notification.read, true);
    assert.equal(storedNotifications.find((item) => item.id === 401).read, true);
    assert.equal(storedNotifications.find((item) => item.id === 402).read, true);
    assert.equal(storedNotifications.find((item) => item.id === 402).recipientId, MEMBER_ID);
  });
});

test('a user cannot read or mutate another recipient Notification', async () => {
  await withMockServer(async () => {
    const response = await fetch(apiUrl('/notifications/401/read'), {
      method: 'PATCH',
      headers: bearer(MEMBER_TOKEN),
    });
    const body = await response.json();

    assert.equal(response.status, 403);
    assert.equal(body.code, 'FORBIDDEN');
  });
});

test('notification feed exposes assignment, mention, and move events only to their recipients', async () => {
  await withMockServer(async ({ repository }) => {
    const previousMemberNotificationIds = new Set(
      repository.read().notifications
        .filter((item) => item.recipientId === MEMBER_ID)
        .map((item) => item.id),
    );
    const assignResponse = await fetch(apiUrl('/cards/201/assignee'), {
      method: 'PATCH',
      headers: { ...bearer(OWNER_TOKEN), 'Content-Type': 'application/json' },
      body: JSON.stringify({ assigneeId: MEMBER_ID }),
    });
    const commentResponse = await fetch(apiUrl('/cards/201/comments'), {
      method: 'POST',
      headers: { ...bearer(OWNER_TOKEN), 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: 'Please review this', mentionedUserIds: [MEMBER_ID] }),
    });
    const moveResponse = await fetch(apiUrl('/cards/201/move'), {
      method: 'PATCH',
      headers: { ...bearer(OWNER_TOKEN), 'Content-Type': 'application/json' },
      body: JSON.stringify({ toColumnId: 102, toPosition: 1 }),
    });
    assert.equal(assignResponse.status, 200);
    assert.equal(commentResponse.status, 201);
    assert.equal(moveResponse.status, 200);

    const memberResponse = await fetch(apiUrl('/notifications/me'), {
      headers: bearer(MEMBER_TOKEN),
    });
    const memberNotifications = await memberResponse.json();
    const cardEvents = memberNotifications.filter(
      (item) => item.cardId === 201 && !previousMemberNotificationIds.has(item.id),
    );

    assert.equal(memberResponse.status, 200);
    assert.deepEqual(
      cardEvents.map((item) => item.type).sort(),
      ['ASSIGNED', 'MENTIONED', 'MOVED'],
    );
    assert.ok(cardEvents.every((item) => item.boardId === 11 && item.read === false));

    const ownerResponse = await fetch(apiUrl('/notifications/me'), {
      headers: bearer(OWNER_TOKEN),
    });
    const ownerNotifications = await ownerResponse.json();
    assert.ok(ownerNotifications.every((item) => item.id === 401));
  });
});

test('notification routes require a valid bearer token and unknown IDs return 404', async () => {
  await withMockServer(async () => {
    const unauthenticated = await fetch(apiUrl('/notifications/me'));
    assert.equal(unauthenticated.status, 401);

    const unauthenticatedRead = await fetch(apiUrl('/notifications/401/read'), {
      method: 'PATCH',
    });
    assert.equal(unauthenticatedRead.status, 401);

    const invalidTokenRead = await fetch(apiUrl('/notifications/401/read'), {
      method: 'PATCH',
      headers: bearer('not-a-valid-token'),
    });
    assert.equal(invalidTokenRead.status, 401);

    const missing = await fetch(apiUrl('/notifications/9999/read'), {
      method: 'PATCH',
      headers: bearer(OWNER_TOKEN),
    });
    assert.equal(missing.status, 404);
  });
});
