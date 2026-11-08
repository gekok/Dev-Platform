import axios from 'axios';
import { expireSession, getAccessToken } from '../../features/auth/session.js';
import { normalizeApiError } from './apiErrors.js';

const api = axios.create({
  headers: { 'Content-Type': 'application/json' },
});

let configured = false;

function hasAuthorizationHeader(config) {
  const headers = config?.headers;
  return Boolean(headers?.get?.('Authorization') ?? headers?.Authorization);
}

export function configureApiBaseUrl(baseURL) {
  if (typeof baseURL !== 'string' || baseURL.trim().length === 0) {
    throw new TypeError('API base URL must be configured before the app starts');
  }
  api.defaults.baseURL = baseURL;
  configured = true;
}

api.interceptors.request.use((config) => {
  if (!configured) {
    throw new Error('API client has not been configured. Resolve runtime configuration before rendering the app.');
  }

  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const normalized = normalizeApiError(error);
    error.apiError = normalized;
    if (normalized.status === 401 && hasAuthorizationHeader(error?.config)) {
      expireSession();
    }
    return Promise.reject(error);
  },
);

export default api;
