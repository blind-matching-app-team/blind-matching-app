package com.bma.matching.service;

import com.bma.chat.repository.ChatRoomRepository;
import com.bma.common.entity.Region;
import com.bma.common.entity.YesNo;
import com.bma.common.service.RegionService;
import com.bma.matching.dto.MatchingDtos.QueueStatusResponse;
import com.bma.matching.entity.Match;
import com.bma.matching.entity.MatchQueue;
import com.bma.matching.repository.MatchQueueRepository;
import com.bma.matching.repository.MatchRepository;
import com.bma.payment.entity.ItemLedger;
import com.bma.payment.entity.ItemType;
import com.bma.payment.service.ItemWalletService;
import com.bma.payment.service.SubscriptionService;
import com.bma.safety.repository.UserBlockRepository;
import com.bma.safety.service.SafetyService;
import com.bma.user.entity.UserPreference;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.UserPreferenceRepository;
import com.bma.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * S9 대기열 매칭 엔진 (BMA-66).
 *
 * <ul>
 *   <li>짝 찾기: 대기 중인 상대를 진입 순서(FIFO)로 훑어 <b>서로의 선호 조건을 모두 만족</b>하고 차단·기존 매칭이
 *       없는 첫 상대와 매칭한다. 요청자가 정밀매칭 구독 중이면 조건을 만족하는 후보 중 공통 관심사가 가장 많은
 *       상대를 고른다(BMA-84 구독 혜택).</li>
 *   <li>타임아웃: 진입 후 {@code app.matching.queue-expire-minutes}(S9 확정 5분)이 지나면 EXPIRED 로 바꾸고,
 *       소모한 이용권(매칭기회·재매칭권)은 돌려준다. 무료 기회도 만료 항목은 세지 않는다(재시도가 손해가 아니게).</li>
 *   <li>알림: 성사/타임아웃을 STOMP {@code /user/queue/matching} 으로 밀어주고, 정본은 폴링 조회다.</li>
 * </ul>
 *
 * <p>진입 시 즉시 한 번 짝을 찾고, 스케줄러가 주기적으로 만료 처리와 재시도를 한다. 단일 인스턴스 전제이며
 * 같은 항목을 두 번 짝짓지 않도록 두 항목에 행 잠금을 걸고 상태를 재확인한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueueMatchingService {

    private final MatchQueueRepository queueRepository;
    private final UserProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final UserBlockRepository blockRepository;
    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MatchCreationService matchCreationService;
    private final RegionService regionService;
    private final SubscriptionService subscriptionService;
    private final ItemWalletService walletService;
    private final InterestOverlapService interestOverlapService;
    private final QueuePushNotifier pushNotifier;
    private final SafetyService safetyService;

    /**
     * 대기 항목의 짝을 찾는다. 찾으면 매칭을 만들고 양쪽 항목을 MATCHED 로 바꾼다.
     *
     * @param entry 대기 중인 항목(영속 상태)
     * @return 성사됐으면 결과
     */
    @Transactional
    public Optional<MatchCreationService.MatchCreated> tryMatch(MatchQueue entry) {
        if (!entry.isWaiting() || entry.isExpired()) {
            return Optional.empty();
        }
        Long userId = entry.getUserId();
        UserProfile myProfile = profileRepository.findByIdAndDeleted(userId, YesNo.N).orElse(null);
        if (myProfile == null || !myProfile.isMatchable()) {
            return Optional.empty();
        }
        // 대기 중에 중대 신고를 받아 즉시검토 대기가 된 사용자는 짝을 맺지 않는다(BMA-30: 신규 매칭 진입 불가).
        if (safetyService.isMatchingOnHold(userId)) {
            return Optional.empty();
        }
        UserPreference myPreference = preferenceRepository.findByIdAndDeleted(userId, YesNo.N).orElse(null);
        Set<Long> excluded = new HashSet<>(blockRepository.findRelatedUserIds(userId));
        excluded.addAll(matchRepository.findPartnerIds(userId));
        boolean precision = subscriptionService.isSubscribed(userId);
        Map<String, String> parentCache = new HashMap<>();

        MatchQueue best = null;
        int bestScore = -1;
        for (MatchQueue candidate : queueRepository.findByQueueStatusAndDeletedOrderByEnterDateAscIdAsc(
                MatchQueue.STATUS_WAITING, YesNo.N)) {
            Long otherId = candidate.getUserId();
            if (otherId.equals(userId) || excluded.contains(otherId)) {
                continue;
            }
            if (candidate.isExpired()) {
                expire(candidate);
                continue;
            }
            if (safetyService.isMatchingOnHold(otherId)) {
                continue;
            }
            UserProfile otherProfile = profileRepository.findByIdAndDeleted(otherId, YesNo.N).orElse(null);
            if (otherProfile == null || !otherProfile.isMatchable()) {
                continue;
            }
            UserPreference otherPreference = preferenceRepository.findByIdAndDeleted(otherId, YesNo.N).orElse(null);
            if (otherPreference != null && !otherPreference.isMatchingEnabled()) {
                continue;
            }
            // 서로의 조건을 모두 만족해야 한다. 한쪽만 맞는 짝은 상대 입장에서 원치 않는 매칭이다.
            if (!PreferenceMatcher.accepts(myPreference, otherProfile, code -> parentOf(code, parentCache))
                    || !PreferenceMatcher.accepts(otherPreference, myProfile, code -> parentOf(code, parentCache))) {
                continue;
            }
            if (!precision) {
                best = candidate;
                break;
            }
            int score = interestOverlapService.commonCount(userId, otherId);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return pair(entry, best);
    }

    /**
     * 만료 처리와 재시도를 한 번 돈다(스케줄러가 주기적으로 호출).
     *
     * @return 이번에 성사된 매칭 수
     */
    @Transactional
    public int sweep() {
        int matched = 0;
        for (MatchQueue entry : new ArrayList<>(queueRepository
                .findByQueueStatusAndDeletedOrderByEnterDateAscIdAsc(MatchQueue.STATUS_WAITING, YesNo.N))) {
            // 앞 항목과 짝이 된 항목은 더 이상 WAITING 이 아니다. 영속 상태라 최신 값이 보인다.
            if (!entry.isWaiting()) {
                continue;
            }
            if (entry.isExpired()) {
                expire(entry);
                continue;
            }
            if (tryMatch(entry).isPresent()) {
                matched++;
            }
        }
        return matched;
    }

    /**
     * 사용자의 현재 대기 상태 (S9 폴링).
     *
     * <p>가장 최근 항목 기준이다. 대기 중인데 시간이 지났으면 이 자리에서 만료(환불) 처리해 TIMEOUT 을 돌려준다.</p>
     *
     * @param userId 사용자
     * @return 상태. 항목이 없으면 {@code status=NONE}
     */
    @Transactional
    public QueueStatusResponse currentStatus(Long userId) {
        MatchQueue latest = queueRepository.findFirstByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N).orElse(null);
        if (latest == null) {
            return QueueStatusResponse.none();
        }
        if (latest.isWaiting() && latest.isExpired()) {
            expire(latest);
        }
        return status(latest);
    }

    /**
     * 대기 항목을 상태 응답으로 바꾼다.
     *
     * @param entry 항목
     * @return 상태
     */
    public QueueStatusResponse status(MatchQueue entry) {
        String status = switch (entry.getQueueStatus()) {
            case MatchQueue.STATUS_WAITING -> QueueStatusResponse.WAITING;
            case MatchQueue.STATUS_MATCHED -> QueueStatusResponse.MATCHED;
            case MatchQueue.STATUS_EXPIRED -> QueueStatusResponse.TIMEOUT;
            default -> QueueStatusResponse.NONE;
        };
        LocalDateTime now = LocalDateTime.now();
        long waited = Math.max(Duration.between(entry.getEnterDate(),
                entry.getMatchedDate() != null ? entry.getMatchedDate() : now).getSeconds(), 0);
        long remaining = QueueStatusResponse.WAITING.equals(status) && entry.getExpireDate() != null
                ? Math.max(Duration.between(now, entry.getExpireDate()).getSeconds(), 0) : 0;

        Long chatRoomId = null;
        Long partnerUserId = null;
        if (entry.getMatchId() != null) {
            chatRoomId = chatRoomRepository.findByMatchIdAndDeleted(entry.getMatchId(), YesNo.N)
                    .map(room -> room.getId()).orElse(null);
            partnerUserId = matchRepository.findById(entry.getMatchId())
                    .map(match -> match.partnerOf(entry.getUserId())).orElse(null);
        }
        return new QueueStatusResponse(status, entry.getId(), entry.getEntrySource(), entry.getEnterDate(),
                entry.getExpireDate(), waited, remaining, entry.getMatchId(), chatRoomId, partnerUserId);
    }

    /**
     * 항목을 타임아웃 처리한다: EXPIRED 로 바꾸고 소모한 이용권을 돌려주고 푸시한다.
     *
     * @param entry 항목
     */
    @Transactional
    public void expire(MatchQueue entry) {
        if (!entry.isWaiting()) {
            return;
        }
        entry.expire();
        refund(entry);
        pushNotifier.push(entry.getUserId(), new QueuePushNotifier.QueuePush(
                QueueStatusResponse.TIMEOUT, entry.getId(), null, null, null));
        log.info("대기열 타임아웃: queueId={}, userId={}, source={}", entry.getId(), entry.getUserId(),
                entry.getEntrySource());
    }

    private Optional<MatchCreationService.MatchCreated> pair(MatchQueue mine, MatchQueue other) {
        // 스케줄러와 진입 시 시도가 겹쳐 같은 항목을 두 번 짝짓지 않도록 잠그고 상태를 다시 본다.
        MatchQueue lockedMine = queueRepository.findForUpdate(mine.getId()).orElse(null);
        MatchQueue lockedOther = queueRepository.findForUpdate(other.getId()).orElse(null);
        if (lockedMine == null || lockedOther == null || !lockedMine.isWaiting() || !lockedOther.isWaiting()) {
            return Optional.empty();
        }
        MatchCreationService.MatchCreated created = matchCreationService.create(
                lockedMine.getUserId(), lockedOther.getUserId(), Match.TYPE_QUEUE);
        // create() 가 두 항목을 MATCHED 로 닫는다(같은 영속성 컨텍스트라 잠근 인스턴스에 반영된다).
        for (MatchQueue entry : List.of(lockedMine, lockedOther)) {
            pushNotifier.push(entry.getUserId(), new QueuePushNotifier.QueuePush(
                    QueueStatusResponse.MATCHED, entry.getId(), created.match().getId(), created.room().getId(),
                    created.match().partnerOf(entry.getUserId())));
        }
        return Optional.of(created);
    }

    private void refund(MatchQueue entry) {
        switch (entry.getEntrySource()) {
            case MatchQueue.SOURCE_ITEM -> walletService.refund(entry.getUserId(), ItemType.MATCH_CHANCE,
                    ItemLedger.REF_MATCH_QUEUE, entry.getId());
            case MatchQueue.SOURCE_REMATCH -> walletService.refund(entry.getUserId(), ItemType.REMATCH_TICKET,
                    ItemLedger.REF_MATCH_QUEUE, entry.getId());
            default -> {
                // 무료 기회는 만료 항목을 세지 않으므로 돌려줄 것이 없다.
            }
        }
    }

    private String parentOf(String regionCode, Map<String, String> cache) {
        return cache.computeIfAbsent(regionCode, code ->
                regionService.findSelectable(code).map(Region::getParentCode).orElse(null));
    }
}
