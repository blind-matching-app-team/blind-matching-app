package com.bma.matching.service;

import com.bma.chat.entity.ChatRoom;
import com.bma.chat.service.ChatService;
import com.bma.common.entity.YesNo;
import com.bma.matching.entity.Match;
import com.bma.matching.entity.MatchQueue;
import com.bma.matching.repository.MatchQueueRepository;
import com.bma.matching.repository.MatchRepository;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.service.NotificationService;
import com.bma.user.entity.User;
import com.bma.user.repository.UserRepository;
import com.bma.reveal.service.RevealService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 매칭 성사 공통 처리. 상호 좋아요(S5)와 대기열 매칭(S9)이 함께 쓴다.
 *
 * <p>매칭 행 생성(또는 종료된 매칭 재활성화) → 채팅방 생성 → Reveal 진행 초기화 → 두 사용자의
 * 대기열 항목 종료 → 양쪽에 S7 매칭 성사 알림. {@code MatchingService} 와 {@code QueueMatchingService}
 * 가 서로를 참조하지 않도록 여기로 뺐다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchCreationService {

    private final MatchRepository matchRepository;
    private final MatchQueueRepository queueRepository;
    private final ChatService chatService;
    private final RevealService revealService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * 성사 결과.
     *
     * @param match 매칭
     * @param room  채팅방
     */
    public record MatchCreated(Match match, ChatRoom room) {
    }

    /**
     * 두 사용자를 매칭한다.
     *
     * @param userIdA   참여자 A
     * @param userIdB   참여자 B
     * @param matchType 성사 경로(LIKE/QUEUE)
     * @return 매칭과 채팅방
     */
    @Transactional
    public MatchCreated create(Long userIdA, Long userIdB, String matchType) {
        Match match = createOrGetMatch(userIdA, userIdB, matchType);
        ChatRoom room = chatService.createRoomForMatch(match.getId(), userIdA, userIdB);
        revealService.initializeProgress(match.getId());
        closeQueueEntries(userIdA, userIdB, match.getId());

        // S7-11(BMA-52/82): 상대가 사진인증을 하지 않았으면 알려 자발적 인증을 유도한다. MATCH_CREATED 가 최신 알림으로
        // 남도록 먼저 발행한다.
        notifyIfPartnerUnverified(userIdA, userIdB, match.getId());
        notifyIfPartnerUnverified(userIdB, userIdA, match.getId());

        notificationService.notifyAll(List.of(userIdA, userIdB),
                NotificationEvent.MATCH_CREATED,
                "매칭이 성사되었어요!",
                "이제 대화를 시작할 수 있습니다. 대화를 나눌수록 상대의 프로필이 더 공개됩니다.",
                "MATCH", match.getId());

        log.info("매칭 성사: matchId={}, users=[{}, {}], roomId={}, type={}",
                match.getId(), userIdA, userIdB, room.getId(), matchType);
        return new MatchCreated(match, room);
    }

    /**
     * 매칭을 만들거나, 이미 있으면 그것을 되살려 반환한다.
     *
     * <p>{@code UK_MT_MATCH_USERS}가 (작은 ID, 큰 ID) 기준이므로 정렬된 값으로 조회한다.
     * 해제 후 다시 만난 경우에는 기존 행을 재활성화한다.</p>
     */
    private Match createOrGetMatch(Long userIdA, Long userIdB, String matchType) {
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
        return matchRepository.save(Match.between(smaller, larger, matchType));
    }

    /**
     * 매칭이 성사된 두 사용자의 대기 중 항목을 MATCHED 로 닫고 성사된 매칭 ID 를 남긴다.
     */
    /**
     * 상대가 사진인증 미완료면 {@code MATCH_UNVERIFIED_PARTNER} 알림을 보낸다(S7-11 → S10).
     */
    private void notifyIfPartnerUnverified(Long userId, Long partnerId, Long matchId) {
        boolean partnerVerified = userRepository.findById(partnerId).map(User::isPhotoVerified).orElse(false);
        if (!partnerVerified) {
            notificationService.notify(userId, NotificationEvent.MATCH_UNVERIFIED_PARTNER,
                    "상대가 아직 사진인증을 하지 않았어요",
                    "사진인증 배지가 없는 상대예요. 대화하며 천천히 알아가 보세요.",
                    "MATCH", matchId);
        }
    }

    private void closeQueueEntries(Long userIdA, Long userIdB, Long matchId) {
        List.of(userIdA, userIdB).forEach(userId -> queueRepository
                .findFirstByUserIdAndQueueStatusAndDeleted(userId, MatchQueue.STATUS_WAITING, YesNo.N)
                .ifPresent(queue -> queue.matchWith(matchId)));
    }
}
