package com.brunosong.identityplatform.auth.service.domain.oauth;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.AccessLevel;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
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
 * <p>시크릿은 있을 수도 없을 수도 있다. 브라우저에서 도는 앱은 시크릿을 지킬 수 없어 시크릿 없이
 * 등록되고(public client), 요청자가 진짜 그 앱인지는 PKCE 가 요청마다 확인한다. 서버에서 도는 앱은
 * 시크릿을 지킬 수 있으니 받아 두고(confidential client), 토큰을 바꾸러 올 때 함께 낸다.
 * 시크릿이 있어도 PKCE 는 그대로 요구한다. 둘이 막는 것이 다르다. 시크릿은 "그 앱인가" 를 보고,
 * PKCE 는 "그 로그인을 시작한 쪽인가" 를 본다.
 */
@Getter
public class OAuthClient {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_RANDOM_LENGTH = 12;
    private static final int NAME_MAX_LENGTH = 100;

    /**
     * 등록할 때 우리가 발급한다. 사람이 짓지 않는다.
     *
     * <p>사람이 지으면 portal, admin, web 같은 흔한 이름으로 몰려 겹치고, 다른 이름과 우연히 같아질
     * 수 있다. 실제로 backoffice 라는 앱 이름이 ADMIN 시스템 이름(access 토큰의 aud)과 같아서,
     * 그 앱의 id_token 이 access 토큰으로 통과할 수 있었다.
     */
    private final String clientId;

    /** 사람이 알아보는 이름. 화면과 로그에만 쓰고 토큰에는 싣지 않는다. 바꿔도 앱은 그대로다. */
    private final String name;

    /** 이 앱이 상대하는 realm. 앱은 realm 하나에만 속한다 - 고객 앱으로 어드민 로그인을 시작할 수 없다. */
    private final Realm realm;

    private final Set<String> redirectUris;

    /** 꺼진 앱은 등록돼 있어도 인가 요청을 시작할 수 없다. 행을 지우는 것과 달리 기록이 남는다. */
    private final boolean enabled;

    /**
     * 시크릿의 해시. 없으면 public client 다. 원문은 발급할 때 한 번 보여주고 어디에도 남기지 않는다.
     *
     * <p>비밀번호처럼 느린 해시를 쓰지 않는다. 사람이 고른 값이 아니라 우리가 뽑은 난수라서 사전을
     * 대입할 여지가 없고, SHA-256 한 번이면 되돌릴 수 없다.
     */
    @Getter(AccessLevel.NONE)
    private final String secretHash;

    private OAuthClient(String clientId, String name, Realm realm, Set<String> redirectUris,
                        boolean enabled, String secretHash) {
        this.clientId = clientId;
        this.name = name;
        this.realm = realm;
        this.redirectUris = redirectUris;
        this.enabled = enabled;
        this.secretHash = secretHash;
    }

    /**
     * 새로 등록한다. client_id 는 여기서 발급한다. 등록된 앱은 켜진 채로 시작한다.
     *
     * <p>주소는 여기서 한 번 걸러진다. 대조는 문자 그대로 하므로, 들어올 때 이상한 값이면
     * 그 앱은 영영 로그인이 안 되고 원인은 로그인 시점에야 드러난다.
     */
    public static OAuthClient register(Realm realm, String name, Collection<String> redirectUris) {
        Realm checkedRealm = requireRealm(realm);
        return new OAuthClient(newClientId(checkedRealm), requireName(name), checkedRealm,
                requireRedirectUris(redirectUris), true, null);
    }

    public static OAuthClient restore(String clientId, String name, Realm realm, Set<String> redirectUris,
                                      boolean enabled, String secretHash) {
        return new OAuthClient(clientId, name, realm, Set.copyOf(redirectUris), enabled, secretHash);
    }

    /**
     * {@code realm 소문자-난수 12자}. 예: {@code portal-17kqqi85h2ks}.
     *
     * <p>접두어는 로그에서 어느 realm 의 앱인지 알아보라고 붙인다. 경로의 realm 과 같은 글자다.
     * 난수는 36^12 가지라 겹칠 일이 사실상 없고, 겹쳐도 저장 전에 이름이 비어 있는지 한 번 더 본다.
     * 값 자체는 비밀이 아니다. 주소창에 그대로 나간다.
     */
    private static String newClientId(Realm realm) {
        StringBuilder id = new StringBuilder(realm.name().toLowerCase()).append('-');
        for (int i = 0; i < ID_RANDOM_LENGTH; i++) {
            id.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }

    /** 이 시크릿을 쥔 앱으로 만든다. 원문은 해시해서 담고 버린다. */
    public OAuthClient withSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        return new OAuthClient(clientId, name, realm, redirectUris, enabled, hash(secret));
    }

    public boolean isConfidential() {
        return secretHash != null;
    }

    /** 저장할 때만 꺼낸다. 대조는 {@link #authenticates(String)} 로 한다. */
    public String secretHash() {
        return secretHash;
    }

    /**
     * 요청이 낸 시크릿이 이 앱의 것인가.
     *
     * <p>public client 는 항상 거짓이다. 시크릿이 없는 앱에 무언가를 냈다고 통과시키면, 시크릿을
     * 검사했다는 말이 무의미해진다.
     *
     * <p>비교는 걸리는 시간이 값에 따라 달라지지 않게 한다. 앞에서부터 맞는 만큼 오래 걸리면 그
     * 차이로 한 글자씩 맞춰갈 수 있다.
     */
    public boolean authenticates(String presentedSecret) {
        if (secretHash == null || presentedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                secretHash.getBytes(StandardCharsets.US_ASCII),
                hash(presentedSecret).getBytes(StandardCharsets.US_ASCII));
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

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
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

    /** base64url(sha256(secret)). 패딩 없이 적는다. */
    private static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 갖춰야 하는 알고리즘이다. 없으면 실행 환경이 깨진 것이다.
            throw new IllegalStateException(e);
        }
    }
}
