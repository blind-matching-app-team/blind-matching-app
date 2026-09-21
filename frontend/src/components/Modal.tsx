import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import './feedback.css';

type ModalProps = {
  open: boolean;
  labelledBy: string;
  describedBy?: string;
  children: ReactNode;
};

/** Native top-layer dialog supplies focus trapping and makes the background inert. */
export default function Modal({ open, labelledBy, describedBy, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    if (!open) return;
    const dialog = ref.current!;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [open]);

  return createPortal(
    <dialog
      ref={ref}
      className="ui-enter cm-modal"
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      onCancel={(event) => event.preventDefault()}
    >
      {open && children}
    </dialog>,
    document.body,
  );
}
