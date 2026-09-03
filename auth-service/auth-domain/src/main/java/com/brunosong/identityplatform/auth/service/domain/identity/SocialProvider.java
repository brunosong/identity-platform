package com.brunosong.identityplatform.auth.service.domain.identity;

/**
 * 소셜 로그인 제공자. providerUid 의 의미는 제공자별로 다르다(OIDC sub 등 안정적 식별자).
 */
public enum SocialProvider {
    GOOGLE,
    KAKAO,
    NAVER,
    APPLE
}
