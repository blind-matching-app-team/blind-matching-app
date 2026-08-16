package com.bma.reveal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reveal(블라인드 해제) API의 요청/응답 DTO 모음.
 */
public final class RevealDtos {

    private RevealDtos() {
    }

    /**
     * 공개 단계 동의 요청.
     *
     * @param revealLevel 동의 대상 단계
     * @param consent     수락 여부
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
     * 공개 단계 진행 상태 응답.
     *
     * @param matchId              매칭 ID
     * @param currentLevel         현재 공개 단계
     * @param currentLevelName     현재 단계명
     * @param messageCount         누적 메시지 수
     * @param chatMinutes          누적 대화 시간(분)
     * @param nextLevel            다음 단계({@code null}이면 최고 단계 도달)
     * @param nextLevelName        다음 단계명
     * @param requiredMessageCount 다음 단계에 필요한 메시지 수
     * @param requiredChatMinutes  다음 단계에 필요한 대화 시간
     * @param activitySatisfied    대화량 조건 충족 여부
     * @param mutualConsentNeeded  상호 동의가 필요한 단계인지
     * @param myConsent            내 동의 상태
     * @param partnerConsent       상대 동의 상태
     * @param lastLevelUpDate      최근 상승 일시
     */
    public record RevealProgressResponse(Long matchId,
                                         Integer currentLevel,
                                         String currentLevelName,
                                         Integer messageCount,
                                         Integer chatMinutes,
                                         Integer nextLevel,
                                         String nextLevelName,
                                         Integer requiredMessageCount,
                                         Integer requiredChatMinutes,
                                         boolean activitySatisfied,
                                         boolean mutualConsentNeeded,
                                         String myConsent,
                                         String partnerConsent,
                                         LocalDateTime lastLevelUpDate) {
    }

    /**
     * 동의 처리 결과.
     *
     * @param matchId      매칭 ID
     * @param revealLevel  동의한 단계
     * @param accepted     내 응답
     * @param leveledUp    이번 요청으로 단계가 올라갔는지
     * @param currentLevel 처리 후 현재 단계
     */
    public record ConsentResult(Long matchId,
                                Integer revealLevel,
                                boolean accepted,
                                boolean leveledUp,
                                Integer currentLevel) {
    }

    /**
     * 공개 단계에 맞춰 마스킹된 상대 프로필.
     *
     * <p>단계별 노출 범위</p>
     * <ul>
     *   <li>0(미공개): 나이대, 지역, MBTI, 자기소개, 실루엣 이미지</li>
     *   <li>1(부분 공개): + 정확한 나이, 키, 직업, 블러 이미지</li>
     *   <li>2(전체 공개): + 닉네임, 원본 이미지</li>
     * </ul>
     *
     * @param userId       상대 사용자 ID
     * @param revealLevel  적용된 공개 단계
     * @param nickname     닉네임(단계 2 이상)
     * @param age          만 나이(단계 1 이상)
     * @param ageGroup     나이대 표기(예: "20대 후반")
     * @param genderCode   성별 코드
     * @param regionCode   지역 코드
     * @param mbtiCode     MBTI
     * @param occupation   직업(단계 1 이상)
     * @param heightCm     키(단계 1 이상)
     * @param introduction 자기소개
     * @param imageKeys    단계에 맞는 이미지 오브젝트 키 목록
     */
    public record MaskedProfileResponse(Long userId,
                                        Integer revealLevel,
                                        String nickname,
                                        Integer age,
                                        String ageGroup,
                                        String genderCode,
                                        String regionCode,
                                        String mbtiCode,
                                        String occupation,
                                        Integer heightCm,
                                        String introduction,
                                        List<String> imageKeys) {
    }
}
