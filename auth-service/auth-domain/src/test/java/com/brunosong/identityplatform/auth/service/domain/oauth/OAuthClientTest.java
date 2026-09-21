package com.brunosong.identityplatform.auth.service.domain.oauth;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 돌려보낼 주소를 고르는 규칙을 고정한다.
 *
 * <p>여기가 느슨해지면 우리가 발급한 code 를 공격자 주소로 배달하게 된다. 편의를 위해 한 칸씩
 * 넓히고 싶어지는 자리라서, 넓히면 깨지도록 테스트로 못을 박아 둔다.
 */
class OAuthClientTest {

    private static final OAuthClient PORTAL_APP = OAuthClient.restore(
            "portal", Realm.PORTAL,
            Set.of("http://localhost:5173/callback"), true);

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
}
