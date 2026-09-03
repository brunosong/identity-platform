package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.web.support.AccessTokenReader;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 로그인 사용자 본인의 권한 조회 API. 프론트 메뉴/버튼 제어용이다.
 *
 * <p>auth 가 발급한 accessToken 의 권한 claim 을 auth 가 직접 읽어 돌려준다 — 호스트의 보안 컨텍스트에
 * 의존하지 않는다. 비로그인/무효 토큰이면 빈 목록을 준다. 여기서 401 을 내면 로그인 화면조차 이 API 로
 * 오류를 받게 된다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthMyPermissionsController {

    private final AccessTokenReader accessTokenReader;
    private final AuthenticatedCaller caller;
    private final AuthCookies authCookies;

    @GetMapping("/my-permissions")
    public MyPermissionsResponse myPermissions(HttpServletRequest request) {
        Optional<Claims> claims = authCookies.readAccessToken(request).flatMap(accessTokenReader::read);
        return claims
                .map(c -> new MyPermissionsResponse(c.get("email", String.class), caller.permissionsOf(c)))
                .orElseGet(() -> new MyPermissionsResponse(null, List.of()));
    }

    public record MyPermissionsResponse(String email, List<String> permissions) {
    }
}
