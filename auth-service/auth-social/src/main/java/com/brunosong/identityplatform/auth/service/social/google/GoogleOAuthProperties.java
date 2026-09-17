package com.brunosong.identityplatform.auth.service.social.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 OAuth 클라이언트 설정.
 *
 * <pre>
 * social:
 *   google:
 *     client-id: ${GOOGLE_CLIENT_ID:}
 *     client-secret: ${GOOGLE_CLIENT_SECRET:}
 * </pre>
 *
 * <p>둘 다 구글 클라우드 콘솔에서 발급받는 값이다. 시크릿은 저장소에 두지 않고 환경변수로 넣는다.
 *
 * <p><b>돌아올 주소(redirect_uri)는 여기 없다.</b> 발급자 주소에서 유도한다
 * ({@link GoogleClientRegistration}). 주소를 두 군데 적으면 포트를 옮길 때 한쪽만 고치게 되고,
 * 그 어긋남은 구글이 {@code redirect_uri_mismatch} 를 뱉을 때까지 보이지 않는다.
 */
@ConfigurationProperties(prefix = "social.google")
public class GoogleOAuthProperties {

    private String clientId;
    private String clientSecret;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
