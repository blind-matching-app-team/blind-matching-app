package com.bma.matching.service;

import com.bma.chat.entity.ChatRoom;
import com.bma.chat.repository.ChatRoomRepository;
import com.bma.chat.service.ChatService;
import com.bma.common.config.AppProperties;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.matching.dto.MatchingDtos.ActionRequest;
import com.bma.matching.dto.MatchingDtos.ActionResult;
import com.bma.matching.dto.MatchingDtos.MatchResponse;
import com.bma.matching.dto.MatchingDtos.QueueResponse;
import com.bma.matching.dto.MatchingDtos.RecommendationResponse;
import com.bma.matching.entity.Match;
import com.bma.matching.entity.MatchQueue;
import com.bma.matching.entity.UserAction;
import com.bma.matching.repository.MatchQueueRepository;
import com.bma.matching.repository.MatchRepository;
import com.bma.matching.repository.MatchingQueryRepository;
import com.bma.matching.repository.UserActionRepository;
import com.bma.notification.entity.Notification;
import com.bma.notification.service.NotificationService;
import com.bma.reveal.entity.RevealPolicy;
import com.bma.reveal.entity.RevealProgress;
import com.bma.reveal.repository.RevealProgressRepository;
import com.bma.reveal.service.ProfileMaskingService;
import com.bma.reveal.service.RevealService;
import com.bma.safety.repository.UserBlockRepository;
import com.bma.user.entity.ProfileImage;
import com.bma.user.entity.UserPreference;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.UserPreferenceRepository;
import com.bma.user.repository.UserProfileRepository;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 추천, 좋아요/패스, 상호 매칭 성사, 대기열 처리.
 *
 * <p>고친 점 — 기존 {@code MatchingController}에는 매칭 앱의 핵심 로직이 비어 있었다.</p>
 * <ul>
 *   <li>{@code boolean mutual = false;}로 하드코딩되어 <b>상호 매칭이 절대 성사되지 않았다</b>.
 *       {@code MT_MATCH} 레코드도, 채팅방도 만들어지지 않았다.</li>
 *   <li>{@code UK_MT_USER_ACTION} 유니크 제약 때문에 <b>같은 상대에게 두 번 액션하면 500</b>이었다.</li>
 *   <li>추천에 선호 조건·차단·중복 제외가 전혀 적용되지 않았다.</li>
 *   <li>추천 응답이 프로필 엔티티 원본이라 매칭 전인데도 신원 정보가 모두 노출됐다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private final MatchRepository matchRepository;
    private final MatchQueueRepository queueRepository;
    private final UserActionRepository actionRepository;
    private final MatchingQueryRepository matchingQueryRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final UserBlockRepository blockRepository;
    private final RevealProgressRepository revealProgressRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;
    private final RevealService revealService;
    private final ProfileMaskingService maskingService;
    private final NotificationService notificationService;
    private final AppProperties properties;

    /**
     * 추천 후보를 조회한다.
     *
     * <p>반환되는 프로필은 항상 공개 단계 0으로 마스킹된다.</p>
     *
     * @param userId       요청자
     * @param size         조회 건수
     * @param cursorScore  커서: 직전 페이지 마지막 완성도 점수
     * @param cursorUserId 커서: 직전 페이지 마지막 사용자 ID
     * @return 추천 목록
     * @throws BusinessException 프로필이 완성되지 않은 경우
     */
    public List<RecommendationResponse> getRecommendations(Long userId, int size,
                                                           Integer cursorScore, Long cursorUserId) {
        // 내 프로필이 완성되지 않았으면 추천을 받을 수 없다(일방적 열람 방지).
        UserProfile myProfile = profileRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE));
        if (!myProfile.isComplete()) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE);
        }

        UserPreference preference = preferenceRepository.findByIdAndDeleted(userId, YesNo.N).orElse(null);
        int limit = Math.min(Math.max(size, 1), properties.matching().recommendationMaxSize());

        List<UserProfile> candidates = matchingQueryRepository.findRecommendations(
                userId, preference, collectExcludedUserIds(userId), cursorScore, cursorUserId, limit);

        if (candidates.isEmpty()) {
            return List.of();
        }

        // 이미지도 한 번에 조회해 N+1을 피한다.
        List<Long> candidateIds = candidates.stream().map(UserProfile::getId).toList();
        Map<Long, List<ProfileImage>> imagesByOwner = maskingService.loadImagesByOwner(candidateIds);

        return maskingService.maskAll(candidates, imagesByOwner, RevealPolicy.LEVEL_HIDDEN).stream()
                .map(RecommendationResponse::new)
                .toList();
    }

    /**
     * 추천 상대에게 좋아요/슈퍼좋아요/패스를 남긴다.
     *
     * <p>상대가 이미 나에게 호감을 표시한 상태라면 이 시점에 매칭이 성사되고,
     * 채팅방과 공개 단계 진행 상태가 함께 만들어진다.</p>
     *
     * @param userId  행동 주체
     * @param request 액션 요청
     * @return 처리 결과(매칭 성사 여부 포함)
     * @throws BusinessException 자기 자신 대상, 존재하지 않는 사용자, 차단 관계인 경우
     */
    @Transactional
    public ActionResult act(Long userId, ActionRequest request) {
        Long targetUserId = request.targetUserId();

        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_ACTION_NOT_ALLOWED);
        }
        if (!userRepository.existsByIdAndDeleted(targetUserId, YesNo.N)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (blockRepository.existsBlockBetween(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.BLOCKED_RELATION);
        }

        // 유니크 제약(FROM_USER_ID, TO_USER_ID) 때문에 재삽입이 불가능하므로 기존 행을 갱신한다.
        UserAction action = actionRepository.findByFromUserIdAndToUserId(userId, targetUserId)
                .map(existing -> {
                    existing.update(request.actionType(), request.message());
                    return existing;
                })
                .orElseGet(() -> UserAction.of(userId, targetUserId,
                        request.actionType(), request.message()));
        actionRepository.save(action);

        // 패스는 매칭으로 이어지지 않는다.
        if (!action.isPositive()) {
            return new ActionResult(targetUserId, request.actionType(), false, null, null);
        }

        // 상대도 나에게 호감을 표시했는지 확인한다. 이것이 상호 매칭 판정의 핵심이다.
        boolean mutual = actionRepository.findByFromUserIdAndToUserId(targetUserId, userId)
                .map(UserAction::isPositive)
                .orElse(false);

        if (!mutual) {
            // 아직 한쪽만 호감을 표시한 상태. 상대에게 알림만 보낸다.
            notificationService.notify(targetUserId, Notification.TYPE_MATCH,
                    "누군가 회원님에게 호감을 보냈어요",
                    "받은 호감을 확인해 보세요.", "USER", userId);
            return new ActionResult(targetUserId, request.actionType(), false, null, null);
        }

        Match match = createOrGetMatch(userId, targetUserId);
        ChatRoom room = chatService.createRoomForMatch(match.getId(), userId, targetUserId);
        revealService.initializeProgress(match.getId());
        closeQueueEntries(userId, targetUserId);

        notificationService.notifyAll(List.of(userId, targetUserId),
                Notification.TYPE_MATCH,
                "매칭이 성사되었어요!",
                "이제 대화를 시작할 수 있습니다. 대화를 나눌수록 상대의 프로필이 더 공개됩니다.",
                "MATCH", match.getId());

        log.info("매칭 성사: matchId={}, users=[{}, {}], roomId={}",
                match.getId(), userId, targetUserId, room.getId());

        return new ActionResult(targetUserId, request.actionType(), true, match.getId(), room.getId());
    }

    /**
     * 내 매칭 목록을 조회한다.
     *
     * @param userId 요청자
     * @return 진행 중인 매칭 목록
     */
    public List<MatchResponse> getMyMatches(Long userId) {
        List<Match> matches = matchRepository.findActiveMatches(userId, Match.STATUS_ACTIVE);
        if (matches.isEmpty()) {
            return List.of();
        }

        List<Long> matchIds = matches.stream().map(Match::getId).toList();

        // 공개 단계와 채팅방을 한 번에 조회해 N+1을 피한다.
        Map<Long, Integer> levelByMatch = revealProgressRepository.findByIdInAndDeleted(matchIds, YesNo.N)
                .stream()
                .collect(Collectors.toMap(RevealProgress::getId, RevealProgress::getCurrentLevel));
        Map<Long, Long> roomIdByMatch = chatRoomRepository.findRoomsOfUser(userId).stream()
                .collect(Collectors.toMap(ChatRoom::getMatchId, ChatRoom::getId, (first, second) -> first));

        return matches.stream()
                .map(match -> MatchResponse.of(match, userId,
                        levelByMatch.getOrDefault(match.getId(), RevealPolicy.LEVEL_HIDDEN),
                        roomIdByMatch.get(match.getId())))
                .toList();
    }

    /**
     * 상대와의 매칭을 해제한다.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @throws BusinessException 참여자가 아닌 경우
     */
    @Transactional
    public void unmatch(Long userId, Long matchId) {
        Match match = matchRepository.findByIdAndParticipant(matchId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));

        match.terminate(Match.STATUS_UNMATCHED, userId, "USER_REQUEST");
        chatRoomRepository.findByMatchIdAndDeleted(matchId, YesNo.N).ifPresent(ChatRoom::close);

        log.info("매칭 해제: matchId={}, byUserId={}", matchId, userId);
    }

    /**
     * 매칭 대기열에 참여한다.
     *
     * @param userId 요청자
     * @return 대기열 상태
     * @throws BusinessException 프로필이 완성되지 않은 경우
     */
    @Transactional
    public QueueResponse joinQueue(Long userId) {
        UserProfile profile = profileRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE));
        if (!profile.isComplete()) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE);
        }

        MatchQueue queue = queueRepository
                .findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                .map(existing -> {
                    // 대기 시간이 지난 항목은 만료 처리하고 새로 진입시킨다.
                    if (existing.isExpired()) {
                        existing.expire();
                        return queueRepository.save(
                                MatchQueue.enter(userId, properties.matching().queueExpireMinutes()));
                    }
                    return existing;
                })
                .orElseGet(() -> queueRepository.save(
                        MatchQueue.enter(userId, properties.matching().queueExpireMinutes())));

        return QueueResponse.from(queue);
    }

    /**
     * 매칭 대기열 참여를 취소한다.
     *
     * @param userId 요청자
     */
    @Transactional
    public void cancelQueue(Long userId) {
        queueRepository
                .findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                .ifPresent(MatchQueue::cancel);
    }

    /**
     * 매칭을 만들거나, 이미 있으면 그것을 되살려 반환한다.
     *
     * <p>{@code UK_MT_MATCH_USERS}가 (작은 ID, 큰 ID) 기준이므로 정렬된 값으로 조회한다.
     * 해제 후 다시 서로 좋아요를 누른 경우에는 기존 행을 재활성화한다.</p>
     *
     * @param userIdA 참여자 A
     * @param userIdB 참여자 B
     * @return 매칭
     */
    private Match createOrGetMatch(Long userIdA, Long userIdB) {
        Long smaller = Math.min(userIdA, userIdB);
        Long larger = Math.max(userIdA, userIdB);

        Optional<Match> existing = matchRepository.findByUser1IdAndUser2Id(smaller, larger);
        if (existing.isPresent()) {
            Match match = existing.get();
            if (!match.isActive()) {
                match.setMatchStatus(Match.STATUS_ACTIVE);
                match.setEndDate(null);
                match.setEndUserId(null);
                match.setEndReasonCode(null);
                match.restore();
            }
            return match;
        }
        return matchRepository.save(Match.between(smaller, larger, Match.TYPE_LIKE));
    }

    /**
     * 매칭이 성사된 두 사용자의 대기열 항목을 종료 처리한다.
     *
     * @param userIdA 참여자 A
     * @param userIdB 참여자 B
     */
    private void closeQueueEntries(Long userIdA, Long userIdB) {
        List.of(userIdA, userIdB).forEach(userId -> queueRepository
                .findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                .ifPresent(queue -> queue.setQueueStatus(MatchQueue.STATUS_MATCHED)));
    }

    /**
     * 추천에서 제외할 사용자 ID를 모은다.
     *
     * <p>이미 좋아요/패스한 상대, 차단 관계인 상대, 이미 매칭된 상대가 대상이다.</p>
     *
     * @param userId 요청자
     * @return 제외 대상 ID 집합
     */
    private Set<Long> collectExcludedUserIds(Long userId) {
        Set<Long> excluded = new HashSet<>();
        excluded.addAll(actionRepository.findActedUserIds(userId, YesNo.N));
        excluded.addAll(blockRepository.findRelatedUserIds(userId));
        excluded.addAll(matchRepository.findPartnerIds(userId));
        return excluded;
    }
}
