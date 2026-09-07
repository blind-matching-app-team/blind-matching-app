package com.bma.user.dto;

import com.bma.common.entity.Region;
import com.bma.common.entity.YesNo;
import com.bma.user.entity.ProfileImage;
import com.bma.user.entity.User;
import com.bma.user.entity.UserPreference;
import com.bma.user.entity.UserProfile;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 회원 API의 요청/응답 DTO 모음.
 *
 * <p>기존 구현은 엔티티를 그대로 요청/응답에 사용해
 * {@code passwordHash}, {@code deleted}, {@code systemRemark} 같은 내부 필드가
 * 응답에 그대로 실려 나갔다. 여기서 노출 범위를 명시적으로 통제한다.</p>
 */
public final class UserDtos {

    private UserDtos() {
    }

    // ── 요청 ────────────────────────────────────────────────────────────────

    /**
     * 프로필 등록/수정 요청.
     *
     * @param nickname     닉네임
     * @param birthDate    생년월일(과거 날짜)
     * @param genderCode   성별 코드
     * @param regionCode   활동 지역 코드
     * @param mbtiCode     MBTI(선택)
     * @param occupation   직업(선택)
     * @param heightCm     키(선택, 100~250)
     * @param introduction 자기소개(선택)
     */
    public record ProfileRequest(
            // 사양서 S3-06: "본명 대신 쓸 별칭 (10자 이내)".
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
            String nickname,

            @NotNull(message = "생년월일은 필수입니다.")
            @Past(message = "생년월일은 과거 날짜여야 합니다.")
            LocalDate birthDate,

            // S3 화면에 성별 입력란이 없어 선택으로 둔다. 매칭(S4)에는 필요하므로
            // 컬럼과 필드는 남기되, 수집 시점은 별도 확정 사항이다.
            String genderCode,

            // 시/군/구 코드만 받는다. 시/도는 CM_REGION 의 상위 관계로 따라온다.
            @NotBlank(message = "지역 코드는 필수입니다.")
            String regionCode,

            @Pattern(regexp = "^$|^[EI][NS][TF][JP]$", message = "MBTI 형식이 올바르지 않습니다.")
            String mbtiCode,

            @Size(max = 100, message = "직업은 100자를 넘을 수 없습니다.")
            String occupation,

            // DB의 CK_US_PROFILE_HEIGHT 체크 제약과 동일한 범위를 애플리케이션에서도 막는다.
            @Min(value = 100, message = "키는 100cm 이상이어야 합니다.")
            @Max(value = 250, message = "키는 250cm 이하여야 합니다.")
            Integer heightCm,

            @Size(max = 1000, message = "자기소개는 1000자를 넘을 수 없습니다.")
            String introduction
    ) {
    }

    /**
     * 선호 조건 등록/수정 요청.
     *
     * @param preferredGenderCode 선호 성별
     * @param minAge              최소 연령
     * @param maxAge              최대 연령
     * @param minHeightCm         최소 키
     * @param maxHeightCm         최대 키
     * @param maxDistanceKm       최대 거리(km)
     * @param preferredRegionCode 선호 지역
     * @param matchingEnabled     추천/매칭 참여 여부
     */
    public record PreferenceRequest(
            String preferredGenderCode,

            @Min(value = 19, message = "최소 연령은 19세 이상이어야 합니다.")
            @Max(value = 99, message = "최소 연령은 99세 이하여야 합니다.")
            Integer minAge,

            @Min(value = 19, message = "최대 연령은 19세 이상이어야 합니다.")
            @Max(value = 99, message = "최대 연령은 99세 이하여야 합니다.")
            Integer maxAge,

            @Min(value = 100, message = "최소 키는 100cm 이상이어야 합니다.")
            @Max(value = 250, message = "최소 키는 250cm 이하여야 합니다.")
            Integer minHeightCm,

            @Min(value = 100, message = "최대 키는 100cm 이상이어야 합니다.")
            @Max(value = 250, message = "최대 키는 250cm 이하여야 합니다.")
            Integer maxHeightCm,

            @Min(value = 1, message = "최대 거리는 1km 이상이어야 합니다.")
            @Max(value = 500, message = "최대 거리는 500km 이하여야 합니다.")
            Integer maxDistanceKm,

            String preferredRegionCode,

            Boolean matchingEnabled
    ) {
    }

    // 이미지 순서 변경/대표 지정 요청 DTO는 제거했다. 프로필 사진이 1장으로
    // 축소되면서 순서를 매길 대상도, 여러 장 중 대표를 고를 대상도 없다.

    // ── 응답 ────────────────────────────────────────────────────────────────

    /**
     * 닉네임 사용 가능 여부 응답.
     *
     * @param nickname  확인한 닉네임
     * @param available 사용 가능하면 {@code true}
     */
    public record NicknameCheckResponse(String nickname, boolean available) {
    }

