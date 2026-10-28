import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  readAdminAccount,
  readReport,
  readReportCounts,
  readReports,
  reviewReport,
  type AdminAccount,
  type AdminReportDetail,
  type AdminReportSummary,
  type ReportCounts,
  type ReportFilter,
  type SanctionAction,
} from '../api/admin';
import { logoutApi } from '../api/auth';
import ConfirmModal from '../components/ConfirmModal';
import { useToast } from '../components/useToast';
import { confirmationContent, toastMessages } from '../components/feedbackContent';
import ErrorPage from './ErrorPage';
import './AdminReportPage.css';

const TABS: { id: ReportFilter; label: (counts: ReportCounts | null) => string }[] = [
  { id: 'PENDING', label: (counts) => `대기중${counts ? ` (${counts.pending})` : ''}` },
  { id: 'DONE', label: () => '처리완료' },
];
const ACTION_LABEL: Record<SanctionAction, string> = {
  NONE: '조치 없음',
  WARNING: '경고',
  SUSPEND: '7일 이용 제한',
  BAN: '영구 차단',
};
const AUDIT_LABEL: Record<string, string> = {
  REPORT_APPROVE: '신고 승인',
  REPORT_REJECT: '신고 반려',
  SANCTION_WARNING: '경고 부과',
  SANCTION_SUSPEND: '이용 제한 부과',
  SANCTION_BAN: '영구 차단',
  SANCTION_LIFT: '제재 해제',
  CONVERSATION_END: '대화 종료',
};

const formatDate = (value: string) => value.slice(0, 16).replace('T', ' ').replaceAll('-', '.');
const adminId = (email: string) => email.split('@')[0];
const errorCode = (error: unknown) =>
  error instanceof Error ? (error.cause as { code?: string } | undefined)?.code : undefined;
const userLabel = (user: AdminReportSummary['reporter']) =>
  `${user.nickname ?? '알 수 없음'} (ID ${user.userId})`;

