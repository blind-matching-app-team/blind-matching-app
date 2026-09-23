package com.bma.safety.service;

import com.bma.chat.entity.ChatMessage;
import com.bma.chat.entity.ChatRoom;
import com.bma.chat.repository.ChatMessageRepository;
import com.bma.chat.repository.ChatRoomRepository;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.response.PageResponse;
import com.bma.matching.entity.Match;
import com.bma.matching.repository.MatchRepository;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.service.NotificationService;
import com.bma.safety.dto.AdminSafetyDtos;
import com.bma.safety.dto.AdminSafetyDtos.AdminReportDetail;
import com.bma.safety.dto.AdminSafetyDtos.AdminReportSummary;
import com.bma.safety.dto.AdminSafetyDtos.AuditLogResponse;
import com.bma.safety.dto.AdminSafetyDtos.PartyInfo;
import com.bma.safety.dto.AdminSafetyDtos.ReportHistoryItem;
import com.bma.safety.dto.AdminSafetyDtos.ReviewRequest;
import com.bma.safety.dto.AdminSafetyDtos.ReviewResponse;
import com.bma.safety.dto.AdminSafetyDtos.SanctionRequest;
import com.bma.safety.dto.AdminSafetyDtos.SanctionResponse;
import com.bma.safety.dto.AdminSafetyDtos.TargetInfo;
import com.bma.safety.entity.AdminAuditLog;
import com.bma.safety.entity.UserReport;
import com.bma.safety.entity.UserSanction;
import com.bma.safety.repository.AdminAuditLogRepository;
import com.bma.safety.repository.UserReportRepository;
import com.bma.user.entity.User;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.UserProfileRepository;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * S12 관리자 신고검토 (BMA-76).
 *
 * <ul>
 *   <li>목록: 대기중(PENDING_REVIEW) / 처리완료(RESOLVED·REJECTED) 탭</li>
 *   <li>승인: 신고를 유효로 판정 → 누적 카운트 반영 → BMA-30 단계(3회 경고·5회 7일 제한·7회 영구 차단) 또는
 *       관리자가 지정한 조치 실행 → 신고자와의 대화를 "관리자에 의해 종료"로 끝내고 피신고자에게 안내</li>
 *   <li>반려: 누적에 반영하지 않는다. 즉시검토로 막혀 있던 새 매칭 진입도 풀린다</li>
 *   <li>모든 행위는 감사 로그(S12-08)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportReviewService {

    /** 관리자 종료 시 채팅방에 남기는 시스템 메시지. */
    public static final String CONVERSATION_ENDED_MESSAGE = "관리자에 의해 대화가 종료되었습니다.";
    private static final int PREVIEW_LENGTH = 200;

    private final UserReportRepository reportRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MatchRepository matchRepository;
    private final NotificationService notificationService;
    private final SanctionService sanctionService;

    /**
     * 신고 목록 (S12 탭).
     *
     * @param filter {@code PENDING}(검토 대기) / {@code DONE}(처리완료) / {@code ALL}
     */
    public PageResponse<AdminReportSummary> list(String filter, int page, int size) {
        Collection<String> statuses = switch (filter == null ? "PENDING" : filter.toUpperCase()) {
            case "DONE" -> List.of(UserReport.STATUS_RESOLVED, UserReport.STATUS_REJECTED);
            case "ALL" -> List.of(UserReport.STATUS_RECEIVED, UserReport.STATUS_COUNTED,
                    UserReport.STATUS_PENDING_REVIEW, UserReport.STATUS_RESOLVED, UserReport.STATUS_REJECTED);
            default -> List.of(UserReport.STATUS_PENDING_REVIEW);
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<UserReport> reports = reportRepository.findByReportStatusInAndDeletedOrderByIdDesc(statuses, YesNo.N, pageable);
        Map<Long, AdminReportSummary> summaries = summarize(reports.getContent());
        return PageResponse.of(reports, report -> summaries.get(report.getId()));
    }

    /**
     * 신고 상세 + 피신고자 이력·제재 + 감사 로그.
     */
    public AdminReportDetail get(Long reportId) {
        UserReport report = requireReport(reportId);
        AdminReportSummary summary = summarize(List.of(report)).get(reportId);
        List<ReportHistoryItem> history = reportRepository
                .findByTargetUserIdAndDeletedOrderByIdDesc(report.getTargetUserId(), YesNo.N).stream()
                .map(r -> new ReportHistoryItem(r.getId(), r.getReportType(), r.getSeverity(), r.getReportStatus(),
                        r.getInsertDate()))
                .toList();
        List<SanctionResponse> sanctions = sanctionService.history(report.getTargetUserId()).stream()
                .map(SanctionResponse::from).toList();
        List<AdminAuditLog> logs = auditLogRepository.findByReportIdAndDeletedOrderByIdAsc(reportId, YesNo.N);
        return new AdminReportDetail(summary, history, sanctions, toAuditResponses(logs));
    }

    /**
     * 승인/반려 (S12-06/07).
     */
    @Transactional
    public ReviewResponse review(Long adminUserId, Long reportId, ReviewRequest request) {
        UserReport report = requireReport(reportId);
        if (!report.isPendingReview()) {
            throw new BusinessException(ErrorCode.REPORT_NOT_REVIEWABLE);
        }
        Long targetId = report.getTargetUserId();

        if (!request.isApprove()) {
            report.reject(adminUserId, request.note());
            auditLogRepository.save(AdminAuditLog.of(adminUserId, AdminAuditLog.ACTION_REPORT_REJECT, targetId,
                    reportId, null, "반려" + (request.note() == null ? "" : ": " + request.note())));
            log.info("신고 반려: adminId={}, reportId={}, targetId={}", adminUserId, reportId, targetId);
            return new ReviewResponse(summarize(List.of(report)).get(reportId), SanctionService.ACTION_NONE, null,
                    false, sanctionService.effectiveReportCount(targetId));
        }

        // 승인: 먼저 유효로 바꿔야 누적 카운트에 이번 건이 들어간다.
        report.approve(adminUserId, SanctionService.ACTION_NONE, request.note());
        reportRepository.flush();
        int count = sanctionService.effectiveReportCount(targetId);
        List<UserSanction> history = sanctionService.history(targetId);
        boolean hadWarning = history.stream().anyMatch(s -> UserSanction.TYPE_WARNING.equals(s.getSanctionType()));
        boolean hadSuspend = history.stream().anyMatch(UserSanction::isSuspend);
        String action = request.action() != null ? request.action()
                : SanctionService.decideLadderAction(count, hadWarning, hadSuspend);
        report.setActionCode(action);

        auditLogRepository.save(AdminAuditLog.of(adminUserId, AdminAuditLog.ACTION_REPORT_APPROVE, targetId, reportId,
                null, "승인 · 누적 " + count + "회 · 조치 " + action
                        + (request.note() == null ? "" : " · " + request.note())));

        UserSanction sanction = null;
        if (!SanctionService.ACTION_NONE.equals(action)) {
            String typeName = AdminSafetyDtos.REPORT_TYPE_NAMES.getOrDefault(report.getReportType(), report.getReportType());
            String reason = "신고(" + typeName + ") 누적 " + count + "회에 따른 조치"
                    + (request.note() == null || request.note().isBlank() ? "" : ": " + request.note());
            sanction = sanctionService.apply(adminUserId, targetId, action, request.suspendDays(), reason, reportId);
        }
        boolean ended = endConversation(adminUserId, report);

        log.info("신고 승인: adminId={}, reportId={}, targetId={}, 누적={}, 조치={}, 대화종료={}",
                adminUserId, reportId, targetId, count, action, ended);
        return new ReviewResponse(summarize(List.of(report)).get(reportId), action,
                sanction == null ? null : SanctionResponse.from(sanction), ended, count);
    }

    /**
     * 직권 제재 (BMA-30 "관리자는 누적 대기 없이 즉시 조치 가능").
     */
    @Transactional
    public SanctionResponse sanction(Long adminUserId, Long userId, SanctionRequest request) {
        return SanctionResponse.from(sanctionService.apply(adminUserId, userId, request.type(), request.days(),
                request.reason(), request.sourceReportId()));
    }

    /**
     * 제재 해제.
     */
    @Transactional
    public SanctionResponse lift(Long adminUserId, Long sanctionId) {
        return SanctionResponse.from(sanctionService.lift(adminUserId, sanctionId));
    }

    /**
     * 사용자의 제재 이력.
     */
    public List<SanctionResponse> sanctions(Long userId) {
        return sanctionService.history(userId).stream().map(SanctionResponse::from).toList();
    }

    /**
     * 감사 로그 목록 (S12-08).
     */
    public PageResponse<AuditLogResponse> auditLogs(Long targetUserId, Long reportId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<AdminAuditLog> logs = auditLogRepository.search(targetUserId, reportId, pageable);
        List<AuditLogResponse> responses = toAuditResponses(logs.getContent());
        Map<Long, AuditLogResponse> byId = responses.stream().collect(Collectors.toMap(AuditLogResponse::auditId, Function.identity()));
        return PageResponse.of(logs, l -> byId.get(l.getId()));
    }

    /**
     * 승인된 신고의 매칭·채팅방을 "관리자에 의해 종료"로 끝낸다(BMA-30). 신고자는 이미 나가 있고, 상대(피신고자)에게는
     * 이때 처음 시스템 메시지와 알림이 간다.
     *
     * @return 매칭이나 방을 실제로 끝냈으면 {@code true}
     */
    private boolean endConversation(Long adminUserId, UserReport report) {
        if (report.getMatchId() == null) {
            return false;
        }
        boolean ended = false;
        Match match = matchRepository.findById(report.getMatchId()).filter(Match::isActive).orElse(null);
        if (match != null) {
            match.terminate(Match.STATUS_BLOCKED, adminUserId, "ADMIN_REPORT");
            ended = true;
        }
        ChatRoom room = chatRoomRepository.findByMatchIdAndDeleted(report.getMatchId(), YesNo.N)
                .filter(ChatRoom::isActive).orElse(null);
        if (room != null) {
            ChatMessage notice = messageRepository.save(ChatMessage.of(room.getId(), adminUserId,
                    ChatMessage.TYPE_SYSTEM, CONVERSATION_ENDED_MESSAGE, null));
            room.touchLastMessage(notice.getId(), notice.getSendDate());
            room.close();
            ended = true;
        }
        if (ended) {
            notificationService.notify(report.getTargetUserId(), NotificationEvent.MATCH_ENDED, "대화가 종료됐어요",
                    CONVERSATION_ENDED_MESSAGE, "MATCH", report.getMatchId());
            auditLogRepository.save(AdminAuditLog.of(adminUserId, AdminAuditLog.ACTION_CONVERSATION_END,
                    report.getTargetUserId(), report.getId(), null, "matchId=" + report.getMatchId()));
        }
        return ended;
    }

    private UserReport requireReport(Long reportId) {
        return reportRepository.findById(reportId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    /**
     * 신고 목록을 요약 응답으로 조립한다. 사용자·프로필·메시지는 일괄 조회한다.
     */
    private Map<Long, AdminReportSummary> summarize(List<UserReport> reports) {
        if (reports.isEmpty()) {
            return Map.of();
        }
        Set<Long> userIds = new HashSet<>();
        reports.forEach(r -> {
            userIds.add(r.getReportUserId());
            userIds.add(r.getTargetUserId());
        });
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, UserProfile> profiles = profileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getId, Function.identity()));
        Map<Long, ChatMessage> messages = messageRepository.findAllById(reports.stream()
                        .map(UserReport::getMessageId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        Map<Long, Integer> counts = reports.stream().map(UserReport::getTargetUserId).distinct()
                .collect(Collectors.toMap(Function.identity(), sanctionService::effectiveReportCount));
        Map<Long, SanctionResponse> activeSanctions = reports.stream().map(UserReport::getTargetUserId).distinct()
                .flatMap(id -> sanctionService.mostSevereEffective(id).map(s -> Map.entry(id, SanctionResponse.from(s))).stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return reports.stream().collect(Collectors.toMap(UserReport::getId, r -> {
            ChatMessage message = r.getMessageId() == null ? null : messages.get(r.getMessageId());
            return new AdminReportSummary(
                    r.getId(), r.getReportType(),
                    AdminSafetyDtos.REPORT_TYPE_NAMES.getOrDefault(r.getReportType(), r.getReportType()),
                    r.getSeverity(), r.getReportStatus(), UserReport.SEVERITY_SEVERE.equals(r.getSeverity()),
                    r.getReportContent(), r.getMatchId(), r.getMessageId(),
                    message == null ? null : preview(message.getMessageContent()),
                    party(r.getReportUserId(), users, profiles),
                    target(r.getTargetUserId(), users, profiles, counts.getOrDefault(r.getTargetUserId(), 0),
                            activeSanctions.get(r.getTargetUserId())),
                    r.getInsertDate(), r.getProcessDate(), r.getProcessUserId(), r.getActionCode(), r.getReviewNote());
        }));
    }

    private static PartyInfo party(Long userId, Map<Long, User> users, Map<Long, UserProfile> profiles) {
        User user = users.get(userId);
        UserProfile profile = profiles.get(userId);
        return new PartyInfo(userId, profile == null ? null : profile.getNickname(),
                user == null ? null : user.getEmail(), user == null ? null : user.getUserStatus());
    }

    private static TargetInfo target(Long userId, Map<Long, User> users, Map<Long, UserProfile> profiles,
                                     int count, SanctionResponse active) {
        PartyInfo p = party(userId, users, profiles);
        return new TargetInfo(p.userId(), p.nickname(), p.email(), p.userStatus(), count, active);
    }

    private List<AuditLogResponse> toAuditResponses(List<AdminAuditLog> logs) {
        Map<Long, String> adminEmails = userRepository.findAllById(
                        logs.stream().map(AdminAuditLog::getAdminUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));
        return logs.stream().map(l -> AuditLogResponse.from(l, adminEmails.get(l.getAdminUserId()))).toList();
    }

    private static String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "...";
    }
}
