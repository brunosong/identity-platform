package com.brunosong.identityplatform.auth.service.messaging.identity.publisher.local;

import com.brunosong.identityplatform.auth.service.application.identity.event.EmployeeRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmployeeRegisteredEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 직원 등록 이벤트의 로컬(인프로세스) 발행 어댑터. 스프링 {@link ApplicationEventPublisher} 로 발행해
 * 같은 호스트에 스캔된 소비 어댑터(employee 프로필 생성)가 받는다.
 * MSA 승급 시 이 어댑터만 브로커/HTTP 구현으로 교체된다.
 */
@Component
@RequiredArgsConstructor
public class LocalEmployeeRegisteredEventPublisher implements EmployeeRegisteredEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(EmployeeRegisteredEvent event) {
        eventPublisher.publishEvent(event);
    }
}
