import { useId } from 'react';
import Modal from './Modal';

export type ConfirmModalContent = {
  title: string;
  description?: string;
  variant?: 'normal' | 'danger';
  confirmLabel: string;
  cancelLabel?: string;
};

type ConfirmModalProps = ConfirmModalContent & {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

/** UI only: callers own the action; confirming invokes it once, then closes. */
export default function ConfirmModal({
  open,
  title,
  description,
  variant = 'normal',
  confirmLabel,
  cancelLabel = '취소',
  onClose,
  onConfirm,
}: ConfirmModalProps) {
  const titleId = useId();
  const descriptionId = useId();
  return (
    <Modal open={open} labelledBy={titleId} describedBy={description ? descriptionId : undefined}>
      <h2 className="cm-title" id={titleId}>
        {title}
      </h2>
      {description && (
        <p className="cm-description" id={descriptionId}>
          {description}
        </p>
      )}
      <div className="cm-actions">
        <button
          type="button"
          className="ui-button ui-button--secondary cm-cancel"
          onClick={onClose}
        >
          {cancelLabel}
        </button>
        <button
          type="button"
          className={`ui-button cm-confirm cm-confirm--${variant}`}
          onClick={() => {
            onConfirm();
            onClose();
          }}
        >
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