    /**
     * 내 계정 정보 응답.
     *
     * <p>비밀번호 해시, 소셜 제공자 키, 논리삭제 플래그 등 내부 필드는 포함하지 않는다.</p>
     *
     * @param userId        사용자 ID
     * @param email         이메일
     * @param userStatus    계정 상태
     * @param userRole      권한 코드
     * @param emailVerified 이메일 인증 여부
     * @param phoneVerified 휴대전화 인증 여부
     * @param lastLoginDate 마지막 로그인 일시
     * @param joinedDate    가입 일시
     */
    public record MeResponse(Long userId,
                             String email,
                             String userStatus,
                             String userRole,
                             boolean emailVerified,
                             boolean phoneVerified,
                             LocalDateTime lastLoginDate,
                             LocalDateTime joinedDate) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param user 사용자 엔티티
         * @return 응답 DTO
         */
        public static MeResponse from(User user) {
            return new MeResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getUserStatus(),
                    user.getUserRole(),
                    YesNo.isY(user.getEmailVerifiedYn()),
                    YesNo.isY(user.getPhoneVerifiedYn()),
                    user.getLastLoginDate(),
                    user.getInsertDate());
        }
    }

    /**
     * 내 프로필 응답. 본인 조회이므로 마스킹하지 않은 전체 값을 담는다.
     *
     * @param userId        사용자 ID
     * @param nickname      닉네임
     * @param birthDate     생년월일
     * @param age           만 나이
     * @param genderCode     성별 코드
     * @param regionCode     지역 코드(시/군/구)
     * @param regionName     지역명(시/군/구)
     * @param regionSidoCode 상위 시/도 코드
     * @param regionSidoName 상위 시/도명
     * @param mbtiCode      MBTI
     * @param occupation    직업
     * @param heightCm      키
     * @param introduction  자기소개
     * @param profileStatus 프로필 상태
     * @param profileScore  완성도 점수
     */
    public record ProfileResponse(Long userId,
                                  String nickname,
                                  LocalDate birthDate,
                                  Integer age,
                                  String genderCode,
                                  String regionCode,
                                  String regionName,
                                  String regionSidoCode,
                                  String regionSidoName,
                                  String mbtiCode,
                                  String occupation,
                                  Integer heightCm,
                                  String introduction,
                                  String profileStatus,
                                  Integer profileScore) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * <p>지역은 코드만으로는 화면에 "서울특별시 · 강남구"를 그릴 수 없어 이름을 함께
         * 내려준다. 프론트가 지역 목록을 따로 받아 코드를 이름으로 되짚는 수고를 없앤다.</p>
         *
         * @param profile 프로필 엔티티
         * @param sigungu 시/군/구. 미설정이거나 코드가 어긋나면 {@code null}
         * @param sido    상위 시/도. 없으면 {@code null}
         * @return 응답 DTO
         */
        public static ProfileResponse of(UserProfile profile, Region sigungu, Region sido) {
            return new ProfileResponse(
                    profile.getId(),
                    profile.getNickname(),
                    profile.getBirthDate(),
                    profile.age(),
                    profile.getGenderCode(),
                    profile.getRegionCode(),
                    sigungu == null ? null : sigungu.getName(),
                    sido == null ? null : sido.getCode(),
                    sido == null ? null : sido.getName(),
                    profile.getMbtiCode(),
                    profile.getOccupation(),
                    profile.getHeightCm(),
                    profile.getIntroduction(),
                    profile.getProfileStatus(),
                    profile.getProfileScore());
        }
    }

    /**
     * 선호 조건 응답.
     *
     * @param preferredGenderCode 선호 성별
     * @param minAge              최소 연령
     * @param maxAge              최대 연령
     * @param minHeightCm         최소 키
     * @param maxHeightCm         최대 키
     * @param maxDistanceKm       최대 거리
     * @param preferredRegionCode 선호 지역
     * @param matchingEnabled     매칭 참여 여부
     */
    public record PreferenceResponse(String preferredGenderCode,
                                     Integer minAge,
                                     Integer maxAge,
                                     Integer minHeightCm,
                                     Integer maxHeightCm,
                                     Integer maxDistanceKm,
                                     String preferredRegionCode,
                                     boolean matchingEnabled) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param preference 선호 조건 엔티티
         * @return 응답 DTO
         */
        public static PreferenceResponse from(UserPreference preference) {
            return new PreferenceResponse(
                    preference.getPreferredGenderCode(),
                    preference.getMinAge(),
                    preference.getMaxAge(),
                    preference.getMinHeightCm(),
                    preference.getMaxHeightCm(),
                    preference.getMaxDistanceKm(),
                    preference.getPreferredRegionCode(),
                    preference.isMatchingEnabled());
        }
    }

    /**
     * 프로필 이미지 응답.
     *
     * <p>저장소 오브젝트 키는 내부 구조를 드러내므로 본인 조회에서만 반환한다.
     * 타인에게 보여줄 때는 Reveal 단계에 맞춘 별도 응답을 사용한다.</p>
     *
     * @param imageId      이미지 ID
     * @param objectKey    저장소 키
     * @param originalName 원본 파일명
     * @param contentType  MIME 타입
     * @param fileSize     크기(바이트)
     * @param reviewStatus 검수 상태
     */
    public record ProfileImageResponse(Long imageId,
                                       String objectKey,
                                       String originalName,
                                       String contentType,
                                       Long fileSize,
                                       String reviewStatus) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * <p>{@code displayOrder}와 {@code primary}는 응답에서 뺐다. 사진이 1장뿐이라
         * 항상 1번이자 대표라서 값이 정보를 담지 않는다. 컬럼 자체는 다중 이미지
         * 전제로 만들어진 것이라 구조 단순화(BMA-14)와 함께 정리한다.</p>
         *
         * @param image 이미지 엔티티
         * @return 응답 DTO
         */
        public static ProfileImageResponse from(ProfileImage image) {
            return new ProfileImageResponse(
                    image.getId(),
                    image.getOriginalObjectKey(),
                    image.getOriginalFileName(),
                    image.getContentType(),
                    image.getFileSize(),
                    image.getReviewStatus());
        }
    }
}
