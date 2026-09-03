package com.brunosong.identityplatform.auth.service.application.identity.session;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 단일 세션 — 나중에 연 세션이 이전 세션을 밀어내는지, realm 이 다르면 서로 간섭하지 않는지 본다.
 */
class SingleSessionServiceTest {

    private InMemorySessionRegistry registry;
    private SingleSessionService service;

    @BeforeEach
    void setUp() {
        registry = new InMemorySessionRegistry();
        service = new SingleSessionService(registry);
    }

    @Test
    @DisplayName("방금 연 세션은 현재 세션이다")
    void openedSessionIsCurrent() {
        String sid = registry.open(Realm.CUSTOMER, "customer-1");

        assertThat(service.isCurrent(Realm.CUSTOMER, "customer-1", sid)).isTrue();
    }

    @Test
    @DisplayName("같은 주체가 다시 로그인하면 이전 세션은 밀려난다")
    void secondLoginEvictsFirst() {
        String first = registry.open(Realm.CUSTOMER, "customer-1");
        String second = registry.open(Realm.CUSTOMER, "customer-1");

        assertThat(service.isCurrent(Realm.CUSTOMER, "customer-1", first)).isFalse();
        assertThat(service.isCurrent(Realm.CUSTOMER, "customer-1", second)).isTrue();
    }

    @Test
    @DisplayName("무효화하면 그 세션은 더 이상 현재가 아니다")
    void invalidateClosesSession() {
        String sid = registry.open(Realm.CUSTOMER, "customer-1");

        service.invalidate(Realm.CUSTOMER, "customer-1");

        assertThat(service.isCurrent(Realm.CUSTOMER, "customer-1", sid)).isFalse();
    }

    @Test
    @DisplayName("realm 이 다르면 같은 주체 식별자라도 세션은 서로 간섭하지 않는다")
    void realmsAreIsolated() {
        String customerSid = registry.open(Realm.CUSTOMER, "same-id");
        String employeeSid = registry.open(Realm.EMPLOYEE, "same-id");

        assertThat(service.isCurrent(Realm.CUSTOMER, "same-id", customerSid)).isTrue();
        assertThat(service.isCurrent(Realm.EMPLOYEE, "same-id", employeeSid)).isTrue();
        assertThat(service.isCurrent(Realm.CUSTOMER, "same-id", employeeSid)).isFalse();
    }

    @Test
    @DisplayName("sid 나 주체가 비어 있으면 현재 세션으로 보지 않는다")
    void nullsAreNotCurrent() {
        registry.open(Realm.CUSTOMER, "customer-1");

        assertThat(service.isCurrent(Realm.CUSTOMER, "customer-1", null)).isFalse();
        assertThat(service.isCurrent(Realm.CUSTOMER, null, "any-sid")).isFalse();
    }
}
