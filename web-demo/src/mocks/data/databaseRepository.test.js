import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  createMockRepository,
  DATABASE_STORAGE_KEY,
} from './databaseRepository.js';
import * as storageKeys from '../../shared/constants/storageKeys.js';
import { createSeedData } from './seed.js';

const V2_STORAGE_KEY = 'dev-platform-demo:db:v2';
const LEGACY_STORAGE_KEY = 'dev-platform-demo:db:v1';
const ACCESS_TOKEN_STORAGE_KEY = 'dev-platform-demo:access-token';


function createMemoryStorage() {
  const values = new Map();

  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
  };
}

function createVersionOneStore() {
  const data = createSeedData();
  data.projects[0].name = 'Edited v1 project';
  data.cards[0].title = 'Edited v1 card';
  data.nextIds.card = 900;
  delete data.defaultBoardPreferences;
  return { version: 1, data };
}

test('seeds a demo Owner, a multi-Board Project, Members, and an unread Notification', () => {
  const repository = createMockRepository(createMemoryStorage());
  const data = repository.read();
  const project = data.projects[0];
  const owner = data.users.find((user) => user.id === project.ownerId);
  const projectBoardIds = data.boards
    .filter((board) => board.projectId === project.id)
    .map((board) => board.id);
  const projectMemberships = data.projectMembers.filter(
    (membership) => membership.projectId === project.id,
  );
  const memberIds = projectMemberships.map((membership) => membership.userId);
  const unreadOwnerNotification = data.notifications.find(
    (notification) =>
      notification.recipientId === project.ownerId && !notification.read,
  );
  const notificationBoard = data.boards.find(
    (board) => board.id === unreadOwnerNotification?.boardId,
  );
  const notificationCard = data.cards.find(
    (card) => card.id === unreadOwnerNotification?.cardId,
  );
  const notificationColumn = data.columns.find(
    (column) => column.id === notificationCard?.columnId,
  );

  assert.ok(owner);
  assert.ok(
    projectMemberships.some(
      (membership) =>
        membership.userId === project.ownerId && membership.role === 'OWNER',
    ),
  );
  assert.ok(projectBoardIds.length >= 2);
  assert.ok(memberIds.length >= 3);
  assert.ok(
    data.boards.every((board) =>
      data.projects.some((candidate) => candidate.id === board.projectId),
    ),
  );
  assert.ok(
    data.projectMembers.every(
      (membership) =>
        data.projects.some((candidate) => candidate.id === membership.projectId) &&
        data.users.some((user) => user.id === membership.userId),
    ),
  );
  assert.ok(unreadOwnerNotification);
  assert.ok(notificationBoard);
  assert.ok(notificationCard);
  assert.ok(notificationColumn);
  assert.equal(notificationCard.boardId, notificationBoard.id);
  assert.equal(notificationColumn.boardId, notificationCard.boardId);
});

test('persists valid versioned data and reloads it in another repository', () => {
  const storage = createMemoryStorage();
  const firstRepository = createMockRepository(storage);

  firstRepository.transact((draft) => {
    draft.projects[0].name = 'Persisted project name';
  });

  const stored = JSON.parse(storage.getItem(DATABASE_STORAGE_KEY));
  const reloadedRepository = createMockRepository(storage);

  assert.equal(stored.version, 2);
  assert.equal(reloadedRepository.read().projects[0].name, 'Persisted project name');
});

test('replaces invalid JSON and unsupported versions with the seed', () => {
  for (const invalidValue of [
    '{not-json',
    JSON.stringify({ version: 999, data: { projects: [] } }),
  ]) {
    const storage = createMemoryStorage();
    storage.setItem(DATABASE_STORAGE_KEY, invalidValue);

    const repository = createMockRepository(storage);
    const stored = JSON.parse(storage.getItem(DATABASE_STORAGE_KEY));

    assert.equal(stored.version, 2);
    assert.ok(repository.read().projects.length > 0);
    assert.ok(repository.read().boards.length > 0);
  }
});

