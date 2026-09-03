package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

/**
 * 직원 계정 등록 명령. auth 가 esntlId 채번 + Principal 생성 후 employee 프로필을 만든다.
 * 직원 로그인은 이메일 OTP 라 비밀번호를 받지 않는다 — 자격증명은 auth 소유다.
 */
public record RegisterEmployeeAccountCommand(
        String employeeId,
        String name,
        String email,
        String mobile,
        String positionName,
        String organizationId,
        String statusCode) {
}
