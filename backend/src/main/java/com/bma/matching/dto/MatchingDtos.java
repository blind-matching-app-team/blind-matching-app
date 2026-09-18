package com.bma.matching.dto;

import com.bma.matching.entity.MatchQueue;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매칭 API의 요청/응답 DTO 모음.
 */
public final class MatchingDtos {

    private MatchingDtos() {
    }

    /**
     * 추천 상대에 대한 액션 요청.
     *
     * @param targetUserId 대상 사용자
     * @param actionType   액션 유형(LIKE/SUPER_LIKE/PASS)
     * @param message      호감 메시지(선택)
     */
    public record ActionRequest(
            @NotNull(message = "대상 사용자 ID는 필수입니다.") Long targetUserId,

            @NotBlank(message = "액션 유형은 필수입니다.")
            @Pattern(regexp = "LIKE|SUPER_LIKE|PASS",
                    message = "액션 유형은 LIKE, SUPER_LIKE, PASS 중 하나여야 합니다.")
            String actionType,

            @Size(max = 500, message = "메시지는 500자를 넘을 수 없습니다.")
            String message
    ) {
    }

    /**
     * 액션 처리 결과.
     *
     * <p>기존 구현은 {@code matched}를 항상 {@code false}로 하드코딩해
     * 상호 매칭이 아예 성사되지 않았다.</p>
     *
     * @param targetUserId 대상 사용자
     * @param actionType   반영된 액션
     * @param matched      이번 액션으로 매칭이 성사되었는지
     * @param matchId      성사된 매칭 ID(성사되지 않았으면 {@code null})
     * @param chatRoomId   생성된 채팅방 ID(성사되지 않았으면 {@code null})
     */
    public record ActionResult(Long targetUserId,
                               String actionType,
                               boolean matched,
                               Long matchId,
                               Long chatRoomId) {
    }

    /**
     * 매칭 응답 (S5 진행중 매칭 카드).
     *
     * <p>카드 하나를 그리는 데 필요한 것을 전부 담는다: 상대의 마스킹된 프로필(S5-09 의
     * 블러 아바타·"???"·MBTI·지역), 공통관심사, Reveal 진행바(S5-10), 채팅방 ID(S5-11).
     * 상대 정보는 현재 공개 단계에 맞춰 마스킹된 것만 실린다.</p>
     *
     * @param matchId         매칭 ID
     * @param partnerUserId   상대 사용자 ID
     * @param matchStatus     매칭 상태
     * @param matchType       매칭 유형
     * @param matchDate       성사 일시
     * @param revealLevel     현재 공개 단계(0/1/2)
     * @param chatRoomId      채팅방 ID. 대화 시작하기(S5-11)가 S11 로 갈 때 쓴다
     * @param partner         상대 프로필(현재 공개 단계로 마스킹)
     * @param commonInterests 양쪽이 온보딩 관심사에서 함께 고른 항목명. 없으면 빈 목록
     * @param reveal          Reveal 진행 요약
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MatchResponse(Long matchId,
                                Long partnerUserId,
                                String matchStatus,
                                String matchType,
                                LocalDateTime matchDate,
                                Integer revealLevel,
                                Long chatRoomId,
                                MaskedProfileResponse partner,
                                List<String> commonInterests,
                                RevealSummary reveal) {
    }

    /**
     * Reveal 진행 요약. S5-10 진행바와 S10 이 같은 컴포넌트를 쓴다.
     *
     * @param currentLevel         현재 공개 단계
     * @param currentLevelName     현재 단계명
     * @param nextLevel            다음 단계. 최고 단계면 {@code null}
     * @param nextLevelName        다음 단계명. 최고 단계면 {@code null}
     * @param messageCount         누적 메시지 수
     * @param requiredMessageCount 다음 단계에 필요한 메시지 수. 최고 단계면 {@code null}
     * @param chatMinutes          누적 대화 시간(분)
     * @param requiredChatMinutes  다음 단계에 필요한 대화 시간. 최고 단계면 {@code null}
     * @param progressRate         다음 단계까지의 진행률(0~100). 최고 단계면 100
     * @param mutualConsentNeeded  다음 단계가 양쪽 동의를 요구하는지
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RevealSummary(Integer currentLevel,
                                String currentLevelName,
                                Integer nextLevel,
                                String nextLevelName,
                                Integer messageCount,
                                Integer requiredMessageCount,
                                Integer chatMinutes,
                                Integer requiredChatMinutes,
                                int progressRate,
                                boolean mutualConsentNeeded) {
    }

    /**
     * S5 메인 허브용 "현재 매칭" 응답.
     *
     * <p>티켓(BMA-47) 요구 2번 "매칭 없음 상태 응답 처리"를 플래그로 푼다. 목록 API 의
     * 빈 배열보다 프론트 분기가 단순하고, 전역 non_null 설정 때문에 {@code match} 키가
     * 사라지는 일이 없도록 이 응답만 null 을 항상 싣는다.</p>
     *
     * @param hasMatch 진행 중인 매칭이 있으면 {@code true}
     * @param match    가장 최근 매칭. 없으면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CurrentMatchResponse(boolean hasMatch, MatchResponse match) {

        /** 매칭 없음. */
        public static CurrentMatchResponse none() {
            return new CurrentMatchResponse(false, null);
        }

        /**
         * 매칭 있음.
         *
         * @param match 최근 매칭
         * @return 응답
         */
        public static CurrentMatchResponse of(MatchResponse match) {
            return new CurrentMatchResponse(true, match);
        }
    }

    /**
     * 추천 카드 응답.
     *
     * <p>프로필 정보는 항상 공개 단계 0(미공개) 기준으로 마스킹된다.
     * 매칭 전이므로 닉네임·직업·정확한 나이는 포함되지 않는다.</p>
     *
     * @param profile 마스킹된 프로필
     */
    public record RecommendationResponse(MaskedProfileResponse profile) {
    }

    /**
     * 매칭 대기열 상태 응답.
     *
     * @param queueId     대기열 ID
     * @param queueStatus 상태
     * @param enterDate   진입 일시
     * @param expireDate  만료 예정 일시
     */
    public record QueueResponse(Long queueId,
                                String queueStatus,
                                LocalDateTime enterDate,
                                LocalDateTime expireDate) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param queue 대기열 엔티티
         * @return 응답 DTO
         */
        public static QueueResponse from(MatchQueue queue) {
            return new QueueResponse(queue.getId(), queue.getQueueStatus(),
                    queue.getEnterDate(), queue.getExpireDate());
        }
    }
}
