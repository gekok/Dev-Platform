import { setupWorker } from 'msw/browser';
import { resolveDemoUserFromToken } from './handlers/auth.js';
import { createHandlers } from './handlers/index.js';
import { mockRepository } from './data/mockRepository.js';

export const worker = setupWorker(
  ...createHandlers(mockRepository, resolveDemoUserFromToken),
);
