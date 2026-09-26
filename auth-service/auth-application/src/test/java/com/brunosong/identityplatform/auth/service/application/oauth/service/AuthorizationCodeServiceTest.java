package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.IssueTokensForPrincipalUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidClientException;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidGrantException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.IssueAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.AuthorizationCodeRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 코드를 토큰으로 바꿔주는 조건을 고정한다.
 *
 * <p>이 코드는 주소창에 실려 나가므로 주워질 수 있다고 보고 만들었다. 아래 조건 중 하나라도
 * 느슨해지면 주운 쪽이 남의 토큰을 받아간다.
 */
class AuthorizationCodeServiceTest {

    private static final String VERIFIER = "verifier-0123456789-0123456789-0123456789";
    private static final String CLIENT = "portal";
    private static final String REDIRECT = "http://localhost:5173/login/callback";
    private static final PrincipalId PRINCIPAL = new PrincipalId("p-1");

    /** 시크릿이 있는 앱. */
    private static final String SERVER_CLIENT = "batch";
    private static final String SECRET = "s3cret-value";

    private FakeCodeRepository codes;
    private AuthorizationCodeService service;

    @BeforeEach
    void setUp() {
        codes = new FakeCodeRepository();
        OAuthClientServiceTest.FakeClientRepository clients = new OAuthClientServiceTest.FakeClientRepository();
        clients.save(OAuthClient.register(CLIENT, Realm.PORTAL, List.of(REDIRECT)));
        clients.save(OAuthClient.register(SERVER_CLIENT, Realm.PORTAL, List.of(REDIRECT)).withSecret(SECRET));
        service = new AuthorizationCodeService(codes, clients, new FakeTokenIssuance());
    }

    private String issuedCode() {
        return issuedCodeFor(CLIENT);
    }

    private String issuedCodeFor(String clientId) {
        return service.issue(new IssueAuthorizationCodeCommand(request(clientId), Realm.PORTAL, PRINCIPAL))
                .getCode();
    }

    private static AuthorizationRequest request() {
        return request(CLIENT);
    }

    private static AuthorizationRequest request(String clientId) {
        return AuthorizationRequest.of("code", clientId, REDIRECT, "openid", "state-1",
                challengeOf(VERIFIER), "S256", "nonce-1");
    }

    private static ExchangeAuthorizationCodeCommand serverExchange(String code, String secret) {
        return new ExchangeAuthorizationCodeCommand(Realm.PORTAL, code, SERVER_CLIENT, REDIRECT, VERIFIER, secret);
    }

    @Test
    @DisplayName("시크릿이 있는 앱은 맞는 시크릿과 PKCE 원본을 함께 내면 토큰을 받는다")
    void confidentialClientWithSecret() {
        AuthenticationResult result = service.exchange(serverExchange(issuedCodeFor(SERVER_CLIENT), SECRET));

        assertThat(result.tokens().accessToken()).isEqualTo("access:p-1");
    }

    @Test
    @DisplayName("시크릿이 있는 앱이 시크릿을 틀리거나 빼면 거절하고, 코드는 탄다")
    void confidentialClientWithoutRightSecret() {
        String wrong = issuedCodeFor(SERVER_CLIENT);
        assertThatThrownBy(() -> service.exchange(serverExchange(wrong, "guess")))
                .isInstanceOf(InvalidClientException.class);

        String missing = issuedCodeFor(SERVER_CLIENT);
        assertThatThrownBy(() -> service.exchange(serverExchange(missing, null)))
                .isInstanceOf(InvalidClientException.class);

        assertThat(codes.stored).isEmpty();
    }

