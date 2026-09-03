package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.BumpAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GetAuthorizationRevisionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.AuthorizationRevisionQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.AuthorizationRevisionRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * realm 별 RBAC 리비전.
 *
 * <ul>
 *   <li>권한/역할 매핑이 바뀌면 {@link #bump(Realm)} → revision_no++</li>
 *   <li>토큰 발급 시 {@link #current(Realm)} 를 claim 에 싣는다</li>
 *   <li>토큰 검증 시 claim 과 current() 가 다르면 무효로 본다(재로그인 유도)</li>
 * </ul>
 *
 * <p>캐시는 realm 을 키로 잡고 bump 한 realm 만 비운다. 전부 비우면 한 realm 의 정책 변경이
 * 다른 realm 의 조회까지 DB 로 떨어뜨린다 — 정책상 서로 영향이 없다는 것과 어긋난다.
 *
 * <p>읽기는 요청마다 지나가고 쓰기는 운영자가 가끔 누르는 경로다. 두 포트를 갈라 두었으니
 * 읽기가 부담이 되면 그쪽만 캐시나 리플리카로 옮길 수 있다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationRevisionService implements GetAuthorizationRevisionUseCase,
        BumpAuthorizationRevisionUseCase {

    public static final String CACHE_NAME = "rbacRevision";

    private final AuthorizationRevisionQuery revisionQuery;
    private final AuthorizationRevisionRepository revisionRepository;

    @Override
    @Cacheable(value = CACHE_NAME, key = "#realm.name()")
    public long current(Realm realm) {
        return revisionQuery.current(realm);
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#realm.name()")
    public void bump(Realm realm) {
        revisionRepository.bump(realm);
        log.info("RBAC revision bumped: realm={}", realm);
    }
}
