package com.brunosong.identityplatform.auth.client;

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
 * <p><b>모르는 kid 를 만나면 한 번 다시 받아온다.</b> 이것이 키 교체를 견디는 방식이다 — auth 가 새 키로
 * 서명하기 시작하면 소비 서비스는 처음 보는 kid 를 만나는데, 그때 JWKS 를 다시 읽으면 새 키를 얻는다.
 * 캐시가 만료되기를 기다렸다면 그 사이 모든 요청이 401 이 된다.
 *
 * <p>대신 <b>재조회에 최소 간격을 둔다</b>. 그러지 않으면 아무 문자열이나 kid 로 넣어 보내는 것만으로
 * auth 에 요청을 무한히 발생시킬 수 있다(증폭 공격). 간격 안의 실패는 그냥 실패다.
 *
 * <p>조회에 실패해도 이전 키를 버리지 않는다. auth 가 잠시 죽었다고 이미 발급된 토큰까지 거부할 이유는
 * 없다 — 검증에 필요한 것은 공개키뿐이고 그 값은 auth 의 상태와 무관하다.
 */
public class JwksKeySource {

    /** 같은 이유로 다시 받아오기까지 최소한 이만큼 기다린다. */
    private static final Duration MIN_REFETCH_INTERVAL = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String jwksUri;
    private final Duration cacheTtl;

    private final Map<String, PublicKey> keysByKid = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastFetchedAt = new AtomicReference<>(Instant.EPOCH);

    public JwksKeySource(RestClient restClient, String jwksUri, Duration cacheTtl) {
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
            refresh();
        }
        // 재조회에 실패했어도 갖고 있던 키로 검증을 시도한다(공개키는 auth 의 가용성과 무관하다).
        return keysByKid.get(kid);
    }

    /** 시작 시 미리 받아두고 싶을 때. 실패해도 던지지 않는다 — auth 보다 먼저 뜰 수 있어야 한다. */
    public void warmUp() {
        try {
            refresh();
        } catch (RuntimeException e) {
            // 첫 검증 때 다시 시도한다.
        }
    }

    public int size() {
        return keysByKid.size();
    }

    private boolean isStale() {
        return lastFetchedAt.get().plus(cacheTtl).isBefore(Instant.now());
    }

    private boolean canRefetch() {
        Instant last = lastFetchedAt.get();
        return last.plus(MIN_REFETCH_INTERVAL).isBefore(Instant.now());
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
