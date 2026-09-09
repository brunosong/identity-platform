package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GetAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.security.PublicKey;
import java.util.Date;

/**
 * 표준 JWT 토큰 발급기(RBAC 적재형). subjectId + realm + 로그인 이메일 + 권한(authLs) + RBAC 리비전(rbacRev)을
 * 담아 RS256 으로 서명한다.
 *
 * <p>게이트웨이/시큐리티가 토큰만으로 인가하도록(요청당 권한 DB 조회 없이) 권한을 토큰에 싣고, realm 의
 * 권한 정책(역할-권한/URL 매핑)이 바뀌면 리비전 bump 로 옛 토큰을 무효화한다.
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
 * <p><b>iss:</b> realm 별 발급자를 싣는다({@link RealmIssuers}). 서명은 "이 키를 가진 누군가"까지만
 * 말하고 <b>어느 배포의 키인지는 말하지 않는다</b> — staging 과 prod 의 포털 토큰은 클레임이 똑같다.
 * 소비 서비스가 JWKS 주소를 잘못 가리켰을 때 그것을 가르는 값이 이것뿐이다.
 *
 * <p><b>realm 클레임은 싣지 않는다.</b> 넣어도 새 정보가 아니다 — realm 마다 서명키가 다르므로
 * <b>어느 키로 검증됐는지가 곧 realm</b> 이다. 토큰이 스스로 "나는 포털 것"이라고 주장하는 것보다
 * 포털 공개키로만 검증되는 편이 강하다. 그래서 모든 엔드포인트가 realm 을 경로로 받고, 검증하는 쪽은
 * 그 realm 의 키 하나만 쓴다(Keycloak 도 같다 — realm 은 {@code iss} 에 있고 별도 클레임은 없다).
 *
 * <p><b>주의(리비전 입도):</b> rbacRev 는 realm <b>전역</b> 정책 변경을 무효화한다. 개별 주체의 역할
 * 부여/회수(subject-role)는 전역 bump 대상이 아니다 — 그 변경은 해당 주체의 다음 토큰 갱신 때 반영된다
 * (즉시 회수가 필요하면 짧은 accessToken TTL 로 좁힌다).
 */
public class RbacJwtTokenIssuer implements TokenIssuerPort {

    private final ListSubjectPermissionsUseCase subjectPermissions;
    private final GetAuthorizationRevisionUseCase revision;
    private final ObjectProvider<SessionRegistryPort> sessionRegistryProvider;
    private final RealmSigningKeys signingKeys;
    private final RealmIssuers issuers;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;

    public RbacJwtTokenIssuer(ListSubjectPermissionsUseCase subjectPermissions,
                              GetAuthorizationRevisionUseCase revision,
                              ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
                              RealmSigningKeys signingKeys,
                              RealmIssuers issuers,
                              long accessExpirationMillis, long refreshExpirationMillis) {
        this.subjectPermissions = subjectPermissions;
        this.revision = revision;
        this.sessionRegistryProvider = sessionRegistryProvider;
        this.signingKeys = signingKeys;
        this.issuers = issuers;
        this.accessExpirationMillis = accessExpirationMillis;
        this.refreshExpirationMillis = refreshExpirationMillis;
    }

    @Override
    public TokenPair issue(Realm realm, Principal principal) {
        String subjectId = principal.getSubjectId().value();

        String authLs = String.join(",", subjectPermissions.of(realm, subjectId));
        long rbacRev = revision.current(realm);

        // 단일 세션을 켠 설정에서만 sid 발급(기존 세션 무효화). 미활성이면 sid 없이 다중 로그인 허용.
        SessionRegistryPort sessionRegistry = sessionRegistryProvider.getIfAvailable();
        String sid = sessionRegistry == null ? null : sessionRegistry.open(realm, subjectId);

        RealmSigningKeys.RealmKey key = signingKeys.of(realm);
        String issuer = issuers.of(realm);
        String access = buildAccess(key, issuer, subjectId, authLs, rbacRev, sid, accessExpirationMillis);
        String refresh = buildRefresh(key, issuer, subjectId, sid, refreshExpirationMillis);
        return new TokenPair(access, refresh);
    }

    @Override
    public String subjectIdFromRefreshToken(Realm realm, String refreshToken) {
        Claims claims = parse(realm, refreshToken);

        // access 토큰을 refresh 로 악용하지 못하게 타입을 검증한다.
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        String subjectId = claims.getSubject();
        if (!StringUtils.hasText(subjectId)) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        // 단일 세션 활성 시 refresh 의 sid 가 현재 세션과 일치해야 재발급을 허용한다
        // (다른 곳에서 로그인해 무효화된 세션의 refresh 로는 재발급 불가).
        SessionRegistryPort sessionRegistry = sessionRegistryProvider.getIfAvailable();
        String sid = claims.get("sid", String.class);
        if (sessionRegistry != null && StringUtils.hasText(sid)
                && !sessionRegistry.isCurrent(realm, subjectId, sid)) {
            throw new AuthenticationFailedException("다른 곳에서 로그인되어 세션이 만료되었습니다.");
        }
        return subjectId;
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

    /** access 토큰 — 게이트웨이가 요청당 인가에 쓰도록 신원 + 권한(authLs) + 리비전을 싣는다. */
    private String buildAccess(RealmSigningKeys.RealmKey key, String issuer, String subjectId,
                               String authLs, long rbacRev, String sid, long ttlMillis) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .header().keyId(key.kid()).and()
                .issuer(issuer)
                .subject(subjectId)
                .claim("type", "access")
                .claim("authLs", authLs)
                .claim("rbacRev", rbacRev)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis));
        if (StringUtils.hasText(sid)) {
            builder.claim("sid", sid);
        }
        return builder.signWith(key.privateKey(), Jwts.SIG.RS256).compact();
    }

    /**
     * refresh 토큰 — 재발급에 필요한 최소 클레임만 담는다: 주체({@code sub})와 단일 세션(sid).
     * 표시정보/권한/리비전은 재발급 때 Principal 에서 다시 실으므로 넣지 않는다(헤더 크기 절감).
     * access 와 구분되도록 {@code type=refresh} 를 박고, 재발급 시 이 타입을 검증한다.
     */
    private String buildRefresh(RealmSigningKeys.RealmKey key, String issuer, String subjectId,
                                String sid, long ttlMillis) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .header().keyId(key.kid()).and()
                .issuer(issuer)
                .subject(subjectId)
                .claim("type", "refresh")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis));
        if (StringUtils.hasText(sid)) {
            builder.claim("sid", sid);
        }
        return builder.signWith(key.privateKey(), Jwts.SIG.RS256).compact();
    }
}
