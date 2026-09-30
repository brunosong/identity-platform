package com.brunosong.identityplatform.auth.service.web.admin.console;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 8080 으로 들어오는 요청은 MASTER 로그인이 있어야 통과한다. 운영 화면만이 아니라 전부다.
 *
 * <p>빼는 것은 넷이다. 토큰 없이 열려야 이 서비스가 돈다.
 *
 * <ul>
 *   <li>{@code /realms/**}: 로그인 화면, 토큰 발급, 공개키. 여기를 막으면 로그인하러 간 곳에서 다시
 *       로그인으로 보내져 끝없이 돈다. 다른 서비스가 공개키를 받아 가는 곳이기도 하다</li>
 *   <li>{@code /api/**}: 앱이 Bearer 헤더로 부르는 API. 각자 토큰을 검사해 401 을 준다.
 *       로그인 화면으로 302 를 보내면 JSON 을 기다리던 앱이 깨진다</li>
 *   <li>{@code /.well-known/**}: 누구나 읽는 공개 문서의 자리다. 없는 문서는 로그인이 아니라 404 여야 한다</li>
 *   <li>로그인이 돌아오는 콜백과 오류 화면</li>
 * </ul>
 *
 * <p>로그인으로 보내는 것은 화면을 달라는 요청뿐이다. 브라우저는 페이지를 열면서 {@code /favicon.ico}
 * 같은 요청을 알아서 보내는데, 그것까지 로그인을 시작하면 로그인 쿠키가 새 state 로 덮여서 사람이
 * 로그인을 마치고 돌아왔을 때 state 가 맞지 않는다. 그런 요청은 쿠키를 건드리지 않고 401 로 끝낸다.
 *
 * <p>지금은 로그인만 본다. 권한 검사는 다음 조각이다.
 */
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class ConsoleWebConfiguration implements WebMvcConfigurer {

    private final ConsoleLogin consoleLogin;

    /** 루트로 오면 운영 화면으로. Keycloak 도 루트가 관리 콘솔로 이어진다. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", ConsoleLogin.DEFAULT_PAGE);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                             Object handler) throws Exception {
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
                })
                .addPathPatterns("/**")
                .excludePathPatterns("/realms/**", "/api/**", "/.well-known/**", ConsoleLogin.CALLBACK_PATH, "/error");
    }

    /** 사람이 화면을 여는 요청인가. 주소창 이동과 폼 제출은 HTML 을 달라고 한다. */
    private static boolean wantsPage(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
