package com.bma.safety.service;

import com.bma.auth.entity.UserToken;
import com.bma.auth.repository.UserTokenRepository;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.security.TokenType;
import com.bma.matching.entity.MatchQueue;
import com.bma.matching.repository.MatchQueueRepository;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.service.NotificationService;
import com.bma.safety.entity.AdminAuditLog;
import com.bma.safety.entity.UserReport;
import com.bma.safety.entity.UserSanction;
import com.bma.safety.repository.AdminAuditLogRepository;
import com.bma.safety.repository.UserReportRepository;
import com.bma.safety.repository.UserSanctionRepository;
import com.bma.user.entity.User;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 제재(경고/이용 제한/영구 차단) 부과·해제와 BMA-30 누적 단계 판정.
 *
 * <p>BMA-30 확정 조치 단계</p>
 * <ul>
 *   <li>누적 3회 → 경고, 5회 → 7일 이용 제한, 7회 → 영구 차단</li>
 *   <li>감형(1회 한정): 첫 7일 제한이 끝나면 카운트 2 감형(5→3). 두 번째 제한부터는 감형 없음</li>
 *   <li>신고 횟수 리셋 없음(평생 누적). 관리자는 직권으로 즉시 조치 가능. 모든 조치는 감사 로그</li>
 * </ul>
 *
 * <p>누적 카운트는 저장하지 않고 계산한다: (자동 반영 + 관리자 유효 판정 신고 수) − (첫 제한이 끝났으면 2).
 * 상태를 따로 들고 있지 않으므로 신고·제재 행만 정본이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SanctionService {

    public static final int WARNING_THRESHOLD = 3;
    public static final int SUSPEND_THRESHOLD = 5;
    public static final int BAN_THRESHOLD = 7;
    public static final int DEFAULT_SUSPEND_DAYS = 7;
    public static final int FIRST_SUSPENSION_REDUCTION = 2;
    public static final String ACTION_NONE = "NONE";

    private final UserSanctionRepository sanctionRepository;
    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final MatchQueueRepository queueRepository;
    private final NotificationService notificationService;
    private final AdminAuditLogRepository auditLogRepository;

    /**
     * BMA-30 누적 카운트.
     *
     * @param userId 피신고자
     * @return 반영된 신고 수 − 감형
     */
    public int effectiveReportCount(Long userId) {
        long counted = reportRepository.countByTargetUserIdAndReportStatusInAndDeleted(
                userId, UserReport.COUNTED_STATUSES, YesNo.N);
        int reduction = reductionFor(history(userId), LocalDateTime.now());
        return (int) Math.max(0, counted - reduction);
    }

    /**
     * 감형량. 가장 먼저 부과된 7일 제한이 끝났으면 2, 아니면 0(두 번째 제한부터는 감형 없음).
     *
     * @param sanctions 오래된 순 제재 이력
     * @param now       기준 시각
     * @return 감형할 횟수
     */
    public static int reductionFor(List<UserSanction> sanctions, LocalDateTime now) {
        return sanctions.stream()
                .filter(UserSanction::isSuspend)
                .findFirst()
                .filter(first -> first.hasEnded(now))
                .map(first -> FIRST_SUSPENSION_REDUCTION)
                .orElse(0);
    }

    /**
     * 누적 카운트로 조치를 정한다.
     *
     * <p>정확히 임계값에 도달했을 때 그 단계를 실행한다. 감형 뒤 다시 5회가 되면 두 번째 제한이 나간다.
     * 임계값을 건너뛴 경우(예: 직권 조치 없이 6회)에는 아직 받은 적 없는 가장 높은 단계를 실행한다.</p>
     *
     * @param count      누적 카운트(이번 승인 포함)
     * @param hadWarning 경고를 받은 적이 있는지
     * @param hadSuspend 이용 제한을 받은 적이 있는지
     * @return NONE/WARNING/SUSPEND/BAN
     */
    public static String decideLadderAction(int count, boolean hadWarning, boolean hadSuspend) {
        if (count >= BAN_THRESHOLD) {
            return UserSanction.TYPE_BAN;
        }
        if (count == SUSPEND_THRESHOLD || (count > SUSPEND_THRESHOLD && !hadSuspend)) {
            return UserSanction.TYPE_SUSPEND;
        }
        if (count == WARNING_THRESHOLD || (count > WARNING_THRESHOLD && !hadWarning)) {
            return UserSanction.TYPE_WARNING;
        }
        return ACTION_NONE;
    }

    /**
     * 제재 이력(해제 포함, 오래된 순).
     */
    public List<UserSanction> history(Long userId) {
        return sanctionRepository.findByUserIdAndDeletedOrderByStartDateAscIdAsc(userId, YesNo.N);
    }

    /**
     * 지금 효력 있는 가장 무거운 제재(BAN > SUSPEND > WARNING).
     */
    public Optional<UserSanction> mostSevereEffective(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return history(userId).stream()
                .filter(s -> s.isEffectiveAt(now))
                .max(Comparator.comparingInt(SanctionService::weight).thenComparing(UserSanction::getStartDate));
    }

    private static int weight(UserSanction s) {
        return switch (s.getSanctionType()) {
            case UserSanction.TYPE_BAN -> 3;
            case UserSanction.TYPE_SUSPEND -> 2;
            default -> 1;
        };
    }

    /**
     * 제재를 부과한다(승인에 따른 자동 조치 또는 직권 조치).
     *
     * <p>이용 제한·영구 차단은 계정을 정지 상태로 바꾸고 리프레시 토큰을 모두 폐기하며 대기 중인 매칭을 취소한다.
     * 이미 발급된 액세스 토큰은 만료(최대 30분)까지 유효하다.</p>
     *
     * @param adminUserId    관리자
     * @param userId         대상
     * @param type           WARNING/SUSPEND/BAN
     * @param days           SUSPEND 일수(null 이면 7)
     * @param reason         사유(사용자에게 노출)
     * @param sourceReportId 근거 신고(선택)
     * @return 저장된 제재
     */
    @Transactional
    public UserSanction apply(Long adminUserId, Long userId, String type, Integer days, String reason,
                              Long sourceReportId) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!UserSanction.TYPE_WARNING.equals(type) && !UserSanction.TYPE_SUSPEND.equals(type)
                && !UserSanction.TYPE_BAN.equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 제재 종류입니다: " + type);
        }
        LocalDateTime endDate = UserSanction.TYPE_SUSPEND.equals(type)
                ? LocalDateTime.now().plusDays(days == null ? DEFAULT_SUSPEND_DAYS : days)
                : null;
        UserSanction sanction = sanctionRepository.save(UserSanction.of(userId, type, endDate, reason, sourceReportId));

        if (sanction.blocksLogin()) {
            user.suspend(endDate);
            revokeRefreshTokens(userId);
            queueRepository.findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                    .ifPresent(MatchQueue::cancel);
        } else {
            // S7-13 경고 알림. 정지·차단은 로그인 자체가 막히므로 알림 대신 로그인 응답(AUTH_009)이 사유를 전달한다.
            notificationService.notify(userId, NotificationEvent.WARNING_ISSUED, "경고를 받았어요", reason,
                    "SANCTION", sanction.getId());
        }

        String action = switch (type) {
            case UserSanction.TYPE_BAN -> AdminAuditLog.ACTION_SANCTION_BAN;
            case UserSanction.TYPE_SUSPEND -> AdminAuditLog.ACTION_SANCTION_SUSPEND;
            default -> AdminAuditLog.ACTION_SANCTION_WARNING;
        };
        auditLogRepository.save(AdminAuditLog.of(adminUserId, action, userId, sourceReportId, sanction.getId(),
                (endDate == null ? "" : "종료 " + endDate + " · ") + reason));
        log.info("제재 부과: adminId={}, userId={}, type={}, endDate={}, reportId={}",
                adminUserId, userId, type, endDate, sourceReportId);
        return sanction;
    }

    /**
     * 제재를 해제한다. 로그인을 막는 제재가 더 없으면 계정을 다시 활성화한다.
     *
     * @param adminUserId 관리자
     * @param sanctionId  제재 ID
     * @return 해제된 제재
     */
    @Transactional
    public UserSanction lift(Long adminUserId, Long sanctionId) {
        UserSanction sanction = sanctionRepository.findById(sanctionId)
                .filter(s -> !s.isDeleted() && YesNo.isY(s.getActiveYn()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SANCTION_NOT_FOUND));
        sanction.lift();
        userRepository.findById(sanction.getUserId()).ifPresent(this::liftExpiredSuspension);
        auditLogRepository.save(AdminAuditLog.of(adminUserId, AdminAuditLog.ACTION_SANCTION_LIFT,
                sanction.getUserId(), sanction.getSourceReportId(), sanctionId, sanction.getSanctionType() + " 해제"));
        log.info("제재 해제: adminId={}, sanctionId={}, userId={}", adminUserId, sanctionId, sanction.getUserId());
        return sanction;
    }

    /**
     * 정지 상태인데 효력 있는 정지·차단이 더 없으면(기간 만료·해제) 계정을 되살린다. 로그인 시 호출된다.
     *
     * @param user 사용자
     */
    @Transactional
    public void liftExpiredSuspension(User user) {
        if (!user.isSuspended()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean stillBlocked = history(user.getId()).stream()
                .anyMatch(s -> s.blocksLogin() && s.isEffectiveAt(now));
        if (!stillBlocked) {
            user.reactivate();
            log.info("정지 만료로 계정 재활성화: userId={}", user.getId());
        }
    }

    private void revokeRefreshTokens(Long userId) {
        List<UserToken> tokens = tokenRepository
                .findAllByUserIdAndTokenTypeAndDeleted(userId, TokenType.REFRESH.value(), YesNo.N);
        tokens.forEach(UserToken::revoke);
    }
}
