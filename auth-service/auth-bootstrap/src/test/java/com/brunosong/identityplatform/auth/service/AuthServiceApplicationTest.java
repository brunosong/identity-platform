package com.brunosong.identityplatform.auth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSigningKeys;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 서비스가 실제로 뜨는지 본다. 지금껏 없던 검증이다 — 단위 테스트와 어댑터 테스트만 있었고,
 * 스프링 컨텍스트가 조립되는지는 아무도 확인하지 않았다. 와이어링 실수(빈 누락, 순환, 프로퍼티 오타,
 * 잘못된 키 인코딩)는 여기서만 드러난다.
 *
 * <p>컨텍스트가 뜨는 것만으로 확인되는 것들이 있다:
 * <ul>
 *   <li>realm 별 RSA 키가 실제로 디코드된다 — {@code RealmSigningKeys} 는 생성자에서 키를 읽고
 *       realm 끼리 kid 가 겹치면 던진다. 설정이 틀리면 여기서 부팅이 깨진다.</li>
 *   <li>토큰 발급기가 선택 빈이 아니게 됐으므로, 없으면 컨텍스트가 뜨지 않는다.</li>
 *   <li>realm 프로퍼티로 껐다 켜던 빈들이 이제 조건 없이 모두 뜬다.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class AuthServiceApplicationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RealmSigningKeys signingKeys;

    @Test
    @DisplayName("컨텍스트가 뜬다")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("두 realm 의 서명 키를 모두 쥔다")
    void bothRealmKeysAreLoaded() {
        // 한 프로세스가 두 realm 을 담당한다는 것이 구조의 전제다. 한쪽 키만 있으면 그 realm 은
        // 로그인 자체가 되지 않는데, 그 사실이 첫 로그인 요청에서야 드러나면 늦다.
        assertThat(signingKeys.of(Realm.ADMIN).kid()).isEqualTo("admin-local");
        assertThat(signingKeys.of(Realm.PORTAL).kid()).isEqualTo("portal-local");

        // kid 가 겹치면 상대 realm 키로 검증이 통과한다. 서로 달라야 한다.
        assertThat(signingKeys.verifyKeys()).hasSize(2);
    }

    @Test
    @DisplayName("토큰 발급기는 선택 빈이 아니다")
    void tokenIssuerIsRequired() {
        assertThat(context.getBean(TokenIssuerPort.class)).isNotNull();
    }
}
