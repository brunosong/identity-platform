package com.brunosong.identityplatform.auth.service.web.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 브라우저에서 직접 부르는 프론트엔드를 위한 CORS 설정.
 *
 * <p>인증 서버와 프론트엔드는 출처(origin)가 다르다. 브라우저는 다른 출처로 가는 요청을 기본적으로
 * 막으므로, 어느 출처를 받아줄지 서버가 밝혀야 한다. 토큰을 쿠키에서 본문/헤더로 옮긴 것도 같은
 * 사정에서 나왔다({@link IssuedTokens}).
 *
 * <p><b>{@code allowCredentials} 를 켜지 않는다.</b> 그것은 쿠키를 실어 보내기 위한 설정인데 이 서비스는
 * 쿠키를 쓰지 않는다. 켜는 순간 와일드카드 출처를 못 쓰게 되고, 무엇보다 브라우저가 자동으로 붙이는
 * 자격증명(쿠키)을 신뢰하는 설계로 되돌아간다 — 토큰은 호출자가 헤더에 명시적으로 싣는다.
 *
 * <p>허용 출처는 설정으로 받고 기본값이 없다. 값이 없으면 이 설정 자체가 켜지지 않는다 —
 * 실수로 아무 출처나 열리는 것보다 브라우저에서 막히는 편이 낫다(서버 간 호출은 CORS 와 무관하다).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.cors", name = "allowed-origins")
public class CorsConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfiguration(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);

        // JWKS 는 누구나 읽어도 되는 공개키다. 다른 서비스가 서버에서 받아가는 것이 보통이지만,
        // 브라우저에서 토큰을 직접 검증해 보는 데모 같은 것도 막을 이유가 없다.
        registry.addMapping("/.well-known/**")
                .allowedOrigins("*")
                .allowedMethods("GET")
                .maxAge(3600);
    }
}
