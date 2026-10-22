import { ACCESS_TOKEN_STORAGE_KEY } from '../../shared/constants/storageKeys.js';

function getStorage() {
  return typeof window === 'undefined' ? null : window.localStorage;
}

export function getAccessToken() {
  return getStorage()?.getItem(ACCESS_TOKEN_STORAGE_KEY) ?? null;
}

export function setAccessToken(token) {
  if (typeof token !== 'string' || token.trim().length === 0) {
    throw new TypeError('Access token must be a non-empty string');
  }

  getStorage()?.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
}

export function clearSession() {
  getStorage()?.removeItem(ACCESS_TOKEN_STORAGE_KEY);
}
