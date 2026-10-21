import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from '../layout/AppLayout.jsx';
import ProtectedRoute from './ProtectedRoute.jsx';
import LoginPage from '../../features/auth/pages/LoginPage.jsx';
import ProjectListPage from '../../features/projects/pages/ProjectListPage.jsx';
import ProjectEntryPage from '../../features/projects/pages/ProjectEntryPage.jsx';
import BoardPage from '../../features/boards/pages/BoardPage.jsx';
import NotFoundPage from '../../shared/components/NotFoundPage.jsx';

const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <ProtectedRoute />,
    children: [{
      path: '/',
      element: <AppLayout />,
      children: [
        { index: true, element: <Navigate to="/projects" replace /> },
        { path: 'projects', element: <ProjectListPage /> },
        { path: 'projects/:projectId', element: <ProjectEntryPage /> },
        { path: 'projects/:projectId/boards/:boardId', element: <BoardPage /> },
        { path: '*', element: <NotFoundPage /> },
      ],
    }],
  },
]);

export default router;
