import { passwordResetLoader } from './passwordResetLoader';
import { createBrowserRouter } from 'react-router-dom';
import ProtectedRoute from '../components/ProtectedRoute';
import AuthPage from '../pages/AuthPage';
import ChatListPage from '../pages/ChatListPage';
import ChatPage from '../pages/ChatPage';
import HomePage from '../pages/HomePage';
import NotificationPage from '../pages/NotificationPage';
import ProfilePage from '../pages/ProfilePage';
import SuspendedPage from '../pages/SuspendedPage';
import PasswordResetPage, { ForgotPasswordPage } from '../pages/PasswordResetPage';

const router = createBrowserRouter([
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  {
    path: '/reset-password',
    loader: passwordResetLoader,
    element: <PasswordResetPage />,
    hydrateFallbackElement: (
      <main className="auth-page-shell">
        <div className="ui-enter auth-card" role="status">
          재설정 링크를 확인하고 있어요…
        </div>
      </main>
    ),
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        path: '/',
        element: <HomePage />,
      },
      {
        path: '/chat',
        element: <ChatListPage />,
      },
      {
        path: '/chat/:id',
        element: <ChatPage />,
      },
      {
        path: '/matching/waiting',
        element: <div style={{ padding: 32 }}>매칭 대기 화면 (S9)</div>,
      },
      {
        path: '/notifications',
        element: <NotificationPage />,
      },
      {
        path: '/profile',
        element: <ProfilePage />,
      },
      {
        path: '/profile/edit',
        element: <div style={{ padding: 32 }}>프로필 수정 화면 (S3)</div>,
      },
      {
        path: '/profile/preferences',
        element: <div style={{ padding: 32 }}>매칭 선호조건 화면 (S4)</div>,
      },
    ],
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
