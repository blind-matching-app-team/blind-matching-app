package com.bma.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 탈퇴(S8-15) 시 익명화 규칙을 고정한다.
 *
 * <p>탈퇴 후에도 매칭·메시지 이력은 남기므로(상대의 채팅목록 = 매칭 히스토리), 남는 행에서
 * 개인을 특정할 수 있는 값이 하나도 없어야 한다. 이메일·닉네임은 유니크 제약 때문에
 * 자리표시자로 바꾼다 — 그래서 같은 이메일/닉네임으로 다시 가입할 수 있다.</p>
 */
class UserWithdrawTest {

    @Test
    @DisplayName("탈퇴하면 상태는 WITHDRAWN, 이메일·전화·비밀번호·소셜 키가 지워지고 논리 삭제된다")
    void withdrawAnonymizesAccount() {
        User user = User.createLocal("me@example.com", "$2a$hash", "010-1234-5678");
        user.setId(42L);
        user.setLoginProvider("KAKAO");
        user.setProviderUserKey("kakao-key");

        user.withdraw();

        assertThat(user.getUserStatus()).isEqualTo(User.STATUS_WITHDRAWN);
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.isActive()).isFalse();
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getEmail()).isEqualTo("withdrawn-42@deleted.invalid");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getProviderUserKey()).isNull();
        assertThat(user.getEmailVerifiedYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("프로필 익명화: 닉네임은 자리표시자, 나머지 식별 값은 비우고 INCOMPLETE·0점·논리 삭제")
    void anonymizeProfile() {
        UserProfile profile = UserProfile.emptyFor(42L);
        profile.setNickname("블라인드");
        profile.setBirthDate(LocalDate.of(1998, 3, 14));
        profile.setGenderCode("F");
        profile.setRegionCode("SEOUL_GANGNAM");
        profile.setMbtiCode("INFP");
        profile.setOccupation("디자이너");
        profile.setHeightCm(165);
        profile.setIntroduction("안녕하세요");
        profile.refreshCompleteness();
        assertThat(profile.isComplete()).isTrue();

        profile.anonymize();

        assertThat(profile.getNickname()).isEqualTo("탈퇴회원42");
        assertThat(profile.getGenderCode()).isNull();
        assertThat(profile.getRegionCode()).isNull();
        assertThat(profile.getMbtiCode()).isNull();
        assertThat(profile.getOccupation()).isNull();
        assertThat(profile.getHeightCm()).isNull();
        assertThat(profile.getIntroduction()).isNull();
        assertThat(profile.getProfileStatus()).isEqualTo(UserProfile.STATUS_INCOMPLETE);
        assertThat(profile.getProfileScore()).isZero();
        assertThat(profile.isDeleted()).isTrue();
        assertThat(profile.isComplete()).isFalse();
        assertThat(profile.isMatchable()).isFalse();
    }

    @Test
    @DisplayName("비밀번호 변경은 해시만 바꾼다")
    void changePasswordReplacesHash() {
        User user = User.createLocal("me@example.com", "old-hash", null);
        user.changePassword("new-hash");
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isActive()).isTrue();
    }
}
