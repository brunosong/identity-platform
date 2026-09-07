package com.brunosong.identityplatform.auth.service.web.identity;

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
 * <p>auth 가 발급한 accessToken 의 권한 claim 을 auth 가 직접 읽어 돌려준다. 비로그인/무효 토큰이면
 * 빈 목록을 준다. 여기서 401 을 내면 로그인 화면조차 이 API 로 오류를 받게 된다.
 *
 * <p>경로에 realm 이 없다. 토큰이 자기 realm 을 들고 다니므로 호출자가 말해줄 필요가 없고,
 * 말하게 두면 남의 realm 을 지목할 수 있다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthMyPermissionsController {

    private final AuthenticatedCaller caller;

    @GetMapping("/my-permissions")
    public MyPermissionsResponse myPermissions(HttpServletRequest request) {
        Optional<Claims> claims = caller.read(request);
        return claims
                .map(c -> new MyPermissionsResponse(
                        c.get("email", String.class), caller.realmOf(c).name(), caller.permissionsOf(c)))
                .orElseGet(() -> new MyPermissionsResponse(null, null, List.of()));
    }

    public record MyPermissionsResponse(String email, String realm, List<String> permissions) {
    }
}
