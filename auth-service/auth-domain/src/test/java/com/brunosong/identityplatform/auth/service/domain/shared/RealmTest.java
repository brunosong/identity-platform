package com.brunosong.identityplatform.auth.service.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * realm 별 기본 인가 결정을 고정한다. 이 프로젝트에서 가장 뒤집히면 안 되는 값이다.
 *
 * <p>매칭되는 URL 규칙이 없을 때 무엇을 할지가 realm 마다 반대다.
 *
 * <ul>
 *   <li>EMPLOYEE(admin) — fail-closed. 등록된 것만 허용한다. 여기가 true 로 뒤집히면 규칙을 등록하지
 *       않은 관리자 화면이 통째로 열린다.</li>
 *   <li>CUSTOMER(portal) — fail-open. 등록된 것만 차단한다. 여기가 false 로 뒤집히면 규칙 없는
 *       사용자 화면이 전부 막혀 서비스가 멈춘다.</li>
 * </ul>
 */
class RealmTest {

    @Test
    @DisplayName("직원 영역은 규칙이 없으면 거부한다")
    void employeeIsFailClosed() {
        assertThat(Realm.EMPLOYEE.failOpen()).isFalse();
    }

    @Test
    @DisplayName("고객 영역은 규칙이 없으면 허용한다")
    void customerIsFailOpen() {
        assertThat(Realm.CUSTOMER.failOpen()).isTrue();
    }

    @Test
    @DisplayName("두 영역의 기본 결정은 서로 반대다")
    void realmsAreOpposite() {
        assertThat(Realm.EMPLOYEE.failOpen()).isNotEqualTo(Realm.CUSTOMER.failOpen());
    }

    @Test
    @DisplayName("영역은 둘뿐이다")
    void onlyTwoRealms() {
        // 영역이 늘면 기본 결정을 반드시 정해야 한다. 여기서 걸려 잊지 않게 한다.
        assertThat(Realm.values()).containsExactly(Realm.EMPLOYEE, Realm.CUSTOMER);
    }
}
