package com.bma.auth.social;

/**
 * 제공자별 응답에서 우리가 필요로 하는 값만 추린 결과.
 *
 * @param provider      제공자
 * @param providerKey   제공자 내 사용자 고유 키. {@code US_USER.PROVIDER_USER_KEY} 에 저장된다
 * @param email         이메일. 제공자가 주지 않았으면 {@code null}
 * @param nickname      닉네임. 없으면 {@code null}
 */
public record SocialUserProfile(SocialProvider provider,
                                String providerKey,
                                String email,
                                String nickname) {

    /**
     * 이메일을 받았는지 확인한다.
     *
     * <p>{@code US_USER.EMAIL} 이 NOT NULL 이라 이메일 없이는 가입할 수 없다.</p>
     *
     * @return 이메일이 있으면 {@code true}
     */
    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
