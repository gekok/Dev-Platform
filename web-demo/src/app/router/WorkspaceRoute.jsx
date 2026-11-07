import { Navigate, Outlet } from 'react-router-dom';
import { useCurrentUser } from '../../features/auth/hooks/useCurrentUser.js';
import { isSystemAdmin } from '../../features/auth/roles.js';

export default function WorkspaceRoute() {
  const { data: user } = useCurrentUser();

  if (isSystemAdmin(user)) {
    return <Navigate to="/admin" replace />;
  }

  return <Outlet />;
}
