package com.brunosong.identityplatform.auth.service.domain.identity;

/**
 * 자격증명 검증 실패(아이디 없음 / 비번 불일치 / OTP 불일치 / 잠김 등).
 * realm별 CredentialVerifier 어댑터가 던지고, 호스트 로그인 컨트롤러가 401 등으로 변환한다.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
