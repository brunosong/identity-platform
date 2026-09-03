package com.brunosong.identityplatform.auth.service.messaging.identity.publisher.local;

import com.brunosong.identityplatform.auth.service.application.identity.event.PrincipalAuthenticatedEvent;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalAuthenticatedEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 인증 성공 이벤트의 로컬(인프로세스) 발행 어댑터. 스프링 {@link ApplicationEventPublisher} 로 발행해
 * 같은 호스트에 스캔된 소비 어댑터가 받는다. MSA 승급 시 이 어댑터만 브로커/HTTP 구현으로 교체된다.
 *
 * <p>발행 시점과 소비 시점의 관계는 소비 쪽이 정한다 — {@code @EventListener} 면 발행자 트랜잭션 안에서
 * 동기로 돌고, {@code @TransactionalEventListener(AFTER_COMMIT)} 면 커밋 후에 돈다. 발행 어댑터는
 * 그 선택을 강제하지 않는다. 인증 성공의 후속 처리(최종접속 갱신 등)는 실패해도 로그인을 되돌리면 안 되므로
 * 커밋 후 소비가 맞다.
 */
@Component
@RequiredArgsConstructor
public class LocalPrincipalAuthenticatedEventPublisher implements PrincipalAuthenticatedEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(PrincipalAuthenticatedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
