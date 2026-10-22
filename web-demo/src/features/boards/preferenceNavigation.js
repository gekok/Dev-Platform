export function getPreferenceErrorClearPath(location, expectedProjectId) {
  if (!location?.state?.defaultPreferenceError) return null;

  const projectMatch = /^\/projects\/(\d+)(?:\/|$)/.exec(location.pathname ?? '');
  if (!projectMatch || Number(projectMatch[1]) !== Number(expectedProjectId)) return null;

  return location.pathname;
}
