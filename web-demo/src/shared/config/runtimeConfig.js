const SUPPORTED_MODES = new Set(['demo', 'backend']);

export class RuntimeConfigurationError extends Error {
  constructor(message) {
    super(message);
    this.name = 'RuntimeConfigurationError';
  }
}

function normalizeApiBaseUrl(value) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new RuntimeConfigurationError(
      'VITE_API_BASE_URL is required. Set it to /api/v1 or an absolute Gateway URL ending in /api/v1.',
    );
  }

  const baseUrl = value.trim().replace(/\/+$/, '');
  if (baseUrl.startsWith('/')) {
    if (baseUrl.startsWith('//') || baseUrl !== '/api/v1') {
      throw new RuntimeConfigurationError(
        'VITE_API_BASE_URL must be /api/v1 or an absolute HTTP(S) Gateway URL ending in /api/v1.',
      );
    }
    return baseUrl;
  }

  let parsed;
  try {
    parsed = new URL(baseUrl);
  } catch {
    throw new RuntimeConfigurationError(
      'VITE_API_BASE_URL must be /api/v1 or an absolute HTTP(S) Gateway URL ending in /api/v1.',
    );
  }

  if (!['http:', 'https:'].includes(parsed.protocol)
    || parsed.pathname.replace(/\/+$/, '') !== '/api/v1'
    || parsed.search
    || parsed.hash) {
    throw new RuntimeConfigurationError(
      'VITE_API_BASE_URL must be /api/v1 or an absolute HTTP(S) Gateway URL ending in /api/v1.',
    );
  }

  return baseUrl;
}

export function resolveRuntimeConfig(env = {}) {
  const mode = typeof env.VITE_APP_MODE === 'string'
    ? env.VITE_APP_MODE.trim().toLowerCase()
    : '';

  if (!SUPPORTED_MODES.has(mode)) {
    throw new RuntimeConfigurationError(
      'VITE_APP_MODE must be explicitly set to "demo" or "backend".',
    );
  }

  return Object.freeze({
    mode,
    apiBaseUrl: normalizeApiBaseUrl(env.VITE_API_BASE_URL),
  });
}

export function resolveApiProxyTarget(env = {}, { mode, command }) {
  const value = typeof env.VITE_API_PROXY_TARGET === 'string'
    ? env.VITE_API_PROXY_TARGET.trim()
    : '';

  if (!value) {
    const isRelativeApiUrl = typeof env.VITE_API_BASE_URL === 'string'
      && env.VITE_API_BASE_URL.trim().startsWith('/');
    if (mode === 'backend' && command === 'serve' && isRelativeApiUrl) {
      throw new RuntimeConfigurationError(
        'VITE_API_PROXY_TARGET is required for Backend mode when VITE_API_BASE_URL is relative. Point it to the Gateway, for example http://localhost:8080.',
      );
    }
    return undefined;
  }

  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new RuntimeConfigurationError(
      'VITE_API_PROXY_TARGET must be an absolute HTTP(S) Gateway origin.',
    );
  }

  if (!['http:', 'https:'].includes(parsed.protocol)
    || parsed.pathname !== '/' || parsed.search || parsed.hash) {
    throw new RuntimeConfigurationError(
      'VITE_API_PROXY_TARGET must be an absolute HTTP(S) Gateway origin without a path, query, or fragment.',
    );
  }

  return value.replace(/\/+$/, '');
}
