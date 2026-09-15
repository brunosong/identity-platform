package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 요청에서 호출자를 확인한다 — auth 가 발급한 access 토큰을 auth 가 직접 검증한다.
 *
 * <p>운영 API(역할·권한 편집, 캐시 리로드, 로그아웃)는 자기 신원을 밝힌 호출자만 부를 수 있어야 한다.
 * 예전에는 그 확인을 "게이트웨이가 앞단에서 해준다"고 두고 auth 자신은 아무것도 보지 않았는데,
 * 그러면 auth 를 독립 배포하는 순간(설계상 목표다) 인증 없는 관리 API 가 그대로 열린다.
 * 서명키를 쥔 쪽이 검증도 할 수 있으므로 여기서 확인한다.
 *
 * <p>토큰은 {@code Authorization: Bearer} 헤더에서만 읽는다. 쿠키 경로는 없앴다 — 인증 서버와
 * 프론트엔드의 도메인이 달라 쿠키가 전달되지 않는다({@link IssuedTokens} 참고).
 *
 * <p><b>realm 은 호출자가 넘긴다.</b> 이 서비스는 두 realm 의 키를 다 쥐고 있어서, 토큰만 보고 realm 을
 * 정하면 결국 토큰이 하는 주장을 믿는 셈이 된다. 그래서 realm 을 먼저 정하고 그 realm 의 공개키로만
 * 검증한다 — 다른 realm 의 토큰은 서명에서 죽는다. 인증 realm 은 경로가 주고
 * ({@code /api/auth/realms/{realm}/...}), 경로에 realm 이 없는 운영 API 는 상수로 못박는다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedCaller {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenReader accessTokenReader;
    private final ListSubjectPermissionsUseCase subjectPermissions;

    /**
     * 그 realm 의 토큰이 있고 유효하면 claims. 없거나 무효면 비어 있다(비로그인을 정상 흐름으로 다루는
     * 쪽에서 쓴다). 다른 realm 의 토큰도 여기서는 "없는 것"과 같다 — 서명 검증을 통과하지 못한다.
     */
    public Optional<Claims> read(Realm realm, HttpServletRequest request) {
        return bearerToken(request).flatMap(token -> accessTokenReader.read(realm, token));
    }

    /** 그 realm 의 토큰이 유효하면 claims, 아니면 401. */
    public Claims require(Realm realm, HttpServletRequest request) {
        return read(realm, request).orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));
    }

    /**
     * <b>admin 채널용.</b> 그 realm 의 토큰이 이 서비스 앞으로 발급됐고({@code aud}) 그 권한을
     * 갖고 있으면 claims, 아니면 401/403.
     *
     * <p>{@link #require} 와 달리 {@code aud} 까지 본다. 여기 걸리는 API 는 auth 자신의 자원을
     * 바꾸므로, 같은 realm 의 다른 클라이언트가 받은 토큰이 통과하면 안 된다.
     */
    public Claims requirePermission(Realm realm, HttpServletRequest request, String permissionCode) {
        Claims claims = bearerToken(request)
                .flatMap(token -> accessTokenReader.readForSelf(realm, token))
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));
        if (!permissionsOf(realm, claims).contains(permissionCode)) {
            throw new ForbiddenException("이 작업에 필요한 권한이 없습니다: " + permissionCode);
        }
        return claims;
    }

    public String subjectId(Claims claims) {
        return claims.getSubject();
    }

    /**
     * 호출자가 가진 권한 코드. <b>토큰이 아니라 DB 에서 읽는다.</b>
     *
     * <p>전에는 토큰의 {@code resource_access} 칸을 읽었다. 토큰에서 인가 클레임을 걷어내면서
     * 그 자리가 없어졌고, auth 는 authz 표를 직접 쥐고 있으므로 조회하면 된다.
     *
     * <p>요청마다 DB 를 한 번 더 타는 대가가 있다. 대신 권한을 회수하면 <b>즉시</b> 반영된다.
     * 토큰에 실어 두었을 때는 그 토큰이 만료될 때까지 살아 있었다.
     *
     * <p>토큰에 실을지 DB 를 볼지는 <b>소비 서비스</b>에서 다시 걸리는 문제다. 그쪽은 auth 의 DB 가
     * 없어서 같은 선택지를 갖지 못한다. 아직 정하지 않았다.
     */
    public List<String> permissionsOf(Realm realm, Claims claims) {
        String subjectId = claims.getSubject();
        if (!StringUtils.hasText(subjectId)) {
            return List.of();
        }
        return subjectPermissions.of(realm, subjectId);
    }

    private Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? Optional.of(token) : Optional.empty();
    }
}
