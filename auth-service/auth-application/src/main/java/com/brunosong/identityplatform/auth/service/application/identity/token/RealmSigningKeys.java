package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * realm 별 서명 키를 담는다. 한 프로세스가 두 realm 을 모두 담당하므로 키도 두 벌을 쥔다.
 *
 * <p>키를 고르는 기준은 {@code kid} 다. 검증하는 쪽은 토큰을 열어보기 전에 어느 공개키를 쓸지 알아야
 * 하는데(서명을 확인해야 내용을 믿을 수 있으므로), 그래서 kid 는 서명 대상 밖인 JWT 헤더에 실린다.
 * kid 는 realm 전체에서 유일해야 한다 — 겹치면 상대 realm 키로 검증이 통과한다.
 *
 * <p>kid 를 realm 이름이 아니라 별도 값으로 두는 이유는 키 교체다. 같은 realm 이 새 키로 넘어가는
 * 동안 옛 kid 로 서명된 토큰이 아직 살아 있으므로, 검증 쪽은 두 kid 를 동시에 알아야 한다.
 */
public final class RealmSigningKeys {

    /** 한 realm 의 현재 키. */
    public record RealmKey(String kid, PrivateKey privateKey, PublicKey publicKey) {
    }

    private final Map<Realm, RealmKey> byRealm;
    private final Map<String, PublicKey> verifyKeysByKid;

    public RealmSigningKeys(Map<Realm, RealmKey> byRealm) {
        if (byRealm == null || byRealm.isEmpty()) {
            throw new IllegalStateException("realm 별 토큰 서명 키가 설정되지 않았습니다(token.realms.*).");
        }
        this.byRealm = Collections.unmodifiableMap(new EnumMap<>(byRealm));

        Map<String, PublicKey> byKid = new HashMap<>();
        for (Map.Entry<Realm, RealmKey> entry : this.byRealm.entrySet()) {
            RealmKey key = entry.getValue();
            PublicKey clash = byKid.put(key.kid(), key.publicKey());
            if (clash != null) {
                // 두 realm 이 같은 kid 를 쓰면 검증 단계에서 realm 이 섞인다. 부팅에서 막는다.
                throw new IllegalStateException("realm 끼리 kid 가 겹칩니다: " + key.kid());
            }
        }
        this.verifyKeysByKid = Collections.unmodifiableMap(byKid);
    }

    /** 발급용 — 해당 realm 의 개인키와 kid. */
    public RealmKey of(Realm realm) {
        RealmKey key = byRealm.get(realm);
        if (key == null) {
            throw new IllegalStateException("이 realm 의 토큰 서명 키가 없습니다: " + realm);
        }
        return key;
    }

    /** 공개키 목록(kid → 공개키). JWKS 로 내보낼 때 쓴다. */
    public Map<String, PublicKey> verifyKeys() {
        return verifyKeysByKid;
    }
}
