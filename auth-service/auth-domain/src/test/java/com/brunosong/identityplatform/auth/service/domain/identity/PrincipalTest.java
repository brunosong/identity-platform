package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalStatus;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인증 신원의 생성과 인증 표시를 고정한다.
 *
 * <p>정지·탈퇴 신원은 인증 표시를 남길 수 없다. 이 검사가 빠지면 잠긴 계정이 로그인 성공 이력만
 * 남기고 통과하는 경로가 생긴다.
 *
 * <p>Principal 은 주체를 subjectId 로만 가리킨다 — customer_seq 같은 대상 BC 의 내부 PK 를 모른다.
 */
class PrincipalTest {

    private static final SubjectId SUBJECT_ID = new SubjectId("customer-uuid");

    private static Principal active() {
        return Principal.create(SUBJECT_ID, Realm.PORTAL);
    }

    @Test
    @DisplayName("만들면 활성 상태이고 인증 이력은 비어 있다")
    void startsActiveWithoutAuthHistory() {
        Principal principal = active();

        assertThat(principal.getStatus()).isEqualTo(PrincipalStatus.ACTIVE);
        assertThat(principal.getLastAuthenticatedAt()).isNull();
    }

    @Test
    @DisplayName("신원마다 자기 식별자를 채번한다")
    void assignsOwnId() {
        assertThat(active().getPrincipalId()).isNotNull();
        assertThat(active().getPrincipalId()).isNotEqualTo(active().getPrincipalId());
    }

    @Test
    @DisplayName("주체는 subjectId 와 유형으로만 가리킨다")
    void referencesSubjectOnly() {
        Principal principal = active();

        assertThat(principal.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(principal.getRealm()).isEqualTo(Realm.PORTAL);
    }

    @Test
    @DisplayName("인증하면 마지막 인증 시각을 찍는다")
    void marksAuthenticated() {
        Principal principal = active();

        principal.markAuthenticated();

        assertThat(principal.getLastAuthenticatedAt()).isNotNull();
    }

    @Test
    @DisplayName("정지된 신원은 인증 표시를 남길 수 없다")
    void suspendedCannotAuthenticate() {
        Principal principal = Principal.restore(PrincipalId.newId(), SUBJECT_ID, Realm.PORTAL,
                PrincipalStatus.SUSPENDED, null);

        assertThatThrownBy(principal::markAuthenticated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUSPENDED");
    }

    @Test
    @DisplayName("탈퇴한 신원도 인증 표시를 남길 수 없다")
    void withdrawnCannotAuthenticate() {
        Principal principal = Principal.restore(PrincipalId.newId(), SUBJECT_ID, Realm.ADMIN,
                PrincipalStatus.WITHDRAWN, null);

        assertThatThrownBy(principal::markAuthenticated).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("막힌 인증은 기존 인증 시각을 건드리지 않는다")
    void blockedAuthKeepsPreviousTimestamp() {
        Instant before = Instant.parse("2026-01-01T00:00:00Z");
        Principal principal = Principal.restore(PrincipalId.newId(), SUBJECT_ID, Realm.PORTAL,
                PrincipalStatus.SUSPENDED, before);

        assertThatThrownBy(principal::markAuthenticated).isInstanceOf(IllegalStateException.class);

        assertThat(principal.getLastAuthenticatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("복원은 저장된 상태와 인증 시각을 되살린다")
    void restoreKeepsStoredState() {
        PrincipalId principalId = PrincipalId.newId();
        Instant authenticatedAt = Instant.parse("2026-08-06T00:00:00Z");

        Principal principal = Principal.restore(principalId, SUBJECT_ID, Realm.ADMIN,
                PrincipalStatus.ACTIVE, authenticatedAt);

        assertThat(principal.getPrincipalId()).isEqualTo(principalId);
        assertThat(principal.getLastAuthenticatedAt()).isEqualTo(authenticatedAt);
    }
}
