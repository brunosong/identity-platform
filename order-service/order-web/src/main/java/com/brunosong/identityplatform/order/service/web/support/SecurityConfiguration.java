package com.brunosong.identityplatform.order.service.web.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
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
 * 첫 줄이 realm 경계다. 발급자 이름에 realm 이 들어 있고 그 발급자의 JWKS 만 받아오므로,
 * 어드민 토큰은 서명 단계에서 죽는다. 이 서비스의 코드가 한 줄도 돌기 전에.
 *
 * <p>둘째 줄이 서비스 경계다. 발급자만 확인하면 그 realm 의 토큰이면 무엇이든 통하고, 다른
 * 서비스로 들어온 토큰을 여기에 그대로 재생할 수 있다.
 *
 * <h2>customer-service 와 같은 발급자를 본다</h2>
 * 두 서비스가 같은 {@code issuer-uri} 를 보면서 각자 다른 {@code audiences} 를 요구한다. 고객
 * 포털 realm 의 토큰은 {@code aud} 가 {@code shop} 하나라서, 한 번 로그인한 토큰이 둘 다 통한다. <b>"한 번만 로그인" 과 "아무 데나 통한다" 는 다른
 * 이야기다</b>. 포털 앱에 없는 서비스를 여기에 적으면 그 토큰은 이 서비스에서 거부된다.
 *
 * <h2>역할은 읽지만 아직 검사하지 않는다</h2>
 * 토큰의 {@code realm_access.roles} 를 {@link RealmRoleAuthorities} 가 권한({@code ROLE_CUSTOMER})으로
 * 옮긴다. 그 권한으로 경로를 막는 규칙은 아직 없어서, 지금은 인증만 통과하면 이 API 들이 열린다.
 *
 * <p>상품 목록({@code GET /api/products})만 로그인 없이 열린다. 누구에게나 같은 값이다.
 *
 * <p>권한 데이터 자체는 auth 에 그대로 있다({@code authz_permission} 의
 * {@code (PORTAL, order-service, ORDER_READ)} 행). 없어진 것은 그것을 토큰으로 나르던 길뿐이다.
 *
 * <p><b>소유권 확인은 그대로다.</b> 그것은 역할이 아니라 조회 키의 문제였고
 * ({@code MyOrderApiController}), 인가가 빠져도 남의 주문은 여전히 404 다.
 *
 * <h2>무상태</h2>
 * 세션을 만들지 않는다. 토큰이 요청마다 신원을 들고 오므로 서버가 기억할 것이 없다. CSRF 도 끈다.
 * 브라우저가 자동으로 붙여주는 자격증명(쿠키)이 없으면 CSRF 가 성립하지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        // 토큰을 Spring 이 아는 사용자로 바꿀 때 realm_access.roles 를 권한으로 옮긴다.
        JwtAuthenticationConverter tokenToUser = new JwtAuthenticationConverter();
        tokenToUser.setJwtGrantedAuthoritiesConverter(new RealmRoleAuthorities());

        return http
                .csrf(AbstractHttpConfigurer::disable)
                // CorsPolicy 가 준 규칙을 시큐리티 필터 안에서 적용한다. WebMvc 쪽 설정만 두면
                // 이 필터체인이 먼저 돌아 preflight 가 401 로 막힌다.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 기본이 거부다. 여는 것을 잊는 실수는 401 로 드러나고, 막는 것을 잊는 실수는 생기지 않는다.
                //
                // 지금 요구하는 것은 인증뿐이다. 역할 규칙은 다음에 이 자리에 건다.
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/products").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(tokenToUser)))
                .build();
    }
}
