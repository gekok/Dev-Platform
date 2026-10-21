import { createAuthHandlers } from './auth.js';
import { createProjectHandlers } from './projects.js';
import { createBoardHandlers } from './boards.js';
import { createCardHandlers } from './cards.js';
import { createNotificationHandlers } from './notifications.js';
import { createPreferenceHandlers } from './preferences.js';

export function createHandlers(repository, resolveUserFromToken) {
  if (typeof resolveUserFromToken !== 'function') {
    throw new TypeError('Mock handlers require a bearer-token resolver');
  }

  return [
    ...createAuthHandlers(repository, resolveUserFromToken),
    ...createProjectHandlers(repository, resolveUserFromToken),
    ...createBoardHandlers(repository, resolveUserFromToken),
    ...createCardHandlers(repository, resolveUserFromToken),
    ...createNotificationHandlers(repository, resolveUserFromToken),
    ...createPreferenceHandlers(repository, resolveUserFromToken),
  ];
}
