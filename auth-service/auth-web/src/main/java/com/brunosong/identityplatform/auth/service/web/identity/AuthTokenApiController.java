package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RefreshTokenUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import com.brunosong.identityplatform.auth.service.web.support.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰 재발급 API. refresh 토큰을 받아 새 access/refresh 를 발급하고 쿠키를 갱신한다.
 * 재발급 시점의 권한과 리비전이 다시 실린다.
 *
 * <p>refresh 토큰은 쿠키에서 읽는 것이 기본이고, 본문으로도 받을 수 있다 — 쿠키를 쓰지 않는
 * 서버 간 호출을 위해 남겨둔다. 둘 다 없으면 401 이다.
 */
@RestController
@RequestMapping("/api/auth/token")
@RequiredArgsConstructor
public class AuthTokenApiController {

    private final RefreshTokenUseCase refreshToken;
    private final AuthCookies authCookies;

    @PostMapping("/refresh")
    public RefreshResponse refresh(@RequestBody(required = false) RefreshRequest body,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        String token = (body != null && StringUtils.hasText(body.refreshToken()))
                ? body.refreshToken()
                : authCookies.readRefreshToken(request)
                        .orElseThrow(() -> new UnauthorizedException("리프레시 토큰이 없습니다."));

        AuthenticationResult result = refreshToken.refresh(token);
        authCookies.write(response, result.tokens());
        return new RefreshResponse(result.subjectId(),
                result.subjectType() == null ? null : result.subjectType().name());
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record RefreshResponse(String subjectId, String subjectType) {
    }
}