    @Test
    @DisplayName("시크릿이 있어도 PKCE 원본이 틀리면 거절한다")
    void confidentialClientStillNeedsPkce() {
        // 시크릿은 "그 앱인가" 를 볼 뿐 "그 로그인을 시작한 쪽인가" 는 보지 않는다.
        String code = issuedCodeFor(SERVER_CLIENT);

        assertThatThrownBy(() -> service.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.PORTAL, code, SERVER_CLIENT, REDIRECT, "틀린-값", SECRET)))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @DisplayName("시크릿이 없는 앱이 시크릿을 내면 거절한다")
    void publicClientMustNotSendSecret() {
        String code = issuedCode();

        assertThatThrownBy(() -> service.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.PORTAL, code, CLIENT, REDIRECT, VERIFIER, "anything")))
                .isInstanceOf(InvalidClientException.class);
    }

    private static ExchangeAuthorizationCodeCommand exchange(String code) {
        return new ExchangeAuthorizationCodeCommand(Realm.PORTAL, code, CLIENT, REDIRECT, VERIFIER, null);
    }

    @Test
    @DisplayName("발급한 코드는 저장되고, 그것으로 토큰을 받는다")
    void exchangeIssuesTokens() {
        AuthenticationResult result = service.exchange(exchange(issuedCode()));

        assertThat(result.principalId()).isEqualTo("p-1");
        assertThat(result.tokens().accessToken()).isEqualTo("access:p-1");
    }

    @Test
    @DisplayName("같은 코드는 두 번 쓰이지 않는다")
    void codeIsSingleUse() {
        String code = issuedCode();
        service.exchange(exchange(code));

        assertThatThrownBy(() -> service.exchange(exchange(code)))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @DisplayName("만료된 코드는 거절한다")
    void rejectsExpiredCode() {
        AuthorizationCode old = AuthorizationCode.issue(Realm.PORTAL, request(), PRINCIPAL,
                Instant.now().minus(Duration.ofMinutes(2)));
        codes.save(old);

        assertThatThrownBy(() -> service.exchange(exchange(old.getCode())))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @DisplayName("PKCE 원본이 다르면 거절한다")
    void rejectsWrongVerifier() {
        // 코드를 주웠지만 원본은 모르는 쪽이 여기서 걸린다.
        String code = issuedCode();

        assertThatThrownBy(() -> service.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.PORTAL, code, CLIENT, REDIRECT, "주운-사람이-지어낸-값", null)))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @DisplayName("다른 앱이 들고 오면 거절한다")
    void rejectsOtherClient() {
        String code = issuedCode();

        assertThatThrownBy(() -> service.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.PORTAL, code, "evil", REDIRECT, VERIFIER, null)))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @DisplayName("시작할 때와 다른 주소를 내면 거절한다")
    void rejectsOtherRedirectUri() {
        String code = issuedCode();

        assertThatThrownBy(() -> service.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.PORTAL, code, CLIENT, "http://localhost:5173/", VERIFIER, null)))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @DisplayName("다른 realm 경로로 들고 오면 거절한다")
    void rejectsOtherRealm() {
        String code = issuedCode();

        assertThatThrownBy(() -> service.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.ADMIN, code, CLIENT, REDIRECT, VERIFIER, null)))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @DisplayName("거절된 코드도 저장소에서 사라진다")
    void rejectedCodeIsBurned() {
        // 꺼내는 것이 곧 지우는 것이다. 한 번 잘못 쓰인 코드는 태운다.
        String code = issuedCode();
        assertThatThrownBy(() -> service.exchange(new ExchangeAuthorizationCodeCommand(
                Realm.PORTAL, code, CLIENT, REDIRECT, "틀린-값", null)));

        assertThat(codes.stored).isEmpty();
    }

    /** 앱이 하는 계산과 같은 것. 여기서 직접 해봐야 우리 검증이 그 계산과 맞물리는지 확인된다. */
    private static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static class FakeCodeRepository implements AuthorizationCodeRepository {

        final Map<String, AuthorizationCode> stored = new HashMap<>();

        @Override
        public void save(AuthorizationCode code) {
            stored.put(code.getCode(), code);
        }

        @Override
        public Optional<AuthorizationCode> consume(String code) {
            return Optional.ofNullable(stored.remove(code));
        }
    }

    static class FakeTokenIssuance implements IssueTokensForPrincipalUseCase {

        @Override
        public AuthenticationResult forPrincipal(PrincipalId principalId) {
            return new AuthenticationResult(principalId.value(), "subject-" + principalId.value(),
                    Realm.PORTAL, new TokenPair("access:" + principalId.value(), "refresh"));
        }
    }
}
