import { http, HttpResponse } from 'msw';
import type {
  AdminReportDetail,
  AdminReportSummary,
  AuditLog,
  ReportUser,
  SanctionAction,
} from '../api/admin';

// Scenarios (bma-admin-scenario): default | forbidden | empty
const ADMIN_EMAIL = 'admin_01@bma.test';
const KEY = 'bma-admin-reports-v1';
type Store = { reports: AdminReportSummary[]; logs: (AuditLog & { reportId: number })[] };

const user = (userId: number, nickname: string): ReportUser => ({
  userId,
  nickname,
  email: `user${userId}@bma.test`,
  userStatus: 'ACTIVE',
});
const pending = (
  reportId: number,
  [reportType, reportTypeName]: [string, string],
  targetId: number,
  reportCount: number,
  reportedAt: string,
  description: string,
  messagePreview: string | null,
): AdminReportSummary => ({
  reportId,
  reportType,
  reportTypeName,
  status: 'PENDING_REVIEW',
  immediateReview: reportType === 'FRAUD' || reportType === 'SEXUAL',
  description,
  matchId: messagePreview ? 40 + reportId : null,
  messagePreview,
  reporter: user(100 + reportId, `신고자${reportId}`),
  target: { ...user(targetId, `피신고자${targetId}`), reportCount },
  reportedAt,
  processedAt: null,
  actionCode: null,
});

function seed(): Store {
  if (sessionStorage.getItem('bma-admin-scenario') === 'empty') return { reports: [], logs: [] };
  return {
    reports: [
      pending(
        77,
        ['FRAUD', '사기 및 금전 요구'],
        12,
        2,
        '2026-10-27T23:01:10',
        '대화 중에 계속 돈을 빌려달라고 했어요.',
        '급해서 그런데 30만원만 빌려줄 수 있어?',
      ),
      pending(
        76,
        ['SEXUAL', '부적절한 성적 콘텐츠'],
        15,
        6,
        '2026-10-27T18:42:03',
        '불쾌한 사진을 보냈어요.',
        '[사진]',
      ),
      pending(
        74,
        ['ABUSE', '욕설 및 괴롭힘'],
        21,
        4,
        '2026-10-26T21:15:47',
        '대화를 거절하자 욕설을 했어요.',
        '그딴 식으로 할 거면 왜 매칭했냐',
      ),
      pending(
        73,
        ['FAKE', '허위 프로필'],
        33,
        0,
        '2026-10-26T09:30:00',
        '프로필 사진이 연예인 사진이에요.',
        null,
      ),
      {
        ...pending(70, ['ETC', '기타'], 40, 1, '2026-10-24T14:20:00', '답장이 너무 늦어요.', null),
        status: 'REJECTED',
        processedAt: '2026-10-24T16:02:11',
        actionCode: 'NONE',
      },
    ],
    logs: [
      {
        auditLogId: 1,
        reportId: 70,
        adminEmail: ADMIN_EMAIL,
        actionCode: 'REPORT_REJECT',
        detail: '정책 위반 아님',
        actionDate: '2026-10-24T16:02:11',
      },
    ],
  };
}

function read(): Store {
  try {
    return JSON.parse(sessionStorage.getItem(KEY) || 'null') || seed();
  } catch {
    return seed();
  }
}

const fail = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message }, { status });
const forbidden = () =>
  sessionStorage.getItem('bma-admin-scenario') === 'forbidden'
    ? fail('AUTH_005', '관리자만 접근할 수 있어요.', 403)
    : null;
const isDone = (report: AdminReportSummary) =>
  report.status === 'RESOLVED' || report.status === 'REJECTED';
const now = () => new Date().toISOString().slice(0, 19);

// BMA-30: 3 -> warning, 5 -> 7-day suspension, 7+ -> permanent ban. The server also applies
// skipped steps and the one-time reduction; the mock only needs the visible thresholds.
function stepFor(count: number): SanctionAction {
  if (count >= 7) return 'BAN';
  if (count === 5) return 'SUSPEND';
  if (count === 3) return 'WARNING';
  return 'NONE';
}

