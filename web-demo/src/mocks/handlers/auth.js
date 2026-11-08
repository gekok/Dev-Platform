import { http, HttpResponse } from 'msw';
import {
  DEMO_ADMIN_CREDENTIALS,
  DEMO_ADMIN_ID,
  DEMO_CREDENTIALS,
  DEMO_OWNER_ID,
} from '../data/seed.js';
import { authenticateRequest, errorResponse, readJson } from './utils.js';

export const DEMO_ACCESS_TOKEN = 'demo-owner-token';
export const DEMO_ADMIN_ACCESS_TOKEN = 'demo-admin-token';

const DEMO_ACCOUNTS = [
  { credentials: DEMO_CREDENTIALS, token: DEMO_ACCESS_TOKEN },
  { credentials: DEMO_ADMIN_CREDENTIALS, token: DEMO_ADMIN_ACCESS_TOKEN },
];

export function createAuthHandlers(repository, resolveUserFromToken) {
  return [
    http.post('*/api/v1/auth/login', async ({ request }) => {
      const credentials = await readJson(request);
      if (!credentials || typeof credentials !== 'object') {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Login credentials are required');
      }
      const account = DEMO_ACCOUNTS.find(
        ({ credentials: expected }) =>
          credentials.email === expected.email &&
          credentials.password === expected.password,
      );
      if (!account) {
        return errorResponse(request, 401, 'UNAUTHORIZED', 'Invalid email or password');
      }

      return HttpResponse.json({
        accessToken: account.token,
        tokenType: 'Bearer',
      });
    }),
    http.get('*/api/v1/users/me', ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;

      const { id, email, fullName, systemRoles } = authentication.user;
      return HttpResponse.json({
        id,
        email,
        fullName,
        ...(systemRoles ? { systemRoles } : {}),
      });
    }),
  ];
}

export function resolveDemoUserFromToken(token) {
  if (token === DEMO_ACCESS_TOKEN) return DEMO_OWNER_ID;
  if (token === DEMO_ADMIN_ACCESS_TOKEN) return DEMO_ADMIN_ID;
  return null;
}
