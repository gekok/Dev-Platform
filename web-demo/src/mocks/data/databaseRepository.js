import { createSeedData } from './seed.js';
import {
  DATABASE_STORAGE_KEY,
  LEGACY_DATABASE_STORAGE_KEY,
} from '../../shared/constants/storageKeys.js';

export { DATABASE_STORAGE_KEY, LEGACY_DATABASE_STORAGE_KEY };
const STORE_VERSION = 2;
const LEGACY_STORE_VERSION = 1;
const RESOURCE_TYPES = [
  'project',
  'board',
  'column',
  'card',
  'comment',
  'notification',
];
const RESOURCE_COLLECTIONS = {
  project: 'projects',
  board: 'boards',
  column: 'columns',
  card: 'cards',
  comment: 'comments',
  notification: 'notifications',
};

function clone(value) {
  return structuredClone(value);
}

function createStore(data = createSeedData()) {
  return { version: STORE_VERSION, data };
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasText(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function hasTimestamp(value) {
  return typeof value === 'string' && Number.isFinite(Date.parse(value));
}

function isPositiveId(value) {
  return Number.isSafeInteger(value) && value > 0;
}

function isPosition(value) {
  return Number.isSafeInteger(value) && value >= 0;
}

function hasUniqueValues(values) {
  return new Set(values).size === values.length;
}

function isValidStore(store, expectedVersion = STORE_VERSION) {
  if (store?.version !== expectedVersion || !isRecord(store.data)) return false;

  const hasCollections = Object.values(RESOURCE_COLLECTIONS).every(
    (collection) => Array.isArray(store.data[collection]),
  );
  const hasUsers = Array.isArray(store.data.users);
  const hasMemberships = Array.isArray(store.data.projectMembers);
  if (!hasCollections || !hasUsers || !hasMemberships || !isRecord(store.data.nextIds)) {
    return false;
  }

  const collections = Object.fromEntries(
    Object.entries(RESOURCE_COLLECTIONS).map(([type, collection]) => [
      type,
      store.data[collection],
    ]),
  );
  const hasValidResourceIds = Object.values(collections).every(
    (items) =>
      items.every((item) => isRecord(item) && isPositiveId(item.id)) &&
      hasUniqueValues(items.map((item) => item.id)),
  );
  const hasValidUsers = store.data.users.every(
    (user) =>
      isRecord(user) &&
      typeof user.id === 'string' &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        user.id,
      ) &&
      hasText(user.email) &&
      hasText(user.fullName),
  );
  const hasUniqueUserIds = hasUniqueValues(store.data.users.map((user) => user.id));
  const userIds = new Set(store.data.users.map((user) => user?.id));
  const projectsById = new Map(store.data.projects.map((project) => [project?.id, project]));
  const boardsById = new Map(store.data.boards.map((board) => [board?.id, board]));
  const columnsById = new Map(store.data.columns.map((column) => [column?.id, column]));
  const cardsById = new Map(store.data.cards.map((card) => [card?.id, card]));
  const memberKeys = store.data.projectMembers.map(
    (membership) => `${membership?.projectId}:${membership?.userId}`,
  );
  const memberSet = new Set(memberKeys);
  const hasValidMemberships = store.data.projectMembers.every(
    (membership) =>
      isRecord(membership) &&
      projectsById.has(membership.projectId) &&
      userIds.has(membership.userId) &&
      ['OWNER', 'MEMBER'].includes(membership.role),
  ) && hasUniqueValues(memberKeys);
  const isProjectMember = (projectId, userId) =>
    memberSet.has(`${projectId}:${userId}`);
  const hasValidProjects = store.data.projects.every(
    (project) =>
      isRecord(project) &&
      hasText(project.name) &&
      userIds.has(project.ownerId) &&
      hasTimestamp(project.createdAt) &&
      store.data.projectMembers.some(
        (membership) =>
          membership.projectId === project.id &&
          membership.userId === project.ownerId &&
          membership.role === 'OWNER',
      ),
  );
  const hasValidBoards = store.data.boards.every(
    (board) =>
      isRecord(board) &&
      projectsById.has(board.projectId) &&
      hasText(board.name) &&
      typeof board.key === 'string' &&
      /^[A-Z0-9]{2,20}$/.test(board.key) &&
      userIds.has(board.ownerId) &&
      isProjectMember(board.projectId, board.ownerId) &&
      hasTimestamp(board.createdAt),
  ) && hasUniqueValues(store.data.boards.map((board) => board.key));
  const hasValidColumns = store.data.columns.every(
    (column) =>
      isRecord(column) &&
      boardsById.has(column.boardId) &&
      hasText(column.name) &&
      isPosition(column.position),
  );
  const hasValidCards = store.data.cards.every((card) => {
    const board = boardsById.get(card?.boardId);
    const column = columnsById.get(card?.columnId);
    const project = board && projectsById.get(board.projectId);

    return (
      isRecord(card) &&
      Boolean(board && column && project) &&
      column.boardId === card.boardId &&
      hasText(card.title) &&
      (typeof card.description === 'string' || card.description === null) &&
      ['TODO', 'IN_PROGRESS', 'DONE'].includes(card.status) &&
      isPosition(card.position) &&
      (card.assigneeId === null || isProjectMember(project.id, card.assigneeId)) &&
      (typeof card.dueDate === 'string' || card.dueDate === null)
    );
  });
  const hasValidComments = store.data.comments.every((comment) => {
    const card = cardsById.get(comment?.cardId);
    const board = card && boardsById.get(card.boardId);
    const project = board && projectsById.get(board.projectId);

    return (
      isRecord(comment) &&
      Boolean(project) &&
      isProjectMember(project.id, comment.authorId) &&
      hasText(comment.content) &&
      hasTimestamp(comment.createdAt) &&
      Array.isArray(comment.mentionedUserIds) &&
      comment.mentionedUserIds.every((userId) => isProjectMember(project.id, userId)) &&
      hasUniqueValues(comment.mentionedUserIds)
    );
  });
  const hasValidNotifications = store.data.notifications.every((notification) => {
    const card = cardsById.get(notification?.cardId);
    const board = card && boardsById.get(card.boardId);
    const project = board && projectsById.get(board.projectId);

    return (
      isRecord(notification) &&
      Boolean(project) &&
      notification.boardId === board.id &&
      isProjectMember(project.id, notification.recipientId) &&
      ['ASSIGNED', 'MENTIONED', 'MOVED'].includes(notification.type) &&
      hasText(notification.title) &&
      typeof notification.read === 'boolean' &&
      hasTimestamp(notification.createdAt)
    );
  });
  const hasValidCounters = RESOURCE_TYPES.every((type) => {
    const nextId = store.data.nextIds[type];
    const collection = collections[type];
    const maxId = collection.reduce((currentMax, item) => Math.max(currentMax, item.id), 0);

    return isPositiveId(nextId) && nextId > maxId;
  });

  return (
    hasValidResourceIds &&
    hasValidUsers &&
    hasUniqueUserIds &&
    hasValidMemberships &&
    hasValidProjects &&
    hasValidBoards &&
    hasValidColumns &&
    hasValidCards &&
    hasValidComments &&
    hasValidNotifications &&
    hasValidCounters
  );
}

function saveStore(storage, store) {
  storage.setItem(DATABASE_STORAGE_KEY, JSON.stringify(store));
}

function sanitizeDefaultBoardPreferences(data) {
  if (!Array.isArray(data.defaultBoardPreferences)) return [];

  const userIds = new Set(data.users.map((user) => user.id));
  const projectIds = new Set(data.projects.map((project) => project.id));
  const boardsById = new Map(data.boards.map((board) => [board.id, board]));
  const membershipKeys = new Set(
    data.projectMembers.map((membership) => `${membership.projectId}:${membership.userId}`),
  );
  const seenKeys = new Set();

  return data.defaultBoardPreferences.filter((preference) => {
    if (
      !isRecord(preference) ||
      !hasText(preference.userId) ||
      !userIds.has(preference.userId) ||
      !isPositiveId(preference.projectId) ||
      !projectIds.has(preference.projectId) ||
      !isPositiveId(preference.boardId) ||
      !membershipKeys.has(`${preference.projectId}:${preference.userId}`) ||
      boardsById.get(preference.boardId)?.projectId !== preference.projectId
    ) {
      return false;
    }

    const key = `${preference.userId}:${preference.projectId}`;
    if (seenKeys.has(key)) return false;
    seenKeys.add(key);
    return true;
  });
}

function normalizeDefaultBoardPreferences(store) {
  const current = store.data.defaultBoardPreferences;
  const normalized = sanitizeDefaultBoardPreferences(store.data);
  const changed =
    !Array.isArray(current) || JSON.stringify(current) !== JSON.stringify(normalized);
  store.data.defaultBoardPreferences = normalized;
  return changed;
}

function removeLegacyStore(storage) {
  if (storage.getItem(LEGACY_DATABASE_STORAGE_KEY) !== null) {
    storage.removeItem(LEGACY_DATABASE_STORAGE_KEY);
  }
}

function loadStore(storage) {
  const rawStore = storage.getItem(DATABASE_STORAGE_KEY);
  if (rawStore !== null) {
    let store = null;
    try {
      store = JSON.parse(rawStore);
    } catch {
      // Invalid v2 data is replaced from the seed, never from the legacy copy.
    }

    if (isValidStore(store)) {
      if (normalizeDefaultBoardPreferences(store)) saveStore(storage, store);
      removeLegacyStore(storage);
      return store;
    }
  } else {
    const rawLegacyStore = storage.getItem(LEGACY_DATABASE_STORAGE_KEY);
    if (rawLegacyStore !== null) {
      let legacyStore = null;
      try {
        legacyStore = JSON.parse(rawLegacyStore);
      } catch {
        // Invalid legacy data falls back to the clean v2 seed below.
      }

      if (isValidStore(legacyStore, LEGACY_STORE_VERSION)) {
        const migratedStore = createStore({
          ...legacyStore.data,
          defaultBoardPreferences: [],
        });
        saveStore(storage, migratedStore);
        removeLegacyStore(storage);
        return migratedStore;
      }
    }
  }

  const seededStore = createStore();
  saveStore(storage, seededStore);
  removeLegacyStore(storage);
  return seededStore;
}

export function createMockRepository(storage) {
  if (
    !storage ||
    typeof storage.getItem !== 'function' ||
    typeof storage.setItem !== 'function' ||
    typeof storage.removeItem !== 'function'
  ) {
    throw new TypeError('Mock repository requires getItem/setItem/removeItem storage methods');
  }

  loadStore(storage);

  return {
    read() {
      return clone(loadStore(storage).data);
    },

    transact(mutator) {
      if (typeof mutator !== 'function') {
        throw new TypeError('Mock repository transaction requires a callback');
      }

      const draft = clone(loadStore(storage).data);
      const allocateId = (type) => {
        if (!RESOURCE_TYPES.includes(type)) {
          throw new TypeError(`Unknown mock resource ID type: ${type}`);
        }

        const id = draft.nextIds[type];
        if (!Number.isSafeInteger(id) || id <= 0) {
          throw new RangeError(`Invalid next ID for mock resource: ${type}`);
        }
        draft.nextIds[type] = id + 1;
        return id;
      };

      const result = mutator(draft, allocateId);
      const clonedResult = clone(result);
      draft.defaultBoardPreferences = sanitizeDefaultBoardPreferences(draft);
      const nextStore = createStore(draft);
      if (!isValidStore(nextStore)) {
        throw new TypeError('Mock repository transaction produced invalid data');
      }
      saveStore(storage, nextStore);
      return clonedResult;
    },

    reset() {
      const seededStore = createStore();
      saveStore(storage, seededStore);
      removeLegacyStore(storage);
      return clone(seededStore.data);
    },
  };
}
