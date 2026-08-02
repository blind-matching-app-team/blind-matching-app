package com.bma.reveal.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.matching.entity.Match;
import com.bma.matching.repository.MatchRepository;
import com.bma.notification.entity.Notification;
import com.bma.notification.service.NotificationService;
import com.bma.reveal.dto.RevealDtos.ConsentRequest;
import com.bma.reveal.dto.RevealDtos.ConsentResult;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.bma.reveal.dto.RevealDtos.RevealProgressResponse;
import com.bma.reveal.entity.RevealConsent;
import com.bma.reveal.entity.RevealPolicy;
import com.bma.reveal.entity.RevealProgress;
import com.bma.reveal.repository.RevealConsentRepository;
import com.bma.reveal.repository.RevealPolicyRepository;
import com.bma.reveal.repository.RevealProgressRepository;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 블라인드 해제(Reveal) 단계 관리.
 *
 * <p>고친 점 — 기존 {@code RevealController}는 다음과 같은 상태였다.</p>
 * <ul>
 *   <li>{@code GET /reveal}이 매칭 참여자인지 확인하지 않아 <b>아무 matchId나 조회</b>할 수 있었고,
 *       존재하지 않는 매칭이면 빈 객체를 만들어 돌려줬다.</li>
 *   <li>{@code POST /reveal/consent}는 <b>요청을 그대로 에코</b>만 하고 아무것도 저장하지 않았다.
 *       {@code RV_REVEAL_CONSENT} 테이블은 사용되지 않았다.</li>
 *   <li>단계 상승 조건({@code RV_REVEAL_POLICY})을 전혀 읽지 않았다.</li>
 * </ul>
 *
 * <p>이제 참여자 검증 → 정책 조회 → 대화량 조건 확인 → (필요 시) 상호 동의 확인 →
 * 단계 상승 + 알림 발행 순서로 동작한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevealService {

    private final MatchRepository matchRepository;
    private final RevealProgressRepository progressRepository;
    private final RevealPolicyRepository policyRepository;
    private final RevealConsentRepository consentRepository;
    private final UserProfileRepository profileRepository;
    private final ProfileMaskingService maskingService;
    private final NotificationService notificationService;

    /**
     * 매칭의 공개 단계 진행 상태를 조회한다.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @return 진행 상태
     * @throws BusinessException 참여자가 아니거나 매칭이 없는 경우
     */
    public RevealProgressResponse getProgress(Long userId, Long matchId) {
        Match match = requireParticipant(userId, matchId);
        RevealProgress progress = requireProgress(matchId);

        int currentLevel = progress.getCurrentLevel();
        Optional<RevealPolicy> nextPolicy = findPolicy(currentLevel + 1);

        String currentLevelName = findPolicy(currentLevel)
                .map(RevealPolicy::getRevealName)
                .orElse("미공개");

        if (nextPolicy.isEmpty()) {
            // 이미 최고 단계에 도달한 경우.
            return new RevealProgressResponse(matchId, currentLevel, currentLevelName,
                    progress.getMessageCount(), progress.getChatMinutes(),
                    null, null, null, null,
                    true, false, null, null, progress.getLastLevelUpDate());
        }

        RevealPolicy policy = nextPolicy.get();
        Long partnerId = match.partnerOf(userId);

        return new RevealProgressResponse(
                matchId,
                currentLevel,
                currentLevelName,
                progress.getMessageCount(),
                progress.getChatMinutes(),
                policy.getRevealLevel(),
                policy.getRevealName(),
                policy.getMinMessageCount(),
                policy.getMinChatMinutes(),
                policy.isActivitySatisfied(progress.getMessageCount(), progress.getChatMinutes()),
                policy.requiresMutualConsent(),
                consentStatusOf(matchId, userId, policy.getRevealLevel()),
                consentStatusOf(matchId, partnerId, policy.getRevealLevel()),
                progress.getLastLevelUpDate());
    }

    /**
     * 공개 단계 동의를 기록하고, 조건이 충족되면 단계를 올린다.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @param request 동의 요청
     * @return 처리 결과
     * @throws BusinessException 참여자가 아니거나 단계/조건이 맞지 않는 경우
     */
    @Transactional
    public ConsentResult consent(Long userId, Long matchId, ConsentRequest request) {
        Match match = requireParticipant(userId, matchId);
        // 쓰기 트랜잭션이므로 여기서는 없으면 실제로 만들어 영속화한다.
        RevealProgress progress = progressRepository.findByIdAndDeleted(matchId, YesNo.N)
                .orElseGet(() -> progressRepository.save(RevealProgress.startFor(matchId)));
        int targetLevel = request.revealLevel();

        // 단계는 반드시 순서대로 올라가야 한다. 건너뛰기나 되돌리기는 허용하지 않는다.
        if (targetLevel != progress.getCurrentLevel() + 1) {
            throw new BusinessException(ErrorCode.REVEAL_LEVEL_INVALID,
                    "다음 단계(" + (progress.getCurrentLevel() + 1) + ")에만 동의할 수 있습니다.");
        }

        RevealPolicy policy = findPolicy(targetLevel)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVEAL_POLICY_NOT_FOUND));

        // 대화량 조건을 먼저 확인한다. 동의만으로 건너뛸 수 없다.
        if (!policy.isActivitySatisfied(progress.getMessageCount(), progress.getChatMinutes())) {
            throw new BusinessException(ErrorCode.REVEAL_CONDITION_NOT_MET,
                    "메시지 " + policy.getMinMessageCount() + "건, 대화 " + policy.getMinChatMinutes()
                            + "분 이상이어야 합니다. (현재 " + progress.getMessageCount() + "건, "
                            + progress.getChatMinutes() + "분)");
        }

        boolean accepted = Boolean.TRUE.equals(request.consent());
        RevealConsent consent = consentRepository
                .findByMatchIdAndUserIdAndRevealLevel(matchId, userId, targetLevel)
                .orElseGet(() -> RevealConsent.of(matchId, userId, targetLevel));
        consent.respond(accepted);
        consentRepository.save(consent);

        Long partnerId = match.partnerOf(userId);
        boolean leveledUp = false;

        if (accepted && isLevelUpAllowed(matchId, targetLevel, policy, userId, partnerId)) {
            progress.levelUp(targetLevel);
            leveledUp = true;

            notificationService.notifyAll(
                    List.of(userId, partnerId),
                    Notification.TYPE_REVEAL,
                    "프로필 공개 단계가 올라갔어요",
                    "'" + policy.getRevealName() + "' 단계로 상대의 정보가 더 공개되었습니다.",
                    "MATCH", matchId);

            log.info("공개 단계 상승: matchId={}, level={}", matchId, targetLevel);
        } else if (accepted) {
            // 내 동의만 기록된 상태. 상대에게 동의를 요청하는 알림을 보낸다.
            notificationService.notify(partnerId, Notification.TYPE_REVEAL,
                    "상대가 프로필 공개를 원해요",
                    "'" + policy.getRevealName() + "' 단계 공개에 동의하면 서로의 정보가 더 공개됩니다.",
                    "MATCH", matchId);
        }

        return new ConsentResult(matchId, targetLevel, accepted, leveledUp, progress.getCurrentLevel());
    }

    /**
     * 상대 프로필을 현재 공개 단계에 맞춰 마스킹해 반환한다.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @return 마스킹된 상대 프로필
     * @throws BusinessException 참여자가 아니거나 상대 프로필이 없는 경우
     */
    public MaskedProfileResponse getPartnerProfile(Long userId, Long matchId) {
        Match match = requireParticipant(userId, matchId);
        RevealProgress progress = requireProgress(matchId);
        Long partnerId = match.partnerOf(userId);

        UserProfile partnerProfile = profileRepository.findByIdAndDeleted(partnerId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

        return maskingService.mask(partnerProfile, progress.getCurrentLevel());
    }

    /**
     * 채팅 메시지 발생을 진행 상태에 반영한다.
     *
     * <p>{@code ChatService}가 메시지를 저장한 뒤 호출한다. 대화량이 쌓여야
     * 다음 공개 단계로 올라갈 수 있다.</p>
     *
     * @param matchId     매칭 ID
     * @param chatMinutes 첫 메시지 이후 경과 시간(분)
     */
    @Transactional
    public void recordMessage(Long matchId, int chatMinutes) {
        progressRepository.findByIdAndDeleted(matchId, YesNo.N)
                .ifPresent(progress -> progress.recordMessage(chatMinutes));
    }

    /**
     * 매칭 성사 시 진행 상태 행을 만든다.
     *
     * @param matchId 매칭 ID
     */
    @Transactional
    public void initializeProgress(Long matchId) {
        if (!progressRepository.existsById(matchId)) {
            progressRepository.save(RevealProgress.startFor(matchId));
        }
    }

    /**
     * 단계 상승이 가능한지 판정한다.
     *
     * @param matchId   매칭 ID
     * @param level     대상 단계
     * @param policy    해당 단계 정책
     * @param userId    요청자
     * @param partnerId 상대
     * @return 상승 가능하면 {@code true}
     */
    private boolean isLevelUpAllowed(Long matchId, int level, RevealPolicy policy,
                                     Long userId, Long partnerId) {
        if (!policy.requiresMutualConsent()) {
            // 상호 동의가 필요 없는 단계는 한쪽 동의 + 대화량 충족만으로 올라간다.
            return true;
        }
        // 상호 동의 단계는 양쪽 모두 ACCEPTED 여야 한다.
        List<RevealConsent> consents =
                consentRepository.findByMatchIdAndRevealLevelAndDeleted(matchId, level, YesNo.N);
        boolean mineAccepted = consents.stream()
                .anyMatch(c -> c.getUserId().equals(userId) && c.isAccepted());
        boolean partnerAccepted = consents.stream()
                .anyMatch(c -> c.getUserId().equals(partnerId) && c.isAccepted());
        return mineAccepted && partnerAccepted;
    }

    /**
     * 요청자가 해당 매칭의 참여자인지 확인하며 매칭을 조회한다.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @return 매칭
     * @throws BusinessException 참여자가 아니거나 매칭이 없는 경우
     */
    private Match requireParticipant(Long userId, Long matchId) {
        Match match = matchRepository.findByIdAndParticipant(matchId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        if (!match.isActive()) {
            throw new BusinessException(ErrorCode.MATCH_NOT_FOUND, "종료된 매칭입니다.");
        }
        return match;
    }

    /**
     * 진행 상태를 조회한다. 행이 아직 없으면 저장하지 않은 초기 상태를 돌려준다.
     *
     * <p>여기서 {@code save()}를 호출하면 안 된다. 이 메서드는 읽기 전용 트랜잭션
     * ({@code getProgress}, {@code getPartnerProfile})에서도 호출되는데,
     * 읽기 전용 트랜잭션은 플러시가 일어나지 않아 저장한 줄 알았던 값이 조용히 사라진다.
     * 실제 생성은 매칭 성사 시점의 {@link #initializeProgress(Long)}와
     * 쓰기 트랜잭션인 {@link #consent}에서만 수행한다.</p>
     *
     * @param matchId 매칭 ID
     * @return 진행 상태(영속 상태가 아닐 수 있음)
     */
    private RevealProgress requireProgress(Long matchId) {
        return progressRepository.findByIdAndDeleted(matchId, YesNo.N)
                .orElseGet(() -> RevealProgress.startFor(matchId));
    }

    /**
     * 사용 중인 정책을 조회한다.
     *
     * @param level 공개 단계
     * @return 정책
     */
    private Optional<RevealPolicy> findPolicy(int level) {
        return policyRepository.findByRevealLevelAndUseYnAndDeleted(level, YesNo.Y, YesNo.N);
    }

    /**
     * 특정 참여자의 동의 상태를 문자열로 반환한다.
     *
     * @param matchId 매칭 ID
     * @param userId  사용자 ID
     * @param level   공개 단계
     * @return 동의 상태. 기록이 없으면 {@code PENDING}
     */
    private String consentStatusOf(Long matchId, Long userId, Integer level) {
        return consentRepository.findByMatchIdAndUserIdAndRevealLevel(matchId, userId, level)
                .filter(consent -> !consent.isDeleted())
                .map(RevealConsent::getConsentStatus)
                .orElse(RevealConsent.STATUS_PENDING);
    }
}
