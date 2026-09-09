package com.brunosong.identityplatform.auth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * auth 의 JWKS 를 받아다 {@code kid} 로 공개키를 찾아준다.
 *
 * <h2>realm 하나의 JWKS 만 본다</h2>
 * auth 는 realm 마다 다른 주소로 공개키를 내보낸다. 이 소스는 그중 하나만 가리키므로
 * <b>다른 realm 의 키는 아예 갖지 못한다.</b> 그런 토큰은 {@code kid} 를 찾지 못해 서명 검증에서
 * 죽는다 — 소비 서비스의 코드가 한 줄도 돌기 전에.
 *
 * <p>전에는 한 문서에 모든 realm 의 키가 함께 있었다. 그러면 어느 서비스든 모든 realm 의 토큰을
 * 검증할 수 있게 되고, realm 경계는 소비 서비스가 클레임을 확인해 주기를 바라는 것으로만 남는다 —
 * 한 곳에서 잊으면 그대로 뚫린다. 나누면 그 경계가 <b>설정</b>이 된다.
 * (Keycloak·Auth0·Okta·Cognito 가 모두 realm/테넌트마다 JWKS 를 나눈다.)
 *
 * <h2>키 교체</h2>
 * <b>모르는 kid 를 만나면 캐시 TTL 과 무관하게 다시 받아온다.</b> auth 가 새 키로 서명하기 시작하면
 * 소비 서비스는 처음 보는 kid 를 만나는데, TTL 만료를 기다렸다면 그 사이 모든 요청이 401 이 된다.
 *
 * <p>대신 <b>재조회에 최소 간격을 둔다</b>. 그러지 않으면 아무 문자열이나 kid 로 넣어 보내는 것만으로
 * auth 에 요청을 무한히 발생시킬 수 있다(증폭 공격). 간격 안의 실패는 그냥 실패다.
 *
 * <p>조회에 실패해도 이전 키를 버리지 않고, 실패를 밖으로 던지지도 않는다. auth 가 잠시 죽었다고
 * 이미 발급된 토큰까지 거부할 이유는 없다 — 검증에 필요한 것은 공개키뿐이고 그 값은 auth 의 상태와
 * 무관하다. 그리고 키를 끝내 못 구하면 그것은 <b>검증 실패(401)</b>이지 서버 오류(500)가 아니다.
 * 여기서 예외가 새어 나가면 auth 의 장애가 소비 서비스의 500 으로 번진다.
 */
public class JwksKeySource {

    private static final Logger log = LoggerFactory.getLogger(JwksKeySource.class);

    /** 같은 이유로 다시 받아오기까지 최소한 이만큼 기다린다. */
    private static final Duration MIN_REFETCH_INTERVAL = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String jwksUri;
    private final Duration cacheTtl;

    private final Map<String, PublicKey> keysByKid = new ConcurrentHashMap<>();
    /** 마지막으로 성공한 조회 — 캐시가 낡았는지 본다. */
    private final AtomicReference<Instant> lastFetchedAt = new AtomicReference<>(Instant.EPOCH);
    /** 마지막으로 시도한 조회 — 실패가 이어질 때 두드리는 간격을 지킨다. */
    private final AtomicReference<Instant> lastAttemptedAt = new AtomicReference<>(Instant.EPOCH);

    public JwksKeySource(RestClient restClient, String jwksUri, Duration cacheTtl) {
        if (jwksUri == null || jwksUri.isBlank()) {
            // 주소가 없으면 아무 토큰도 검증할 수 없다. 조용히 모든 요청을 401 로 만드는 것보다
            // 부팅에서 실패하는 편이 낫다.
            throw new IllegalStateException("auth.client.jwks-uri 가 필요합니다.");
        }
        this.restClient = restClient;
        this.jwksUri = jwksUri;
        this.cacheTtl = cacheTtl;
    }

    /** kid 에 해당하는 공개키. 캐시에 없거나 오래됐으면 한 번 다시 받아본다. 끝내 없으면 null. */
    public PublicKey find(String kid) {
        if (kid == null || kid.isBlank()) {
            return null;
        }
        PublicKey cached = keysByKid.get(kid);
        if (cached != null && !isStale()) {
            return cached;
        }
        if (canRefetch()) {
            refreshQuietly();
        }
        // 재조회에 실패했어도 갖고 있던 키로 검증을 시도한다(공개키는 auth 의 가용성과 무관하다).
        // 그 키도 없으면 null 이고, 호출자는 그것을 검증 실패로 다룬다.
        return keysByKid.get(kid);
    }

    /** 시작 시 미리 받아두고 싶을 때. 실패해도 던지지 않는다 — auth 보다 먼저 뜰 수 있어야 한다. */
    public void warmUp() {
        refreshQuietly();
    }

    /** 지금 쥐고 있는 키 개수. 진단용. */
    public int size() {
        return keysByKid.size();
    }

    private void refreshQuietly() {
        // 시도한 사실부터 남긴다. 실패가 이어질 때 요청마다 auth 를 두드리지 않기 위해서다 —
        // 성공했을 때만 기록하면 auth 가 죽어 있는 동안 모든 요청이 조회를 다시 시도한다.
        lastAttemptedAt.set(Instant.now());
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("JWKS 를 받아오지 못했습니다({}). 갖고 있는 키로 검증을 계속합니다: {}",
                    jwksUri, e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void refresh() {
        Map<String, Object> document = restClient.get().uri(jwksUri).retrieve().body(Map.class);
        if (document == null) {
            return;
        }
        List<Map<String, Object>> jwks = (List<Map<String, Object>>) document.get("keys");
        if (jwks == null) {
            return;
        }
        for (Map<String, Object> jwk : jwks) {
            String kid = (String) jwk.get("kid");
            PublicKey key = toPublicKey(jwk);
            if (kid != null && key != null) {
                keysByKid.put(kid, key);
            }
        }
        lastFetchedAt.set(Instant.now());
        // 사라진 kid 를 지우지 않는다. 교체 중에는 옛 kid 로 서명된 토큰이 아직 만료 전이다.
    }

    private boolean isStale() {
        return lastFetchedAt.get().plus(cacheTtl).isBefore(Instant.now());
    }

    private boolean canRefetch() {
        return lastAttemptedAt.get().plus(MIN_REFETCH_INTERVAL).isBefore(Instant.now());
    }

    private static PublicKey toPublicKey(Map<String, Object> jwk) {
        if (!"RSA".equals(jwk.get("kty"))) {
            return null;
        }
        try {
            BigInteger modulus = unsigned((String) jwk.get("n"));
            BigInteger exponent = unsigned((String) jwk.get("e"));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            // 못 읽는 키 하나 때문에 나머지까지 버리지 않는다.
            return null;
        }
    }

    /** JWK 의 수는 부호 없는 big-endian base64url 이다. 부호 있는 값으로 읽으면 음수가 될 수 있다. */
    private static BigInteger unsigned(String base64Url) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64Url));
    }
}
