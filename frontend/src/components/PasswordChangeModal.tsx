import { useId, useState, type FormEvent } from 'react';
import Modal from './Modal';
import PasswordRules from './PasswordRules';
import { changePassword } from '../api/profile';
import { getPasswordRules } from '../lib/passwordRules';

const errorCode = (error: unknown) =>
  error instanceof Error ? (error.cause as { code?: string } | undefined)?.code : undefined;

/**
 * CM-08~12 비밀번호 변경 폼 모달. The server ends every session on success,
 * so the caller decides where to send the user (S1).
 */
export default function PasswordChangeModal({
  open,
  onClose,
  onChanged,
}: {
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const titleId = useId();
  const rulesId = useId();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<{ field: 'current' | 'next'; message: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const mismatch = confirm !== '' && confirm !== next;
  const invalid = !current || !getPasswordRules(next).valid || next.length > 64 || confirm !== next;

  function close() {
    setCurrent('');
    setNext('');
    setConfirm('');
    setError(null);
    onClose();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (invalid || saving) return;
    setSaving(true);
    setError(null);
    try {
      await changePassword(current, next);
      setCurrent('');
      setNext('');
      setConfirm('');
      onChanged();
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '다시 시도해주세요.';
      setError({ field: errorCode(reason) === 'AUTH_015' ? 'current' : 'next', message });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open={open} labelledBy={titleId}>
      <form className="password-change" onSubmit={submit} noValidate>
        <h2 className="cm-title" id={titleId}>
          비밀번호 변경
        </h2>
        <label className="password-change-field">
          현재 비밀번호
          <input
            className="ui-input"
            type="password"
            autoComplete="current-password"
            value={current}
            aria-invalid={error?.field === 'current'}
            onChange={(event) => {
              setCurrent(event.target.value);
              setError(null);
            }}
          />
        </label>
        <label className="password-change-field">
          새 비밀번호
          <input
            className="ui-input"
            type="password"
            autoComplete="new-password"
            value={next}
            aria-describedby={rulesId}
            aria-invalid={error?.field === 'next'}
            onChange={(event) => {
              setNext(event.target.value);
              setError(null);
            }}
          />
        </label>
        <PasswordRules password={next} id={rulesId} />
        <label className="password-change-field">
          새 비밀번호 확인
          <input
            className="ui-input"
            type="password"
            autoComplete="new-password"
            value={confirm}
            aria-invalid={mismatch}
            onChange={(event) => setConfirm(event.target.value)}
          />
        </label>
        {mismatch && <p className="password-change-error">새 비밀번호가 일치하지 않아요</p>}
        {error && (
          <p className="password-change-error" role="alert">
            {error.message}
          </p>
        )}
        <p className="cm-description password-change-note">
          변경하면 모든 기기에서 로그아웃되고, 새 비밀번호로 다시 로그인해야 해요.
        </p>
        <div className="cm-actions">
          <button
            type="button"
            className="ui-button ui-button--secondary"
            disabled={saving}
            onClick={close}
          >
            취소
          </button>
          <button
            type="submit"
            className="ui-button cm-confirm cm-confirm--normal"
            disabled={invalid || saving}
          >
            {saving ? '변경 중…' : '변경하기'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
