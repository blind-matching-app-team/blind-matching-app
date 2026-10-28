import { useEffect, useId, useState } from 'react';
import Modal from './Modal';
import { useToast } from './useToast';
import { toastMessages } from './feedbackContent';
import {
  purchaseItem,
  readProducts,
  readSubscription,
  subscribe,
  type Product,
} from '../api/payments';

type Tab = Product['productType'];
const TABS: [Tab, string][] = [
  ['ITEM', '소모형'],
  ['SUBSCRIPTION', '구독형'],
];
const GRANT_LABEL: Record<string, string> = {
  MATCH_CHANCE: '매칭기회',
  REMATCH_TICKET: '재매칭권',
};
const won = (price: number) => `${price.toLocaleString('ko-KR')}원`;

// CM-16 card: the four benefit lines come from the product's `benefit`.
function benefitLines({ benefit }: Product) {
  return [
    benefit.precisionMatching && '매칭 정밀도 향상',
    benefit.revealWaitSkip && 'Reveal 24시간 대기 스킵',
    benefit.monthlyGrants &&
      `매월 ${Object.entries(benefit.monthlyGrants)
        .map(([type, count]) => `${GRANT_LABEL[type] ?? type} ${count}개`)
        .join(' · ')} 지급`,
    benefit.carryOverMonths && `남은 이용권 최대 ${benefit.carryOverMonths}개월 이월`,
  ].filter(Boolean) as string[];
}

/** CM-13~17 이용권 구매 모달. Payment runs through the mock until the Toss widget lands. */
export default function MatchingTicketModal({
  open,
  onClose,
  message,
  closeLabel = '닫기',
  initialTab = 'ITEM',
}: {
  open: boolean;
  onClose: () => void;
  /** Why the modal opened (e.g. chances exhausted); omitted when opened from a menu. */
  message?: string;
  closeLabel?: string;
  initialTab?: Tab;
}) {
  const titleId = useId();
  const showToast = useToast();
  const [tab, setTab] = useState<Tab>(initialTab);
  const [products, setProducts] = useState<Product[] | null>(null);
  const [subscribed, setSubscribed] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [pending, setPending] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    Promise.all([readProducts(controller.signal), readSubscription(controller.signal)])
      .then(([items, subscription]) => {
        setLoadFailed(false);
        setProducts(items);
        setSubscribed(subscription.subscribed);
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadFailed(true);
      });
    return () => controller.abort();
  }, [open]);

  async function buy(product: Product) {
    if (pending) return;
    setPending(product.productCode);
    try {
      if (product.productType === 'ITEM') {
        await purchaseItem(product.productCode);
        // CM-15: the modal stays open so more items can be bought.
        showToast(toastMessages.paymentCompleted);
      } else {
        await subscribe(product.productCode);
        showToast(toastMessages.subscriptionStarted);
        onClose();
      }
    } catch {
      showToast(toastMessages.paymentFailed);
    } finally {
      setPending(null);
    }
  }

  const visible = products?.filter((product) => product.productType === tab) ?? [];

  return (
    <Modal open={open} labelledBy={titleId}>
      {/* CM-13 */}
      <h2 className="cm-title" id={titleId}>
        이용권 구매
      </h2>
      {message && <p className="cm-description">{message}</p>}
      {/* CM-14 */}
      <div className="ticket-tabs" role="tablist" aria-label="이용권 종류">
        {TABS.map(([id, label]) => (
          <button
            key={id}
            type="button"
            role="tab"
            id={`${titleId}-${id}`}
            aria-selected={tab === id}
            aria-controls={`${titleId}-panel`}
            className="ticket-tab"
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </div>
      <div
        className="ticket-panel"
        role="tabpanel"
        id={`${titleId}-panel`}
        aria-labelledby={`${titleId}-${tab}`}
      >
        {loadFailed ? (
          <p className="cm-description">상품을 불러오지 못했어요. 잠시 후 다시 시도해주세요.</p>
        ) : !products ? (
          <p className="cm-description" role="status">
            상품을 불러오고 있어요…
          </p>
        ) : (
          visible.map((product) =>
            product.productType === 'ITEM' ? (
              // CM-15
              <div key={product.productCode} className="ticket-card">
                <div className="ticket-card-head">
                  <strong>{product.productName}</strong>
                  <span>{won(product.price)}</span>
                </div>
                <button
                  type="button"
                  className="ui-button ui-button--primary ticket-buy"
                  disabled={pending !== null}
                  aria-label={`${product.productName} 구매`}
                  onClick={() => void buy(product)}
                >
                  {pending === product.productCode ? '결제 중…' : '구매'}
                </button>
              </div>
            ) : (
              // CM-16 / CM-17
              <div key={product.productCode} className="ticket-card ticket-card--subscription">
                <div className="ticket-card-head">
                  <strong>{product.productName}</strong>
                  <span>월 {won(product.price)}</span>
                </div>
                <ul>
                  {benefitLines(product).map((line) => (
                    <li key={line}>{line}</li>
                  ))}
                </ul>
                <button
                  type="button"
                  className="ui-button ui-button--primary ticket-subscribe"
                  disabled={pending !== null || subscribed}
                  onClick={() => void buy(product)}
                >
                  {subscribed
                    ? '이용 중이에요'
                    : pending === product.productCode
                      ? '결제 중…'
                      : '구독하기'}
                </button>
              </div>
            ),
          )
        )}
      </div>
      <button
        type="button"
        className="ui-button ui-button--secondary ticket-close"
        disabled={pending !== null}
        onClick={onClose}
      >
        {closeLabel}
      </button>
    </Modal>
  );
}
