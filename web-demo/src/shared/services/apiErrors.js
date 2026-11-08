function responseMessage(error) {
  const message = error?.response?.data?.message;
  return typeof message === 'string' && message.trim().length > 0
    ? message.trim()
    : null;
}

function messageForStatus(status, serverMessage, requestUrl) {
  if (status === 401) {
    return requestUrl?.replace(/\/$/, '').endsWith('/auth/login')
      ? serverMessage ?? 'Email or password is incorrect.'
      : 'Your session has expired. Sign in again.';
  }
  if (status === 403) return 'You do not have permission to perform this action.';
  if (status === 404) return serverMessage ?? 'The requested resource was not found.';
  if (status === 408) return 'The request timed out. Try again.';
  if (status === 429) return 'Too many requests. Wait a moment and try again.';
  if (status >= 500) return 'The service is temporarily unavailable. Try again later.';
  if (status >= 400) return serverMessage ?? `The request failed (${status}).`;
  return serverMessage ?? 'The request could not be completed.';
}

export function normalizeApiError(error) {
  const response = error?.response;
  const status = Number.isInteger(response?.status) ? response.status : null;
  const serverMessage = responseMessage(error);
  const networkFailure = status === null;
  const kind = status === 401
    ? 'authentication'
    : status === 403
      ? 'permission'
      : networkFailure
        ? 'network'
        : status >= 500
          ? 'server'
          : 'request';

  return Object.freeze({
    kind,
    status,
    code: typeof response?.data?.code === 'string'
      ? response.data.code
      : status === null
        ? error?.code ?? 'NETWORK_ERROR'
        : `HTTP_${status}`,
    message: networkFailure
      ? error?.code === 'ECONNABORTED'
        ? 'The request timed out. Check your connection and try again.'
        : 'Could not reach the API. Check your connection and try again.'
      : messageForStatus(status, serverMessage, error?.config?.url),
    retryable: networkFailure || status >= 500 || status === 408 || status === 429,
  });
}

export function getApiErrorMessage(error, fallback = 'The request could not be completed.') {
  if (error?.apiError?.message) return error.apiError.message;
  if (error?.response || error?.isAxiosError) return normalizeApiError(error).message;
  return fallback;
}

export function getApiErrorStatus(error) {
  return error?.apiError?.status ?? error?.response?.status ?? null;
}

export function isRetryableApiError(error) {
  if (typeof error?.apiError?.retryable === 'boolean') return error.apiError.retryable;
  if (error?.response) return normalizeApiError(error).retryable;
  return Boolean(error?.isAxiosError);
}
