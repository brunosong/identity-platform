package com.brunosong.identityplatform.auth.service.web.admin.console.login;

import com.brunosong.identityplatform.auth.service.web.support.SecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 운영 화면 체인. 앞의 체인(관리 API, 공개 경로)이 가져가지 않은 요청은 전부 여기로 온다.
 * 8080 의 나머지 전부가 MASTER 로그인 뒤에 있다는 뜻이다.
 *
 * <pre>
 * ConsoleAuthenticationFilter   쿠키의 토큰을 검증하고, 끝났으면 refresh 로 갱신한다. 되면 로그인한 사람으로 등록
 * authenticated()               등록 안 됐으면 막는다. 콜백과 로그아웃만 뺀다
 * ConsoleLoginEntryPoint        막혔을 때: 화면 요청이면 MASTER 로그인으로, 아니면 401
 * </pre>
 *
 * <p>무엇이 앞 체인으로 빠지는지는 {@link SecurityConfiguration} 이 정한다. 새 엔드포인트를 그쪽 경로
 * 밖에 두면 로그인 뒤로 들어간다.
 *
 * <p>토큰이 쿠키에 있어 서버가 기억할 것이 없으니 세션을 만들지 않는다. CSRF 는 원래대로 끈 채
 * {@code SameSite=Lax} 쿠키에 기댄다. 지금은 로그인만 본다. 권한 검사는 아직 없다.
 */
@Configuration
@Profile("local")
public class ConsoleSecurityConfiguration implements WebMvcConfigurer {

    @Bean
    @Order(SecurityConfiguration.CONSOLE_ORDER)
    SecurityFilterChain console(HttpSecurity http, ConsoleLogin consoleLogin) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        // 로그인을 끝내는 자리와 로그아웃은 로그인이 안 된 채로도 끝까지 가야 한다.
                        .requestMatchers(ConsoleLogin.CALLBACK_PATH, ConsoleLogin.LOGOUT_PATH).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new ConsoleAuthenticationFilter(consoleLogin), AnonymousAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(new ConsoleLoginEntryPoint(consoleLogin)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .build();
    }

    /** 루트로 오면 운영 화면으로. Keycloak 도 루트가 관리 콘솔로 이어진다. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", ConsoleLogin.DEFAULT_PAGE);
    }
}
