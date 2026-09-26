package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.RefreshedToken;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.RefreshChain;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.util.StringUtils;

import java.security.PublicKey;
import java.util.Date;

/**
 * 표준 JWT 토큰 발급기. <b>신원만</b> 담아 RS256 으로 서명한다.
 *
 * <h2>인가에 쓸 값은 토큰에 싣지 않는다</h2>
 * 전에는 역할과 권한을 실었다({@code realm_access}, {@code resource_access}) 그리고 그것이 낡았는지
 * 가리는 리비전({@code rbacRev})까지 함께 실었다. 토큰 하나가 신원과 인가를 겸하면서, 같은
 * 판단이 세 군데(토큰 클레임, authz DB, 소비 서비스의 matcher)에 나뉘어 읽기 어려웠다.
 *
 * <p>지금 토큰이 답하는 것은 하나다. <b>이 요청이 누구인가.</b>
 *
 * <pre>
 * "iss":  "http://localhost:8080/realms/portal"   어느 realm 이 발급했나
 * "aud":  ["shop"]                                 어느 시스템이 받아주나
 * "sub":  "a4120a37-..."                           누구인가
 * "type": "access"
 * </pre>
 *
 * <p>인가는 아직 자리를 정하지 않았다. 권한 데이터는 authz 표에 그대로 있고
 * ({@code authz_role}, {@code authz_permission}, {@code authz_service}), auth 자신의 관리 API 는
 * 그 표를 직접 조회해 판정한다. 소비 서비스가 어떻게 판정할지는 다음에 정한다.
 *
 * <p><b>서명:</b> RS256(비대칭). realm 마다 다른 RSA 키페어를 쓴다({@link RealmSigningKeys}).
 * 한 프로세스가 두 realm 을 모두 담당하므로 발급기가 두 벌을 다 쥐고 realm 에 맞는 키를 고른다 —
 * 전에는 프로세스당 realm 하나·키 한 벌이었고, 상대 realm 공개키가 없다는 사실 자체가 격리 장치였다.
 * 이제 한 프로세스가 두 벌을 다 쥐지만, 검증하는 쪽은 realm 을 먼저 정하고 그 realm 의 공개키
 * 하나로만 확인한다. 그래서 격리는 여전히 <b>키</b>가 맡는다.
 *
 * <p><b>kid:</b> 서명 헤더에 키 식별자를 박는다. 검증하는 쪽은 토큰을 열어보기 전에 어느 공개키를 쓸지
 * 정해야 하므로(내용을 믿으려면 먼저 서명을 확인해야 한다) 그 정보는 서명 대상 밖인 헤더에 있어야 한다.
 * 다른 서비스에 공개키를 나눠줄 때(JWKS)도 이 kid 로 고른다.
 *
 * <p><b>typ:</b> refresh 토큰만 헤더에 {@code typ=Refresh} 를 박는다. access 는 기본값({@code JWT})이다.
 * 표준 JWT 처리기는 {@code typ} 이 {@code JWT} 이거나 없을 때만 받아들이므로, <b>리소스 서버는
 * refresh 토큰을 설정 한 줄 없이 거부한다.</b> 두 토큰을 가르는 일을 클레임({@code type})에만 맡기면
 * 그 클레임을 확인해 주는 검증기에서만 갈리는데, 표준 검증기는 커스텀 클레임을 모른다 —
 * 실제로 Spring Security 로 옮긴 직후 refresh 토큰이 access 로 통과했다.
 * ({@code type} 클레임은 그대로 둔다. auth 자신의 재발급 검증이 그것을 본다.)
 *
 * <p><b>iss:</b> realm 별 발급자를 싣는다({@link RealmIssuers}). 서명은 "이 키를 가진 누군가"까지만
 * 말하고 <b>어느 배포의 키인지는 말하지 않는다</b> — staging 과 prod 의 포털 토큰은 클레임이 똑같다.
 * 소비 서비스가 JWKS 주소를 잘못 가리켰을 때 그것을 가르는 값이 이것뿐이다.
 *
 * <p><b>aud:</b> 이 토큰이 향하는 <b>시스템</b> 하나({@link TokenClients}). 없으면 발급자만 맞으면
 * 누구든 받아들이게 되어, 한 곳이 침해되면 그 토큰을 realm 의 다른 곳에 그대로 재생할 수 있다.
 *
 * <p>전에는 여기에 서비스 이름을 나열했다. 그러면 서비스를 붙일 때마다 auth 를 재배포해야 했고,
 * 이미 발급된 access 토큰은 만료돼 재발급될 때까지 새 서비스에 닿지 못했다. 내부 분해가 토큰에
 * 새어나간 것이다. 이제 시스템 하나를 가리키고, 그 안에 서비스가 몇 개인지는 DB 만 안다.
 *
 * <p><b>통합 로그인은 세션이 맡는다.</b> 시스템이 여럿이면 토큰도 여럿이다. 사용자 눈에 로그인이
 * 한 번인 것은 세션이 하나이기 때문이지 토큰을 나눠 쓰기 때문이 아니다(OIDC, Keycloak 도 같다).
 *
 * <p><b>realm 클레임은 싣지 않는다.</b> 넣어도 새 정보가 아니다 — realm 마다 서명키가 다르므로
 * <b>어느 키로 검증됐는지가 곧 realm</b> 이다. 토큰이 스스로 "나는 포털 것"이라고 주장하는 것보다
 * 포털 공개키로만 검증되는 편이 강하다. 그래서 모든 엔드포인트가 realm 을 경로로 받고, 검증하는 쪽은
 * 그 realm 의 키 하나만 쓴다(Keycloak 도 같다 — realm 은 {@code iss} 에 있고 별도 클레임은 없다).
 *
 */
