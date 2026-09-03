package com.brunosong.identityplatform.auth.service.application.identity.session;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.SingleSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 단일 세션("한 곳에서 한 명") 와이어링 — 호스트가 {@code authorization.single-session.enabled=true} 로 켠다.
 *
 * <p>켜지 않은 호스트(예: 다중 로그인 허용 portal)에는 {@link SessionRegistryPort}/{@link SingleSessionUseCase}
 * 빈이 생성되지 않는다. 그러면 {@code RbacJwtTokenIssuer} 는 sid 를 토큰에 넣지 않고, 게이트웨이는 세션 검증을
 * 건너뛴다. RBAC 토큰 발급기(RbacTokenIssuerConfiguration)와 같은 프로퍼티-옵트인 방식이다.
 */
@Configuration
@ConditionalOnProperty(name = "authorization.single-session.enabled", havingValue = "true")
public class SingleSessionConfiguration {

    @Bean
    public SessionRegistryPort sessionRegistryPort() {
        return new InMemorySessionRegistry();
    }

    @Bean
    public SingleSessionUseCase singleSessionUseCase(SessionRegistryPort sessionRegistryPort) {
        return new SingleSessionService(sessionRegistryPort);
    }
}
