package com.brunosong.identityplatform.auth.service.external.google;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SocialIdentityVerifierPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.VerifiedSocialIdentity;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/**
 * {@link SocialIdentityVerifierPort} 구글 구현 - 돌아온 code 를 신원으로 바꾼다.
 *
 * <p>두 단계다. <b>교환</b>과 <b>확인</b>.
 *
 * <h2>교환</h2>
 * 주소창으로 온 {@code code} 는 그 자체로 아무것도 못 하는 교환권이다. 시크릿을 쥔 서버가 구글에
 * 직접 물어야 토큰이 나온다. 이 요청은 브라우저를 거치지 않는다 - 그래서 진짜 토큰은 주소창에도,
 * 브라우저 히스토리에도, 리퍼러에도 실리지 않는다. 옛날 방식(implicit)은 토큰을 주소창에 실어
 * 보냈고 그래서 없어졌다.
 *
 * <p>{@code redirect_uri} 를 여기서 한 번 더 보낸다. 인가 요청 때 이미 보냈는데 또 보내는 이유는,
 * 구글이 "그때 그 요청이 맞느냐" 를 대조하기 때문이다.
 *
 * <h2>확인</h2>
 * 받은 {@code id_token} 이 <b>우리 것인지</b> 본다. 구글이 만든 진짜 토큰이어도 다른 앱에게 발급된
 * 것일 수 있다 - 공격자가 자기 앱을 구글에 등록해두고 피해자를 거기 로그인시키면, 피해자 이름이
 * 적힌 진짜 구글 토큰을 손에 넣는다. 그것을 우리에게 내밀었을 때 {@code aud} 를 안 보면 우리는
 * 피해자로 인정한다.
 *
 * <h2>서명은 아직 확인하지 않는다</h2>
 * 이 토큰은 우리가 TLS 로 구글 토큰 엔드포인트에 직접 물어서 받은 응답이라, 위조된 것이 이 자리에
 * 올 길이 없다. OIDC 명세도 이 경로에서는 서명 검증 대신 TLS 서버 검증에 기댈 수 있다고 적어
 * 두었다(Core 3.1.3.7). 다만 경로가 바뀌면(브라우저가 id_token 을 직접 보내오는 구글 원탭 같은 것)
 * 그때는 필수가 된다. JWKS 로 서명을 확인하는 부분은 다음에 이 클래스 안에 붙인다.
 */
class GoogleIdentityVerifier implements SocialIdentityVerifierPort {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    /** 구글은 두 표기를 모두 쓴다. 어느 쪽으로 오든 받아야 한다. */
    private static final Set<String> ISSUERS = Set.of(
            "https://accounts.google.com", "accounts.google.com");

    private final RestClient restClient;
    private final GoogleClientRegistration registration;
    private final ObjectMapper objectMapper;

    GoogleIdentityVerifier(RestClient restClient, GoogleClientRegistration registration,
                           ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.registration = registration;
        this.objectMapper = objectMapper;
    }

    @Override
    public VerifiedSocialIdentity verify(SocialProvider provider, String authorizationCode) {
        if (provider != SocialProvider.GOOGLE) {
            throw new IllegalArgumentException("이 어댑터는 구글만 상대한다: " + provider);
        }

        IdTokenClaims claims = readClaims(exchange(authorizationCode));
        verifyClaims(claims);

        // 키는 sub 이다. 이메일은 바뀌지만 sub 은 그 구글 계정에 붙어 안 바뀐다.
        // 이메일은 기존 신원을 찾을 때만 쓰고, 구글이 소유를 확인해준 것만 넘긴다.
        return new VerifiedSocialIdentity(claims.sub(), verifiedEmail(claims), claims.name());
    }

    /** code 를 토큰으로 바꾼다. 서버끼리 주고받는 유일한 구간이다. */
    private String exchange(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        form.add("client_id", registration.clientId());
        form.add("client_secret", registration.clientSecret());
        // 인가 요청 때 쓴 주소와 같아야 한다. 구글이 "그때 그 요청이 맞느냐" 를 이 값으로 본다.
        form.add("redirect_uri", registration.redirectUri());

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            // code 가 이미 쓰였거나, 만료됐거나, redirect_uri 가 인가 요청 때와 다르면 여기서 걸린다.
            // 사용자에게는 어느 쪽인지 알릴 이유가 없다.
            throw new AuthenticationFailedException("구글 로그인에 실패했습니다.");
        }

        if (response == null || !StringUtils.hasText(response.idToken())) {
            // scope 에 openid 가 빠지면 id_token 없이 access_token 만 온다.
            throw new AuthenticationFailedException("구글이 id_token 을 주지 않았습니다.");
        }
        return response.idToken();
    }

    /**
     * JWT 의 가운데 토막(payload)을 읽는다. base64url 이라 누구나 풀어 읽을 수 있다 -
     * 서명이 지키는 것은 내용의 비밀이 아니라 위조 여부다.
     */
    private IdTokenClaims readClaims(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new AuthenticationFailedException("구글 id_token 형식이 아닙니다.");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payload, IdTokenClaims.class);
        } catch (Exception e) {
            throw new AuthenticationFailedException("구글 id_token 을 읽지 못했습니다.");
        }
    }

    private void verifyClaims(IdTokenClaims claims) {
        if (claims.iss() == null || !ISSUERS.contains(claims.iss())) {
            throw new AuthenticationFailedException("구글이 발급한 토큰이 아닙니다.");
        }
        // 가장 중요한 확인이다. 진짜 구글 토큰이지만 남의 앱에게 발급된 것을 걸러낸다.
        if (!registration.clientId().equals(claims.aud())) {
            throw new AuthenticationFailedException("다른 앱에게 발급된 토큰입니다.");
        }
        if (claims.exp() == null || Instant.ofEpochSecond(claims.exp()).isBefore(Instant.now())) {
            throw new AuthenticationFailedException("구글 토큰이 만료됐습니다.");
        }
        if (!StringUtils.hasText(claims.sub())) {
            throw new AuthenticationFailedException("구글 토큰에 sub 이 없습니다.");
        }
    }

    /**
     * 구글이 소유를 확인해 준 주소만 넘긴다. 확인되지 않았으면 없는 것으로 다룬다 - 이 값이
     * 기존 신원을 찾는 열쇠라, 미확인 주소를 넘기면 남의 계정에 붙을 수 있다.
     */
    private static String verifiedEmail(IdTokenClaims claims) {
        return Boolean.TRUE.equals(claims.emailVerified()) ? claims.email() : null;
    }

    /** 교환 응답. 우리가 쓰는 것은 id_token 하나뿐이라 나머지는 받지 않는다. */
    private record TokenResponse(@JsonProperty("id_token") String idToken) {
    }

    /** id_token 페이로드에서 우리가 보는 것들. */
    private record IdTokenClaims(
            String iss,
            String aud,
            String sub,
            String email,
            @JsonProperty("email_verified") Boolean emailVerified,
            String name,
            Long exp) {
    }
}
