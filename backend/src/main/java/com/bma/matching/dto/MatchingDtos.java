package com.bma.matching.dto;

import com.bma.matching.entity.Match;
import com.bma.matching.entity.MatchQueue;
import com.bma.reveal.dto.RevealDtos.MaskedProfileResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

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
     * 매칭 응답.
     *
     * @param matchId       매칭 ID
     * @param partnerUserId 상대 사용자 ID
     * @param matchStatus   매칭 상태
     * @param matchType     매칭 유형
     * @param matchDate     성사 일시
     * @param revealLevel   현재 공개 단계
     * @param chatRoomId    채팅방 ID
     */
    public record MatchResponse(Long matchId,
                                Long partnerUserId,
                                String matchStatus,
                                String matchType,
                                LocalDateTime matchDate,
                                Integer revealLevel,
                                Long chatRoomId) {

        /**
         * 엔티티와 부가 정보를 응답 DTO로 변환한다.
         *
         * @param match       매칭 엔티티
         * @param userId      요청자 ID
         * @param revealLevel 현재 공개 단계
         * @param chatRoomId  채팅방 ID
         * @return 응답 DTO
         */
        public static MatchResponse of(Match match, Long userId, Integer revealLevel, Long chatRoomId) {
            return new MatchResponse(
                    match.getId(),
                    match.partnerOf(userId),
                    match.getMatchStatus(),
                    match.getMatchType(),
                    match.getMatchDate(),
                    revealLevel,
                    chatRoomId);
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
