import { http, HttpResponse } from 'msw';
import { DEMO_CREDENTIALS, DEMO_OWNER_ID } from '../data/seed.js';
import { authenticateRequest, errorResponse, readJson } from './utils.js';

export const DEMO_ACCESS_TOKEN = 'demo-owner-token';

export function createAuthHandlers(repository, resolveUserFromToken) {
  return [
    http.post('*/api/v1/auth/login', async ({ request }) => {
      const credentials = await readJson(request);
      if (!credentials || typeof credentials !== 'object') {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Login credentials are required');
      }
      if (
        credentials.email !== DEMO_CREDENTIALS.email ||
        credentials.password !== DEMO_CREDENTIALS.password
      ) {
        return errorResponse(request, 401, 'UNAUTHORIZED', 'Invalid email or password');
      }

      return HttpResponse.json({
        accessToken: DEMO_ACCESS_TOKEN,
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

      const { id, email, fullName } = authentication.user;
      return HttpResponse.json({ id, email, fullName });
    }),
  ];
}

export function resolveDemoUserFromToken(token) {
  return token === DEMO_ACCESS_TOKEN ? DEMO_OWNER_ID : null;
}
