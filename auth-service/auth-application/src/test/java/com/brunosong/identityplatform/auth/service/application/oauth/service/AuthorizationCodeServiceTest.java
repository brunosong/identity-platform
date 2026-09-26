package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.IssueTokensForPrincipalUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.RefreshedToken;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidClientException;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidGrantException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.IssueAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.result.ExchangedTokens;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.AuthorizationCodeRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.RefreshChain;
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
import java.util.Set;

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
        // client_id 를 이 테스트가 정해야 하므로 등록이 아니라 저장소에서 꺼낸 모양으로 만든다.
        clients.save(OAuthClient.restore(CLIENT, "포털", Realm.PORTAL, Set.of(REDIRECT), true, null));
        clients.save(OAuthClient.restore(SERVER_CLIENT, "배치", Realm.PORTAL, Set.of(REDIRECT), true, null)
                .withSecret(SECRET));
        service = new AuthorizationCodeService(codes, clients, new FakeTokenIssuance(), new FakeIdTokenIssuer());
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
        AuthenticationResult result = service.exchange(serverExchange(issuedCodeFor(SERVER_CLIENT), SECRET))
                .authentication();

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
        AuthenticationResult result = service.exchange(exchange(issuedCode())).authentication();

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

    @Test
    @DisplayName("openid 를 달라고 했으면 id_token 이 붙는다. aud 와 nonce 는 코드에 적어 둔 값이다")
    void openIdScopeAddsIdToken() {
        ExchangedTokens exchanged = service.exchange(exchange(issuedCode()));

        assertThat(exchanged.idToken()).isEqualTo("id:PORTAL:portal:subject-p-1:nonce-1");
    }

    @Test
    @DisplayName("openid 를 달라고 하지 않았으면 id_token 이 없다")
    void noOpenIdScopeNoIdToken() {
        AuthorizationRequest withoutOpenId = AuthorizationRequest.of("code", CLIENT, REDIRECT, "profile",
                "state-1", challengeOf(VERIFIER), "S256", null);
        String code = service.issue(new IssueAuthorizationCodeCommand(withoutOpenId, Realm.PORTAL, PRINCIPAL))
                .getCode();

        ExchangedTokens exchanged = service.exchange(exchange(code));

        assertThat(exchanged.idToken()).isNull();
        assertThat(exchanged.authentication().tokens().accessToken()).isEqualTo("access:p-1");
    }

    /** id_token 에 무엇을 실으라고 했는지만 글자로 남긴다. 나머지 발급은 이 테스트가 부르지 않는다. */
    static class FakeIdTokenIssuer implements TokenIssuerPort {

        @Override
        public String issueIdToken(Realm realm, String subjectId, String clientId, String nonce) {
            return "id:" + realm + ":" + clientId + ":" + subjectId + ":" + nonce;
        }

        @Override
        public TokenPair issue(Realm realm, Principal principal, RefreshChain chain) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RefreshedToken readRefreshToken(Realm realm, String refreshToken) {
            throw new UnsupportedOperationException();
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
