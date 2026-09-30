import { useId } from 'react';
import Modal from './Modal';

/** CM-13 entry shell. Product catalogue and payment are a separate integration. */
export default function MatchingTicketModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const titleId = useId();
  return (
    <Modal open={open} labelledBy={titleId}>
      <h2 className="cm-title" id={titleId}>
        이용권 구매
      </h2>
      <div role="tablist" aria-label="이용권 종류">
        <button
          type="button"
          role="tab"
          aria-selected="true"
          aria-controls={`${titleId}-panel`}
          id={`${titleId}-tab`}
          className="ui-button ui-button--secondary"
        >
          소모형
        </button>
      </div>
      <div role="tabpanel" id={`${titleId}-panel`} aria-labelledby={`${titleId}-tab`}>
        <p className="cm-description">
          매칭 기회를 모두 사용했어요. 매칭을 시작하려면 이용권이 필요해요.
        </p>
        <p className="cm-description">이용권 구매 기능은 준비 중이에요.</p>
      </div>
      <button type="button" className="ui-button ui-button--primary" onClick={onClose}>
        홈으로 돌아가기
      </button>
    </Modal>
  );
}
