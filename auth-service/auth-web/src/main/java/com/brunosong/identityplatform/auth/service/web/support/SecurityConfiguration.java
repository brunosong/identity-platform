package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmIssuers;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSigningKeys;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * auth 가 받는 토큰을 어디서 어떻게 검증하나. 경로마다 필터 체인이 하나씩 있다.
 *
 * <pre>
 * /api/admin/**   관리 API.  ADMIN 키로 검증하고 AUTHZ_MANAGE 가 있어야 들여보낸다
 * 나머지 전부      아직 각자 막는다. 운영 화면은 인터셉터, 로그인 서버와 공개 문서는 열려 있다
 * </pre>
 *
 * <p>체인이 realm 을 정한다. 관리 API 체인의 검증기는 ADMIN 공개키 하나만 안다. 그래서 포털이나 MASTER
 * 토큰은 서명에서 떨어진다. 전에는 컨트롤러 메서드마다 {@code access.require(request)} 를 불러 같은
 * 일을 했고, 한 곳에서 빠뜨리면 그 API 는 인증 없이 열렸다. 지금은 경로가 체인을 고르니 빠뜨릴 수 없다.
 *
 * <p>auth 는 발급자 자신이라 공개키를 JWKS 로 받아 오지 않는다. 서명할 때 쓰는 키의 짝을 메모리에서 바로 쓴다.
 */
@Configuration
public class SecurityConfiguration {

    /** 관리 API. 브라우저 쿠키가 아니라 Bearer 헤더로만 오므로 세션도 CSRF 도 필요 없다. */
    @Bean
    @Order(1)
    SecurityFilterChain adminApi(HttpSecurity http,
                                 JwtDecoder adminJwtDecoder,
                                 ListSubjectPermissionsUseCase subjectPermissions,
                                 @Value("${authorization.manage-permission:AUTHZ_MANAGE}") String managePermission,
                                 ObjectMapper objectMapper) throws Exception {
        return http.securityMatcher("/api/admin/**")
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority(managePermission))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(adminJwtDecoder)
                                // 토큰에는 권한이 없다. 그 사람의 권한을 DB 에서 읽어 authorities 로 채운다.
                                // 권한을 회수하면 다음 요청부터 바로 막힌다.
                                .jwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt,
                                        subjectPermissions.of(Realm.ADMIN, jwt.getSubject()).stream()
                                                .map(SimpleGrantedAuthority::new).toList(),
                                        jwt.getSubject())))
                        .authenticationEntryPoint((request, response, e) -> {
                            new BearerTokenAuthenticationEntryPoint().commence(request, response, e);
                            writeError(response, objectMapper, "로그인이 필요합니다.");
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            new BearerTokenAccessDeniedHandler().handle(request, response, e);
                            writeError(response, objectMapper, "이 작업에 필요한 권한이 없습니다: " + managePermission);
                        }))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .build();
    }

    /**
     * 나머지 전부. 스프링 시큐리티의 기본값(전부 막고 폼 로그인)을 끄고 지금까지처럼 통과시킨다.
     *
     * <p>CSRF 를 끄는 것은 원래 상태 그대로다. 로그인 폼과 운영 화면의 폼에는 CSRF 토큰이 없고,
     * {@code SameSite=Lax} 쿠키에 기대고 있다.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain everythingElse(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .build();
    }

    /**
     * 관리 API 가 받는 토큰. ADMIN 공개키로 서명을, 그리고 발급자, 만료, 받는 쪽({@code aud}), 종류를 본다.
     *
     * <p>{@code aud} 는 이 서비스가 속한 시스템(backoffice)이다. 같은 ADMIN realm 이라도 다른 시스템 앞으로
     * 나간 토큰은 받지 않는다. {@code type} 은 refresh 를 access 자리에 넣는 것을 막는다. 둘 다 같은 키로
     * 서명돼 있어 서명만 보면 통과한다.
     */
    @Bean
    JwtDecoder adminJwtDecoder(RealmSigningKeys signingKeys, RealmIssuers issuers,
                               @Value("${auth.audience:backoffice}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) signingKeys.of(Realm.ADMIN).publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuers.of(Realm.ADMIN)),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>("type", "access"::equals)));
        return decoder;
    }

    /** 관리 API 의 다른 실패와 같은 모양({@code {"message": ...}})으로 본문을 쓴다. 상태와 헤더는 앞에서 정했다. */
    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, String message)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(message));
    }
}
