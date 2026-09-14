package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 클라이언트가 토큰의 audience 를 정한다.
 *
 * <p>여기서 고정하려는 것은 둘이다 — <b>통합 로그인이 깨지지 않는다</b>(한 클라이언트가 audience 를
 * 여럿 가질 수 있다)는 것과, <b>realm 을 넘나들 수 없다</b>는 것.
 */
class TokenClientsTest {

    private static TokenProperties.ClientProperties client(Realm realm, String... audiences) {
        TokenProperties.ClientProperties c = new TokenProperties.ClientProperties();
        c.setRealm(realm);
        c.setAudiences(List.of(audiences));
        return c;
    }

    private final TokenClients clients = new TokenClients(Map.of(
            "customer-portal", client(Realm.PORTAL, "customer-service", "order-service"),
            "employee-admin", client(Realm.ADMIN, "auth-service"),
            "broken", client(Realm.PORTAL)));

    @Test
    @DisplayName("한 클라이언트가 여러 audience 를 갖는다 — 토큰 하나로 여러 서비스를 쓴다")
    void oneClientManyAudiences() {
        assertThat(clients.audiencesOf(Realm.PORTAL, "customer-portal"))
                .containsExactly("customer-service", "order-service");
    }

    @Test
    @DisplayName("모르는 클라이언트는 인증 실패다 — 400 이면 존재하는 clientId 를 훑을 수 있다")
    void unknownClientFails() {
        assertThatThrownBy(() -> clients.audiencesOf(Realm.PORTAL, "nope"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("클라이언트는 자기 realm 에서만 토큰을 받는다")
    void clientCannotCrossRealm() {
        // 어드민 앱이 포털 realm 으로 로그인해 포털 audience 를 얻는 길을 막는다.
        assertThatThrownBy(() -> clients.audiencesOf(Realm.PORTAL, "employee-admin"))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> clients.audiencesOf(Realm.ADMIN, "customer-portal"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("두 클라이언트의 audience 가 겹치지 않으면 서로의 서비스에 통하지 않는다")
    void audiencesAreIsolatedBetweenClients() {
        assertThat(clients.audiencesOf(Realm.ADMIN, "employee-admin"))
                .doesNotContain("customer-service");
    }

    @Test
    @DisplayName("audience 가 비어 있으면 설정 오류다 — 받아 줄 서비스가 없는 토큰은 만들 이유가 없다")
    void emptyAudienceIsConfigurationError() {
        assertThatThrownBy(() -> clients.audiencesOf(Realm.PORTAL, "broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audience");
    }
}
