package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmIssuers;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSigningKeys;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSystems;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * auth 가 받는 토큰을 어디서 어떻게 검증하나. 경로마다 필터 체인이 하나씩 있다.
 *
 * <pre>
 * 1  /api/admin/**                      관리 API. MASTER 키로 검증하고 AUTHZ_MANAGE 가 있어야 들여보낸다
 * 2  /realms/{realm}/userinfo           토큰 주인의 정보. 경로의 realm 키로 검증한다
 * 3  /realms/**, /api/**, /.well-known/**, /error
 *                                       공개. 로그인 서버, 토큰 발급, 공개 문서, 사용자 API 는 각자 알아서 한다
 * 4  나머지 전부 (local)                 운영 화면. MASTER 로그인 쿠키 ({@code ConsoleSecurityConfiguration})
 * 4  나머지 전부 (local 이 아닐 때)       그냥 통과. 운영 화면이 없다
 * </pre>
 *
 * <p>위에서부터 처음 맞는 체인 하나만 돈다. 그래서 3번에 넣지 않은 경로는 local 에서 모두 MASTER 로그인
 * 뒤로 들어간다.
 *
 * <p>체인이 realm 을 정한다. 관리 API 체인의 검증기는 MASTER 공개키 하나만 안다. 그래서 직원(ADMIN)이나
 * 고객(PORTAL) 토큰은 서명에서 떨어진다. auth 를 관리하는 것은 MASTER realm 의 관리자다. Keycloak 의
 * Admin REST API 를 master realm 관리자가 부르는 것과 같다. 직원 realm 은 업무 시스템을 쓰는 사람들의 자리다. 전에는 컨트롤러 메서드마다 {@code access.require(request)} 를 불러 같은
 * 일을 했고, 한 곳에서 빠뜨리면 그 API 는 인증 없이 열렸다. 지금은 경로가 체인을 고르니 빠뜨릴 수 없다.
 *
 * <p>auth 는 발급자 자신이라 공개키를 JWKS 로 받아 오지 않는다. 서명할 때 쓰는 키의 짝을 메모리에서 바로 쓴다.
 */
@Configuration
public class SecurityConfiguration {

    /** 운영 화면 체인의 자리. 공개 체인 뒤, 그냥 통과 체인 앞이다. */
    public static final int CONSOLE_ORDER = 4;