test('reseeds same-version storage with malformed rows or stale ID counters', () => {
  const invalidators = [
    (store) => {
      store.data.projects[0].name = '';
    },
    (store) => {
      store.data.nextIds.project = 1;
    },
    (store) => {
      store.data.users[4].id = store.data.users[0].id;
    },
    (store) => {
      store.data.projects[0].createdAt = 'not-a-date';
    },
  ];

  for (const invalidate of invalidators) {
    const storage = createMemoryStorage();
    const initialRepository = createMockRepository(storage);
    const initialData = initialRepository.read();
    const initialProjectName = initialData.projects[0].name;
    const seededMaxProjectId = Math.max(
      ...initialData.projects.map((project) => project.id),
    );
    const corruptedStore = JSON.parse(storage.getItem(DATABASE_STORAGE_KEY));
    invalidate(corruptedStore);
    storage.setItem(DATABASE_STORAGE_KEY, JSON.stringify(corruptedStore));

    const recoveredRepository = createMockRepository(storage);
    const nextProjectId = recoveredRepository.transact((draft, allocateId) =>
      allocateId('project'),
    );

    assert.equal(recoveredRepository.read().projects[0].name, initialProjectName);
    assert.ok(nextProjectId > seededMaxProjectId);
    const recoveredUserIds = recoveredRepository.read().users.map((user) => user.id);
    assert.equal(new Set(recoveredUserIds).size, recoveredUserIds.length);
    assert.ok(
      recoveredRepository
        .read()
        .projects.every((project) => Number.isFinite(Date.parse(project.createdAt))),
    );
  }
});

test('reset restores product data without clearing the separate session token', () => {
  const storage = createMemoryStorage();
  const repository = createMockRepository(storage);
  const seededName = repository.read().projects[0].name;
  storage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'demo-session-token');

  repository.transact((draft) => {
    draft.projects[0].name = 'Changed name';
  });
  repository.reset();

  assert.equal(repository.read().projects[0].name, seededName);
  assert.equal(storage.getItem(ACCESS_TOKEN_STORAGE_KEY), 'demo-session-token');
});

test('does not persist a partial update when the transaction callback throws', () => {
  const storage = createMemoryStorage();
  const repository = createMockRepository(storage);
  repository.transact((draft, allocateId) => allocateId('project'));
  const before = storage.getItem(DATABASE_STORAGE_KEY);

  let attemptedId;
  assert.throws(
    () =>
      repository.transact((draft, allocateId) => {
        attemptedId = allocateId('project');
        draft.projects[0].name = 'Must not persist';
        throw new Error('abort transaction');
      }),
    /abort transaction/,
  );

  assert.equal(storage.getItem(DATABASE_STORAGE_KEY), before);
  assert.notEqual(repository.read().projects[0].name, 'Must not persist');
  assert.equal(
    repository.transact((draft, allocateId) => allocateId('project')),
    attemptedId,
  );
});

test('does not persist a transaction when its result cannot be cloned', () => {
  const storage = createMemoryStorage();
  const repository = createMockRepository(storage);
  const before = storage.getItem(DATABASE_STORAGE_KEY);

  assert.throws(() =>
    repository.transact((draft) => {
      draft.projects[0].name = 'Must not persist';
      return () => {};
    }),
  );

  assert.equal(storage.getItem(DATABASE_STORAGE_KEY), before);
  assert.notEqual(repository.read().projects[0].name, 'Must not persist');
});

test('allocates positive monotonic IDs across reloads and resets above seeded IDs', () => {
  const storage = createMemoryStorage();
  const repository = createMockRepository(storage);
  const resourceTypes = [
    ['project', 'projects'],
    ['board', 'boards'],
    ['column', 'columns'],
    ['card', 'cards'],
    ['comment', 'comments'],
    ['notification', 'notifications'],
  ];
  const seedData = repository.read();
  for (const [type, collection] of resourceTypes) {
    assert.ok(seedData[collection].length > 0, `seed has no ${type} resource`);
    assert.ok(
      seedData[collection].every(
        (resource) => Number.isSafeInteger(resource.id) && resource.id > 0,
      ),
      `${type} seed IDs must be positive safe integers`,
    );
  }
  const seededMaxIds = Object.fromEntries(
    resourceTypes.map(([type, collection]) => [
      type,
      Math.max(...seedData[collection].map((resource) => resource.id)),
    ]),
  );
  const firstIds = repository.transact((draft, allocateId) =>
    Object.fromEntries(resourceTypes.map(([type]) => [type, allocateId(type)])),
  );
  const reloadedRepository = createMockRepository(storage);
  const secondIds = reloadedRepository.transact((draft, allocateId) =>
    Object.fromEntries(resourceTypes.map(([type]) => [type, allocateId(type)])),
  );

  for (const [type] of resourceTypes) {
    assert.ok(firstIds[type] > seededMaxIds[type]);
    assert.ok(secondIds[type] > firstIds[type]);
    assert.ok(Number.isInteger(firstIds[type]) && firstIds[type] > 0);
    assert.ok(Number.isInteger(secondIds[type]) && secondIds[type] > 0);
  }

  reloadedRepository.reset();
  const afterResetIds = reloadedRepository.transact((draft, allocateId) =>
    Object.fromEntries(resourceTypes.map(([type]) => [type, allocateId(type)])),
  );

  for (const [type] of resourceTypes) {
    assert.ok(afterResetIds[type] > seededMaxIds[type]);
  }
});

