package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GetAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

/**
 * 표준 JWT 토큰 발급기(RBAC 적재형). subjectId + 로그인 이메일 + 권한(authLs) + RBAC 리비전(rbacRev)을
 * 담아 RS256 으로 서명한다.
 *
 * <p>게이트웨이/시큐리티가 토큰만으로 인가하도록(요청당 권한 DB 조회 없이) 권한을 토큰에 싣고, realm 의
 * 권한 정책(역할-권한/URL 매핑)이 바뀌면 리비전 bump 로 옛 토큰을 무효화한다. 발급 규칙·서명은 auth 가
 * 소유하고, 호스트는 realm 과 키만 주입해 이 전략을 @Bean 으로 선택한다.
 *
 * <p><b>서명:</b> RS256(비대칭). 호스트(realm)마다 다른 RSA 키페어를 주입한다(portal=customer, admin=employee).
 * 각 호스트는 자기 realm 키페어로 발급·검증하고 상대 realm 공개키를 갖지 않으므로, 상대 realm 토큰은
 * 서명검증 단계에서 거부된다(같은 localhost 를 공유해도 토큰이 교차 수용되지 않음).
 *
 * <p><b>주의(리비전 입도):</b> rbacRev 는 realm <b>전역</b> 정책 변경을 무효화한다. 개별 주체의 역할
 * 부여/회수(subject-role)는 전역 bump 대상이 아니다 — 그 변경은 해당 주체의 다음 토큰 갱신 때 반영된다
 * (즉시 회수가 필요하면 짧은 accessToken TTL 로 좁힌다).
 */
public class RbacJwtTokenIssuer implements TokenIssuerPort {

    private final Realm realm;
    private final EmailAccountRepository emailAccountRepository;
    private final ListSubjectPermissionsUseCase subjectPermissions;
    private final GetAuthorizationRevisionUseCase revision;
    private final ObjectProvider<SessionRegistryPort> sessionRegistryProvider;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;

    public RbacJwtTokenIssuer(Realm realm,
                              EmailAccountRepository emailAccountRepository,
                              ListSubjectPermissionsUseCase subjectPermissions,
                              GetAuthorizationRevisionUseCase revision,
                              ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
                              PrivateKey privateKey, PublicKey publicKey,
                              long accessExpirationMillis, long refreshExpirationMillis) {
        this.realm = realm;
        this.emailAccountRepository = emailAccountRepository;
        this.subjectPermissions = subjectPermissions;
        this.revision = revision;
        this.sessionRegistryProvider = sessionRegistryProvider;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.accessExpirationMillis = accessExpirationMillis;
        this.refreshExpirationMillis = refreshExpirationMillis;
    }

    @Override
    public TokenPair issue(Principal principal) {
        String subjectId = principal.getSubjectId().value();
        // 이름 같은 표시정보는 싣지 않는다. 그것은 주체 도메인이 소유하고 값이 바뀌면 토큰이 낡는다.
        // 필요한 화면이 그때 조회한다. 이메일은 auth 자신의 로그인 식별자(EmailAccount)라 여기서 낸다.
        String email = emailAccountRepository.findByPrincipalId(principal.getPrincipalId())
                .map(a -> a.getEmail()).orElse(null);
        String authLs = String.join(",", subjectPermissions.of(realm, subjectId));
        long rbacRev = revision.current(realm);

        // 단일 세션 활성 호스트에서만 sid 발급(기존 세션 무효화). 미활성이면 sid 없이 다중 로그인 허용.
        SessionRegistryPort sessionRegistry = sessionRegistryProvider.getIfAvailable();
        String sid = sessionRegistry == null ? null : sessionRegistry.open(realm, subjectId);

        String access = buildAccess(subjectId, email, authLs, rbacRev, sid, accessExpirationMillis);
        String refresh = buildRefresh(subjectId, sid, refreshExpirationMillis);
        return new TokenPair(access, refresh);
    }

    @Override
    public String subjectIdFromRefreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(publicKey).build()
                    .parseSignedClaims(refreshToken).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        // access 토큰을 refresh 로 악용하지 못하게 타입을 검증한다.
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        String subjectId = claims.get("userId", String.class);
        if (!StringUtils.hasText(subjectId)) {
            throw new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다.");
        }
        // 단일 세션 활성 호스트에선 refresh 의 sid 가 현재 세션과 일치해야 재발급을 허용한다
        // (다른 곳에서 로그인해 무효화된 세션의 refresh 로는 재발급 불가).
        SessionRegistryPort sessionRegistry = sessionRegistryProvider.getIfAvailable();
        String sid = claims.get("sid", String.class);
        if (sessionRegistry != null && StringUtils.hasText(sid)
                && !sessionRegistry.isCurrent(realm, subjectId, sid)) {
            throw new AuthenticationFailedException("다른 곳에서 로그인되어 세션이 만료되었습니다.");
        }
        return subjectId;
    }

    /** access 토큰 — 게이트웨이가 요청당 인가에 쓰도록 신원 + 권한(authLs) + 리비전을 싣는다. */
    private String buildAccess(String subjectId, String email, String authLs, long rbacRev,
                               String sid, long ttlMillis) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .subject("Token")
                .claim("type", "access")
                .claim("userId", subjectId)
                .claim("uniqId", subjectId)
                .claim("email", email == null ? "" : email)
                .claim("authLs", authLs)
                .claim("rbacRev", rbacRev)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis));
        if (StringUtils.hasText(sid)) {
            builder.claim("sid", sid);
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    /**
     * refresh 토큰 — 재발급에 필요한 최소 클레임만 담는다: 주체(userId)와 단일 세션(sid).
     * 표시정보/권한/리비전은 재발급 때 Principal 에서 다시 실으므로 넣지 않는다(쿠키 헤더 크기 절감).
     * access 와 구분되도록 {@code type=refresh} 를 박고, 재발급 시 이 타입을 검증한다.
     */
    private String buildRefresh(String subjectId, String sid, long ttlMillis) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .subject("Token")
                .claim("type", "refresh")
                .claim("userId", subjectId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis));
        if (StringUtils.hasText(sid)) {
            builder.claim("sid", sid);
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }
}
