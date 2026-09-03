package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
import java.util.Optional;

/**
 * URL 접근 규칙 쓰기 포트(SPI). 조회는 {@link UrlAccessQuery} 가 맡는다.
 *
 * <p>여기서도 읽는 메서드가 있지만 목적이 다르다 — 권한↔URL 매핑을 입력과 맞출 때 지금 무엇이
 * 붙어 있는지 알아야 지울 것과 더할 것을 가른다. 화면에 보여주려는 읽기가 아니라 쓰기의 일부다.
 */
public interface UrlAccessRepository {

    /** 수정 대상 애그리거트를 싣는다. */
    Optional<UrlAccess> findById(Long urlAccessId);

    /** 같은 (패턴, 메서드) 리소스 — 매핑을 붙일 대상을 찾거나 없으면 만들 때 쓴다. */
    List<UrlAccess> findByPatternAndMethod(Realm realm, String urlPattern, String httpMethod);

    /** 이 권한에 지금 붙어 있는 리소스들 — 동기화 대상을 가르는 근거. */
    List<UrlAccess> findByPermissionId(Long permissionId);

    /** 리소스 기본 필드 저장(패턴/메서드/정렬/설명/활성). 권한 매핑은 건드리지 않는다. */
    UrlAccess saveBasics(UrlAccess urlAccess);

    void deleteById(Long urlAccessId);

    /** 리소스에 권한 매핑 1건 추가(이미 있으면 무시). */
    void addPermission(Long urlAccessId, Long permissionId);

    /** 리소스에서 권한 매핑 1건 제거. */
    void removePermission(Long urlAccessId, Long permissionId);
}