test('migrates a valid v1 store to v2 without reseeding product data and removes the legacy key', () => {
  const storage = createMemoryStorage();
  storage.setItem(LEGACY_STORAGE_KEY, JSON.stringify(createVersionOneStore()));

  const repository = createMockRepository(storage);
  const migratedStore = JSON.parse(storage.getItem(V2_STORAGE_KEY));

  assert.equal(DATABASE_STORAGE_KEY, V2_STORAGE_KEY);
  assert.equal(storageKeys.DATABASE_STORAGE_KEY, V2_STORAGE_KEY);
  assert.equal(storageKeys.LEGACY_DATABASE_STORAGE_KEY, LEGACY_STORAGE_KEY);
  assert.equal(migratedStore.version, 2);
  assert.deepEqual(migratedStore.data.defaultBoardPreferences, []);
  assert.equal(migratedStore.data.projects[0].name, 'Edited v1 project');
  assert.equal(migratedStore.data.boards[0].id, 11);
  assert.equal(migratedStore.data.cards[0].title, 'Edited v1 card');
  assert.equal(migratedStore.data.nextIds.card, 900);
  assert.equal(storage.getItem(LEGACY_STORAGE_KEY), null);
  assert.equal(repository.read().projects[0].name, 'Edited v1 project');
});

test('seeds v2 when v1 is missing or invalid', () => {
  for (const legacyValue of [null, '{not-json']) {
    const storage = createMemoryStorage();
    if (legacyValue) storage.setItem(LEGACY_STORAGE_KEY, legacyValue);

    const repository = createMockRepository(storage);
    const stored = JSON.parse(storage.getItem(V2_STORAGE_KEY));

    assert.equal(stored.version, 2);
    assert.deepEqual(stored.data.defaultBoardPreferences, []);
    assert.equal(
      repository.read().projects[0].name,
      createSeedData().projects[0].name,
    );
    assert.equal(storage.getItem(LEGACY_STORAGE_KEY), null);
  }
});

test('does not resurrect v1 when an existing v2 store is invalid', () => {
  const storage = createMemoryStorage();
  storage.setItem(V2_STORAGE_KEY, JSON.stringify({ version: 999, data: {} }));
  storage.setItem(LEGACY_STORAGE_KEY, JSON.stringify(createVersionOneStore()));

  const repository = createMockRepository(storage);

  assert.equal(repository.read().projects[0].name, createSeedData().projects[0].name);
  assert.equal(JSON.parse(storage.getItem(V2_STORAGE_KEY)).version, 2);
  assert.equal(storage.getItem(LEGACY_STORAGE_KEY), null);
});

test('keeps v1 intact when writing migrated v2 data fails', () => {
  const storage = createMemoryStorage();
  const legacyData = JSON.stringify(createVersionOneStore());
  storage.setItem(LEGACY_STORAGE_KEY, legacyData);
  const setItem = storage.setItem;
  let v2Writes = 0;
  storage.setItem = (key, value) => {
    if (key === V2_STORAGE_KEY && ++v2Writes === 1) {
      throw new Error('storage quota exceeded');
    }
    setItem(key, value);
  };

  assert.throws(() => createMockRepository(storage), /storage quota exceeded/);
  assert.equal(v2Writes, 1);
  assert.equal(storage.getItem(LEGACY_STORAGE_KEY), legacyData);
});

