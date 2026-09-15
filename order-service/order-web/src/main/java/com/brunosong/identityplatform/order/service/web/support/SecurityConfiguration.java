package com.brunosong.identityplatform.order.service.web.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 이 서비스의 인증 전부. customer-service 의 같은 이름 클래스와 거의 같다.
 *
 * <h2>설정 두 줄이 auth 와의 계약 전부다</h2>
 * <pre>
 * spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8080/realms/portal
 * spring.security.oauth2.resourceserver.jwt.audiences:  order-service
 * </pre>
 *
 * 첫 줄이 realm 경계다 — 발급자 이름에 realm 이 들어 있고 그 발급자의 JWKS 만 받아오므로,
 * 어드민 토큰은 서명 단계에서 죽는다. 이 서비스의 코드가 한 줄도 돌기 전에.
 *
 * <p>둘째 줄이 서비스 경계다. 발급자만 확인하면 그 realm 의 토큰이면 무엇이든 통하고, 다른
 * 서비스로 들어온 토큰을 여기에 그대로 재생할 수 있다.
 *
 * <h2>customer-service 와 같은 발급자를 본다</h2>
 * 두 서비스가 같은 {@code issuer-uri} 를 보면서 각자 다른 {@code audiences} 를 요구한다. 고객
 * 포털의 토큰에는 {@code aud} 가 둘 다 실려 있어({@code token.clients.customer-portal.audiences})
 * 한 번 로그인한 토큰 하나가 둘 다 통한다. <b>"한 번만 로그인" 과 "아무 데나 통한다" 는 다른
 * 이야기다</b> — 포털 앱에 없는 서비스를 여기에 적으면 그 토큰은 이 서비스에서 거부된다.
 *
 * <h2>역할을 둘로 가른다</h2>
 * customer-service 는 역할이 하나였다. 여기는 읽기와 쓰기가 갈린다.
 *
 * <pre>
 * POST /api/orders          ORDER_WRITE
 * 그 밖의 /api/orders/**    ORDER_READ
 * </pre>
 *
 * 순서가 중요하다. 필터체인은 <b>먼저 맞는 규칙</b>에서 멈추므로 POST 규칙이 위에 있어야 한다.
 * 아래에 두면 {@code /api/orders/**} 가 POST 까지 먼저 잡아 쓰기가 ORDER_READ 로 열린다.
 *
 * <p><b>이 이름들이 무엇을 여는지는 여기서 정한다.</b> auth 는 이름만 보관한다
 * ({@code authz_permission} 에 {@code (PORTAL, order-service, ORDER_READ)} 행). 그래서 이 서비스가
 * 엔드포인트를 늘려도 auth 를 배포하지 않고, 규칙이 엔드포인트와 같은 PR 에서 리뷰된다.
 *
 * <h2>무상태</h2>
 * 세션을 만들지 않는다. 토큰이 요청마다 신원을 들고 오므로 서버가 기억할 것이 없다. CSRF 도 끈다 —
 * 브라우저가 자동으로 붙여주는 자격증명(쿠키)이 없으면 CSRF 가 성립하지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final String ORDER_READ = "ORDER_READ";
    private static final String ORDER_WRITE = "ORDER_WRITE";

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // CorsPolicy 가 준 규칙을 시큐리티 필터 안에서 적용한다. WebMvc 쪽 설정만 두면
                // 이 필터체인이 먼저 돌아 preflight 가 401 로 막힌다.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 기본이 거부다. 여는 것을 잊는 실수는 401 로 드러나고, 막는 것을 잊는 실수는 생기지 않는다.
                //
                // 토큰의 resource_access["order-service"].roles 가 authority 로 들어와 있으므로
                // (ClientRoleAuthorities) 그대로 요구하면 된다.
                .authorizeHttpRequests(requests -> requests
                        // 쓰기 규칙이 먼저다. 아래 규칙은 경로만 보므로 순서가 뒤집히면 POST 도 통과한다.
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasAuthority(ORDER_WRITE)
                        // 나머지는 최소한 읽기 권한을 요구한다. 메서드를 적지 않은 것은,
                        // 앞으로 늘어날 메서드가 아무 규칙에도 걸리지 않고 통과하는 일을 막기 위해서다.
                        .requestMatchers("/api/orders/**").hasAuthority(ORDER_READ)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
