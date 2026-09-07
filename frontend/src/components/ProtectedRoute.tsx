import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { hasActiveSession } from '../lib/auth';

export default function ProtectedRoute() {
  const location = useLocation();

  if (!hasActiveSession()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
