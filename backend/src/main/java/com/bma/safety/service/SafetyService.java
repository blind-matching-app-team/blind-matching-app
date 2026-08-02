package com.bma.safety.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.matching.entity.Match;
import com.bma.matching.repository.MatchRepository;
import com.bma.safety.dto.SafetyDtos.BlockResult;
import com.bma.safety.dto.SafetyDtos.BlockedUserResponse;
import com.bma.safety.dto.SafetyDtos.ReportRequest;
import com.bma.safety.dto.SafetyDtos.ReportResponse;
import com.bma.safety.entity.UserBlock;
import com.bma.safety.entity.UserReport;
import com.bma.safety.repository.UserBlockRepository;
import com.bma.safety.repository.UserReportRepository;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 차단 및 신고 처리.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li><b>차단 정보를 DB에 영속화</b>했다. 기존에는 컨트롤러의 인메모리
 *       {@code ConcurrentHashMap.newKeySet()}에 담아 서버를 재시작하면 사라지고,
 *       인스턴스가 둘 이상이면 아예 동작하지 않았다.</li>
 *   <li>차단하면 진행 중이던 매칭도 함께 종료한다(차단해도 계속 대화할 수 있는 상태를 없앴다).</li>
 *   <li>자기 자신 차단/신고와 존재하지 않는 사용자에 대한 요청을 막았다.</li>
 *   <li>단시간 내 중복 신고를 차단했다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyService {

    /** 같은 대상에 대한 중복 신고를 막는 기간(시간). */
    private static final int DUPLICATE_REPORT_WINDOW_HOURS = 24;

    private final UserBlockRepository blockRepository;
    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;

    /**
     * 사용자를 차단한다.
     *
     * @param userId       차단하는 사용자
     * @param targetUserId 차단 대상
     * @param reason       사유(선택)
     * @return 처리 결과
     * @throws BusinessException 자기 자신이거나 존재하지 않는 대상인 경우
     */
    @Transactional
    public BlockResult block(Long userId, Long targetUserId, String reason) {
        validateTarget(userId, targetUserId);

        // PK가 (차단자, 대상)이므로 해제 이력이 있으면 그 행을 되살린다.
        UserBlock block = blockRepository.findByBlockUserIdAndTargetUserId(userId, targetUserId)
                .map(existing -> {
                    existing.reactivate(reason);
                    return existing;
                })
                .orElseGet(() -> UserBlock.of(userId, targetUserId, reason));
        blockRepository.save(block);

        // 차단했는데 채팅은 계속 가능한 상태를 없애기 위해 진행 중인 매칭을 종료한다.
        boolean matchEnded = endActiveMatch(userId, targetUserId);

        log.info("사용자 차단: userId={}, targetUserId={}, 매칭종료={}", userId, targetUserId, matchEnded);
        return new BlockResult(targetUserId, true, matchEnded);
    }

    /**
     * 차단을 해제한다.
     *
     * @param userId       차단했던 사용자
     * @param targetUserId 대상
     * @return 처리 결과
     */
    @Transactional
    public BlockResult unblock(Long userId, Long targetUserId) {
        blockRepository.findByBlockUserIdAndTargetUserId(userId, targetUserId)
                .ifPresent(UserBlock::markDeleted);
        log.info("차단 해제: userId={}, targetUserId={}", userId, targetUserId);
        // 차단 해제는 매칭을 되살리지 않는다. 다시 매칭되려면 추천을 통해 새로 성사되어야 한다.
        return new BlockResult(targetUserId, false, false);
    }

    /**
     * 내가 차단한 사용자 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 차단 목록
     */
    public List<BlockedUserResponse> getBlockedUsers(Long userId) {
        return blockRepository.findByBlockUserIdAndDeleted(userId, YesNo.N).stream()
                .map(block -> new BlockedUserResponse(
                        block.getTargetUserId(), block.getBlockReason(), block.getBlockDate()))
                .toList();
    }

    /**
     * 두 사용자 사이에 차단 관계가 있는지 확인한다(양방향).
     *
     * @param userA 사용자 A
     * @param userB 사용자 B
     * @return 어느 방향으로든 차단이 있으면 {@code true}
     */
    public boolean isBlockedBetween(Long userA, Long userB) {
        return blockRepository.existsBlockBetween(userA, userB);
    }

    /**
     * 사용자를 신고한다.
     *
     * @param userId       신고자
     * @param targetUserId 피신고자
     * @param request      신고 내용
     * @return 접수 결과
     * @throws BusinessException 자기 자신 신고, 존재하지 않는 대상, 중복 신고인 경우
     */
    @Transactional
    public ReportResponse report(Long userId, Long targetUserId, ReportRequest request) {
        validateTarget(userId, targetUserId);

        LocalDateTime since = LocalDateTime.now().minusHours(DUPLICATE_REPORT_WINDOW_HOURS);
        if (reportRepository.existsByReportUserIdAndTargetUserIdAndInsertDateAfterAndDeleted(
                userId, targetUserId, since, YesNo.N)) {
            throw new BusinessException(ErrorCode.REPORT_DUPLICATED,
                    DUPLICATE_REPORT_WINDOW_HOURS + "시간 내에 이미 신고한 사용자입니다.");
        }

        UserReport report = UserReport.of(userId, targetUserId, request.reportType(),
                request.description(), request.matchId(), request.messageId());
        UserReport saved = reportRepository.save(report);

        log.info("신고 접수: reportId={}, userId={}, targetUserId={}, type={}",
                saved.getId(), userId, targetUserId, request.reportType());
        return ReportResponse.from(saved);
    }

    /**
     * 진행 중인 매칭이 있으면 차단 사유로 종료한다.
     *
     * @param userId       차단자
     * @param targetUserId 대상
     * @return 종료된 매칭이 있으면 {@code true}
     */
    private boolean endActiveMatch(Long userId, Long targetUserId) {
        Optional<Match> match = matchRepository.findByUser1IdAndUser2Id(
                Math.min(userId, targetUserId), Math.max(userId, targetUserId));

        if (match.isPresent() && match.get().isActive()) {
            match.get().terminate(Match.STATUS_BLOCKED, userId, "BLOCK");
            return true;
        }
        return false;
    }

    /**
     * 대상 사용자가 유효한지 확인한다.
     *
     * @param userId       요청자
     * @param targetUserId 대상
     * @throws BusinessException 자기 자신이거나 존재하지 않는 사용자인 경우
     */
    private void validateTarget(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_ACTION_NOT_ALLOWED);
        }
        if (!userRepository.existsByIdAndDeleted(targetUserId, YesNo.N)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
