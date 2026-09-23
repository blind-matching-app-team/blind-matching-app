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
import com.bma.matching.dto.MatchingDtos.CurrentMatchResponse;
import com.bma.matching.dto.MatchingDtos.MatchResponse;
import com.bma.matching.dto.MatchingDtos.QueueResponse;
import com.bma.matching.dto.MatchingDtos.RematchResponse;
import com.bma.matching.dto.MatchingDtos.RecommendationResponse;
import com.bma.matching.dto.MatchingDtos.RevealSummary;
import com.bma.matching.entity.Match;
import com.bma.matching.entity.MatchQueue;
import com.bma.matching.entity.UserAction;
import com.bma.matching.repository.MatchQueueRepository;
import com.bma.matching.repository.MatchRepository;
import com.bma.matching.repository.MatchingQueryRepository;
import com.bma.matching.repository.UserActionRepository;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.service.NotificationService;
import com.bma.onboarding.entity.Question;
import com.bma.onboarding.entity.QuestionOption;
import com.bma.onboarding.entity.UserAnswer;
import com.bma.onboarding.repository.QuestionOptionRepository;
import com.bma.onboarding.repository.QuestionRepository;
import com.bma.onboarding.repository.UserAnswerRepository;
import com.bma.payment.entity.ItemLedger;
import com.bma.payment.entity.ItemType;
import com.bma.payment.service.ItemWalletService;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.bma.reveal.entity.RevealPolicy;
import com.bma.reveal.entity.RevealProgress;
import com.bma.reveal.repository.RevealPolicyRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
    private final RevealPolicyRepository revealPolicyRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final UserAnswerRepository answerRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;
    private final RevealService revealService;
    private final ProfileMaskingService maskingService;
    private final NotificationService notificationService;
    private final AppProperties properties;
    private final ItemWalletService walletService;

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
        if (!myProfile.isMatchable()) {
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
            notificationService.notify(targetUserId, NotificationEvent.MATCH_LIKED,
                    "누군가 회원님에게 호감을 보냈어요",
                    "받은 호감을 확인해 보세요.", "USER", userId);
            return new ActionResult(targetUserId, request.actionType(), false, null, null);
        }

        Match match = createOrGetMatch(userId, targetUserId);
        ChatRoom room = chatService.createRoomForMatch(match.getId(), userId, targetUserId);
        revealService.initializeProgress(match.getId());
        closeQueueEntries(userId, targetUserId);

        notificationService.notifyAll(List.of(userId, targetUserId),
                NotificationEvent.MATCH_CREATED,
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
     * <p>각 항목에 S5 카드가 필요로 하는 것(상대 마스킹 프로필, 공통관심사, Reveal 진행 요약,
     * 채팅방 ID)을 함께 싣는다. 진행 중인 매칭이 없으면 빈 배열이다.</p>
     *
     * @param userId 요청자
     * @return 진행 중인 매칭 목록(최신순)
     */
    public List<MatchResponse> getMyMatches(Long userId) {
        List<Match> matches = matchRepository.findActiveMatches(userId, Match.STATUS_ACTIVE);
        if (matches.isEmpty()) {
            return List.of();
        }
        return buildMatchResponses(userId, matches);
    }

    /**
     * 가장 최근 진행 중인 매칭 1건을 조회한다 (S5 메인 허브).
     *
     * @param userId 요청자
     * @return 매칭 유무 플래그와 최근 매칭. 없으면 {@code hasMatch=false, match=null}
     */
    public CurrentMatchResponse getCurrentMatch(Long userId) {
        List<Match> matches = matchRepository.findActiveMatches(userId, Match.STATUS_ACTIVE);
        if (matches.isEmpty()) {
            return CurrentMatchResponse.none();
        }
        // findActiveMatches 가 성사 일시 내림차순이라 첫 항목이 가장 최근이다.
        return CurrentMatchResponse.of(buildMatchResponses(userId, List.of(matches.get(0))).get(0));
    }

    /**
     * 매칭 목록을 카드 응답으로 조립한다. 부가 정보는 전부 일괄 조회해 N+1 을 피한다.
     *
     * @param userId  요청자
     * @param matches 진행 중인 매칭들
     * @return 응답 목록(입력 순서 유지)
     */
    private List<MatchResponse> buildMatchResponses(Long userId, List<Match> matches) {
        List<Long> matchIds = matches.stream().map(Match::getId).toList();
        List<Long> partnerIds = matches.stream().map(match -> match.partnerOf(userId)).toList();

        Map<Long, RevealProgress> progressByMatch = revealProgressRepository
                .findByIdInAndDeleted(matchIds, YesNo.N).stream()
                .collect(Collectors.toMap(RevealProgress::getId, Function.identity()));
        Map<Long, Long> roomIdByMatch = chatRoomRepository.findRoomsOfUser(userId).stream()
                .collect(Collectors.toMap(ChatRoom::getMatchId, ChatRoom::getId, (first, second) -> first));
        Map<Long, UserProfile> profileByUser = profileRepository.findAllById(partnerIds).stream()
                .filter(profile -> !profile.isDeleted())
                .collect(Collectors.toMap(UserProfile::getId, Function.identity()));
        Map<Long, List<ProfileImage>> imagesByOwner = maskingService.loadImagesByOwner(partnerIds);
        Map<Integer, RevealPolicy> policyByLevel = revealPolicyRepository
                .findByUseYnAndDeletedOrderByRevealLevelAsc(YesNo.Y, YesNo.N).stream()
                .collect(Collectors.toMap(RevealPolicy::getRevealLevel, Function.identity()));

        InterestCatalog interests = loadInterestCatalog();
        Set<Long> myInterests = selectedInterests(interests, userId);

        List<MatchResponse> responses = new ArrayList<>(matches.size());
        for (Match match : matches) {
            Long partnerId = match.partnerOf(userId);
            RevealProgress progress = progressByMatch.get(match.getId());
            int level = progress == null ? RevealPolicy.LEVEL_HIDDEN : progress.getCurrentLevel();

            UserProfile partnerProfile = profileByUser.get(partnerId);
            MaskedProfileResponse partner = partnerProfile == null ? null
                    : maskingService.mask(partnerProfile, imagesByOwner.getOrDefault(partnerId, List.of()), level);

            responses.add(new MatchResponse(
                    match.getId(),
                    partnerId,
                    match.getMatchStatus(),
                    match.getMatchType(),
                    match.getMatchDate(),
                    level,
                    roomIdByMatch.get(match.getId()),
                    partner,
                    interests.commonNames(myInterests, selectedInterests(interests, partnerId)),
                    summarizeReveal(progress, policyByLevel)));
        }
        return responses;
    }

    /**
     * Reveal 진행 요약을 만든다. S5-10 진행바가 이 값으로 그려진다.
     *
     * @param progress      진행 상태. 아직 없으면 {@code null}(단계 0, 대화 0 으로 본다)
     * @param policyByLevel 단계별 정책
     * @return 요약
     */
    private RevealSummary summarizeReveal(RevealProgress progress, Map<Integer, RevealPolicy> policyByLevel) {
        int level = progress == null ? RevealPolicy.LEVEL_HIDDEN : progress.getCurrentLevel();
        int messages = progress == null ? 0 : progress.getMessageCount();
        int minutes = progress == null ? 0 : progress.getChatMinutes();

        String currentName = Optional.ofNullable(policyByLevel.get(level))
                .map(RevealPolicy::getRevealName)
                .orElse("미공개");
        RevealPolicy next = policyByLevel.get(level + 1);
        if (next == null) {
            // 최고 단계. 더 올라갈 곳이 없으므로 진행률은 100 이다.
            return new RevealSummary(level, currentName, null, null, messages, null, minutes, null, 100, false);
        }
        return new RevealSummary(level, currentName,
                next.getRevealLevel(), next.getRevealName(),
                messages, next.getMinMessageCount(),
                minutes, next.getMinChatMinutes(),
                next.progressRate(messages, minutes),
                next.requiresMutualConsent());
    }

    /**
     * 관심사 문항의 보기 목록을 읽어 둔다. 공통관심사 계산에 쓴다.
     *
     * @return 관심사 카탈로그. 관심사 문항이 없으면 빈 카탈로그
     */
    private InterestCatalog loadInterestCatalog() {
        List<Long> questionIds = questionRepository
                .findByCategoryCodeAndUseYnAndDeleted(Question.CATEGORY_INTEREST, YesNo.Y, YesNo.N).stream()
                .map(Question::getId)
                .toList();
        if (questionIds.isEmpty()) {
            return new InterestCatalog(List.of(), List.of());
        }
        return new InterestCatalog(questionIds,
                optionRepository.findByQuestionIdInAndDeletedOrderBySortOrderAsc(questionIds, YesNo.N));
    }

    /**
     * 사용자가 관심사 문항에서 고른 보기 ID 집합.
     *
     * <p>논리 삭제된 답변(선택 해제)은 뺀다. 유니크 제약 때문에 재선택 시 같은 행을
     * 되살리는 구조라 삭제 플래그를 반드시 봐야 한다({@code OnboardingService} 참고).</p>
     *
     * @param catalog 관심사 카탈로그
     * @param userId  사용자
     * @return 보기 ID 집합. 관심사 문항이 없으면 빈 집합
     */
    private Set<Long> selectedInterests(InterestCatalog catalog, Long userId) {
        if (catalog.questionIds().isEmpty()) {
            return Set.of();
        }
        return answerRepository.findByUserIdAndQuestionIdIn(userId, catalog.questionIds()).stream()
                .filter(answer -> !answer.isDeleted() && answer.getOptionId() != null)
                .map(UserAnswer::getOptionId)
                .collect(Collectors.toSet());
    }

    /**
     * 관심사 문항·보기 묶음. 공통 항목을 계산한다.
     *
     * @param questionIds 관심사 문항 ID
     * @param options     관심사 보기(정렬된 상태)
     */
    private record InterestCatalog(List<Long> questionIds, List<QuestionOption> options) {

        /**
         * 두 집합에 모두 있는 보기의 이름을 보기 정렬 순서대로 돌려준다.
         *
         * @param mine    내 선택
         * @param partner 상대 선택
         * @return 공통 관심사 이름 목록
         */
        List<String> commonNames(Set<Long> mine, Set<Long> partner) {
            if (mine.isEmpty() || partner.isEmpty()) {
                return List.of();
            }
            return options.stream()
                    .filter(option -> mine.contains(option.getId()) && partner.contains(option.getId()))
                    .map(QuestionOption::getOptionText)
                    .toList();
        }
    }

    /**
     * 상대와의 매칭을 해제한다 (S5-16 매칭 그만두기).
     *
     * <p>신고·차단과 다른 완충 수단이다. 상대에게는 매칭이 끝났다는 알림만 가고
     * 누가·왜 끝냈는지는 알리지 않는다(사양서 S5-16: 구체적 사유 비공개).</p>
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @throws BusinessException 참여자가 아닌 경우
     */
    @Transactional
    public void unmatch(Long userId, Long matchId) {
        Match match = matchRepository.findByIdAndParticipant(matchId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        if (!match.isActive()) {
            // 이미 끝난 매칭을 다시 끝내도 상태는 같다. 알림이 두 번 가지 않게 여기서 멈춘다.
            return;
        }

        match.terminate(Match.STATUS_UNMATCHED, userId, "USER_REQUEST");
        chatRoomRepository.findByMatchIdAndDeleted(matchId, YesNo.N).ifPresent(ChatRoom::close);

        // S7-14 문구 그대로. 누가·왜 끝냈는지는 담지 않는다.
        notificationService.notify(match.partnerOf(userId), NotificationEvent.MATCH_ENDED,
                "매칭이 종료됐어요",
                "진행 중이던 대화가 마무리되었습니다. 새로운 매칭을 시작해 보세요.",
                "MATCH", matchId);

        log.info("매칭 해제: matchId={}, byUserId={}", matchId, userId);
    }

    /**
     * 매칭 대기열에 참여한다.
     *
     * <p>진입 재원(BMA-17 "매칭 기회"): 하루 무료 횟수({@code app.matching.daily-free-chances})가 남았으면
     * 무료로, 다 썼으면 매칭기회 이용권 1개를 소모한다. 둘 다 없으면 {@code PAY_006} 을 던져 프론트가
     * 구매 모달(소모형 탭)을 열게 한다. 이미 대기 중이면 새로 소모하지 않고 그 항목을 돌려준다.</p>
     *
     * @param userId 요청자
     * @return 대기열 상태
     * @throws BusinessException 프로필 미완성, 매칭 기회 소진
     */
    @Transactional
    public QueueResponse joinQueue(Long userId) {
        UserProfile profile = profileRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE));
        if (!profile.isMatchable()) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE);
        }

        MatchQueue existing = queueRepository
                .findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                .orElse(null);
        if (existing != null) {
            if (!existing.isExpired()) {
                return QueueResponse.from(existing);
            }
            // 대기 시간이 지난 항목은 만료 처리하고 새로 진입시킨다. 새 진입은 기회를 다시 소모한다.
            existing.expire();
        }
        return QueueResponse.from(enterQueue(userId, null));
    }

    /**
     * 재매칭권을 써서 현재 매칭을 끝내고 대기 없이 바로 대기열에 다시 들어간다 (S5-12, S10-19).
     *
     * <p>상대에게는 매칭 그만두기와 똑같이 S7-14 종료 알림만 간다. 실제 짝 배정은 대기열 매칭(BMA-66)이
     * 처리하며, 이 진입은 무료 일일 기회를 쓰지 않는다.</p>
     *
     * @param userId  요청자
     * @param matchId 끝낼 매칭
     * @return 결과
     * @throws BusinessException 매칭 없음, 재매칭권 없음
     */
    @Transactional
    public RematchResponse rematch(Long userId, Long matchId) {
        Match match = matchRepository.findByIdAndParticipant(matchId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        if (!match.isActive()) {
            throw new BusinessException(ErrorCode.MATCH_NOT_FOUND);
        }
        if (!walletService.consume(userId, ItemType.REMATCH_TICKET, ItemLedger.REF_MATCH, matchId)) {
            throw new BusinessException(ErrorCode.REMATCH_TICKET_EXHAUSTED);
        }

        match.terminate(Match.STATUS_UNMATCHED, userId, "REMATCH");
        chatRoomRepository.findByMatchIdAndDeleted(matchId, YesNo.N).ifPresent(ChatRoom::close);
        notificationService.notify(match.partnerOf(userId), NotificationEvent.MATCH_ENDED,
                "매칭이 종료됐어요",
                "진행 중이던 대화가 마무리되었습니다. 새로운 매칭을 시작해 보세요.",
                "MATCH", matchId);

        queueRepository.findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                .ifPresent(MatchQueue::cancel);
        MatchQueue queue = queueRepository.save(
                MatchQueue.enter(userId, properties.matching().queueExpireMinutes(), MatchQueue.SOURCE_REMATCH));

        log.info("재매칭권 사용: userId={}, endedMatchId={}, queueId={}", userId, matchId, queue.getId());
        return new RematchResponse(matchId, QueueResponse.from(queue),
                walletService.balance(userId, ItemType.REMATCH_TICKET));
    }

    /**
     * 오늘 무료 기회로 대기열에 들어간 횟수.
     *
     * @param userId 사용자
     * @return 횟수
     */
    public int freeChancesUsedToday(Long userId) {
        return (int) queueRepository.countByUserIdAndEnterDateGreaterThanEqualAndEntrySourceAndDeleted(
                userId, LocalDate.now().atStartOfDay(), MatchQueue.SOURCE_FREE, YesNo.N);
    }

    /**
     * 진입 재원을 정해 대기열 행을 만든다. 무료 기회 → 매칭기회 이용권 순서로 쓴다.
     */
    private MatchQueue enterQueue(Long userId, String forcedSource) {
        String source = forcedSource;
        if (source == null) {
            source = freeChancesUsedToday(userId) < properties.matching().dailyFreeChances()
                    ? MatchQueue.SOURCE_FREE : MatchQueue.SOURCE_ITEM;
        }
        MatchQueue queue = queueRepository.save(
                MatchQueue.enter(userId, properties.matching().queueExpireMinutes(), source));
        if (MatchQueue.SOURCE_ITEM.equals(source)
                && !walletService.consume(userId, ItemType.MATCH_CHANCE, ItemLedger.REF_MATCH_QUEUE, queue.getId())) {
            // 예외로 트랜잭션이 롤백되어 방금 만든 대기열 행도 사라진다.
            throw new BusinessException(ErrorCode.MATCH_CHANCE_EXHAUSTED);
        }
        return queue;
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
