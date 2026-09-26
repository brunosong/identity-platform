package com.brunosong.identityplatform.auth.service.domain.oauth;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 돌려보낼 주소를 고르는 규칙을 고정한다.
 *
 * <p>여기가 느슨해지면 우리가 발급한 code 를 공격자 주소로 배달하게 된다. 편의를 위해 한 칸씩
 * 넓히고 싶어지는 자리라서, 넓히면 깨지도록 테스트로 못을 박아 둔다.
 */
class OAuthClientTest {

    private static final OAuthClient PORTAL_APP = OAuthClient.restore(
            "portal-17kqqi85h2ks", "포털", Realm.PORTAL,
            Set.of("http://localhost:5173/callback"), true, null);

    @Test
    @DisplayName("등록된 주소는 통과한다")
    void allowsRegisteredUri() {
        assertThat(PORTAL_APP.allowsRedirect("http://localhost:5173/callback")).isTrue();
    }

    @Test
    @DisplayName("등록되지 않은 주소는 거절한다")
    void rejectsUnregisteredUri() {
        assertThat(PORTAL_APP.allowsRedirect("http://evil.example.com/callback")).isFalse();
    }

    @Test
    @DisplayName("같은 앱의 다른 경로여도 등록돼 있지 않으면 거절한다")
    void rejectsOtherPathOnSameOrigin() {
        // 출처가 같으니 괜찮지 않냐는 생각이 드는 자리다. 그 앱에 열린 리다이렉트가 하나만 있어도
        // code 가 새어나가므로, 출처가 아니라 주소 전체를 대조한다.
        assertThat(PORTAL_APP.allowsRedirect("http://localhost:5173/")).isFalse();
    }

    @Test
    @DisplayName("슬래시 하나만 달라도 거절한다")
    void rejectsTrailingSlashVariant() {
        assertThat(PORTAL_APP.allowsRedirect("http://localhost:5173/callback/")).isFalse();
    }

    @Test
    @DisplayName("주소가 없으면 거절한다")
    void rejectsNull() {
        assertThat(PORTAL_APP.allowsRedirect(null)).isFalse();
    }

    @Nested
    @DisplayName("시크릿")
    class Secret {

        private final OAuthClient confidential = PORTAL_APP.withSecret("s3cret-value");

        @Test
        @DisplayName("같은 시크릿을 내면 통과한다")
        void authenticatesWithSameSecret() {
            assertThat(confidential.isConfidential()).isTrue();
            assertThat(confidential.authenticates("s3cret-value")).isTrue();
        }

        @Test
        @DisplayName("다른 시크릿이나 빈 값은 거절한다")
        void rejectsWrongSecret() {
            assertThat(confidential.authenticates("s3cret-valuE")).isFalse();
            assertThat(confidential.authenticates(null)).isFalse();
        }

        @Test
        @DisplayName("원문을 담지 않는다")
        void storesOnlyHash() {
            assertThat(confidential.secretHash()).isNotBlank().doesNotContain("s3cret-value");
        }

        @Test
        @DisplayName("시크릿이 없는 앱은 무엇을 내도 통과하지 않는다")
        void publicClientNeverAuthenticates() {
            // 시크릿 없는 앱에 아무 값이나 내고 통과하면 시크릿 검사가 뚫린 것과 같다.
            assertThat(PORTAL_APP.isConfidential()).isFalse();
            assertThat(PORTAL_APP.authenticates("anything")).isFalse();
        }

        @Test
        @DisplayName("빈 시크릿으로는 만들 수 없다")
        void rejectsBlankSecret() {
            assertThatThrownBy(() -> PORTAL_APP.withSecret(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("등록")
    class Register {

        @Test
        @DisplayName("등록한 앱은 켜진 채로 시작하고 주소를 그대로 들고 있다")
        void registersEnabled() {
            OAuthClient client = OAuthClient.register(Realm.PORTAL, "쇼핑몰",
                    List.of("https://shop.example.com/callback"));

            assertThat(client.getName()).isEqualTo("쇼핑몰");
            assertThat(client.isEnabled()).isTrue();
            assertThat(client.allowsRedirect("https://shop.example.com/callback")).isTrue();
        }

        @Test
        @DisplayName("주소가 하나도 없으면 등록할 수 없다")
        void rejectsEmptyRedirectUris() {
            // 돌려보낼 곳이 없는 앱은 로그인을 시작해 봐야 갈 데가 없다.
            assertThatThrownBy(() -> OAuthClient.register(Realm.PORTAL, "쇼핑몰", List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("상대 주소는 거절한다")
        void rejectsRelativeUri() {
            assertThatThrownBy(() -> OAuthClient.register(Realm.PORTAL, "쇼핑몰", List.of("/callback")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("http/https 가 아니면 거절한다")
        void rejectsOtherScheme() {
            // 커스텀 스킴(myapp://)은 모바일 앱의 자리다. 지금 상대하는 것은 브라우저 앱뿐이고,
            // 받아주려면 그 스킴을 누가 가져갈 수 있는지부터 따져야 한다.
            assertThatThrownBy(() -> OAuthClient.register(Realm.PORTAL, "쇼핑몰",
                    List.of("myapp://callback"))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("조각이 붙은 주소는 거절한다")
        void rejectsFragment() {
            // 조각은 브라우저가 서버로 보내지 않아 대조할 수가 없다(RFC 6749 3.1.2).
            assertThatThrownBy(() -> OAuthClient.register(Realm.PORTAL, "쇼핑몰",
                    List.of("https://shop.example.com/callback#done")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("앞뒤 공백은 떼고 담는다")
        void trimsInput() {
            OAuthClient client = OAuthClient.register(Realm.PORTAL, "  쇼핑몰  ",
                    List.of("  https://shop.example.com/callback  "));

            assertThat(client.getName()).isEqualTo("쇼핑몰");
            assertThat(client.allowsRedirect("https://shop.example.com/callback")).isTrue();
        }

        @Test
        @DisplayName("client_id 는 realm 접두어에 난수 12자를 붙여 발급한다. 등록할 때마다 다르다")
        void issuesClientId() {
            OAuthClient portal = OAuthClient.register(Realm.PORTAL, "쇼핑몰", List.of("https://shop.example.com/cb"));
            OAuthClient again = OAuthClient.register(Realm.PORTAL, "쇼핑몰", List.of("https://shop.example.com/cb"));
            OAuthClient admin = OAuthClient.register(Realm.ADMIN, "백오피스", List.of("https://admin.example.com/cb"));

            assertThat(portal.getClientId()).matches("portal-[a-z0-9]{12}");
            assertThat(admin.getClientId()).matches("admin-[a-z0-9]{12}");
            // 같은 이름으로 두 번 등록해도 다른 앱이다. 이름은 식별자가 아니다.
            assertThat(again.getClientId()).isNotEqualTo(portal.getClientId());
        }

        @Test
        @DisplayName("이름이 비었거나 100자를 넘으면 등록할 수 없다")
        void rejectsBadName() {
            assertThatThrownBy(() -> OAuthClient.register(Realm.PORTAL, " ", List.of("https://shop.example.com/cb")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> OAuthClient.register(Realm.PORTAL, "가".repeat(101),
                    List.of("https://shop.example.com/cb"))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
