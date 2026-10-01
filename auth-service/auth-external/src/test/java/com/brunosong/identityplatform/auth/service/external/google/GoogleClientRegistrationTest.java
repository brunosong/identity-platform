package com.brunosong.identityplatform.auth.service.external.google;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구글에 등록해 둔 주소와 우리가 만들어 보내는 주소가 어긋나지 않게 고정한다.
 *
 * <p>둘이 한 글자라도 다르면 구글이 {@code redirect_uri_mismatch} 로 거절한다. 그 실패는 사용자가
 * 구글 화면까지 갔다가 보게 되는 것이라, 여기서 못을 박아 둔다.
 */
class GoogleClientRegistrationTest {

    @Test
    @DisplayName("돌아올 주소는 realm 발급자 주소 뒤에 콜백 경로를 붙인 값이다")
    void buildsRedirectUriFromIssuer() {
        GoogleClientRegistration registration = GoogleClientRegistration.of(
                "1234.apps.googleusercontent.com", "GOCSPX-secret",
                "http://localhost:8080/realms/portal");

        // 구글 콘솔의 Authorized redirect URIs 에 등록한 값과 글자 그대로 같아야 한다.
        assertThat(registration.redirectUri())
                .isEqualTo("http://localhost:8080/realms/portal/broker/google/endpoint");
    }

    @Test
    @DisplayName("발급자 주소를 옮기면 돌아올 주소도 따라 움직인다")
    void redirectUriFollowsIssuer() {
        // 포트를 옮겼는데 콜백 주소가 그대로면 구글이 거절한다. 한 곳에서만 나오게 해둔 이유다.
        GoogleClientRegistration registration = GoogleClientRegistration.of(
                "1234.apps.googleusercontent.com", "GOCSPX-secret",
                "http://localhost:8090/realms/portal");

        assertThat(registration.redirectUri())
                .isEqualTo("http://localhost:8090/realms/portal/broker/google/endpoint");
    }

    @Test
    @DisplayName("시크릿이 없으면 부팅에서 죽는다")
    void failsFastWithoutSecret() {
        // 없어도 구글로 보내는 것까지는 된다. 실패는 사용자가 구글에서 돌아온 뒤 교환 단계에서
        // 나타나므로, 그때까지 기다리지 않고 뜰 때 막는다.
        assertThatThrownBy(() -> GoogleClientRegistration.of(
                "1234.apps.googleusercontent.com", "  ", "http://localhost:8080/realms/portal"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social.google.client-secret");
    }
}
