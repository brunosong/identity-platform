package com.brunosong.identityplatform.auth.service.application.identity.ports.in.command;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;

/**
 * 이메일 OTP 로그인 명령. 코드 발송은 별도(호스트), 여기선 검증만.
 *
 * <p>주체 유형을 함께 받는다. 이메일만으로 주체를 찾으면 상대 realm 의 Principal 이 걸려,
 * 직원 로그인 경로로 고객 신원의 토큰이 나갈 수 있다.
 */
public record EmailOtpAuthCommand(SubjectType subjectType, String email, String otpCode) {
}
