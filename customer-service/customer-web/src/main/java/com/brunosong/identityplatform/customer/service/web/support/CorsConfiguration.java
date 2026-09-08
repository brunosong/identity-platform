package com.brunosong.identityplatform.customer.service.web.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 브라우저에서 이 서비스를 직접 부를 때 필요한 CORS.
 *
 * <p>프론트엔드는 auth 와 이 서비스를 <b>둘 다</b> 부른다. 두 서비스가 각각 자기 CORS 를 밝혀야 하고,
 * 한쪽만 열어두면 다른 쪽 호출이 브라우저에서 막힌다 — 게이트웨이가 없을 때의 대가다.
 *
 * <p>{@code allowCredentials} 는 켜지 않는다. 토큰은 쿠키가 아니라 Authorization 헤더로 온다.
 * 허용 출처는 기본값이 없어 설정하지 않으면 이 설정 자체가 켜지지 않는다.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.cors", name = "allowed-origins")
public class CorsConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfiguration(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        // 공백 하나가 섞이면 그 출처의 모든 요청이 403 이 되고, 로그에는 "Invalid CORS request" 만 남는다.
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}
