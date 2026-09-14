package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 수단별 계정(이메일·소셜)이 Principal 연결을 강제하는지 고정한다.
 *
 * <p>이 계정들은 "이 외부 신원이 어느 Principal 인가"의 매핑만 갖는다. principalId 없이 만들 수 있으면
 * 어디에도 매달리지 않은 자격증명이 생겨 로그인 해석이 끊긴다.
 *
 * <p>소셜 계정은 토큰·시크릿을 저장하지 않는다 — 자격 검증은 provider 가 한다.
 */
class CredentialAccountTest {

    private static final PrincipalId PRINCIPAL_ID = PrincipalId.newId();

    @Nested
    @DisplayName("이메일 계정")
    class Email {

        @Test
        @DisplayName("연결할 신원 없이는 만들 수 없다")
        void requiresPrincipal() {
            assertThatThrownBy(() -> EmailAccount.unverified(null, Realm.ADMIN, "user@example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("principalId");
        }

        @Test
        @DisplayName("이메일이나 realm 이 비어 있으면 만들 수 없다")
        void requiresEmail() {
            assertThatThrownBy(() -> EmailAccount.unverified(PRINCIPAL_ID, Realm.ADMIN, " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email");

            assertThatThrownBy(() -> EmailAccount.unverified(PRINCIPAL_ID, null, "user@example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("realm");
        }

        @Test
        @DisplayName("만들면 자기 식별자를 채번하고 신원에 매단다")
        void createsLinked() {
            EmailAccount account = EmailAccount.unverified(PRINCIPAL_ID, Realm.PORTAL, "user@example.com");

            assertThat(account.getEmailAccountId()).isNotBlank();
            assertThat(account.getPrincipalId()).isEqualTo(PRINCIPAL_ID);
            assertThat(account.getRealm()).isEqualTo(Realm.PORTAL);
            assertThat(account.getEmail()).isEqualTo("user@example.com");
            assertThat(account.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("만드는 방법이 확인 여부를 정한다 — 기본값이 없다")
        void verifiedIsExplicitAtCreation() {
            assertThat(EmailAccount.verified(PRINCIPAL_ID, Realm.PORTAL, "a@b.c").isVerified()).isTrue();
            assertThat(EmailAccount.unverified(PRINCIPAL_ID, Realm.PORTAL, "a@b.c").isVerified()).isFalse();
        }

        @Test
        @DisplayName("소유가 증명되면 확인됨으로 올라가고 되돌아가지 않는다")
        void markVerifiedIsOneWay() {
            EmailAccount account = EmailAccount.unverified(PRINCIPAL_ID, Realm.PORTAL, "a@b.c");

            account.markVerified();

            assertThat(account.isVerified()).isTrue();
            // 되돌리는 연산은 없다 — 한 번 증명된 사실이 나중에 거짓이 되지 않는다.
            assertThat(EmailAccount.class.getDeclaredMethods())
                    .noneMatch(m -> m.getName().equals("markUnverified"));
        }

        @Test
        @DisplayName("복원은 저장된 식별자와 생성 시각을 되살린다")
        void restore() {
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

            EmailAccount account = EmailAccount.restore(
                    "acc-1", PRINCIPAL_ID, Realm.ADMIN, "user@example.com", true, createdAt);

            assertThat(account.getEmailAccountId()).isEqualTo("acc-1");
            assertThat(account.getCreatedAt()).isEqualTo(createdAt);
            assertThat(account.isVerified()).isTrue();
        }
    }

    @Nested
    @DisplayName("소셜 계정")
    class Social {

        @Test
        @DisplayName("연결할 신원 없이는 만들 수 없다")
        void requiresPrincipal() {
            assertThatThrownBy(() -> SocialAccount.create(null, Realm.PORTAL, SocialProvider.KAKAO, "uid-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("principalId");
        }

        @Test
        @DisplayName("공급자 없이는 만들 수 없다")
        void requiresProvider() {
            assertThatThrownBy(() -> SocialAccount.create(PRINCIPAL_ID, Realm.PORTAL, null, "uid-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("provider");
        }

        @Test
        @DisplayName("외부 신원 식별자가 비어 있으면 만들 수 없다")
        void requiresProviderUid() {
            assertThatThrownBy(() -> SocialAccount.create(PRINCIPAL_ID, Realm.PORTAL, SocialProvider.KAKAO, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerUid");
        }

        @Test
        @DisplayName("만들면 공급자와 외부 식별자로 외부 신원을 가리킨다")
        void createsLinked() {
            SocialAccount account = SocialAccount.create(PRINCIPAL_ID, Realm.PORTAL, SocialProvider.NAVER, "uid-1");

            assertThat(account.getSocialAccountId()).isNotBlank();
            assertThat(account.getProvider()).isEqualTo(SocialProvider.NAVER);
            assertThat(account.getProviderUid()).isEqualTo("uid-1");
            assertThat(account.getPrincipalId()).isEqualTo(PRINCIPAL_ID);
        }
    }

    @Nested
    @DisplayName("식별자 값 객체")
    class Identifiers {

        @Test
        @DisplayName("신원 식별자는 비어 있을 수 없다")
        void principalIdNotBlank() {
            assertThatThrownBy(() -> new PrincipalId(" "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PrincipalId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("새 신원 식별자는 매번 다르다")
        void principalIdIsUnique() {
            assertThat(PrincipalId.newId()).isNotEqualTo(PrincipalId.newId());
        }

        @Test
        @DisplayName("주체 식별자는 비어 있을 수 없다")
        void subjectIdNotBlank() {
            assertThatThrownBy(() -> new SubjectId(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("subjectId");
        }

        @Test
        @DisplayName("같은 값이면 같은 식별자다")
        void valueEquality() {
            assertThat(new SubjectId("customer-uuid")).isEqualTo(new SubjectId("customer-uuid"));
        }
    }
}
