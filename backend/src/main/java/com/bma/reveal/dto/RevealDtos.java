package com.bma.reveal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reveal(블라인드 해제) API DTO 모음 (S10 사양서 v1.6, BMA-69).
 */
public final class RevealDtos {

    private RevealDtos() {
    }

    /**
     * (구) 공개 단계 동의 요청. {@code POST /reveal/consent} 호환용.
     *
     * @param revealLevel 동의 대상 단계(현재 단계 + 1 이어야 한다)
     * @param consent     동의 여부. {@code false} 는 "나중에"와 같이 아무것도 기록하지 않는다
     */
    public record ConsentRequest(
            @NotNull(message = "공개 단계는 필수입니다.")
            @Min(value = 1, message = "동의는 1단계부터 가능합니다.")
            @Max(value = 2, message = "공개 단계는 최대 2입니다.")
            Integer revealLevel,

            @NotNull(message = "동의 여부는 필수입니다.")
            Boolean consent
    ) {
    }

    /**
     * 동의 모달 응답 (S10-16 동의하고 열기 / S10-17 나중에).
     *
     * @param consent {@code true}=동의하고 열기, {@code false} 또는 생략=나중에(기록 없음, 상대에게 비노출)
     */
    public record RevealConsentRequest(Boolean consent) {

        public boolean isAccepted() {
            return Boolean.TRUE.equals(consent);
        }
    }

    /**
     * S10 화면 상태. 진행바(S10-05), 안내(S10-06/11), 다음 단계 칩(S10-12), 동의 모달(S10-14~17)이 이 값으로 그려진다.
     *
     * <p>구독 여부는 어느 필드에도 드러나지 않는다. 구독자가 24시간 전에 요청해도 상대는 일반 요청과 같은
     * 모양({@code incomingRequest=true})으로 본다.</p>
     *
     * @param matchId                  매칭 ID
     * @param currentLevel             현재 단계 0(실루엣)/1(부분 공개)/2(전체 공개)
     * @param currentLevelName         현재 단계명
     * @param nextLevel                다음 단계. 최고 단계면 {@code null}
     * @param nextLevelName            다음 단계명
     * @param maxLevelReached          전체 공개 도달 여부
     * @param matchedAt                매칭 성사 일시
     * @param requiredHours            다음 단계까지 필요한 매칭 후 경과 시간(시간)
     * @param hoursSatisfied           경과 시간 조건 충족 여부(실제 경과 기준)
     * @param hoursRemainingMinutes    충족까지 남은 시간(분). 충족했으면 0 — S10-12 "18시간 12분 후"
     * @param myMessages               내가 보낸 메시지 수
     * @param partnerMessages          상대가 보낸 메시지 수
     * @param requiredMessagesPerUser  각자 필요한 메시지 수
     * @param messagesSatisfied        양측 모두 충족했는지
     * @param myMessagesRemaining      내가 더 보내야 하는 수 — S10-12 "대화 7개 더 필요"
     * @param partnerMessagesRemaining 상대가 더 보내야 하는 수
     * @param totalMessages            합산 메시지 수
     * @param requiredTotalMessages    합산 기준(정책 MIN_MESSAGE_COUNT)
     * @param progressRate             다음 단계까지 진행률 0~100 (S10-05)
     * @param canRequest               내가 지금 '다음 단계 요청하기'를 누를 수 있는지(칩 활성)
     * @param myConsent                내 동의 상태 PENDING/ACCEPTED (요청 = 동의)
     * @param partnerConsent           상대 동의 상태 PENDING/ACCEPTED
     * @param incomingRequest          상대가 요청했고 내 동의를 기다리는 중(동의 모달 노출 조건)
     * @param requestedAt              상대의 요청 일시
     * @param lastLevelUpDate          최근 단계 상승 일시
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RevealStatusResponse(Long matchId,
                                       Integer currentLevel,
                                       String currentLevelName,
                                       Integer nextLevel,
                                       String nextLevelName,
                                       boolean maxLevelReached,
                                       LocalDateTime matchedAt,
                                       Integer requiredHours,
                                       boolean hoursSatisfied,
                                       long hoursRemainingMinutes,
                                       int myMessages,
                                       int partnerMessages,
                                       Integer requiredMessagesPerUser,
                                       boolean messagesSatisfied,
                                       int myMessagesRemaining,
                                       int partnerMessagesRemaining,
                                       int totalMessages,
                                       Integer requiredTotalMessages,
                                       int progressRate,
                                       boolean canRequest,
                                       String myConsent,
                                       String partnerConsent,
                                       boolean incomingRequest,
                                       LocalDateTime requestedAt,
                                       LocalDateTime lastLevelUpDate) {
    }

    /**
     * 요청/동의 처리 결과.
     *
     * @param leveledUp 이번 호출로 단계가 올라갔는지(→ 진행바 애니메이션, 화면 갱신)
     * @param status    처리 후 상태
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RevealActionResponse(boolean leveledUp, RevealStatusResponse status) {
    }

    /**
     * (구) 동의 처리 결과. {@code POST /reveal/consent} 호환용.
     */
    public record ConsentResult(Long matchId,
                                Integer revealLevel,
                                boolean accepted,
                                boolean leveledUp,
                                Integer currentLevel) {
    }

    /**
     * 단계에 맞춰 마스킹된 상대 프로필.
     *
     * <p>단계에 따라 가려진 필드는 "키 없음"이 아니라 null 로 명시한다. 전역 non_null 설정을
     * 그대로 두면 nickname 같은 필드가 단계마다 있다 없다 해서 프론트 바인딩이 흔들린다.</p>
     *
     * @param nickname       전체 공개(2)에서만 원문. 그 전에는 {@code null}
     * @param nicknameMasked 부분 공개(1)에서 앞 절반만 남긴 이름(S10-10 "김민??"), 전체 공개면 원문, 실루엣이면 {@code null}
     * @param heightCm       부분 공개(1)부터 노출(S10-18). 본인이 입력하지 않았으면 {@code null}
     * @param photoVerified  사진인증 배지(S5/S10 카드, BMA-82). 단계와 무관하게 항상 노출
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MaskedProfileResponse(Long userId,
                                        Integer revealLevel,
                                        String nickname,
                                        String nicknameMasked,
                                        Integer age,
                                        String ageGroup,
                                        String genderCode,
                                        String regionCode,
                                        String regionName,
                                        String mbtiCode,
                                        String occupation,
                                        Integer heightCm,
                                        String introduction,
                                        List<String> imageKeys,
                                        boolean photoVerified) {
    }
}
