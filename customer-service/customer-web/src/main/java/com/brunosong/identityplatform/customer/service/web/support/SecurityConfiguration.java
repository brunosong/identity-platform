package com.brunosong.identityplatform.customer.service.web.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 이 서비스의 인증 전부.
 *
 * <h2>직접 만든 검증기를 걷어냈다</h2>
 * 전에는 {@code AuthenticatedCaller} 가 컨트롤러마다 불려서 헤더를 읽고 토큰을 검증했다.
 * 그 방식의 문제는 <b>부르는 것을 잊으면 그대로 열린다</b>는 것이다 — 새 컨트롤러를 추가하면서
 * 한 줄을 빠뜨리면 인증 없는 API 가 조용히 생긴다.
 *
 * <p>지금은 필터가 <b>모든 요청 앞에</b> 있고 기본값이 "거부" 다({@code anyRequest().authenticated()}).
 * 열려면 명시적으로 적어야 한다. 잊었을 때의 결과가 반대 방향으로 뒤집힌 것이 핵심이다.
 *
 * <h2>설정 한 줄이 auth 와의 계약 전부다</h2>
 * <pre>
 * spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8080/realms/portal
 * </pre>
 *
 * 이 한 줄로 Spring Security 가 하는 일:
 * <ol>
 *   <li>{@code {issuer}/.well-known/openid-configuration} 을 받아 {@code jwks_uri} 를 찾는다</li>
 *   <li>거기서 공개키를 받아 캐시하고, 모르는 {@code kid} 를 만나면 다시 받아온다(키 교체)</li>
 *   <li>서명·만료({@code exp}/{@code nbf})·발급자({@code iss})를 검증한다</li>
 * </ol>
 *
 * realm 경계는 여전히 <b>설정</b>에 있다. 발급자 이름에 realm 이 들어 있고, 그 발급자의 JWKS 만
 * 받아오므로 어드민 토큰은 서명 단계에서 죽는다 — 이 서비스의 코드가 한 줄도 돌기 전에.
 *
 * <p><b>auth 보다 먼저 떠도 된다.</b> {@code issuer-uri} 를 쓰면 Spring 이 디코더 생성을 첫 검증까지
 * 미룬다({@code SupplierJwtDecoder}). 부팅 시점에 auth 를 부르지 않는다.
 *
 * <h2>권한은 토큰에서 오고, 규칙은 여기 있다</h2>
 * 전에는 auth 가 권한 코드를 평면 목록으로 실어주고 게이트웨이가 URL 규칙까지 auth 에서
 * 가져다 판정했다. 그러면 이 서비스가 엔드포인트를 하나 추가할 때마다 auth 에 등록해야 하고,
 * 배포가 서로 묶인다.
 *
 * <p>지금은 auth 가 <b>역할 이름</b>만 주고({@code resource_access["customer-service"].roles}),
 * 그 이름이 어느 URL 을 여는지는 이 클래스가 정한다. 규칙이 코드와 같이 배포되므로
 * "환불 API 를 만들었다" 와 "환불 역할 규칙" 이 같은 PR 에서 리뷰된다.
 *
 * <p>규칙을 <b>배포 없이</b> 바꿔야 한다면 그때 이 서비스의 DB 로 내리면 된다. 대부분의 업무
 * API 는 규칙이 엔드포인트와 같은 속도로 바뀌므로 코드가 맞다.
 *
 * <h2>무상태</h2>
 * 세션을 만들지 않는다. 토큰이 요청마다 신원을 들고 오므로 서버가 기억할 것이 없고, 기억하기
 * 시작하면 인스턴스를 늘릴 때 그 상태를 공유해야 한다. CSRF 도 끈다 — 브라우저가 자동으로 붙여주는
 * 자격증명(쿠키)이 없으면 CSRF 가 성립하지 않는다. 토큰은 스크립트가 명시적으로 싣는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * 이 서비스가 요구하는 역할. auth 에는 {@code (PORTAL, customer-service, PROFILE_READ)} 행으로
     * 등록돼 있고, 가입할 때 자동으로 부여된다.
     *
     * <p><b>이 이름이 무엇을 여는지는 여기서 정한다.</b> auth 는 이름만 보관한다 — 그래서
     * 이 서비스가 엔드포인트를 늘려도 auth 를 배포하지 않는다.
     */
    private static final String PROFILE_READ = "PROFILE_READ";

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                // 쿠키를 쓰지 않으므로 CSRF 가 성립하지 않는다.
                .csrf(AbstractHttpConfigurer::disable)
                // CorsPolicy 가 준 규칙을 시큐리티 필터 안에서 적용한다. WebMvc 쪽 설정만 두면
                // 이 필터체인이 먼저 돌아 preflight 가 401 로 막힌다.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 기본이 거부다. 여는 것을 잊는 실수는 401 로 드러나고, 막는 것을 잊는 실수는 생기지 않는다.
                //
                // 그리고 여기가 이 서비스의 인가 규칙이 사는 자리다. 토큰의
                // resource_access["customer-service"].roles 가 authority 로 들어와 있으므로
                // (ClientRoleAuthorities) 그대로 요구하면 된다.
                //
                // 규칙이 auth 가 아니라 이 코드에 있다는 것이 요점이다 — 엔드포인트를 추가하면서
                // 같은 PR 에서 규칙도 바뀌고, auth 스키마를 건드리지 않고 배포된다.
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/customers/**").hasAuthority(PROFILE_READ)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
