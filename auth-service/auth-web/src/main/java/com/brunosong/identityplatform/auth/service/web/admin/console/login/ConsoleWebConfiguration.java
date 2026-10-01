package com.brunosong.identityplatform.auth.service.web.admin.console.login;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
 *   <li>로그인이 돌아오는 콜백, 로그아웃, 오류 화면. 로그아웃은 access 가 만료된 뒤에 눌러도
 *       끝까지 가야 한다. 막으면 로그인으로 보내져 로그아웃이 안 된다</li>
 * </ul>
 *
 * <p>새 엔드포인트를 위 경로 밖에 두면 로그인 뒤로 들어간다. 지금은 로그인만 본다. 권한 검사는 아직 없다.
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
        registry.addInterceptor(new ConsoleLoginInterceptor(consoleLogin))
                .addPathPatterns("/**")
                .excludePathPatterns("/realms/**", "/api/**", "/.well-known/**",
                        ConsoleLogin.CALLBACK_PATH, ConsoleLogin.LOGOUT_PATH, "/error");
    }
}
