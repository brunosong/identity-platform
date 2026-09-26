package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.RegisterOAuthClientCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.result.RegisteredOAuthClient;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.OAuthClientRepository;
import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등록할 때 시크릿을 언제, 어떻게 내주는지 고정한다.
 */
class OAuthClientServiceTest {

    private static final List<String> REDIRECTS = List.of("https://batch.example.com/callback");

    private FakeClientRepository clients;
    private OAuthClientService service;

    @BeforeEach
    void setUp() {
        clients = new FakeClientRepository();
        service = new OAuthClientService(clients);
    }

    @Test
    @DisplayName("confidential 로 등록하면 시크릿 원문을 한 번 돌려주고, 저장된 앱은 그것으로 통과한다")
    void confidentialGetsSecret() {
        RegisteredOAuthClient registered = service.register(
                new RegisterOAuthClientCommand(Realm.PORTAL, "배치", REDIRECTS, true));

        assertThat(registered.secret()).isNotBlank();
        OAuthClient saved = clients.findByClientId(Realm.PORTAL, registered.client().getClientId()).orElseThrow();
        assertThat(saved.isConfidential()).isTrue();
        assertThat(saved.authenticates(registered.secret())).isTrue();
    }

    @Test
    @DisplayName("시크릿은 등록할 때마다 새로 뽑는다")
    void secretsDiffer() {
        String first = service.register(
                new RegisterOAuthClientCommand(Realm.PORTAL, "배치 A", REDIRECTS, true)).secret();
        String second = service.register(
                new RegisterOAuthClientCommand(Realm.PORTAL, "배치 B", REDIRECTS, true)).secret();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("public 으로 등록하면 시크릿이 없다")
    void publicGetsNoSecret() {
        RegisteredOAuthClient registered = service.register(
                new RegisterOAuthClientCommand(Realm.PORTAL, "쇼핑몰", REDIRECTS, false));

        assertThat(registered.secret()).isNull();
        assertThat(clients.findByClientId(Realm.PORTAL, registered.client().getClientId()).orElseThrow().isConfidential())
                .isFalse();
    }

    static class FakeClientRepository implements OAuthClientRepository {

        private final Map<String, OAuthClient> store = new HashMap<>();

        @Override
        public Optional<OAuthClient> findByClientId(Realm realm, String clientId) {
            return Optional.ofNullable(store.get(clientId)).filter(c -> c.getRealm() == realm);
        }

        @Override
        public List<OAuthClient> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean exists(String clientId) {
            return store.containsKey(clientId);
        }

        @Override
        public OAuthClient save(OAuthClient client) {
            store.put(client.getClientId(), client);
            return client;
        }
    }
}
