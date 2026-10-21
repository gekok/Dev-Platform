import { setupServer } from 'msw/node';
import { createMockRepository } from './data/databaseRepository.js';
import { DEMO_OWNER_ID } from './data/seed.js';
import { DEMO_ACCESS_TOKEN } from './handlers/auth.js';
import { createHandlers } from './handlers/index.js';

export const OWNER_TOKEN = DEMO_ACCESS_TOKEN;
export const MEMBER_TOKEN = 'demo-member-token';
export const OUTSIDER_TOKEN = 'demo-outsider-token';
export const MEMBER_ID = 'a1000000-0000-4000-8000-000000000002';
export const OUTSIDER_ID = 'a1000000-0000-4000-8000-000000000005';

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

export async function withMockServer(run) {
  const repository = createMockRepository(createMemoryStorage());
  const usersByToken = new Map([
    [OWNER_TOKEN, DEMO_OWNER_ID],
    [MEMBER_TOKEN, MEMBER_ID],
    [OUTSIDER_TOKEN, OUTSIDER_ID],
  ]);
  const server = setupServer(
    ...createHandlers(repository, (token) => usersByToken.get(token)),
  );
  server.listen({ onUnhandledRequest: 'error' });

  try {
    await run({ repository, server });
  } finally {
    server.close();
  }
}

export function apiUrl(path) {
  return `http://localhost/api/v1${path}`;
}

export function bearer(token) {
  return { Authorization: `Bearer ${token}` };
}
