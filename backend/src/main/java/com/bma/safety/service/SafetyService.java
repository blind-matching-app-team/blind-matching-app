package com.bma.safety.service;

import com.bma.chat.entity.ChatMessage;
import com.bma.chat.entity.ChatRoom;
import com.bma.chat.entity.ChatRoomMember;
import com.bma.chat.repository.ChatMessageRepository;
import com.bma.chat.repository.ChatRoomMemberRepository;
import com.bma.chat.repository.ChatRoomRepository;
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
 * 사용자 차단 및 신고 처리 (BMA-30 확정 정책, S11-08~12).
 *
 * <p>차단과 신고가 채팅방·매칭에 미치는 영향</p>
 * <ul>
 *   <li><b>차단</b>: 차단자만 채팅방에서 나가고 상대는 아무 변화가 없다. 매칭 행은 그대로 두어
 *       차단당한 쪽의 화면(매칭 목록·채팅방)이 바뀌지 않게 한다(차단 사실 비노출, S11-08).
 *       차단자의 매칭 목록에서는 빠지고 매칭 알고리즘에서 서로 영구 제외된다. 차단당한 쪽이 보내는
 *       메시지는 저장은 되지만 상대에게 전달되지 않는다({@code ChatService}).</li>
 *   <li><b>신고</b>: 신고자만 채팅방에서 나가고 상대는 무변화. 관리자가 유효로 판정하면 그때 상대에게
 *       안내한다(관리자 API 는 별도 티켓). 사기·부적절한 콘텐츠는 1회로 즉시 관리자 검토이며, 검토
 *       대기 중인 사용자는 앱은 평소처럼 쓰되 새 매칭에만 들어갈 수 없다.</li>
 *   <li><b>중복 신고</b>: 같은 신고자가 같은 대상을 매칭 1건당 1회만 신고할 수 있다. 매칭이 없는
 *       신고는 24시간 창으로 연타를 막는다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyService {

    /** 매칭이 없는 대상에 대한 중복 신고를 막는 기간(시간). */
    private static final int DUPLICATE_REPORT_WINDOW_HOURS = 24;

    private final UserBlockRepository blockRepository;
    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;

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

        // 차단자만 조용히 방에서 나간다. 매칭·방·상대 화면은 건드리지 않는다(S11-08).
        boolean left = findMatchBetween(userId, targetUserId)
                .map(match -> leaveRoomOfMatch(userId, match.getId()))
                .orElse(false);

        log.info("사용자 차단: userId={}, targetUserId={}, 채팅방 퇴장={}", userId, targetUserId, left);
        return new BlockResult(targetUserId, true, left);
    }

    /**
     * 차단을 해제한다.
     *
     * @param userId       차단자
     * @param targetUserId 대상
     * @return 처리 결과
     */
    @Transactional
    public BlockResult unblock(Long userId, Long targetUserId) {
        blockRepository.findByBlockUserIdAndTargetUserId(userId, targetUserId)
                .ifPresent(UserBlock::markDeleted);
        log.info("차단 해제: userId={}, targetUserId={}", userId, targetUserId);
        // 차단 해제는 나간 채팅방을 되돌리지 않는다. 상대가 새 메시지를 보내면 다시 나타난다.
        return new BlockResult(targetUserId, false, false);
    }

    /**
     * 내가 차단한 사용자 목록을 조회한다.
     *
     * @param userId 차단자
     * @return 차단 목록
     */
    public List<BlockedUserResponse> getBlockedUsers(Long userId) {
        return blockRepository.findByBlockUserIdAndDeleted(userId, YesNo.N).stream()
                .map(block -> new BlockedUserResponse(
                        block.getTargetUserId(), block.getBlockReason(), block.getBlockDate()))
                .toList();
    }

    /**
     * 두 사용자 사이에 어느 방향으로든 차단이 있는지 확인한다.
     *
     * @param userA 사용자 A
     * @param userB 사용자 B
     * @return 어느 방향으로든 차단이 있으면 {@code true}
     */
    public boolean isBlockedBetween(Long userA, Long userB) {
        return blockRepository.existsBlockBetween(userA, userB);
    }

    /**
     * 사용자를 신고한다 (S11-12 신고 제출, BMA-30 정책).
     *
     * @param userId  신고자
     * @param request 신고 내용(피신고자 포함)
     * @return 접수 결과
     * @throws BusinessException 자기 자신 신고, 존재하지 않는 대상, 남의 매칭·메시지 지정, 중복 신고인 경우
     */
    @Transactional
    public ReportResponse report(Long userId, ReportRequest request) {
        Long targetUserId = request.targetUserId();
        if (targetUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "피신고자 ID 는 필수입니다.");
        }
        validateTarget(userId, targetUserId);

        Long matchId = resolveMatchId(userId, targetUserId, request.matchId());
        checkDuplicate(userId, targetUserId, matchId);
        validateMessage(targetUserId, request.messageId());

        long countedBefore = reportRepository.countByTargetUserIdAndReportStatusInAndDeleted(
                targetUserId, UserReport.COUNTED_STATUSES, YesNo.N);
        UserReport saved = reportRepository.save(UserReport.of(userId, targetUserId, request.reportType(),
                request.description(), matchId, request.messageId(), countedBefore));

        // 신고자만 방에서 나간다. 상대는 무변화이며 관리자 판정 후에야 안내한다.
        boolean left = matchId != null && leaveRoomOfMatch(userId, matchId);

        log.info("신고 접수: reportId={}, userId={}, targetUserId={}, type={}, severity={}, status={}, matchId={}, 누적={}",
                saved.getId(), userId, targetUserId, saved.getReportType(), saved.getSeverity(),
                saved.getReportStatus(), matchId, countedBefore);
        return ReportResponse.from(saved, left);
    }

    /**
     * 사용자가 즉시검토(중대 신고) 대기 상태인지 확인한다.
     *
     * @param userId 사용자
     * @return 대기 중이면 {@code true}
     */
    public boolean isMatchingOnHold(Long userId) {
        return reportRepository.existsByTargetUserIdAndSeverityAndReportStatusAndDeleted(
                userId, UserReport.SEVERITY_SEVERE, UserReport.STATUS_PENDING_REVIEW, YesNo.N);
    }

    /**
     * 새 매칭(좋아요 성사, 대기열 진입, 재매칭)을 시작할 수 있는지 확인한다.
     *
     * @param userId 사용자
     * @throws BusinessException 즉시검토 대기 중이면 {@code MATCH_007}
     */
    public void assertCanStartMatching(Long userId) {
        if (isMatchingOnHold(userId)) {
            throw new BusinessException(ErrorCode.MATCHING_ON_HOLD);
        }
    }

    /**
     * 신고에 연결할 매칭을 정한다. 지정했으면 두 사람의 매칭이 맞는지 확인하고, 생략했으면 찾아 채운다.
     */
    private Long resolveMatchId(Long userId, Long targetUserId, Long requestedMatchId) {
        Optional<Match> pair = findMatchBetween(userId, targetUserId);
        if (requestedMatchId == null) {
            return pair.map(Match::getId).orElse(null);
        }
        if (pair.isEmpty() || !pair.get().getId().equals(requestedMatchId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "신고 대상과의 매칭이 아닙니다.");
        }
        return requestedMatchId;
    }

    /**
     * 매칭 1건당 1회 규칙(매칭이 없으면 24시간 창)으로 중복 신고를 막는다.
     */
    private void checkDuplicate(Long userId, Long targetUserId, Long matchId) {
        if (matchId != null) {
            if (reportRepository.existsByReportUserIdAndTargetUserIdAndMatchIdAndDeleted(
                    userId, targetUserId, matchId, YesNo.N)) {
                throw new BusinessException(ErrorCode.REPORT_DUPLICATED, "이 매칭에 대해 이미 신고했습니다.");
            }
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusHours(DUPLICATE_REPORT_WINDOW_HOURS);
        if (reportRepository.existsByReportUserIdAndTargetUserIdAndInsertDateAfterAndDeleted(
                userId, targetUserId, since, YesNo.N)) {
            throw new BusinessException(ErrorCode.REPORT_DUPLICATED,
                    DUPLICATE_REPORT_WINDOW_HOURS + "시간 내에 이미 신고한 사용자입니다.");
        }
    }

    /**
     * 메시지를 지정했다면 피신고자가 보낸 메시지여야 한다. 남의 메시지 ID 로 신고 근거를 꾸미는 것을 막는다.
     */
    private void validateMessage(Long targetUserId, Long messageId) {
        if (messageId == null) {
            return;
        }
        ChatMessage message = messageRepository.findById(messageId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "신고할 메시지를 찾을 수 없습니다."));
        if (!message.getSenderUserId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "신고 대상이 보낸 메시지가 아닙니다.");
        }
    }

    /**
     * 두 사용자의 매칭(상태 무관)을 찾는다. 매칭은 사용자 쌍당 1건이다.
     */
    private Optional<Match> findMatchBetween(Long userId, Long targetUserId) {
        return matchRepository.findByUser1IdAndUser2Id(
                        Math.min(userId, targetUserId), Math.max(userId, targetUserId))
                .filter(match -> !match.isDeleted());
    }

    /**
     * 매칭의 채팅방에서 나만 나간다(S6-11 나가기와 같은 처리). 읽음 위치를 마지막 메시지로 옮겨 두어
     * 상대가 새 메시지를 보내 방이 다시 나타나도 그 이후만 안읽음으로 센다.
     *
     * @return 실제로 나갔으면 {@code true}(이미 나갔거나 방이 없으면 {@code false})
     */
    private boolean leaveRoomOfMatch(Long userId, Long matchId) {
        Optional<ChatRoom> room = chatRoomRepository.findByMatchIdAndDeleted(matchId, YesNo.N);
        if (room.isEmpty()) {
            return false;
        }
        return memberRepository.findByChatRoomIdAndUserId(room.get().getId(), userId)
                .filter(ChatRoomMember::isActiveMember)
                .map(member -> {
                    member.updateLastRead(room.get().getLastMessageId());
                    member.leave();
                    return true;
                })
                .orElse(false);
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