test('keeps v1 intact when writing seed v2 for invalid-v2 recovery fails', () => {
  const storage = createMemoryStorage();
  const legacyData = JSON.stringify(createVersionOneStore());
  storage.setItem(V2_STORAGE_KEY, JSON.stringify({ version: 999, data: {} }));
  storage.setItem(LEGACY_STORAGE_KEY, legacyData);
  const setItem = storage.setItem;
  let v2Writes = 0;
  storage.setItem = (key, value) => {
    if (key === V2_STORAGE_KEY && ++v2Writes === 1) {
      throw new Error('storage quota exceeded');
    }
    setItem(key, value);
  };

  assert.throws(() => createMockRepository(storage), /storage quota exceeded/);
  assert.equal(v2Writes, 1);
  assert.equal(storage.getItem(LEGACY_STORAGE_KEY), legacyData);
});

test('keeps v1 intact when reset cannot write the clean v2 seed', () => {
  const storage = createMemoryStorage();
  const repository = createMockRepository(storage);
  const legacyData = JSON.stringify(createVersionOneStore());
  storage.setItem(LEGACY_STORAGE_KEY, legacyData);
  storage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'demo-session-token');
  const setItem = storage.setItem;
  let failWrites = false;
  storage.setItem = (key, value) => {
    if (failWrites && key === V2_STORAGE_KEY) {
      throw new Error('storage quota exceeded');
    }
    setItem(key, value);
  };
  failWrites = true;

  assert.throws(() => repository.reset(), /storage quota exceeded/);
  assert.equal(storage.getItem(LEGACY_STORAGE_KEY), legacyData);
  assert.equal(storage.getItem(ACCESS_TOKEN_STORAGE_KEY), 'demo-session-token');
});

test('discards invalid preference rows without reseeding valid project data', () => {
  const storage = createMemoryStorage();
  const store = { version: 2, data: createSeedData() };
  store.data.projects[0].name = 'Preserved v2 project';
  store.data.defaultBoardPreferences = [
    { userId: 'missing-user', projectId: 1, boardId: 11 },
    { userId: store.data.users[0].id, projectId: 1, boardId: 12 },
    { userId: store.data.users[0].id, projectId: 1, boardId: 11 },
    { userId: store.data.users[0].id, projectId: 999, boardId: 11 },
  ];
  storage.setItem(V2_STORAGE_KEY, JSON.stringify(store));

  const repository = createMockRepository(storage);
  const stored = JSON.parse(storage.getItem(V2_STORAGE_KEY));

  assert.equal(repository.read().projects[0].name, 'Preserved v2 project');
  assert.deepEqual(stored.data.defaultBoardPreferences, [
    { userId: store.data.users[0].id, projectId: 1, boardId: 12 },
  ]);

  const storageWithFailedSanitizationWrite = createMemoryStorage();
  const recoverableStore = { version: 2, data: createSeedData() };
  recoverableStore.data.projects[0].name = 'Preserved after preference cleanup failure';
  recoverableStore.data.defaultBoardPreferences = [
    { userId: 'missing-user', projectId: 1, boardId: 11 },
  ];
  storageWithFailedSanitizationWrite.setItem(
    V2_STORAGE_KEY,
    JSON.stringify(recoverableStore),
  );
  const setItem = storageWithFailedSanitizationWrite.setItem;
  let sanitizationWrites = 0;
  storageWithFailedSanitizationWrite.setItem = (key, value) => {
    if (key === V2_STORAGE_KEY && ++sanitizationWrites === 1) {
      throw new Error('storage quota exceeded');
    }
    setItem(key, value);
  };

  assert.throws(
    () => createMockRepository(storageWithFailedSanitizationWrite),
    /storage quota exceeded/,
  );
  assert.equal(sanitizationWrites, 1);
  assert.equal(
    JSON.parse(storageWithFailedSanitizationWrite.getItem(V2_STORAGE_KEY)).data.projects[0].name,
    'Preserved after preference cleanup failure',
  );
});

test('reset clears saved defaults and legacy storage while preserving the access token', () => {
  const storage = createMemoryStorage();
  const repository = createMockRepository(storage);
  storage.setItem(LEGACY_STORAGE_KEY, JSON.stringify(createVersionOneStore()));
  storage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'demo-session-token');
  repository.transact((draft) => {
    draft.defaultBoardPreferences = [
      { userId: draft.users[0].id, projectId: 1, boardId: 11 },
    ];
  });

  repository.reset();
  const resetStore = JSON.parse(storage.getItem(V2_STORAGE_KEY));

  assert.deepEqual(resetStore.data.defaultBoardPreferences, []);
  assert.equal(storage.getItem(LEGACY_STORAGE_KEY), null);
  assert.equal(storage.getItem(ACCESS_TOKEN_STORAGE_KEY), 'demo-session-token');
});
