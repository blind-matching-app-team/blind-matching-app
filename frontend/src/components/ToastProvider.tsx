import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { ToastContext } from './useToast';
import './feedback.css';

function Toast({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    // A manual popover also appears above native modal dialogs without taking focus.
    ref.current?.showPopover();
    const timer = window.setTimeout(onDismiss, 3000);
    return () => window.clearTimeout(timer);
  }, [onDismiss]);
  return createPortal(
    <div
      ref={ref}
      popover="manual"
      className="cm-toast"
      role="status"
      aria-live="polite"
      aria-atomic="true"
    >
      {message}
    </div>,
    document.body,
  );
}

export default function ToastProvider({ children }: { children: ReactNode }) {
  const nextId = useRef(0);
  const [toast, setToast] = useState<{ id: number; message: string } | null>(null);
  const showToast = useCallback((message: string) => {
    setToast({ id: ++nextId.current, message });
  }, []);
  const dismiss = useCallback(() => setToast(null), []);
  return (
    <ToastContext.Provider value={showToast}>
      {children}
      {toast && <Toast key={toast.id} message={toast.message} onDismiss={dismiss} />}
    </ToastContext.Provider>
  );
}
