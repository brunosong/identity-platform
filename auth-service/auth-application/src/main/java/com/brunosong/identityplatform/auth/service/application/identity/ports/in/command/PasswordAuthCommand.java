package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

/**
 * 아이디/비밀번호 로그인 명령.
 */
public record PasswordAuthCommand(String loginId, String password) {
}
