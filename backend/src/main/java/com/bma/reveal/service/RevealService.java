package com.bma.reveal.service;

import com.bma.chat.repository.ChatMessageRepository;
import com.bma.chat.repository.ChatRoomRepository;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.matching.entity.Match;
import com.bma.matching.repository.MatchRepository;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.service.NotificationService;
import com.bma.payment.service.SubscriptionService;
import com.bma.reveal.dto.RevealDtos.ConsentRequest;
import com.bma.reveal.dto.RevealDtos.ConsentResult;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.bma.reveal.dto.RevealDtos.RevealActionResponse;
import com.bma.reveal.dto.RevealDtos.RevealStatusResponse;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 블라인드 해제(Reveal) 단계 관리 (S10 사양서 v1.6, BMA-19 안건1 확정).
 *
 * <p>단계 전환 규칙(정책 행 {@code RV_REVEAL_POLICY} 기준):</p>
 * <ol>
 *   <li>매칭 후 {@code MIN_HOURS_SINCE_MATCH}(24시간) 경과 — 정밀매칭 구독자(한쪽이라도)는 스킵.</li>
 *   <li>양측 각자 {@code MIN_MESSAGES_PER_USER}(10개) 이상 보냄(합산 20).</li>
 *   <li>양쪽 상호 동의. 요청 = 동의이며, 상대가 동의하는 순간 단계가 올라간다.</li>
 * </ol>
 *
 * <p>"나중에"(S10-17)는 아무것도 기록하지 않는다. 명시적 거절 상태는 저장하지도 노출하지도 않는다(BMA-19 안건4 원칙).
 * 구독 여부도 응답 어디에도 드러나지 않는다.</p>
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
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository messageRepository;
    private final ProfileMaskingService maskingService;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    /**
     * S10 화면 상태를 조회한다.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @return 상태
     * @throws BusinessException 참여자가 아니거나 매칭이 없는 경우
     */
    public RevealStatusResponse getStatus(Long userId, Long matchId) {
        Match match = requireParticipant(userId, matchId);
        return buildStatus(match, userId, requireProgress(matchId));
    }

    /**
     * 다음 단계를 요청한다 (S10-12 칩). 요청은 곧 내 동의다.
     *
     * <p>상대가 이미 동의(요청)해 두었으면 이 호출로 단계가 올라간다. 아니면 상대에게 S7-12 알림을 보낸다
     * (이미 보냈으면 다시 보내지 않는다).</p>
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @return 처리 결과
     * @throws BusinessException 조건 미충족(409 REVEAL_002), 최고 단계(400 REVEAL_003)
     */
    @Transactional
    public RevealActionResponse request(Long userId, Long matchId) {
        Match match = requireParticipant(userId, matchId);
        RevealProgress progress = progressRepository.findByIdAndDeleted(matchId, YesNo.N)
                .orElseGet(() -> progressRepository.save(RevealProgress.startFor(matchId)));
        return accept(match, userId, progress);
    }

    /**
     * 상대의 요청에 응답한다 (S10-16 동의하고 열기 / S10-17 나중에).
     *
     * @param userId   응답자
     * @param matchId  매칭 ID
     * @param accepted {@code true}=동의. {@code false}=나중에(기록 없음)
     * @return 처리 결과
     */
    @Transactional
    public RevealActionResponse consent(Long userId, Long matchId, boolean accepted) {
        Match match = requireParticipant(userId, matchId);
        RevealProgress progress = progressRepository.findByIdAndDeleted(matchId, YesNo.N)
                .orElseGet(() -> progressRepository.save(RevealProgress.startFor(matchId)));
        if (!accepted) {
            // "나중에": 무응답과 같다. 상대에게 어떤 상태 변화도 보이지 않는다.
            return new RevealActionResponse(false, buildStatus(match, userId, progress));
        }
        return accept(match, userId, progress);
    }

    /**
     * (구) 단계를 지정한 동의. {@code POST /reveal/consent} 호환.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @param request 단계·동의 여부
     * @return 처리 결과
     */
    @Transactional
    public ConsentResult consentLegacy(Long userId, Long matchId, ConsentRequest request) {
        Match match = requireParticipant(userId, matchId);
        RevealProgress progress = progressRepository.findByIdAndDeleted(matchId, YesNo.N)
                .orElseGet(() -> progressRepository.save(RevealProgress.startFor(matchId)));
        int targetLevel = request.revealLevel();
        // 단계는 반드시 순서대로 올라가야 한다. 건너뛰기나 되돌리기는 허용하지 않는다.
        if (targetLevel != progress.getCurrentLevel() + 1) {
            throw new BusinessException(ErrorCode.REVEAL_LEVEL_INVALID,
                    "다음 단계(" + (progress.getCurrentLevel() + 1) + ")에만 동의할 수 있습니다.");
        }
        boolean accepted = Boolean.TRUE.equals(request.consent());
        RevealActionResponse result = accepted
                ? accept(match, userId, progress)
                : new RevealActionResponse(false, buildStatus(match, userId, progress));
        return new ConsentResult(matchId, targetLevel, accepted, result.leveledUp(), progress.getCurrentLevel());
    }

    /**
     * 상대 프로필을 현재 공개 단계에 맞춰 마스킹해 반환한다.
     *
     * @param userId  요청자
     * @param matchId 매칭 ID
     * @return 마스킹된 상대 프로필
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
     * 채팅 메시지 발생을 진행 상태(합산 카운트)에 반영한다. {@code ChatService} 가 저장 후 호출한다.
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

    // ── 내부 ────────────────────────────────────────────────────────────────

    /**
     * 내 동의를 기록하고, 조건과 상대 동의가 갖춰졌으면 단계를 올린다.
     */
    private RevealActionResponse accept(Match match, Long userId, RevealProgress progress) {
        Long matchId = match.getId();
        Long partnerId = match.partnerOf(userId);
        int targetLevel = progress.getCurrentLevel() + 1;
        RevealPolicy policy = findPolicy(targetLevel)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVEAL_LEVEL_INVALID, "이미 전체 공개 단계입니다."));

        Conditions c = evaluate(match, userId, partnerId, policy, progress);
        if (!c.messagesSatisfied()) {
            throw new BusinessException(ErrorCode.REVEAL_CONDITION_NOT_MET,
                    "양쪽 모두 메시지 " + policy.getMinMessagesPerUser() + "개 이상이어야 합니다. (나 "
                            + c.myMessages() + "개, 상대 " + c.partnerMessages() + "개)");
        }
        boolean partnerAlreadyAccepted = consentRepository
                .findByMatchIdAndUserIdAndRevealLevel(matchId, partnerId, targetLevel)
                .map(RevealConsent::isAccepted)
                .orElse(false);
        // 시간 조건: 실제 경과 || 내 구독 || (상대의 요청에 동의하는 경우 상대의 구독)
        if (!c.hoursSatisfied() && !c.hoursSkipped() && !(partnerAlreadyAccepted && c.hoursSkippedForMatch())) {
            throw new BusinessException(ErrorCode.REVEAL_CONDITION_NOT_MET,
                    "매칭 후 " + policy.getMinHoursSinceMatch() + "시간이 지나야 합니다. (약 "
                            + (c.hoursRemainingMinutes() / 60) + "시간 " + (c.hoursRemainingMinutes() % 60) + "분 남음)");
        }

        RevealConsent mine = consentRepository.findByMatchIdAndUserIdAndRevealLevel(matchId, userId, targetLevel)
                .orElseGet(() -> RevealConsent.of(matchId, userId, targetLevel));
        boolean alreadyAccepted = mine.isAccepted();
        mine.respond(true);
        consentRepository.save(mine);

        boolean partnerAccepted = partnerAlreadyAccepted;

        boolean leveledUp = false;
        if (partnerAccepted || !policy.requiresMutualConsent()) {
            progress.levelUp(targetLevel);
            leveledUp = true;
            notificationService.notifyAll(List.of(userId, partnerId), NotificationEvent.REVEAL_LEVEL_UP,
                    "프로필 공개 단계가 올라갔어요",
                    "'" + policy.getRevealName() + "' 단계로 상대의 정보가 더 공개되었습니다.",
                    "MATCH", matchId);
            log.info("공개 단계 상승: matchId={}, level={}", matchId, targetLevel);
        } else if (!alreadyAccepted) {
            // 처음 요청할 때만 상대에게 S7-12 알림. 상대는 아직 블라인드 상태라 "???" 로 부른다.
            notificationService.notify(partnerId, NotificationEvent.REVEAL_REQUESTED,
                    "???님이 다음 단계를 요청했어요",
                    "동의하면 서로 조금 더 선명하게 보여요.",
                    "MATCH", matchId);
            log.info("공개 단계 요청: matchId={}, from={}, level={}", matchId, userId, targetLevel);
        }
        return new RevealActionResponse(leveledUp, buildStatus(match, userId, progress));
    }

    private RevealStatusResponse buildStatus(Match match, Long userId, RevealProgress progress) {
        Long matchId = match.getId();
        Long partnerId = match.partnerOf(userId);
        int level = progress.getCurrentLevel();
        String currentName = findPolicy(level).map(RevealPolicy::getRevealName).orElse("실루엣");
        Optional<RevealPolicy> next = findPolicy(level + 1);
        if (next.isEmpty()) {
            MessageCounts m = countMessages(match, userId, partnerId);
            return new RevealStatusResponse(matchId, level, currentName, null, null, true, match.getMatchDate(),
                    null, true, 0, m.mine(), m.partner(), null, true, 0, 0, m.mine() + m.partner(), null, 100,
                    false, RevealConsent.STATUS_ACCEPTED, RevealConsent.STATUS_ACCEPTED, false, null,
                    progress.getLastLevelUpDate());
        }
        RevealPolicy policy = next.get();
        Conditions c = evaluate(match, userId, partnerId, policy, progress);
        RevealConsent mine = consentRepository.findByMatchIdAndUserIdAndRevealLevel(matchId, userId, policy.getRevealLevel())
                .filter(RevealConsent::isAccepted).orElse(null);
        RevealConsent partner = consentRepository.findByMatchIdAndUserIdAndRevealLevel(matchId, partnerId, policy.getRevealLevel())
                .filter(RevealConsent::isAccepted).orElse(null);
        boolean incoming = partner != null && mine == null;
        boolean canRequest = mine == null && c.messagesSatisfied() && (c.hoursSatisfied() || c.hoursSkipped());

        return new RevealStatusResponse(matchId, level, currentName, policy.getRevealLevel(), policy.getRevealName(),
                false, match.getMatchDate(), policy.getMinHoursSinceMatch(), c.hoursSatisfied(), c.hoursRemainingMinutes(),
                c.myMessages(), c.partnerMessages(), policy.getMinMessagesPerUser(), c.messagesSatisfied(),
                Math.max(policy.getMinMessagesPerUser() - c.myMessages(), 0),
                Math.max(policy.getMinMessagesPerUser() - c.partnerMessages(), 0),
                c.myMessages() + c.partnerMessages(), policy.getMinMessageCount(),
                progressRate(c, policy),
                canRequest,
                mine == null ? RevealConsent.STATUS_PENDING : RevealConsent.STATUS_ACCEPTED,
                partner == null ? RevealConsent.STATUS_PENDING : RevealConsent.STATUS_ACCEPTED,
                incoming, partner == null ? null : partner.getResponseDate(),
                progress.getLastLevelUpDate());
    }

    /** 조건 평가 결과. */
    private record Conditions(int myMessages, int partnerMessages, boolean messagesSatisfied,
                              boolean hoursSatisfied, boolean hoursSkipped, boolean hoursSkippedForMatch,
                              long hoursRemainingMinutes) {
    }

    private record MessageCounts(int mine, int partner) {
    }

    private Conditions evaluate(Match match, Long userId, Long partnerId, RevealPolicy policy, RevealProgress progress) {
        MessageCounts m = countMessages(match, userId, partnerId);
        int perUser = policy.getMinMessagesPerUser() == null ? 0 : policy.getMinMessagesPerUser();
        boolean messages = m.mine() >= perUser && m.partner() >= perUser;

        int hours = policy.getMinHoursSinceMatch() == null ? 0 : policy.getMinHoursSinceMatch();
        LocalDateTime due = match.getMatchDate().plusHours(hours);
        long remaining = Math.max(Duration.between(LocalDateTime.now(), due).toMinutes(), 0);
        boolean hoursOk = remaining == 0;
        // 정밀매칭 구독은 한쪽만 해도 유효하다(BMA-19 안건4). 다만 "내가 지금 요청할 수 있는가"는 내 구독만 보고,
        // 상대 구독은 상대의 요청에 동의할 때만 적용한다 — 비구독자 화면에서 상대의 구독을 유추하지 못하게.
        boolean skippedForMe = !hoursOk && hours > 0 && subscriptionService.isSubscribed(userId);
        boolean skippedForMatch = skippedForMe || (!hoursOk && hours > 0 && subscriptionService.isSubscribed(partnerId));
        return new Conditions(m.mine(), m.partner(), messages, hoursOk, skippedForMe, skippedForMatch, remaining);
    }

    private MessageCounts countMessages(Match match, Long userId, Long partnerId) {
        return chatRoomRepository.findByMatchIdAndDeleted(match.getId(), YesNo.N)
                .map(room -> new MessageCounts(
                        (int) messageRepository.countByChatRoomIdAndSenderUserIdAndDeleted(room.getId(), userId, YesNo.N),
                        (int) messageRepository.countByChatRoomIdAndSenderUserIdAndDeleted(room.getId(), partnerId, YesNo.N)))
                .orElse(new MessageCounts(0, 0));
    }

    /**
     * 진행률: 시간·양측 메시지 세 조건 중 가장 덜 채워진 쪽. 둘 다 채워야 올라가므로 평균을 쓰면 착시가 생긴다.
     */
    private int progressRate(Conditions c, RevealPolicy policy) {
        int perUser = policy.getMinMessagesPerUser() == null ? 0 : policy.getMinMessagesPerUser();
        double mine = perUser == 0 ? 1.0 : Math.min(1.0, (double) c.myMessages() / perUser);
        double partner = perUser == 0 ? 1.0 : Math.min(1.0, (double) c.partnerMessages() / perUser);
        int hours = policy.getMinHoursSinceMatch() == null ? 0 : policy.getMinHoursSinceMatch();
        double time = (c.hoursSatisfied() || c.hoursSkipped() || hours == 0) ? 1.0
                : Math.min(1.0, 1.0 - (double) c.hoursRemainingMinutes() / (hours * 60.0));
        return (int) Math.floor(Math.min(Math.min(mine, partner), time) * 100);
    }

    private Match requireParticipant(Long userId, Long matchId) {
        Match match = matchRepository.findByIdAndParticipant(matchId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        if (!match.isActive()) {
            throw new BusinessException(ErrorCode.MATCH_NOT_FOUND, "종료된 매칭입니다.");
        }
        return match;
    }

    /**
     * 진행 상태를 조회한다. 행이 없으면 저장하지 않은 초기 상태를 돌려준다(읽기 전용 경로에서 save 금지).
     */
    private RevealProgress requireProgress(Long matchId) {
        return progressRepository.findByIdAndDeleted(matchId, YesNo.N)
                .orElseGet(() -> RevealProgress.startFor(matchId));
    }

    private Optional<RevealPolicy> findPolicy(int level) {
        return policyRepository.findByRevealLevelAndUseYnAndDeleted(level, YesNo.Y, YesNo.N);
    }
}
