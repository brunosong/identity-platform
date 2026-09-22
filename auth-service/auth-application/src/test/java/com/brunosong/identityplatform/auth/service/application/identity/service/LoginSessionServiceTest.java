package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.LoginSessionRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.brunosong.identityplatform.auth.service.application.identity.service.IdentityFakes.FakePrincipalRepository;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠키로 들어온 세션을 언제 믿는지 고정한다.
 *
 * <p>여기서 값이 나오면 <b>로그인 화면을 건너뛴다.</b> 조건이 느슨해지면 남의 세션이나 죽은
 * 세션으로 코드를 받아가는 길이 열린다.
 */
class LoginSessionServiceTest {

    private FakeSessionRepository sessions;
    private FakePrincipalRepository principals;
    private LoginSessionService service;

    private Principal principal;

    @BeforeEach
    void setUp() {
        sessions = new FakeSessionRepository();
        principals = new FakePrincipalRepository();
        service = new LoginSessionService(sessions, principals);

        principal = principals.seed("customer-uuid-1", Realm.PORTAL);
    }

    private String startedSession() {
        return service.start(Realm.PORTAL, principal.getPrincipalId()).getSessionId();
    }

    @Test
    @DisplayName("살아 있는 세션이면 누구인지 돌려준다")
    void activeSessionTellsWho() {
        AuthenticatedSubject subject = service.findActive(Realm.PORTAL, startedSession()).orElseThrow();

        assertThat(subject.principalId()).isEqualTo(principal.getPrincipalId());
        assertThat(subject.subjectId()).isEqualTo("customer-uuid-1");
        assertThat(subject.realm()).isEqualTo(Realm.PORTAL);
    }

    @Test
    @DisplayName("만료된 세션은 비어 있다")
    void expiredSessionIsEmpty() {
        LoginSession old = LoginSession.start(Realm.PORTAL, principal.getPrincipalId(),
                Instant.now().minus(Duration.ofHours(9)));
        sessions.save(old);

        assertThat(service.findActive(Realm.PORTAL, old.getSessionId())).isEmpty();
    }

    @Test
    @DisplayName("realm 이 다르면 비어 있다")
    void otherRealmSessionIsEmpty() {
        // 쿠키 경로가 realm 으로 갈려 있어도 쿠키는 결국 요청자가 실어 보내는 값이다.
        assertThat(service.findActive(Realm.ADMIN, startedSession())).isEmpty();
    }

    @Test
    @DisplayName("없는 세션과 빈 값은 비어 있다")
    void unknownSessionIsEmpty() {
        assertThat(service.findActive(Realm.PORTAL, "없는-세션")).isEmpty();
        assertThat(service.findActive(Realm.PORTAL, null)).isEmpty();
        assertThat(service.findActive(Realm.PORTAL, "  ")).isEmpty();
    }

    @Test
    @DisplayName("세션이 가리키는 신원이 사라졌으면 비어 있다")
    void danglingSessionIsEmpty() {
        LoginSession orphan = LoginSession.start(Realm.PORTAL, new PrincipalId("사라진-사람"),
                Instant.now());
        sessions.save(orphan);

        assertThat(service.findActive(Realm.PORTAL, orphan.getSessionId())).isEmpty();
    }

    static class FakeSessionRepository implements LoginSessionRepository {

        final Map<String, LoginSession> stored = new HashMap<>();

        @Override
        public void save(LoginSession session) {
            stored.put(session.getSessionId(), session);
        }

        @Override
        public Optional<LoginSession> findById(String sessionId) {
            return Optional.ofNullable(stored.get(sessionId));
        }
    }
}
