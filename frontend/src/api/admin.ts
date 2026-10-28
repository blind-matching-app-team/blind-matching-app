import { apiRequest } from '../lib/auth';

// Mirrors backend/docs/ADMIN_REPORT_API.md (BMA-76).
export type ReportFilter = 'PENDING' | 'DONE';
export type ReportStatus = 'PENDING_REVIEW' | 'RESOLVED' | 'REJECTED' | 'COUNTED';
export type SanctionAction = 'NONE' | 'WARNING' | 'SUSPEND' | 'BAN';
export type ReportUser = {
  userId: number;
  nickname: string | null;
  email: string | null;
  userStatus: string;
};
export type AdminReportSummary = {
  reportId: number;
  reportType: string;
  reportTypeName: string;
  status: ReportStatus;
  immediateReview: boolean;
  description: string | null;
  matchId: number | null;
  messagePreview: string | null;
  reporter: ReportUser;
  target: ReportUser & { reportCount: number };
  reportedAt: string;
  processedAt: string | null;
  actionCode: SanctionAction | null;
};
export type AuditLog = {
  auditLogId: number;
  adminEmail: string;
  actionCode: string;
  detail: string | null;
  actionDate: string;
};
export type AdminReportDetail = AdminReportSummary & { auditLogs: AuditLog[] };
export type ReportCounts = { pending: number; done: number; all: number };
export type AdminAccount = { userId: number; email: string; userRole: string };

// Phase 2 contract; swap /api/mock/admin for /api/v1/admin when the API subtask is integrated.
export async function readAdminAccount(signal: AbortSignal) {
  const result = await apiRequest<{ data: AdminAccount }>('/api/mock/admin/me', { signal }, true);
  return result.data;
}

export async function readReports(status: ReportFilter, signal: AbortSignal) {
  const result = await apiRequest<{ data: AdminReportSummary[] }>(
    `/api/mock/admin/reports?status=${status}`,
    { signal },
    true,
  );
  return result.data;
}

export async function readReportCounts(signal: AbortSignal) {
  const result = await apiRequest<{ data: ReportCounts }>(
    '/api/mock/admin/reports/counts',
    { signal },
    true,
  );
  return result.data;
}

export async function readReport(reportId: number, signal: AbortSignal) {
  const result = await apiRequest<{ data: AdminReportDetail }>(
    `/api/mock/admin/reports/${reportId}`,
    { signal },
    true,
  );
  return result.data;
}

export async function reviewReport(reportId: number, decision: 'APPROVE' | 'REJECT') {
  const result = await apiRequest<{
    data: { report: AdminReportSummary; actionTaken: SanctionAction; reportCountAfter: number };
  }>(
    `/api/mock/admin/reports/${reportId}/review`,
    // Omitting `action` lets the server pick the BMA-30 step from the accumulated count.
    { method: 'POST', body: JSON.stringify({ decision }) },
    true,
  );
  return result.data;
}
