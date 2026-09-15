package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 앱이 상대하는 시스템이 토큰의 audience 가 된다.
 *
 * <p>여기서 고정하려는 것은 둘이다. <b>aud 는 시스템 하나</b>라는 것과, <b>realm 을 넘나들 수
 * 없다</b>는 것.
 *
 * <p>전에는 여기에 서비스 이름을 여럿 적었고 그것이 통합 로그인의 근거였다. 지금은 아니다.
 * 서비스가 몇 개인지는 DB(authz_service)만 알고, 통합 로그인은 세션이 맡는다.
 */
class TokenClientsTest {

    private static TokenProperties.ClientProperties client(Realm realm, String system) {
        TokenProperties.ClientProperties c = new TokenProperties.ClientProperties();
        c.setRealm(realm);
        c.setSystem(system);
        return c;
    }

    private final TokenClients clients = new TokenClients(Map.of(
            "customer-portal", client(Realm.PORTAL, "shop"),
            "employee-admin", client(Realm.ADMIN, "backoffice"),
            "broken", client(Realm.PORTAL, null)));

    @Test
    @DisplayName("앱이 상대하는 시스템 하나가 aud 가 된다")
    void clientResolvesToOneSystem() {
        assertThat(clients.systemOf(Realm.PORTAL, "customer-portal")).isEqualTo("shop");
        assertThat(clients.systemOf(Realm.ADMIN, "employee-admin")).isEqualTo("backoffice");
    }

    @Test
    @DisplayName("서비스를 붙여도 이 값은 그대로다. 그것이 aud 를 시스템으로 올린 이유다")
    void addingAServiceDoesNotChangeTheAudience() {
        // customer-service 하나뿐이던 시절과 order-service 가 붙은 지금이 같은 값이다.
        // 전에는 여기에 서비스를 나열해서, 하나 붙일 때마다 설정을 고치고 이미 발급된 토큰은
        // 새 서비스에 닿지 못했다.
        assertThat(clients.systemOf(Realm.PORTAL, "customer-portal")).isEqualTo("shop");
    }

    @Test
    @DisplayName("모르는 앱은 인증 실패다. 400 이면 존재하는 clientId 를 훑을 수 있다")
    void unknownClientFails() {
        assertThatThrownBy(() -> clients.systemOf(Realm.PORTAL, "nope"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("앱은 자기 realm 에서만 토큰을 받는다")
    void clientCannotCrossRealm() {
        // 어드민 앱이 포털 realm 으로 로그인해 포털 시스템의 토큰을 얻는 길을 막는다.
        assertThatThrownBy(() -> clients.systemOf(Realm.PORTAL, "employee-admin"))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> clients.systemOf(Realm.ADMIN, "customer-portal"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("두 앱의 시스템이 다르면 서로의 토큰이 통하지 않는다")
    void systemsAreIsolatedBetweenClients() {
        assertThat(clients.systemOf(Realm.ADMIN, "employee-admin"))
                .isNotEqualTo(clients.systemOf(Realm.PORTAL, "customer-portal"));
    }

    @Test
    @DisplayName("system 이 비어 있으면 설정 오류다. 향할 곳이 없는 토큰은 만들 이유가 없다")
    void emptySystemIsConfigurationError() {
        assertThatThrownBy(() -> clients.systemOf(Realm.PORTAL, "broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("system");
    }
}
