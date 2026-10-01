package com.brunosong.identityplatform.auth.service.web.admin.console.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 요청마다 로그인을 확인한다. 셋 중 하나로 끝난다.
 *
 * <ul>
 *   <li>쿠키의 access 가 유효하면 통과</li>
 *   <li>아니고 화면을 달라는 요청이면 MASTER 로그인으로 보낸다</li>
 *   <li>아니면 쿠키를 건드리지 않고 401</li>
 * </ul>
 *
 * <p>로그인으로 보내는 것은 화면 요청뿐이다. 브라우저는 페이지를 열면서 {@code /favicon.ico} 같은
 * 요청을 알아서 보내는데, 그것까지 로그인을 시작하면 로그인 쿠키가 새 state 로 덮여서 사람이 로그인을
 * 마치고 돌아왔을 때 state 가 맞지 않는다.
 *
 * <p>어느 경로에 걸리는지는 {@link ConsoleWebConfiguration} 이 정한다.
 */
@RequiredArgsConstructor
class ConsoleLoginInterceptor implements HandlerInterceptor {

    private final ConsoleLogin consoleLogin;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (consoleLogin.loggedIn(request)) {
            return true;
        }
        if (wantsPage(request)) {
            response.sendRedirect(consoleLogin.start(request, response));
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
        return false;
    }

    /** 사람이 화면을 여는 요청인가. 주소창 이동과 폼 제출은 HTML 을 달라고 한다. */
    private static boolean wantsPage(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
