package com.brunosong.identityplatform.auth.service.messaging.identity.publisher.local;

import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SubjectRegisteredEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 주체 등록 이벤트의 로컬(인프로세스) 발행 어댑터. 스프링 {@link ApplicationEventPublisher} 로 발행해
 * 같은 호스트에 스캔된 소비 어댑터(customer 프로필 생성, 기본 역할 부여)가 받는다.
 * MSA 승급 시 이 어댑터만 브로커/HTTP 구현으로 교체된다.
 */
@Component
@RequiredArgsConstructor
public class LocalSubjectRegisteredEventPublisher implements SubjectRegisteredEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(SubjectRegisteredEvent event) {
        eventPublisher.publishEvent(event);
    }
}
