package com.brunosong.identityplatform.order.service.web.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 브라우저에서 이 서비스를 직접 부를 때 필요한 CORS.
 *
 * <p>게이트웨이가 없어서 프론트엔드가 auth, customer, order 를 <b>각각</b> 부른다. 서비스를 하나
 * 붙일 때마다 그 서비스가 자기 CORS 를 또 밝혀야 한다는 뜻이고, 이것이 게이트웨이를 두지 않을
 * 때의 비용이 어디서 늘어나는지 보여주는 자리다.
 *
 * <p><b>{@link CorsConfigurationSource} 빈으로 낸다.</b> 시큐리티 필터체인이 DispatcherServlet 보다
 * 먼저 돌아서, {@code WebMvcConfigurer} 로만 등록하면 preflight(OPTIONS, Authorization 헤더 없음)가
 * 인증 실패로 막힌다. 이 빈을 두면 {@code http.cors()} 가 그것을 필터 안에서 처리한다.
 *
 * <p>{@code allowCredentials} 는 켜지 않는다. 토큰은 쿠키가 아니라 Authorization 헤더로 온다.
 * 허용 출처는 기본값이 없어 설정하지 않으면 이 설정 자체가 켜지지 않는다.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.cors", name = "allowed-origins")
public class CorsPolicy {

    private final List<String> allowedOrigins;

    public CorsPolicy(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        // 공백 하나가 섞이면 그 출처의 모든 요청이 막히고, 로그에는 "Invalid CORS request" 만 남는다.
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration policy = new CorsConfiguration();
        policy.setAllowedOrigins(allowedOrigins);
        policy.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        policy.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        policy.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", policy);
        return source;
    }
}
