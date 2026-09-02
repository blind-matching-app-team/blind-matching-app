import { createBrowserRouter } from 'react-router-dom';
import AuthPage from '../pages/AuthPage';
import SuspendedPage from '../pages/SuspendedPage';

const router = createBrowserRouter([
  {
    path: '/',
    element: <AuthPage />,
  },
  {
    path: '/login',
    element: <AuthPage />,
  },
  {
    path: '/signup',
    element: <AuthPage />,
  },
  {
    path: '/suspended',
    element: <SuspendedPage />,
  },
]);

export default router;
