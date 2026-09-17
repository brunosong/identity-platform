package com.brunosong.identityplatform.auth.service.social.google;

import org.springframework.util.StringUtils;

/**
 * 구글에 등록해 둔 우리 앱 - 구글을 상대할 때 우리가 내미는 신분.
 *
 * <p>여기서 우리는 발급자가 아니라 <b>클라이언트</b>다. 평소 이 서비스가 앱들에게 요구하던 것을
 * 이번엔 우리가 구글에게 요구받는다. 그래서 값 셋이 필요하다.
 *
 * <ul>
 *   <li>{@code clientId} - 묻는 게 누구인지. 공개값이다</li>
 *   <li>{@code clientSecret} - 진짜 그 앱인지. code 를 토큰으로 바꿀 때만 쓰고 브라우저에 나가지 않는다</li>
 *   <li>{@code redirectUri} - 구글이 브라우저를 돌려보낼 주소</li>
 * </ul>
 *
 * <p>시크릿을 쥘 수 있는 것은 우리가 서버이기 때문이다. 브라우저에서 도는 앱은 이걸 못 해서
 * PKCE 를 대신 쓴다 - 우리가 발급자로서 {@code customer-portal} 에게 요구하게 될 것이 그것이다.
 * 같은 흐름인데 어느 쪽에 서 있느냐에 따라 장치가 갈린다.
 */
public record GoogleClientRegistration(String clientId, String clientSecret, String redirectUri) {

    /** 구글 콘솔의 Authorized redirect URIs 에 등록해 둔 경로. realm 발급자 주소 뒤에 붙는다. */
    private static final String CALLBACK_PATH = "/broker/google/endpoint";

    /**
     * @param realmIssuer 이 realm 의 발급자 주소({@code http://localhost:8080/realms/portal}).
     *                    돌아올 주소를 여기서 유도한다 - 발급자 주소가 곧 이 서비스의 바깥에서
     *                    보이는 주소라, 포트를 옮기면 콜백 주소도 따라 움직여야 맞다.
     */
    public static GoogleClientRegistration of(String clientId, String clientSecret, String realmIssuer) {
        // clientId 가 있는데 시크릿이 없으면 교환 요청이 구글에서 거절된다. 그 실패는 사용자가
        // 구글까지 다녀온 다음에야 나타나므로, 부팅에서 미리 죽는 편이 낫다.
        if (!StringUtils.hasText(clientSecret)) {
            throw new IllegalStateException(
                    "구글 클라이언트 시크릿이 없습니다: social.google.client-secret");
        }
        return new GoogleClientRegistration(clientId, clientSecret, realmIssuer + CALLBACK_PATH);
    }
}
