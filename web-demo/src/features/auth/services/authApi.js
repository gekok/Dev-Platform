import api from '../../../shared/services/axios.js';

export function validateLoginSession(session) {
  if (
    typeof session?.accessToken !== 'string'
    || session.accessToken.trim().length === 0
    || typeof session.tokenType !== 'string'
    || session.tokenType.toLowerCase() !== 'bearer'
  ) {
    throw new Error('The login response did not contain a valid Bearer token.');
  }

  return session;
}

export async function login(credentials) {
  const response = await api.post('/auth/login', credentials);
  return validateLoginSession(response.data);
}

export async function getCurrentUser() {
  const response = await api.get('/users/me');
  return response.data;
}
