package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

/**
 * 생성된 OTP 코드를 이메일로 발송하는 드리븐 포트. auth 가 코드를 생성해 이 포트로 넘기고,
 * 전송 방식(notification 이벤트 등)은 호스트 어댑터가 정한다. auth 는 발송 인프라를 모른다.
 *
 * <p>포트 시그니처는 원시값만 노출한다 — notification 타입을 여기 두면 auth 커널이 그 모듈에
 * 의존하게 되므로 금지(호스트 어댑터에서만 notification 이벤트를 조립한다).
 */
public interface OtpEmailSenderPort {

    void send(String email, String code);
}
