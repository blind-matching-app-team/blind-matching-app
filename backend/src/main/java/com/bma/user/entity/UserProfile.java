package com.bma.user.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * 매칭에 사용하는 사용자 프로필({@code US_USER_PROFILE}).
 *
 * <p>PK가 {@code USER_ID}이므로 사용자 1명당 프로필은 최대 1건이다.</p>
 *
 * <p>주의: 이 엔티티에는 실명에 준하는 식별 정보(직업, 정확한 키, 닉네임)가 들어 있어
 * 블라인드 단계에서는 그대로 노출하면 안 된다. 외부 노출은 반드시
 * {@code com.bma.reveal.service.ProfileMaskingService}를 거친다.</p>
 */
@Entity
@Table(name = "US_USER_PROFILE")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BaseAuditEntity {

    /** 프로필 상태: 작성 미완료. */
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";

    /** 프로필 상태: 검수 대기. */
    public static final String STATUS_REVIEW = "REVIEW";

    /** 프로필 상태: 매칭 가능. */
    public static final String STATUS_COMPLETE = "COMPLETE";

    /** 프로필 상태: 반려. */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 사용자 ID(PK이자 {@code US_USER}에 대한 FK). */
    @Id
    @Column(name = "USER_ID")
    private Long id;

    /** 서비스 표시 닉네임. 유니크 제약이 걸려 있다. */
    @Column(name = "NICKNAME", nullable = false, unique = true)
    private String nickname;

    /** 생년월일. 나이는 이 값에서 파생한다. */
    @Column(name = "BIRTH_DATE", nullable = false)
    private LocalDate birthDate;

    /** 성별 코드. */
    @Column(name = "GENDER_CODE")
    private String genderCode;

    /** MBTI. */
    @Column(name = "MBTI_CODE")
    private String mbtiCode;

    /** 활동 지역 코드. */
    @Column(name = "REGION_CODE")
    private String regionCode;

    /** 직업. */
    @Column(name = "OCCUPATION")
    private String occupation;

    /**
     * 키(cm).
     *
     * <p>스키마가 {@code SMALLINT UNSIGNED}라 Integer 기본 매핑({@code INTEGER})과
     * 타입이 어긋난다. {@code ddl-auto=validate}를 통과하도록 명시한다.</p>
     */
    @Column(name = "HEIGHT_CM", columnDefinition = "SMALLINT UNSIGNED")
    private Integer heightCm;

    /** 자기소개. */
    @Column(name = "INTRODUCTION", length = 1000)
    private String introduction;

    /** 프로필 상태. */
    @Column(name = "PROFILE_STATUS", nullable = false)
    private String profileStatus = STATUS_INCOMPLETE;

    /** 프로필 완성도 점수(0~100). 추천 정렬 가중치로 사용한다. */
    @Column(name = "PROFILE_SCORE", nullable = false)
    private Integer profileScore = 0;

    /** 마지막 활동 일시. */
    @Column(name = "LAST_ACTIVE_DATE")
    private LocalDateTime lastActiveDate;

    /**
     * 새 프로필의 빈 껍데기를 만든다.
     *
     * @param userId 사용자 ID
     * @return 프로필 엔티티
     */
    public static UserProfile emptyFor(Long userId) {
        UserProfile profile = new UserProfile();
        profile.id = userId;
        return profile;
    }

    /**
     * 만 나이를 계산한다.
     *
     * @return 만 나이. 생년월일이 없으면 {@code null}
     */
    public Integer age() {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * 매칭에 참여할 수 있는 상태인지 확인한다.
     *
     * @return 프로필이 완성 상태이고 삭제되지 않았으면 {@code true}
     */
    public boolean isComplete() {
        return STATUS_COMPLETE.equals(profileStatus) && !isDeleted();
    }

    /**
     * 매칭(S4)에 실제로 넣을 수 있는지 확인한다.
     *
     * <p>{@link #isComplete()}와 나눠 둔 이유는 두 조건이 다르기 때문이다.
     * 프로필 완성은 "S3 화면을 다 채웠는가"이고, 매칭 가능은 거기에 더해
     * 상대 성별 필터에 쓸 성별이 있는가까지 본다. 성별은 S3 화면에 입력란이
     * 없어 지금은 다른 경로(S15 본인인증 등, 미확정)로만 채워질 수 있다.</p>
     *
     * @return 프로필이 완성이고 성별이 있으면 {@code true}
     */
    public boolean isMatchable() {
        return isComplete() && genderCode != null && !genderCode.isBlank();
    }

    /**
     * 필수 항목 충족 여부에 따라 상태와 완성도 점수를 다시 계산한다.
     *
     * <p>기존 구현은 저장할 때마다 무조건 {@code COMPLETE} + 점수 100을 넣어
     * 완성도 개념이 사실상 없었다. 선택 항목을 채울수록 점수가 올라가고,
     * 그 점수가 추천 정렬에 반영되도록 한다.</p>
     */
    public void refreshCompleteness() {
        // S3 프로필 설정 화면에서 실제로 받을 수 있는 필수 3개 항목이다.
        // 성별은 S3 에도 S1 회원가입에도 입력란이 없어 완성 판정에서 뺐다.
        // 넣어 두면 사용자가 화면을 다 채워도 INCOMPLETE 로 남아 프론트가
        // S3 로 되돌리는 무한 루프에 빠진다. 매칭 진입 조건은 별도로 본다
        // ({@link #isMatchable()}).
        boolean requiredFilled = nickname != null && !nickname.isBlank()
                && birthDate != null
                && regionCode != null && !regionCode.isBlank();

        // 필수 50 + 선택 5개 x 10 = 100. 성별이 선택 항목으로 넘어오면서
        // 기존 배점(필수 60 + 선택 4개)으로는 합계가 110 이 되어 조정했다.
        int score = requiredFilled ? 50 : 0;
        if (genderCode != null && !genderCode.isBlank()) {
            score += 10;
        }
        if (mbtiCode != null && !mbtiCode.isBlank()) {
            score += 10;
        }
        if (occupation != null && !occupation.isBlank()) {
            score += 10;
        }
        if (heightCm != null) {
            score += 10;
        }
        if (introduction != null && introduction.trim().length() >= 20) {
            score += 10;
        }

        this.profileScore = score;
        this.profileStatus = requiredFilled ? STATUS_COMPLETE : STATUS_INCOMPLETE;
        this.lastActiveDate = LocalDateTime.now();
    }
}
