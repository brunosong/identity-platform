package com.brunosong.identityplatform.auth.service.application.identity.session;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.SessionRegistryPort;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SessionRegistryPort} 인메모리 구현 — 주체(realm+subjectId)당 현재 sid 하나를 맵으로 보관한다.
 * 단일 인스턴스 기준(재시작 시 전원 재로그인). 멀티 인스턴스/MSA 승급 시 Redis 어댑터로 교체한다.
 */
public class InMemorySessionRegistry implements SessionRegistryPort {

    private final ConcurrentHashMap<String, String> activeSessions = new ConcurrentHashMap<>();

    private static String key(Realm realm, String subjectId) {
        return realm.name() + ":" + subjectId;
    }

    @Override
    public String open(Realm realm, String subjectId) {
        String sid = UUID.randomUUID().toString();
        activeSessions.put(key(realm, subjectId), sid);
        return sid;
    }

    @Override
    public boolean isCurrent(Realm realm, String subjectId, String sid) {
        if (subjectId == null || sid == null) {
            return false;
        }
        return sid.equals(activeSessions.get(key(realm, subjectId)));
    }

    @Override
    public void close(Realm realm, String subjectId) {
        if (subjectId != null) {
            activeSessions.remove(key(realm, subjectId));
        }
    }
}
