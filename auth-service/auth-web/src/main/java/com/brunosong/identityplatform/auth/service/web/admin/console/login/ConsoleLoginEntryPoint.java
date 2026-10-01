package com.brunosong.identityplatform.auth.service.web.admin.console.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 운영 화면 체인이 로그인 안 된 요청을 막았을 때 무엇을 돌려주나. 둘 중 하나다.
 *
 * <ul>
 *   <li>화면을 달라는 요청이면 MASTER 로그인으로 보낸다</li>
 *   <li>아니면 로그인 쿠키를 건드리지 않고 401</li>
 * </ul>
 *
 * <p>로그인으로 보내는 것은 화면 요청뿐이다. 브라우저는 페이지를 열면서 {@code /favicon.ico} 같은
 * 요청을 알아서 보내는데, 그것까지 로그인을 시작하면 로그인 쿠키가 새 state 로 덮여서 사람이 로그인을
 * 마치고 돌아왔을 때 state 가 맞지 않는다.
 */
@RequiredArgsConstructor
class ConsoleLoginEntryPoint implements AuthenticationEntryPoint {

    private final ConsoleLogin consoleLogin;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (wantsPage(request)) {
            response.sendRedirect(consoleLogin.start(request, response));
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /** 사람이 화면을 여는 요청인가. 주소창 이동과 폼 제출은 HTML 을 달라고 한다. */
    private static boolean wantsPage(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
