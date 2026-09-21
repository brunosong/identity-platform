package com.brunosong.identityplatform.auth.service.domain.oauth;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 등록된 클라이언트 - 이 realm 에 로그인을 요청할 수 있는 앱.
 *
 * <p>담고 있는 것은 사실상 하나다. <b>로그인이 끝나면 브라우저를 어디로 돌려보내도 되는가.</b>
 * 그 주소는 인가 요청이 적어 보내는 값이라 그대로 믿을 수 없다 - 믿으면 공격자가 자기 주소를
 * 적어 보내고, 우리가 발급한 code 를 우리 손으로 그쪽에 배달하게 된다.
 *
 * <p>시크릿은 없다. 브라우저에서 도는 앱은 시크릿을 지킬 수 없어 public client 로만 등록되고,
 * 시크릿이 하던 일(요청자가 진짜 그 앱인지 확인)은 PKCE 가 요청마다 대신한다.
 */
@Getter
public class OAuthClient {

    private final String clientId;

    /** 이 앱이 상대하는 realm. 앱은 realm 하나에만 속한다 - 고객 앱으로 어드민 로그인을 시작할 수 없다. */
    private final Realm realm;

    private final Set<String> redirectUris;

    /** 꺼진 앱은 등록돼 있어도 인가 요청을 시작할 수 없다. 행을 지우는 것과 달리 기록이 남는다. */
    private final boolean enabled;

    private OAuthClient(String clientId, Realm realm, Set<String> redirectUris, boolean enabled) {
        this.clientId = clientId;
        this.realm = realm;
        this.redirectUris = redirectUris;
        this.enabled = enabled;
    }

    /**
     * 새로 등록한다. 등록된 앱은 켜진 채로 시작한다 - 꺼진 채로 만들 이유가 없다.
     *
     * <p>주소는 여기서 한 번 걸러진다. 대조는 문자 그대로 하므로, 들어올 때 이상한 값이면
     * 그 앱은 영영 로그인이 안 되고 원인은 로그인 시점에야 드러난다.
     */
    public static OAuthClient register(String clientId, Realm realm, Collection<String> redirectUris) {
        return new OAuthClient(requireClientId(clientId), requireRealm(realm),
                requireRedirectUris(redirectUris), true);
    }

    public static OAuthClient restore(String clientId, Realm realm, Set<String> redirectUris,
                                      boolean enabled) {
        return new OAuthClient(clientId, realm, Set.copyOf(redirectUris), enabled);
    }

    /**
     * 이 주소로 돌려보내도 되는가. <b>문자 그대로 대조한다.</b>
     *
     * <p>와일드카드도, 접두어 일치도, 슬래시 보정도 하지 않는다. 편의를 위해 조금이라도 느슨하게
     * 만들면 그 틈이 곧 열린 리다이렉트가 된다 - 등록된 앱에 열린 리다이렉트가 하나만 있어도
     * code 가 공격자에게 배달되는 길이 열린다. 주소가 늘면 규칙을 느슨하게 하지 말고 등록을 늘린다.
     *
     * <p>OAuth 명세(RFC 6749 3.1.2.3)가 요구하는 비교도 단순 문자열 비교다.
     */
    public boolean allowsRedirect(String redirectUri) {
        return redirectUri != null && redirectUris.contains(redirectUri);
    }

    private static String requireClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        return clientId.trim();
    }

    private static Realm requireRealm(Realm realm) {
        if (realm == null) throw new IllegalArgumentException("realm must not be null");
        return realm;
    }

    /**
     * 돌아갈 주소를 검사한다. 주소가 하나도 없으면 등록할 수 없다 - 그런 앱은 로그인을 시작해도
     * 사람을 돌려보낼 곳이 없다.
     *
     * <p>조각(#뒤)이 붙은 주소는 받지 않는다(RFC 6749 3.1.2). 조각은 브라우저가 서버로 보내지
     * 않는 부분이라 대조할 수가 없고, 우리가 code 를 붙여 돌려보낼 때도 자리가 겹친다.
     */
    private static Set<String> requireRedirectUris(Collection<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new IllegalArgumentException("redirectUri must be registered at least one");
        }
        Set<String> checked = new LinkedHashSet<>();
        for (String candidate : redirectUris) {
            checked.add(requireRedirectUri(candidate));
        }
        return Set.copyOf(checked);
    }

    private static String requireRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri must not be blank");
        }
        String trimmed = redirectUri.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("redirectUri is not a valid URI: " + trimmed);
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new IllegalArgumentException("redirectUri must be an absolute URI: " + trimmed);
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("redirectUri must be http or https: " + trimmed);
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("redirectUri must not have a fragment: " + trimmed);
        }
        return trimmed;
    }
}
