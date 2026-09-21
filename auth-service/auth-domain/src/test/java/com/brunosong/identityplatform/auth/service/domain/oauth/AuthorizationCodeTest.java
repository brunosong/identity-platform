package com.brunosong.identityplatform.auth.service.domain.oauth;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인가 코드를 누가 쓸 수 있는지 고정한다.
 *
 * <p>이 값은 주소창에 실려 나가므로 주워질 수 있다고 보고 만들어야 한다. 아래 조건 중 하나라도
 * 느슨해지면, 주운 쪽이 남의 토큰을 받아간다.
 */
class AuthorizationCodeTest {

    private static final String VERIFIER = "verifier-0123456789-0123456789-0123456789";
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    private static AuthorizationCode issued() {
        AuthorizationRequest request = AuthorizationRequest.of("code", "portal",
                "http://localhost:5173/login/callback", "openid", "state-1",
                challengeOf(VERIFIER), "S256", "nonce-1");
        return AuthorizationCode.issue(Realm.PORTAL, request, new PrincipalId("p-1"), NOW);
    }

    @Test
    @DisplayName("발급하면 요청의 값들을 그대로 묶어 둔다")
    void keepsRequestValues() {
        AuthorizationCode code = issued();

        assertThat(code.getClientId()).isEqualTo("portal");
        assertThat(code.getRedirectUri()).isEqualTo("http://localhost:5173/login/callback");
        assertThat(code.getPrincipalId().value()).isEqualTo("p-1");
        assertThat(code.getNonce()).isEqualTo("nonce-1");
        assertThat(code.getRealm()).isEqualTo(Realm.PORTAL);
    }

    @Test
    @DisplayName("코드 값은 추측할 수 없는 난수다")
    void codeIsRandom() {
        assertThat(issued().getCode())
                .isNotBlank()
                .isNotEqualTo(issued().getCode())
                .hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("1분이 지나면 죽는다")
    void expiresInAMinute() {
        AuthorizationCode code = issued();

        assertThat(code.isExpired(NOW.plus(Duration.ofSeconds(59)))).isFalse();
        assertThat(code.isExpired(NOW.plus(Duration.ofMinutes(1)))).isTrue();
    }

    @Test
    @DisplayName("PKCE 원본을 쥔 쪽만 쓸 수 있다")
    void onlyTheVerifierHolderCanUseIt() {
        AuthorizationCode code = issued();

        assertThat(code.belongsTo("portal", "http://localhost:5173/login/callback", VERIFIER)).isTrue();
        // 코드를 주웠지만 원본은 모르는 쪽.
        assertThat(code.belongsTo("portal", "http://localhost:5173/login/callback", "다른-값")).isFalse();
        assertThat(code.belongsTo("portal", "http://localhost:5173/login/callback", null)).isFalse();
    }

    @Test
    @DisplayName("다른 앱이 들고 와도 쓸 수 없다")
    void rejectsOtherClient() {
        assertThat(issued().belongsTo("evil", "http://localhost:5173/login/callback", VERIFIER)).isFalse();
    }

    @Test
    @DisplayName("시작할 때와 다른 주소를 내면 쓸 수 없다")
    void rejectsOtherRedirectUri() {
        assertThat(issued().belongsTo("portal", "http://localhost:5173/", VERIFIER)).isFalse();
    }

    /** 앱이 하는 계산과 같은 것. 여기서 직접 해봐야 우리 검증이 그 계산과 맞는지 확인된다. */
    private static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
