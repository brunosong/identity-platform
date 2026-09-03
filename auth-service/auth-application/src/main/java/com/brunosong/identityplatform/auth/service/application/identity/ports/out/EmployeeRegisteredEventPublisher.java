package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.application.identity.event.EmployeeRegisteredEvent;

/**
 * 직원 등록 이벤트 발행 드리븐 포트. application 은 전송 수단(로컬 인프로세스/브로커)을 모르고 이 포트로만
 * 발행한다. 로컬 어댑터는 auth-messaging 의 {@code messaging.identity.publisher.local} 에 있다.
 */
public interface EmployeeRegisteredEventPublisher {

    void publish(EmployeeRegisteredEvent event);
}
