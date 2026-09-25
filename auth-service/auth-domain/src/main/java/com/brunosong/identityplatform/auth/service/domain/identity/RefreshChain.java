package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * refresh 토큰의 계보. 로그인 하나에 계보 하나가 열리고, 그 안에서 살아 있는 토큰은 언제나 하나다.
 *
 * <h2>왜 이런 것이 필요한가</h2>
 * refresh 토큰은 서명된 JWT 라 그 자체로는 몇 번을 써도 유효하다. 한 번 새어 나가면 만료까지
 * 공격자와 사용자가 같은 토큰을 나란히 쓴다. 서버가 그 사실을 알 방법도 없다.
 *
 * <p>회전은 그 성질을 뒤집는다. 재발급할 때마다 새 토큰을 내주고 앞의 것을 죽이므로, 토큰 하나는
 * <b>한 번만</b> 쓸 수 있다. 그러려면 "이미 쓴 토큰인가" 를 서버가 기억해야 하고, 그 기억이 이것이다.
 *
 * <h2>재사용이 곧 탈취 신호다</h2>
 * 이미 회전된 토큰이 다시 오면 둘 중 하나다. 공격자가 훔친 토큰을 쓴 것이거나, 사용자가 쓴 뒤에
 * 공격자가 옛 토큰으로 따라온 것이다. 어느 쪽인지는 가릴 수 없다. 그래서 그 계보를 통째로 끊는다.
 * 진짜 사용자도 함께 로그아웃되지만, 둘 중 누가 진짜인지 모르는 채로 계보를 살려두는 것보다 낫다.
 *
 * <p>이 판단은 계보 안에서만 끝난다. 다른 기기의 로그인은 다른 계보라 영향을 받지 않는다.
 *
 * <h2>수명은 회전할 때마다 다시 붙는다</h2>
 * 쓰는 동안에는 로그인이 안 끊긴다. 대신 탈취된 계보도 조용히 회전되는 동안은 함께 연장된다.
 * 그것을 막으려면 로그인 시점부터의 절대 만료가 따로 필요하고, 그것은 아직 없다.
 */
@Getter
public class RefreshChain {

    /** 계보의 이름. 회전해도 바뀌지 않는다. 토큰의 {@code fid} 클레임으로 실려 나간다. */
    private final String familyId;

    private final Realm realm;
    private final String subjectId;

    /** 지금 살아 있는 토큰 하나. 토큰의 {@code jti} 와 대조한다. */
    private final String currentJti;

    private final Instant createdAt;
    private final Instant expiresAt;

    private RefreshChain(String familyId, Realm realm, String subjectId, String currentJti,
                         Instant createdAt, Instant expiresAt) {
        this.familyId = familyId;
        this.realm = realm;
        this.subjectId = subjectId;
        this.currentJti = currentJti;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** 로그인할 때 연다. 계보 이름과 첫 토큰의 이름이 여기서 나온다. */
    public static RefreshChain start(Realm realm, String subjectId, Instant now, Duration lifetime) {
        return new RefreshChain(newId(), realm, subjectId, newId(), now, now.plus(lifetime));
    }

    public static RefreshChain restore(String familyId, Realm realm, String subjectId,
                                       String currentJti, Instant createdAt, Instant expiresAt) {
        return new RefreshChain(familyId, realm, subjectId, currentJti, createdAt, expiresAt);
    }

    /** 회전. 계보는 그대로 두고 살아 있는 토큰만 갈아끼운다. */
    public RefreshChain rotate(Instant now, Duration lifetime) {
        return new RefreshChain(familyId, realm, subjectId, newId(), createdAt, now.plus(lifetime));
    }

    /** 낸 토큰이 이 계보의 현재 것인가. 아니면 이미 쓴 토큰이다. */
    public boolean isCurrent(String jti) {
        return currentJti.equals(jti);
    }

    /**
     * 이 계보가 그 realm 의 그 사람 것인가.
     *
     * <p>서명이 맞아도 대조한다. 토큰에 실린 계보 이름을 갈아끼워 남의 계보를 지목하는 길을 막는다.
     */
    public boolean belongsTo(Realm realm, String subjectId) {
        return this.realm == realm && this.subjectId.equals(subjectId);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
