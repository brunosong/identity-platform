package com.brunosong.identityplatform.order.service.web.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * <h2>지금 이 서비스에 역할 검사가 없다</h2>
 * 토큰에서 인가 클레임을 걷어냈다. 전에는 {@code resource_access} 의 이 서비스 칸을 authority 로
 * 옮겨 {@code ORDER_READ} / {@code ORDER_WRITE} 를 요구했는데, 그 칸이 더 이상 오지 않는다.
 *
 * <p>그래서 <b>지금은 인증만 통과하면 이 API 들이 열린다.</b> 인가를 어디서 판정할지
 * (게이트웨이, 이 서비스의 auth 조회, 토큰 재적재)는 아직 정하지 않았다.
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
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // CorsPolicy 가 준 규칙을 시큐리티 필터 안에서 적용한다. WebMvc 쪽 설정만 두면
                // 이 필터체인이 먼저 돌아 preflight 가 401 로 막힌다.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 기본이 거부다. 여는 것을 잊는 실수는 401 로 드러나고, 막는 것을 잊는 실수는 생기지 않는다.
                //
                // 지금 요구하는 것은 인증뿐이다. 역할 검사는 토큰에서 인가 클레임을 걷어내면서
                // 함께 빠졌고, 어디서 판정할지 정해지면 이 자리로 돌아온다.
                .authorizeHttpRequests(requests -> requests
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
