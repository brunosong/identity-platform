package com.brunosong.identityplatform.auth.service.application.identity.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 단일 세션 검증/무효화 인바운드 포트 — 호스트 게이트웨이가 쓴다.
 *
 * <p>게이트웨이(토큰 검증 필터)는 요청마다 토큰의 sid 가 현재 세션과 같은지 {@link #isCurrent} 로 확인하고,
 * 로그아웃 시 {@link #invalidate} 로 세션을 지운다. 단일 세션 저장/발급은 auth 가 소유하며(SessionRegistryPort),
 * 호스트는 이 in-port 만 호출한다(auth-agnostic 유지). 단일 세션 미활성 호스트에서는 항상 통과/무동작한다.
 */
public interface SingleSessionUseCase {

    /** 단일 세션 미활성 호스트에서는 항상 true(다중 로그인 허용). */
    boolean isCurrent(Realm realm, String subjectId, String sid);

    /** 로그아웃 — 해당 주체의 세션 제거. 미활성 시 무동작. */
    void invalidate(Realm realm, String subjectId);
}
