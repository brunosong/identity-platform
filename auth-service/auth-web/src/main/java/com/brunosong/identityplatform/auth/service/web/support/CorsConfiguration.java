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
 * <p><b>{@code allowCredentials} 를 켠 자리와 아닌 자리가 있다.</b> 쿠키를 주고받는 두 경로에만 켠다.
 * refresh 토큰이 {@code HttpOnly} 쿠키로 나가고({@link RefreshTokenCookie}) 재발급 때 다시 실려
 * 오는데, 이 설정이 없으면 브라우저가 {@code Set-Cookie} 를 <b>말없이 버린다.</b> 오류도 경고도
 * 없어서 원인을 찾기 어려운 자리다.
 *
 * <p>access 토큰은 여전히 호출자가 헤더에 명시적으로 싣는다. 그쪽은 customer-service 처럼 다른
 * 도메인으로 가야 해서 쿠키로 옮길 수가 없다({@link IssuedTokens}).
 *
 * <p>JWKS 에는 켜지 않는다. 거기는 출처가 와일드카드인데 <b>와일드카드와 credentials 는 같이 쓸 수
 * 없다.</b> 애초에 쿠키가 갈 일도 없는 공개 문서다.
 *
 * <p>허용 출처는 설정으로 받고 기본값이 없다. 값이 없으면 이 설정 자체가 켜지지 않는다 —
 * 실수로 아무 출처나 열리는 것보다 브라우저에서 막히는 편이 낫다(서버 간 호출은 CORS 와 무관하다).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.cors", name = "allowed-origins")
public class CorsConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfiguration(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        // 앞뒤 공백을 다듬는다. 출처 비교는 문자열이 정확히 같아야 하므로 공백 하나가 섞이면
        // 그 출처의 모든 요청이 403 이 된다 — 설정 파일에서 줄을 나누다 생기기 쉬운 실수이고,
        // 서버 로그에는 "Invalid CORS request" 만 남아 원인을 찾기 어렵다.
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 재발급(/api/auth/realms/*/token/refresh)이 refresh 쿠키를 주고받으므로 credentials 를 켠다.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);

        // 코드를 토큰으로 바꾸는 자리. 앱이 브라우저에서 직접 부른다.
        //
        // 로그인 화면으로 가는 /realms/*/auth 는 여기 없어도 된다. 그쪽은 주소창이 통째로 옮겨가는
        // 화면 이동이라 CORS 가 걸리지 않는다. 스크립트가 부르는 것은 이 하나뿐이다.
        // 여기 응답이 refresh 쿠키를 심는다. credentials 없이는 브라우저가 그 쿠키를 버린다.
        registry.addMapping("/realms/*/token")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("POST")
                .allowedHeaders("Content-Type")
                .allowCredentials(true)
                .maxAge(3600);

        // JWKS 는 누구나 읽어도 되는 공개키다. 다른 서비스가 서버에서 받아가는 것이 보통이지만,
        // 브라우저에서 토큰을 직접 검증해 보는 데모 같은 것도 막을 이유가 없다.
        registry.addMapping("/realms/*/.well-known/**")
                .allowedOrigins("*")
                .allowedMethods("GET")
                .maxAge(3600);
    }
}