    /** 관리 API. 브라우저 쿠키가 아니라 Bearer 헤더로만 오므로 세션도 CSRF 도 필요 없다. */
    @Bean
    @Order(1)
    SecurityFilterChain adminApi(HttpSecurity http,
                                 JwtDecoder masterJwtDecoder,
                                 ListSubjectPermissionsUseCase subjectPermissions,
                                 @Value("${authorization.manage-permission:AUTHZ_MANAGE}") String managePermission,
                                 ObjectMapper objectMapper) throws Exception {
        return http.securityMatcher("/api/admin/**")
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority(managePermission))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(masterJwtDecoder)
                                // 토큰에는 권한이 없다. 그 사람의 권한을 DB 에서 읽어 authorities 로 채운다.
                                // 권한을 회수하면 다음 요청부터 바로 막힌다.
                                .jwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt,
                                        subjectPermissions.of(Realm.MASTER, jwt.getSubject()).stream()
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
     * userinfo. 앱이 "이 토큰 주인이 누구인가" 를 묻는 OIDC 의 자리다.
     *
     * <p>realm 이 경로에 있어서 검증기를 하나로 고정할 수 없다. 경로의 realm 을 읽어 그 realm 의 검증기를
     * 고른다. {@code /realms/portal/userinfo} 면 PORTAL 공개키로만 본다. 그래서 직원 토큰을 포털 자리에
     * 내면 서명에서 떨어진다. 토큰이 스스로 말하는 {@code iss} 나 {@code kid} 로 고르지 않는 것이 요점이다.
     *
     * <p>받는 쪽({@code aud})은 따지지 않는다. 고객 시스템(shop) 앞으로 나간 토큰으로도 자기 정보는 볼 수
     * 있어야 한다. Keycloak 의 userinfo 도 그렇다. 종류({@code type=access})는 본다.
     */
    @Bean
    @Order(2)
    SecurityFilterChain userInfo(HttpSecurity http, RealmSigningKeys signingKeys, RealmIssuers issuers)
            throws Exception {
        Map<Realm, AuthenticationManager> managers = new EnumMap<>(Realm.class);
        for (Realm realm : Realm.values()) {
            managers.put(realm, new ProviderManager(
                    new JwtAuthenticationProvider(accessTokenDecoder(signingKeys, issuers, realm, null))));
        }
        AuthenticationManager unknownRealm = authentication -> {
            throw new InvalidBearerTokenException("모르는 realm 입니다.");
        };
        AuthenticationManagerResolver<HttpServletRequest> byPathRealm =
                request -> realmInPath(request).map(managers::get).orElse(unknownRealm);

        return http.securityMatcher("/realms/*/userinfo")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.authenticationManagerResolver(byPathRealm))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .build();
    }

    /**
     * 공개 경로. 토큰 없이 열려야 이 서비스가 돈다. 막히면 로그인하러 간 곳에서 다시 로그인으로 보내진다.
     * 사용자 API({@code /api/auth/**})는 자기 컨트롤러가 토큰을 본다.
     *
     * <p>CSRF 를 끄는 것은 원래 상태 그대로다. 로그인 폼에는 CSRF 토큰이 없고 {@code SameSite=Lax} 쿠키에
     * 기대고 있다.
     */
    @Bean
    @Order(3)
    SecurityFilterChain publicPaths(HttpSecurity http) throws Exception {
        return http.securityMatcher("/realms/**", "/api/**", "/.well-known/**", "/error")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .build();
    }

    /**
     * 나머지 전부, local 이 아닐 때. 운영 화면이 없어서 남는 경로는 404 다. 스프링 시큐리티의 기본값
     * (전부 막고 폼 로그인)만 꺼 둔다.
     *
     * <p>local 에서는 운영 화면 체인이 이 자리를 맡는다. 모든 요청을 받는 체인은 하나만 둘 수 있어서
     * 프로파일로 갈랐다. 둘이면 스프링 시큐리티가 뒤의 것은 영영 안 돈다며 시작을 거부한다.
     */
    @Bean
    @Profile("!local")
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain everythingElse(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .build();
    }

    /**
     * 관리 API 가 받는 토큰. MASTER 공개키로 서명을, 그리고 발급자, 만료, 받는 쪽({@code aud}), 종류를 본다.
     *
     * <p>{@code aud} 는 MASTER realm 의 시스템(auth-console, {@code token.realms.MASTER.system})이다.
     * {@code type} 은 refresh 를 access 자리에 넣는 것을 막는다. 둘 다 같은 키로 서명돼 있어 서명만 보면
     * 통과한다.
     */
    @Bean
    JwtDecoder masterJwtDecoder(RealmSigningKeys signingKeys, RealmIssuers issuers, RealmSystems systems) {
        return accessTokenDecoder(signingKeys, issuers, Realm.MASTER, systems.of(Realm.MASTER));
    }

    /**
     * 그 realm 의 access 토큰 검증기. 서명, 발급자, 만료, 종류를 보고, {@code audience} 가 있으면 받는 쪽도 본다.
     */
    private static JwtDecoder accessTokenDecoder(RealmSigningKeys signingKeys, RealmIssuers issuers, Realm realm,
                                                 String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) signingKeys.of(realm).publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>(List.of(
                JwtValidators.createDefaultWithIssuer(issuers.of(realm)),
                new JwtClaimValidator<String>("type", "access"::equals)));
        if (audience != null) {
            validators.add(new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                    aud -> aud != null && aud.contains(audience)));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /** {@code /realms/{realm}/...} 의 realm. 모르는 값이면 비어 있다. */
    private static Optional<Realm> realmInPath(HttpServletRequest request) {
        String[] segments = request.getRequestURI().substring(request.getContextPath().length()).split("/");
        if (segments.length < 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(Realm.valueOf(segments[2].toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 관리 API 의 다른 실패와 같은 모양({@code {"message": ...}})으로 본문을 쓴다. 상태와 헤더는 앞에서 정했다. */
    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, String message)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(message));
    }
}
