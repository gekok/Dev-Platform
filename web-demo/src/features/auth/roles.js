export const SYSTEM_ADMIN_ROLE = 'SYSTEM_ADMIN';

export function getSystemRoles(user) {
  const roles = user?.systemRoles ?? user?.roles ?? [];
  return Array.isArray(roles) ? roles.filter((role) => typeof role === 'string') : [];
}

export function hasSystemRole(user, role) {
  return getSystemRoles(user).includes(role);
}

export function isSystemAdmin(user) {
  return hasSystemRole(user, SYSTEM_ADMIN_ROLE);
}
