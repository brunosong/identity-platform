package com.brunosong.identityplatform.auth.service.domain.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 어떤 인가 요청을 받아들이는지 고정한다.
 *
 * <p>느슨하게 열어주고 싶어지는 자리들이다. implicit 를 받아주거나 PKCE 를 선택으로 두면
 * 당장은 붙이기 쉬워지고, 대신 code 나 토큰이 새는 길이 열린다.
 */
class AuthorizationRequestTest {

    private static AuthorizationRequest valid() {
        return AuthorizationRequest.of("code", "portal", "http://localhost:5173/login/callback",
                "openid", "state-1", "challenge-1", "S256", "nonce-1");
    }

    @Test
    @DisplayName("갖출 것을 갖추면 만들어진다")
    void accepts() {
        AuthorizationRequest request = valid();

        assertThat(request.getClientId()).isEqualTo("portal");
        assertThat(request.getRedirectUri()).isEqualTo("http://localhost:5173/login/callback");
        assertThat(request.getState()).isEqualTo("state-1");
        assertThat(request.getCodeChallenge()).isEqualTo("challenge-1");
    }

    @Test
    @DisplayName("토큰을 바로 달라는 요청은 받지 않는다")
    void rejectsImplicit() {
        assertThatThrownBy(() -> AuthorizationRequest.of("token", "portal",
                "http://localhost:5173/login/callback", "openid", "state-1", "challenge-1", "S256", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PKCE 없이는 받지 않는다")
    void rejectsMissingChallenge() {
        assertThatThrownBy(() -> AuthorizationRequest.of("code", "portal",
                "http://localhost:5173/login/callback", "openid", "state-1", null, "S256", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("해시하지 않는 PKCE(plain)는 받지 않는다")
    void rejectsPlainChallengeMethod() {
        // plain 은 값을 그대로 보내므로, 요청을 들여다본 쪽이 그대로 흉내낼 수 있다.
        assertThatThrownBy(() -> AuthorizationRequest.of("code", "portal",
                "http://localhost:5173/login/callback", "openid", "state-1", "challenge-1", "plain", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("돌아갈 주소가 없으면 받지 않는다")
    void rejectsMissingRedirectUri() {
        assertThatThrownBy(() -> AuthorizationRequest.of("code", "portal", null,
                "openid", "state-1", "challenge-1", "S256", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("없어도 되는 값은 비워 둔다")
    void optionalsMayBeMissing() {
        AuthorizationRequest request = AuthorizationRequest.of("code", "portal",
                "http://localhost:5173/login/callback", null, null, "challenge-1", "S256", null);

        assertThat(request.getScope()).isNull();
        assertThat(request.getState()).isNull();
        assertThat(request.getNonce()).isNull();
    }
}
