package com.brunosong.identityplatform.auth.service.domain.identity.valueobject;

/**
 * 발급된 토큰쌍. 실제 형식(JWT 암호화/클레임)은 realm/host 의 TokenIssuer 어댑터가 정한다.
 * refreshToken 은 없을 수 있다(null 허용).
 */
public record TokenPair(String accessToken, String refreshToken) {
}