export const adminHandlers = [
  http.get('/api/mock/admin/me', () => {
    const denied = forbidden();
    if (denied) return denied;
    return HttpResponse.json({ data: { userId: 1, email: ADMIN_EMAIL, userRole: 'ADMIN' } });
  }),
  http.get('/api/mock/admin/reports/counts', () => {
    const { reports } = read();
    const pendingCount = reports.filter((report) => report.status === 'PENDING_REVIEW').length;
    const done = reports.filter(isDone).length;
    return HttpResponse.json({ data: { pending: pendingCount, done, all: reports.length } });
  }),
  http.get('/api/mock/admin/reports', ({ request }) => {
    const denied = forbidden();
    if (denied) return denied;
    const status = new URL(request.url).searchParams.get('status') || 'PENDING';
    const { reports } = read();
    const data = reports
      .filter((report) => (status === 'DONE' ? isDone(report) : report.status === 'PENDING_REVIEW'))
      .sort((a, b) =>
        status === 'DONE'
          ? (b.processedAt ?? '').localeCompare(a.processedAt ?? '')
          : b.reportedAt.localeCompare(a.reportedAt),
      );
    return HttpResponse.json({ data });
  }),
  http.get('/api/mock/admin/reports/:id', ({ params }) => {
    const store = read();
    const report = store.reports.find((item) => item.reportId === Number(params.id));
    if (!report) return fail('SAFE_003', '존재하지 않는 신고예요.', 404);
    const auditLogs = store.logs.filter((log) => log.reportId === report.reportId);
    return HttpResponse.json({ data: { ...report, auditLogs } satisfies AdminReportDetail });
  }),
  http.post('/api/mock/admin/reports/:id/review', async ({ params, request }) => {
    const store = read();
    const report = store.reports.find((item) => item.reportId === Number(params.id));
    if (!report) return fail('SAFE_003', '존재하지 않는 신고예요.', 404);
    if (report.status !== 'PENDING_REVIEW') return fail('SAFE_002', '이미 처리된 신고예요.', 409);
    const { decision } = (await request.json()) as { decision?: string };
    if (decision !== 'APPROVE' && decision !== 'REJECT')
      return fail('COMMON_001', 'decision 값이 올바르지 않아요.', 400);

    const approved = decision === 'APPROVE';
    const reportCountAfter = report.target.reportCount + (approved ? 1 : 0);
    const actionTaken = approved ? stepFor(reportCountAfter) : 'NONE';
    const processedAt = now();
    const updated: AdminReportSummary = {
      ...report,
      status: approved ? 'RESOLVED' : 'REJECTED',
      processedAt,
      actionCode: actionTaken,
      target: { ...report.target, reportCount: reportCountAfter },
    };
    let nextId = Math.max(0, ...store.logs.map((log) => log.auditLogId)) + 1;
    const log = (actionCode: string, detail: string) => ({
      auditLogId: nextId++,
      reportId: report.reportId,
      adminEmail: ADMIN_EMAIL,
      actionCode,
      detail,
      actionDate: processedAt,
    });
    const logs = [
      log(
        approved ? 'REPORT_APPROVE' : 'REPORT_REJECT',
        approved ? `누적 ${reportCountAfter}회` : '누적 미반영',
      ),
    ];
    if (actionTaken !== 'NONE')
      logs.push(log(`SANCTION_${actionTaken}`, `누적 ${reportCountAfter}회`));
    if (approved && report.matchId) logs.push(log('CONVERSATION_END', `매칭 ${report.matchId}`));

    sessionStorage.setItem(
      KEY,
      JSON.stringify({
        reports: store.reports.map((item) => (item.reportId === report.reportId ? updated : item)),
        logs: [...store.logs, ...logs],
      } satisfies Store),
    );
    return HttpResponse.json({ data: { report: updated, actionTaken, reportCountAfter } });
  }),
];
