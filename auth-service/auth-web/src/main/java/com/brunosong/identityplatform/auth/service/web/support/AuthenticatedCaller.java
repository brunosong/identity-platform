package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
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
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedCaller {

    /** access 토큰에 실린 권한 코드 목록(쉼표 구분). */
    private static final String PERMISSIONS_CLAIM = "authLs";
    private static final String SUBJECT_ID_CLAIM = "userId";
    private static final String REALM_CLAIM = "realm";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenReader accessTokenReader;

    /** 토큰이 있고 유효하면 claims. 없거나 무효면 비어 있다(비로그인을 정상 흐름으로 다루는 쪽에서 쓴다). */
    public Optional<Claims> read(HttpServletRequest request) {
        return bearerToken(request).flatMap(accessTokenReader::read);
    }

    /** 토큰이 유효하면 claims, 아니면 401. */
    public Claims require(HttpServletRequest request) {
        return read(request).orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));
    }

    /** 토큰이 유효하고 그 권한을 갖고 있으면 claims, 없으면 403. */
    public Claims requirePermission(HttpServletRequest request, String permissionCode) {
        Claims claims = require(request);
        if (!permissionsOf(claims).contains(permissionCode)) {
            throw new ForbiddenException("이 작업에 필요한 권한이 없습니다: " + permissionCode);
        }
        return claims;
    }

    public String subjectId(Claims claims) {
        return claims.get(SUBJECT_ID_CLAIM, String.class);
    }

    /**
     * 호출자가 속한 realm — 토큰에 실려 온다. 한 서비스가 두 realm 을 담당하므로 설정으로는 알 수 없고,
     * 요청 본문이 정하게 두면 남의 realm 을 지목할 수 있다. 서명된 토큰이 유일하게 믿을 수 있는 출처다.
     */
    public Realm realmOf(Claims claims) {
        String realm = claims.get(REALM_CLAIM, String.class);
        if (!StringUtils.hasText(realm)) {
            throw new UnauthorizedException("토큰에 realm 이 없습니다.");
        }
        try {
            return Realm.valueOf(realm);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("토큰의 realm 을 알 수 없습니다.");
        }
    }

    public List<String> permissionsOf(Claims claims) {
        String permissions = claims.get(PERMISSIONS_CLAIM, String.class);
        if (!StringUtils.hasText(permissions)) {
            return List.of();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
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
