package com.brunosong.identityplatform.auth.service.web.admin.console;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 운영 화면({@code /page/**}) 앞에서 로그인을 확인한다. 안 돼 있으면 MASTER 로그인으로 보낸다.
 *
 * <p>로그인이 돌아오는 콜백만 뺀다. 거기까지 막으면 로그인을 끝낼 길이 없다.
 *
 * <p>지금은 로그인만 본다. 권한 검사는 다음 조각이다.
 */
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class ConsoleWebConfiguration implements WebMvcConfigurer {

    private final ConsoleLogin consoleLogin;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                             Object handler) throws Exception {
                        if (consoleLogin.loggedIn(request)) {
                            return true;
                        }
                        response.sendRedirect(consoleLogin.start(request));
                        return false;
                    }
                })
                .addPathPatterns("/page/**")
                .excludePathPatterns(ConsoleLogin.CALLBACK_PATH);
    }
}
