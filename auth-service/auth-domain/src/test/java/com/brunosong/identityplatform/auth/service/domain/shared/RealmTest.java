package com.brunosong.identityplatform.auth.service.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * realm 정책을 고정한다. 이 프로젝트에서 가장 뒤집히면 안 되는 값이다.
 *
 * <p>셀프 가입도 마찬가지다. 가입 경로는 방식마다 하나뿐이고, 이 값이 그것을 realm 마다 열고 닫는다.
 * <b>어드민에 비밀번호 가입이 열리면 안 된다</b> — 직원은 비밀번호 계정을 갖지 않으므로 이 서비스가
 * 만들 수 없는 계정을 요구하는 경로가 생긴다.
 */
class RealmTest {

    @Test
    @DisplayName("포털은 두 방식 모두로 스스로 가입할 수 있다")
    void portalAllowsBothMethods() {
        assertThat(Realm.PORTAL.allowsSelfRegistration()).isTrue();
        assertThat(Realm.PORTAL.allowsSelfRegistrationWith(RegistrationMethod.PASSWORD)).isTrue();
        assertThat(Realm.PORTAL.allowsSelfRegistrationWith(RegistrationMethod.EMAIL_OTP)).isTrue();
    }

    @Test
    @DisplayName("어드민은 이메일 인증번호로만 가입할 수 있다")
    void adminAllowsOnlyEmailOtp() {
        assertThat(Realm.ADMIN.allowsSelfRegistration()).isTrue();
        assertThat(Realm.ADMIN.allowsSelfRegistrationWith(RegistrationMethod.EMAIL_OTP)).isTrue();
    }

    @Test
    @DisplayName("어드민에 비밀번호 가입은 열리지 않는다")
    void adminDeniesPasswordRegistration() {
        // 직원은 비밀번호 계정을 갖지 않는다. 열리면 만들 수 없는 계정을 요구하는 경로가 생긴다.
        assertThat(Realm.ADMIN.allowsSelfRegistrationWith(RegistrationMethod.PASSWORD)).isFalse();
    }

    @Test
    @DisplayName("가입 방식이 비어 있으면 셀프 가입이 닫힌 것이다")
    void emptyMethodsMeansClosed() {
        // 켜짐/꺼짐을 따로 두지 않는 이유 — "열려 있는데 방식이 없다" 는 상태가 생기지 않는다.
        for (Realm realm : Realm.values()) {
            assertThat(realm.allowsSelfRegistration())
                    .isEqualTo(!realm.registrationMethods().isEmpty());
        }
    }

    @Test
    @DisplayName("어느 realm 도 스스로 가입을 전부 닫고 있지 않다")
    void everyRealmOpensAtLeastOneMethod() {
        // 닫으려면 방식 집합을 비우면 된다. 지금은 둘 다 열려 있고, 무엇으로 여는지가 다르다.
        for (Realm realm : Realm.values()) {
            assertThat(realm.registrationMethods()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("영역은 둘뿐이다")
    void onlyTwoRealms() {
        // 영역이 늘면 기본 결정을 반드시 정해야 한다. 여기서 걸려 잊지 않게 한다.
        assertThat(Realm.values()).containsExactly(Realm.ADMIN, Realm.PORTAL);
    }
}