export default function AdminReportPage() {
  const navigate = useNavigate();
  const showToast = useToast();
  const detailRef = useRef<HTMLElement>(null);
  const [account, setAccount] = useState<AdminAccount | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [tab, setTab] = useState<ReportFilter>('PENDING');
  const [reports, setReports] = useState<AdminReportSummary[] | null>(null);
  const [counts, setCounts] = useState<ReportCounts | null>(null);
  const [listError, setListError] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminReportDetail | null>(null);
  const [version, setVersion] = useState(0);
  const [reviewing, setReviewing] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    readAdminAccount(controller.signal)
      .then(setAccount)
      .catch((reason) => {
        if (!controller.signal.aborted && errorCode(reason) === 'AUTH_005') setForbidden(true);
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([readReports(tab, controller.signal), readReportCounts(controller.signal)])
      .then(([items, nextCounts]) => {
        setListError(false);
        setReports(items);
        setCounts(nextCounts);
        // Keep the current selection (e.g. a just-processed report) when it is in this tab.
        setSelectedId((current) =>
          items.some((item) => item.reportId === current) ? current : (items[0]?.reportId ?? null),
        );
      })
      .catch((reason) => {
        if (controller.signal.aborted) return;
        if (errorCode(reason) === 'AUTH_005') setForbidden(true);
        else setListError(true);
      });
    return () => controller.abort();
  }, [tab, version]);

  useEffect(() => {
    if (selectedId === null) return;
    const controller = new AbortController();
    readReport(selectedId, controller.signal)
      .then(setDetail)
      .catch(() => {
        if (!controller.signal.aborted) showToast(toastMessages.networkError);
      });
    return () => controller.abort();
  }, [selectedId, version, showToast]);

  async function review(decision: 'APPROVE' | 'REJECT') {
    if (!detail || reviewing) return;
    setReviewing(true);
    try {
      const result = await reviewReport(detail.reportId, decision);
      showToast(
        decision === 'APPROVE'
          ? `승인했어요 · ${ACTION_LABEL[result.actionTaken]}`
          : '반려했어요 · 누적에 반영되지 않아요',
      );
      // AC: the processed report leaves the pending list and is shown under 처리완료.
      setReports(null);
      setTab('DONE');
      setVersion((value) => value + 1);
    } catch (reason) {
      if (errorCode(reason) === 'SAFE_002') {
        showToast('이미 처리된 신고예요');
        setVersion((value) => value + 1);
      } else showToast(reason instanceof Error ? reason.message : toastMessages.networkError);
    } finally {
      setReviewing(false);
    }
  }

  async function signOut() {
    await logoutApi();
    navigate('/login', { replace: true });
  }

  if (forbidden)
    return (
      <ErrorPage
        title="관리자만 접근할 수 있어요"
        description="관리자 계정으로 다시 로그인해주세요"
      />
    );

  const selected = detail?.reportId === selectedId ? detail : null;
  const isPending = selected?.status === 'PENDING_REVIEW';

  return (
    <div className="admin-shell">
      <header className="admin-header">
        {/* S12-01 */}
        <h1 className="admin-title">신고 검토</h1>
        {/* S12-09 */}
        <div className="admin-account">
          <span className="admin-account-id">{account ? adminId(account.email) : '…'}</span>
          <button
            type="button"
            className="ui-interactive admin-logout"
            onClick={() => setLogoutOpen(true)}
          >
            로그아웃
          </button>
        </div>
      </header>

      {/* S12-02 */}
      <div className="admin-tabs" role="tablist" aria-label="신고 상태">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            id={`admin-tab-${item.id}`}
            aria-selected={tab === item.id}
            aria-controls="admin-report-list"
            className="ui-interactive admin-tab"
            onClick={() => {
              if (item.id === tab) return;
              setReports(null);
              setTab(item.id);
            }}
          >
            {item.label(counts)}
          </button>
        ))}
      </div>

      <div className="admin-body">
        <section
          id="admin-report-list"
          role="tabpanel"
          aria-labelledby={`admin-tab-${tab}`}
          className="admin-list"
        >
          {listError ? (
            <div className="admin-empty">
              <p>{toastMessages.networkError}</p>
              <button
                type="button"
                className="ui-button ui-button--secondary admin-retry"
                onClick={() => setVersion((value) => value + 1)}
              >
                다시 시도
              </button>
            </div>
          ) : !reports ? (
            <p className="admin-empty" role="status">
              신고를 불러오고 있어요…
            </p>
          ) : reports.length === 0 ? (
            <p className="admin-empty">
              {tab === 'PENDING' ? '대기 중인 신고가 없어요' : '처리한 신고가 없어요'}
            </p>
          ) : (
            <ul>
              {reports.map((report) => (
                <li key={report.reportId}>
                  {/* S12-03 */}
                  <button
                    type="button"
                    className="admin-item"
                    aria-current={report.reportId === selectedId}
                    onClick={() => {
                      setSelectedId(report.reportId);
                      detailRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
                    }}
                  >
                    <span className="admin-badge">{report.reportTypeName}</span>
                    <span className="admin-item-target">대상 ID {report.target.userId}</span>
                    <time className="admin-item-time" dateTime={report.reportedAt}>
                      {formatDate(report.reportedAt)}
                    </time>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section ref={detailRef} className="admin-detail" aria-labelledby="admin-detail-title">
          {/* S12-04 */}
          <h2 id="admin-detail-title" className="admin-detail-title">
            신고 상세
          </h2>
          {!selected ? (
            <p className="admin-empty">
              {selectedId === null ? '목록에서 신고를 선택해주세요' : '불러오고 있어요…'}
            </p>
          ) : (
            <>
              {/* S12-05 */}
              <dl className="admin-report-box">
                <div>
                  <dt>유형</dt>
                  <dd>
                    {selected.reportTypeName}
                    {selected.immediateReview && <span className="admin-flag">즉시 검토</span>}
                  </dd>
                </div>
                <div>
                  <dt>신고자</dt>
                  <dd>{userLabel(selected.reporter)}</dd>
                </div>
                <div>
                  <dt>대상</dt>
                  <dd>
                    {userLabel(selected.target)} · 누적 {selected.target.reportCount}회
                  </dd>
                </div>
                <div>
                  <dt>사유</dt>
                  <dd>{selected.description || '작성된 사유가 없어요'}</dd>
                </div>
                {selected.messagePreview && (
                  <div>
                    <dt>신고 메시지</dt>
                    <dd className="admin-quote">{selected.messagePreview}</dd>
                  </div>
                )}
                <div>
                  <dt>접수</dt>
                  <dd>{formatDate(selected.reportedAt)}</dd>
                </div>
              </dl>

              {isPending ? (
                <div className="admin-actions">
                  {/* S12-06: v1.4 위험 변형 확인모달(CM-06) 후 실행 */}
                  <button
                    type="button"
                    className="ui-button ui-button--primary admin-approve"
                    disabled={reviewing}
                    onClick={() => setApproveOpen(true)}
                  >
                    승인
                  </button>
                  {/* S12-07 */}
                  <button
                    type="button"
                    className="ui-button ui-button--secondary admin-reject"
                    disabled={reviewing}
                    onClick={() => void review('REJECT')}
                  >
                    반려
                  </button>
                </div>
              ) : (
                <p className="admin-result">
                  {selected.status === 'REJECTED' ? '반려됨' : '승인됨'}
                  {selected.processedAt && ` · ${formatDate(selected.processedAt)}`}
                  {selected.status === 'RESOLVED' &&
                    selected.actionCode &&
                    ` · ${ACTION_LABEL[selected.actionCode]}`}
                </p>
              )}
            </>
          )}
        </section>
      </div>

      {/* S12-08 */}
      <footer className="admin-audit" aria-labelledby="admin-audit-title">
        <h2 id="admin-audit-title">감사 로그</h2>
        {selected?.auditLogs.length ? (
          <ol>
            {selected.auditLogs.map((log) => (
              <li key={log.auditLogId}>
                {formatDate(log.actionDate)} · {adminId(log.adminEmail)} ·{' '}
                {AUDIT_LABEL[log.actionCode] ?? log.actionCode}
                {log.detail && ` (${log.detail})`}
              </li>
            ))}
          </ol>
        ) : (
          <p>{selected ? '아직 기록된 조치가 없어요' : '신고를 선택하면 처리 기록이 표시돼요'}</p>
        )}
      </footer>

      <ConfirmModal
        {...confirmationContent.approveReport}
        description={
          selected
            ? `대상의 현재 누적은 ${selected.target.reportCount}회예요. ${confirmationContent.approveReport.description}`
            : confirmationContent.approveReport.description
        }
        open={approveOpen}
        onClose={() => setApproveOpen(false)}
        onConfirm={() => void review('APPROVE')}
      />
      <ConfirmModal
        {...confirmationContent.logout}
        open={logoutOpen}
        onClose={() => setLogoutOpen(false)}
        onConfirm={() => void signOut()}
      />
    </div>
  );
}
