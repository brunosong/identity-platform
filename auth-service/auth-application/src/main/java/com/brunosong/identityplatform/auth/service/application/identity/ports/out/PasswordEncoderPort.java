package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

/**
 * 비밀번호 해시/검증 드리븐 포트. 구현은 호스트/데이터액세스(BCrypt) 어댑터.
 */
public interface PasswordEncoderPort {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
