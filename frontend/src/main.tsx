import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import './index.css';
import ToastProvider from './components/ToastProvider';

async function enableMocking() {
  if (!import.meta.env.DEV) {
    return;
  }

  const { worker } = await import('./mocks/browser');

  await worker.start({
    onUnhandledRequest: 'bypass',
  });
}

enableMocking().then(async () => {
  // Initial route loaders must run after MSW starts, including emailed reset links.
  const { default: router } = await import('./router');
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </React.StrictMode>,
  );
});
