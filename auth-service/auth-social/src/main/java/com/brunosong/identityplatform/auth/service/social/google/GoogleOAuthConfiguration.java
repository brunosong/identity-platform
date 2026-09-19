package com.brunosong.identityplatform.auth.service.social.google;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialAuthorizationPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialIdentityVerifierPort;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmIssuers;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 구글 소셜 로그인 와이어링.
 *
 * <p>설정이 없으면 이 설정 자체가 켜지지 않고, 소셜 로그인은 꺼진 채로 뜬다
 * ({@code SocialIdentityVerifierPort} 를 선택 의존으로 조회하는 쪽이 그것을 견딘다).
 * 토큰 발급과 다른 점이다 - 그건 이 서비스의 본래 기능이라 없으면 부팅이 실패해야 하지만,
 * 소셜은 붙였다 뗄 수 있는 것이다.
 *
 * <p><b>{@code @ConditionalOnProperty} 가 아니라 {@code @ConditionalOnExpression} 인 이유</b>가 있다.
 * 설정에 {@code client-id: ${GOOGLE_CLIENT_ID:}} 라고 적혀 있으면 환경변수가 없어도 프로퍼티는
 * <b>빈 문자열로 존재한다.</b> {@code @ConditionalOnProperty} 는 "값이 있느냐" 가 아니라 "키가 있느냐" 를
 * 보기 때문에 그 상태에서 켜져 버리고, 빈 clientId 로 구글에 붙으러 간다.
 */
@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties.class)
@ConditionalOnExpression("'${social.google.client-id:}' != ''")
public class GoogleOAuthConfiguration {

    /**
     * 소셜 로그인은 포털에서만 연다. 처음 들어온 소셜 계정에 신원을 새로 만들어주기 때문에
     * ({@code AuthenticateWithSocialService} 의 JIT 프로비저닝) 어드민에 열면 아무나 직원 신원을
     * 만들 수 있다. 그래서 돌아올 주소도 포털 발급자에서만 유도한다.
     *
     * <p>어드민에도 소셜을 열 날이 오면 이 값이 realm 별 맵이 되고, 구글 콘솔에도 주소를 하나 더
     * 등록해야 한다. 지금 미리 맵으로 두지 않는 이유는 고를 것이 하나뿐이라서다.
     */
    @Bean
    public GoogleClientRegistration googleClientRegistration(GoogleOAuthProperties properties,
                                                             RealmIssuers issuers) {
        return GoogleClientRegistration.of(
                properties.getClientId(), properties.getClientSecret(), issuers.of(Realm.PORTAL),
                properties.getAppRedirectUri());
    }

    /**
     * 브라우저를 구글로 보낼 주소를 만드는 어댑터.
     *
     * <p>이 빈이 있느냐 없느냐가 곧 "이 서버에 소셜 로그인이 있느냐" 다. 설정이 없으면 이 설정
     * 클래스가 통째로 꺼지므로 빈도 생기지 않고, 브로커 경로는 404 가 된다.
     */
    @Bean
    public SocialAuthorizationPort googleAuthorizationAdapter(GoogleClientRegistration registration) {
        return new GoogleAuthorizationAdapter(registration);
    }

    /**
     * 돌아온 code 를 신원으로 바꾸는 어댑터.
     *
     * <p>이 빈이 생기는 순간 기존 {@code POST /api/auth/realms/portal/login/social} 도 함께 살아난다.
     * 그 경로는 어댑터가 없어 500 이던 자리였다({@code AuthenticateWithSocialService} 가 선택 조회로
     * 들고 있었다).
     *
     * <p>{@code RestClient} 를 여기서 만든다. 구글 두 곳(토큰 엔드포인트, 뒤에 JWKS)만 부르는
     * 클라이언트라 공용 빈으로 둘 이유가 없다.
     */
    @Bean
    public SocialIdentityVerifierPort googleIdentityVerifier(GoogleClientRegistration registration,
                                                             ObjectMapper objectMapper) {
        return new GoogleIdentityVerifier(RestClient.create(), registration, objectMapper);
    }
}
