package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;

/**
 * 이메일 OTP 발송 요청 인바운드 포트. 로그인 전 단계로 인증번호(챌린지) 발송을 요청한다.
 *
 * <p>코드 생성·해시·저장은 auth 가 직접 소유하고(등록 여부도 auth principal 로 판단), 실제 전송만
 * {@link com.brunosong.identityplatform.auth.service.application.identity.ports.out.OtpEmailSenderPort} 로
 * 호스트에 위임한다(호스트가 notification 이벤트 등으로 구현).
 */
public interface RequestEmailOtpUseCase {

    /**
     * 이메일로 OTP 챌린지 발송을 요청한다.
     * 계정 열거 방지를 위해 미등록/쿨다운 등은 예외로 드러내지 않고 조용히 흡수한다.
     */
    void request(SubjectType subjectType, String email);
}
