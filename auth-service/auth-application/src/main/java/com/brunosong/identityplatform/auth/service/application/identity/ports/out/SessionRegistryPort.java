package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 단일 세션("한 곳에서 한 명") 저장 드리븐 포트. 주체(realm+subjectId)당 현재 유효한 세션 ID(sid) 하나만
 * 보관한다. 새 로그인 시 sid 가 갱신되어 기존 토큰(다른 기기/브라우저)은 무효화된다.
 *
 * <p>토큰 발급기(RbacJwtTokenIssuer)가 발급 시 {@link #open} 으로 sid 를 받아 토큰에 싣고, 게이트웨이가
 * 요청마다 {@link #isCurrent} 로 대조한다. 이 포트를 제공하는 호스트에서만 단일 세션이 시행된다
 * (제공하지 않으면 발급기가 sid 를 넣지 않고 다중 로그인을 허용).
 *
 * <p>지금은 인메모리 구현(단일 인스턴스). 멀티 인스턴스/MSA 승급 시 Redis 등 공유 저장 어댑터로 교체한다.
 */
public interface SessionRegistryPort {

    /** 새 세션 ID 발급 — 해당 주체의 기존 세션은 즉시 무효화된다. */
    String open(Realm realm, String subjectId);

    /** 토큰의 세션 ID 가 현재 유효한 세션과 일치하는지 확인. */
    boolean isCurrent(Realm realm, String subjectId, String sid);

    /** 로그아웃 — 해당 주체의 세션 제거. */
    void close(Realm realm, String subjectId);
}