public class RbacJwtTokenIssuer implements TokenIssuerPort {

    private final RealmSigningKeys signingKeys;
    private final RealmIssuers issuers;
    private final RealmSystems systems;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;

    public RbacJwtTokenIssuer(RealmSigningKeys signingKeys,
                              RealmIssuers issuers,
                              RealmSystems systems,
                              long accessExpirationMillis, long refreshExpirationMillis) {
        this.signingKeys = signingKeys;
        this.issuers = issuers;
        this.systems = systems;
        this.accessExpirationMillis = accessExpirationMillis;
        this.refreshExpirationMillis = refreshExpirationMillis;
    }

    @Override
    public TokenPair issue(Realm realm, Principal principal, RefreshChain chain) {
        String subjectId = principal.getSubjectId().value();
        String systemId = systems.of(realm);

        RealmSigningKeys.RealmKey key = signingKeys.of(realm);
        String issuer = issuers.of(realm);
        String access = buildAccess(key, issuer, subjectId, systemId, accessExpirationMillis);
        String refresh = buildRefresh(key, issuer, subjectId, chain, refreshExpirationMillis);
        return new TokenPair(access, refresh);
    }

    /**
     * id_token. 로그인을 요청한 앱에게 "누가 로그인했나" 를 알려주는 값이다(OIDC Core 2).
     *
     * <p>access 토큰과 같은 키로 서명한다. 앱은 이미 아는 JWKS 로 검증하면 된다.
     * 수명도 access 토큰과 같다. 앱이 받자마자 검증하고 버리는 값이라 길 이유가 없다.
     *
     * <p>{@code type} 클레임은 싣지 않는다. 그 클레임은 auth 가 access 와 refresh 를 가르는 값이고,
     * id_token 은 auth 에 다시 들어올 일이 없다.
     */
    @Override
    public String issueIdToken(Realm realm, String subjectId, String clientId, String nonce) {
        RealmSigningKeys.RealmKey key = signingKeys.of(realm);
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .header().keyId(key.kid()).and()
                .issuer(issuers.of(realm))
                // 배열이 아니라 문자열 하나로 싣는다. 명세는 둘 다 허용하지만, 배열이면 azp 를 함께
                // 따지는 라이브러리가 있다.
                .audience().single(clientId)
                .subject(subjectId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessExpirationMillis));
        if (StringUtils.hasText(nonce)) {
            builder.claim("nonce", nonce);
        }
        return builder.signWith(key.privateKey(), Jwts.SIG.RS256).compact();
    }

    @Override
    public RefreshedToken readRefreshToken(Realm realm, String refreshToken) {
        Claims claims = parse(realm, refreshToken);

        // access 토큰을 refresh 로 악용하지 못하게 타입을 검증한다.
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        String subjectId = claims.getSubject();
        if (!StringUtils.hasText(subjectId)) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        // 회전이 대조할 값. 이 둘이 없는 토큰은 회전을 붙이기 전에 나간 것이라 더는 받지 않는다.
        String familyId = claims.get("fid", String.class);
        String jti = claims.getId();
        if (!StringUtils.hasText(familyId) || !StringUtils.hasText(jti)) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        return new RefreshedToken(subjectId, familyId, jti);
    }

    /**
     * 요청한 realm 의 공개키로만 검증한다. kid 로 키를 고르지 않는 이유는, 여기서는 "어느 realm 의
     * 토큰인지" 가 이미 정해져 있기 때문이다 — kid 를 따라가면 상대 realm 토큰도 서명은 통과한다.
     *
     * <p>{@code iss} 까지 대조한다. 다른 배포(staging 등)의 같은 realm 키로 서명된 토큰을 막는다 —
     * 그런 토큰은 서명도 realm 도 맞아떨어질 수 있다.
     */
    private Claims parse(Realm realm, String token) {
        PublicKey verifyKey = signingKeys.of(realm).publicKey();
        try {
            return Jwts.parser()
                    .verifyWith(verifyKey)
                    .requireIssuer(issuers.of(realm))
                    .build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
    }

    /** access 토큰. <b>신원만 싣는다.</b> 인가에 쓸 값은 담지 않는다. */
    private String buildAccess(RealmSigningKeys.RealmKey key, String issuer, String subjectId,
                               String systemId, long ttlMillis) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .header().keyId(key.kid()).and()
                .issuer(issuer)
                .audience().add(systemId).and()
                .subject(subjectId)
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis));
        return builder.signWith(key.privateKey(), Jwts.SIG.RS256).compact();
    }

    /**
     * refresh 토큰. 재발급에 필요한 최소 클레임만 담는다: 주체({@code sub}) 와 계보({@code jti}, {@code fid}).
     *
     * <p><b>audience 는 싣지 않는다.</b> 이 토큰이 갈 곳은 재발급 엔드포인트 하나뿐이고 그곳은 auth
     * 자신이다. 재발급될 access 토큰의 {@code aud} 는 <b>경로의 realm</b> 이 정하므로, 이 토큰을
     * 쥔 쪽이 향할 시스템을 갈아끼울 방법이 없다. 한때 {@code cid} 를 박아 그것을 막았는데,
     * 요청에서 받을 값 자체가 없어지면서 함께 필요 없어졌다.
     *
     * <p>access 와 구분되도록 {@code type=refresh} 를 박고, 재발급 시 이 타입을 검증한다.
     *
     * <p><b>{@code jti} 와 {@code fid} 가 회전을 가능하게 한다.</b> 토큰 한 장의 이름({@code jti})과
     * 그 토큰이 속한 계보의 이름({@code fid})이다. 서명만으로는 이미 쓴 토큰인지 알 수 없으므로,
     * 재발급은 이 둘을 저장된 계보와 대조한다({@code RefreshChain}).
     */
    private String buildRefresh(RealmSigningKeys.RealmKey key, String issuer, String subjectId,
                                RefreshChain chain, long ttlMillis) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                // 헤더의 typ 으로 access 와 가른다. 표준 JWT 처리기는 typ 이 "JWT"(또는 없음)일 때만
                // 받아들이므로, 리소스 서버는 이 토큰을 설정 한 줄 없이 거부한다 — 클레임을 대조해
                // 주기를 바라지 않아도 된다. (Keycloak 도 refresh 토큰 헤더에 다른 typ 을 박는다.)
                .header().keyId(key.kid()).type("Refresh").and()
                .issuer(issuer)
                .subject(subjectId)
                .claim("type", "refresh")
                .id(chain.getCurrentJti())
                .claim("fid", chain.getFamilyId())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis));
        return builder.signWith(key.privateKey(), Jwts.SIG.RS256).compact();
    }
}
